package com.addiyon.keyboard.suggestion

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.addiyon.keyboard.SafeLog
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

internal class SQLiteLanguageStore(
    context: Context,
    val assetName: String,
    val isLowRam: Boolean,
) {
    private enum class Status {
        CLOSED,
        INSTALLING,
        READY,
        FAILED,
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val callbacks = ArrayList<() -> Unit>()
    private val storeDir = File(appContext.noBackupFilesDir, "dictionaries")

    @Volatile
    private var status = Status.CLOSED

    @Volatile
    private var database: SQLiteDatabase? = null

    private var generation = 0L
    private var failureCount = 0
    private var retryAfterElapsed = 0L

    val isReady: Boolean
        get() = status == Status.READY && database?.isOpen == true

    val isLoading: Boolean
        get() = status == Status.INSTALLING

    fun databaseOrNull(): SQLiteDatabase? =
        database?.takeIf { status == Status.READY && it.isOpen }

    fun loadAsync(onComplete: () -> Unit) {
        var startGeneration: Long? = null
        synchronized(lock) {
            if (status == Status.READY && database?.isOpen == true) {
                mainHandler.post(onComplete)
                return
            }
            if (status == Status.FAILED && SystemClock.elapsedRealtime() < retryAfterElapsed) {
                mainHandler.post(onComplete)
                return
            }
            callbacks.add(onComplete)
            if (status != Status.INSTALLING) {
                status = Status.INSTALLING
                generation += 1
                startGeneration = generation
            }
        }
        val requestedGeneration = startGeneration ?: return
        ioExecutor.execute {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            } catch (_: Throwable) {
            }
            val opened = try {
                installAndOpen()
            } catch (oom: OutOfMemoryError) {
                SafeLog.e(oom, "SQLiteLanguageStore load OOM")
                null
            } catch (t: Throwable) {
                SafeLog.e(t, "SQLiteLanguageStore load")
                null
            }
            mainHandler.post {
                val notify: List<() -> Unit>
                synchronized(lock) {
                    if (requestedGeneration != generation) {
                        try {
                            opened?.close()
                        } catch (_: Throwable) {
                        }
                        return@post
                    }
                    database = opened
                    status = if (opened != null) Status.READY else Status.FAILED
                    if (opened != null) {
                        failureCount = 0
                        retryAfterElapsed = 0L
                    } else {
                        failureCount += 1
                        retryAfterElapsed = SystemClock.elapsedRealtime() +
                            (RETRY_BASE_MS shl (failureCount - 1).coerceAtMost(4))
                    }
                    notify = callbacks.toList()
                    callbacks.clear()
                }
                notify.forEach { callback ->
                    try {
                        callback()
                    } catch (t: Throwable) {
                        SafeLog.e(t, "SQLiteLanguageStore callback")
                    }
                }
            }
        }
    }

    fun release() {
        val toClose: SQLiteDatabase?
        synchronized(lock) {
            generation += 1
            callbacks.clear()
            toClose = database
            database = null
            status = Status.CLOSED
        }
        try {
            toClose?.close()
        } catch (t: Throwable) {
            SafeLog.e(t, "SQLiteLanguageStore release")
        }
    }

    fun handleQueryFailure(t: Throwable) {
        if (t !is SQLiteDatabaseCorruptException) return
        release()
        databaseFileOrNull()?.delete()
        checksumFileOrNull()?.delete()
    }

    private fun installAndOpen(): SQLiteDatabase {
        val metadata = appContext.assets.open(METADATA_ASSET).use(DictionaryAssetMetadata::read)
        val asset = metadata.asset(assetName)
        storeDir.mkdirs()
        require(storeDir.isDirectory)
        val contentId = asset.sha256.take(CONTENT_ID_LENGTH)
        val finalFile = File(
            storeDir,
            "${assetName.removeSuffix(".db")}-v${metadata.schemaVersion}-$contentId.db",
        )
        val checksumFile = File(finalFile.parentFile, "${finalFile.name}.sha256")

        if (!isReusable(finalFile, checksumFile, metadata, asset)) {
            require(storeDir.usableSpace >= asset.length + MIN_FREE_SPACE_AFTER_COPY)
            finalFile.delete()
            checksumFile.delete()
            installAtomically(finalFile, checksumFile, metadata, asset)
        }

        val opened = openReadOnly(finalFile)
        validateDatabase(opened, metadata)
        configure(opened)
        cleanupOldFiles(finalFile)
        return opened
    }

    private fun isReusable(
        file: File,
        checksumFile: File,
        metadata: DictionaryAssetMetadata,
        asset: DictionaryAssetMetadata.Asset,
    ): Boolean {
        if (!file.isFile || file.length() != asset.length) return false
        if (!checksumFile.isFile || checksumFile.readText().trim() != asset.sha256) return false
        val opened = try {
            openReadOnly(file)
        } catch (_: Throwable) {
            return false
        }
        return try {
            validateDatabase(opened, metadata)
            true
        } catch (_: Throwable) {
            false
        } finally {
            try {
                opened.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun installAtomically(
        finalFile: File,
        checksumFile: File,
        metadata: DictionaryAssetMetadata,
        asset: DictionaryAssetMetadata.Asset,
    ) {
        val temporary = File(
            finalFile.parentFile,
            "${finalFile.name}.tmp-${Process.myPid()}-${System.nanoTime()}",
        )
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            appContext.assets.open(assetName).use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        copied += read
                    }
                    output.fd.sync()
                }
            }
            val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
            require(copied == asset.length)
            require(actualDigest == asset.sha256)
            val opened = openReadOnly(temporary)
            try {
                validateDatabase(opened, metadata)
                opened.rawQuery("PRAGMA quick_check(1)", null).use { cursor ->
                    require(cursor.moveToFirst() && cursor.getString(0) == "ok")
                }
            } finally {
                opened.close()
            }
            require(temporary.renameTo(finalFile))
            checksumFile.writeText(asset.sha256)
        } finally {
            temporary.delete()
        }
    }

    private fun openReadOnly(file: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            DatabaseErrorHandler { corrupt ->
                SafeLog.w("Corrupt dictionary database ${corrupt.path}")
            },
        )

    private fun validateDatabase(
        db: SQLiteDatabase,
        metadata: DictionaryAssetMetadata,
    ) {
        require(pragmaInt(db, "user_version") == metadata.schemaVersion)
        require(pragmaInt(db, "application_id") == metadata.applicationId)
        val required = setOf("words", "prefix_top", "vocab", "bigrams", "trigrams")
        val found = HashSet<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('words','prefix_top','vocab','bigrams','trigrams')",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) found.add(cursor.getString(0))
        }
        require(found == required)
    }

    private fun pragmaInt(db: SQLiteDatabase, name: String): Int =
        db.rawQuery("PRAGMA $name", null).use { cursor ->
            require(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun configure(db: SQLiteDatabase) {
        val cacheKb = if (isLowRam) 512 else 1536
        db.rawQuery("PRAGMA query_only=ON", null).use { it.moveToFirst() }
        db.rawQuery("PRAGMA cache_size=-$cacheKb", null).use { it.moveToFirst() }
    }

    private fun cleanupOldFiles(active: File) {
        val activeChecksum = File(active.parentFile, "${active.name}.sha256")
        storeDir.listFiles()?.forEach { file ->
            if (file != active &&
                file != activeChecksum &&
                file.name.startsWith(assetName.removeSuffix(".db"))
            ) {
                file.delete()
            }
        }
        File(appContext.cacheDir, "dict-${assetName.replace('/', '_')}.db").delete()
        File(appContext.cacheDir, "ngram-${assetName.replace('/', '_')}.db").delete()
    }

    private fun databaseFileOrNull(): File? =
        storeDir.listFiles()?.firstOrNull {
            it.name.startsWith(assetName.removeSuffix(".db")) && it.extension == "db"
        }

    private fun checksumFileOrNull(): File? =
        databaseFileOrNull()?.let { File(it.parentFile, "${it.name}.sha256") }

    companion object {
        private const val METADATA_ASSET = "dictionary_manifest.properties"
        private const val CONTENT_ID_LENGTH = 16
        private const val RETRY_BASE_MS = 5_000L
        private const val MIN_FREE_SPACE_AFTER_COPY = 16L * 1024L * 1024L
        private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "AddiyonDictionaryIo")
        }
    }
}
