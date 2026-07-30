package com.addiyon.keyboard.suggestion

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import com.addiyon.keyboard.SafeLog
import com.addiyon.keyboard.telemetry.NonFatalCategory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

internal class SQLiteLanguageStore(
    context: Context,
    val assetName: String,
    val isLowRam: Boolean,
    private val onOutOfMemory: () -> Unit = {},
    private val usableSpace: (File) -> Long = { it.usableSpace },
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
        var notifyImmediately = false
        synchronized(lock) {
            if (status == Status.READY && database?.isOpen == true) {
                notifyImmediately = true
            } else if (
                status == Status.FAILED &&
                (failureCount >= MAX_FAILURES || SystemClock.elapsedRealtime() < retryAfterElapsed)
            ) {
                notifyImmediately = true
            } else {
                if (callbacks.size < MAX_CALLBACKS) {
                    callbacks.add(onComplete)
                } else {
                    notifyImmediately = true
                }
                if (status != Status.INSTALLING) {
                    status = Status.INSTALLING
                    generation += 1
                    startGeneration = generation
                }
            }
        }
        if (notifyImmediately) postCallback(onComplete)
        val requestedGeneration = startGeneration ?: return
        ioExecutor.execute {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            } catch (_: Throwable) {
            }
            val opened = try {
                SuggestionTrace.section("database_install_open") {
                    installAndOpen()
                }
            } catch (oom: OutOfMemoryError) {
                SafeLog.e(oom, "SQLiteLanguageStore load OOM", NonFatalCategory.DATABASE)
                onOutOfMemory()
                null
            } catch (t: Throwable) {
                SafeLog.e(t, "SQLiteLanguageStore load", NonFatalCategory.DATABASE)
                null
            }
            postToMain main@{
                val notify: List<() -> Unit>
                synchronized(lock) {
                    if (requestedGeneration != generation) {
                        try {
                            opened?.close()
                        } catch (_: Throwable) {
                        }
                        return@main
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
                        SafeLog.e(t, "SQLiteLanguageStore callback", NonFatalCategory.DATABASE)
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
            SafeLog.e(t, "SQLiteLanguageStore release", NonFatalCategory.DATABASE)
        }
    }

    fun handleQueryFailure(t: Throwable) {
        if (!DatabaseFailurePolicy.shouldRecover(t)) return
        val toClose: SQLiteDatabase?
        synchronized(lock) {
            generation += 1
            callbacks.clear()
            toClose = database
            database = null
            status = Status.FAILED
            failureCount = (failureCount + 1).coerceAtMost(MAX_FAILURES)
            retryAfterElapsed = SystemClock.elapsedRealtime() + RETRY_BASE_MS
        }
        try {
            toClose?.close()
        } catch (closeFailure: Throwable) {
            SafeLog.e(
                closeFailure,
                "SQLiteLanguageStore failure close",
                NonFatalCategory.DATABASE
            )
        }
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
        restoreInterruptedSwap(finalFile, checksumFile, metadata, asset)

        var swap: InstalledSwap? = null
        if (!isReusable(finalFile, checksumFile, metadata, asset)) {
            require(usableSpace(storeDir) >= asset.length + MIN_FREE_SPACE_AFTER_COPY)
            swap = installAtomically(finalFile, checksumFile, metadata, asset)
        }

        var opened: SQLiteDatabase? = null
        return try {
            opened = openReadOnly(finalFile)
            validateDatabase(opened, metadata)
            configure(opened)
            swap?.discard()
            cleanupOldFiles(finalFile)
            opened
        } catch (t: Throwable) {
            try {
                opened?.close()
            } catch (_: Throwable) {
            }
            swap?.restore()
            throw t
        }
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
    ): InstalledSwap {
        val temporary = File(
            finalFile.parentFile,
            "${finalFile.name}.tmp-${Process.myPid()}-${System.nanoTime()}",
        )
        val temporaryChecksum = File(
            checksumFile.parentFile,
            "${checksumFile.name}.tmp-${Process.myPid()}-${System.nanoTime()}",
        )
        val backup = File(finalFile.parentFile, "${finalFile.name}.previous")
        val backupChecksum = File(checksumFile.parentFile, "${checksumFile.name}.previous")
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
            writeSynced(temporaryChecksum, asset.sha256)
            backup.delete()
            backupChecksum.delete()
            val swap = InstalledSwap(finalFile, checksumFile, backup, backupChecksum)
            try {
                swap.backUpCurrent()
                require(temporary.renameTo(finalFile))
                swap.databaseInstalled = true
                require(temporaryChecksum.renameTo(checksumFile))
                swap.checksumInstalled = true
                fsyncDirectory(storeDir)
                return swap
            } catch (t: Throwable) {
                swap.restore()
                throw t
            }
        } finally {
            temporary.delete()
            temporaryChecksum.delete()
        }
    }

    private fun restoreInterruptedSwap(
        finalFile: File,
        checksumFile: File,
        metadata: DictionaryAssetMetadata,
        asset: DictionaryAssetMetadata.Asset,
    ) {
        val backup = File(finalFile.parentFile, "${finalFile.name}.previous")
        val backupChecksum = File(checksumFile.parentFile, "${checksumFile.name}.previous")
        if (!backup.exists() && !backupChecksum.exists()) return
        if (isReusable(finalFile, checksumFile, metadata, asset)) {
            backup.delete()
            backupChecksum.delete()
            return
        }
        if (isReusable(finalFile, backupChecksum, metadata, asset)) {
            checksumFile.delete()
            require(backupChecksum.renameTo(checksumFile))
            backup.delete()
            fsyncDirectory(storeDir)
            return
        }
        if (isReusable(backup, checksumFile, metadata, asset)) {
            finalFile.delete()
            require(backup.renameTo(finalFile))
            backupChecksum.delete()
            fsyncDirectory(storeDir)
            return
        }
        if (isReusable(backup, backupChecksum, metadata, asset)) {
            finalFile.delete()
            checksumFile.delete()
            require(backup.renameTo(finalFile))
            require(backupChecksum.renameTo(checksumFile))
            fsyncDirectory(storeDir)
        }
    }

    private fun writeSynced(file: File, value: String) {
        FileOutputStream(file).use { output ->
            output.write(value.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }

    private fun fsyncDirectory(directory: File) {
        val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    private fun postCallback(callback: () -> Unit) {
        postToMain {
            try {
                callback()
            } catch (t: Throwable) {
                SafeLog.e(t, "SQLiteLanguageStore callback", NonFatalCategory.DATABASE)
            }
        }
    }

    private fun postToMain(block: () -> Unit) {
        val runnable = Runnable(block)
        try {
            if (!mainHandler.post(runnable)) runnable.run()
        } catch (t: Throwable) {
            SafeLog.e(t, "SQLiteLanguageStore post", NonFatalCategory.DATABASE)
            runnable.run()
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

    private inner class InstalledSwap(
        private val finalFile: File,
        private val checksumFile: File,
        private val backup: File,
        private val backupChecksum: File
    ) {
        private var databaseBackedUp = false
        private var checksumBackedUp = false
        var databaseInstalled = false
        var checksumInstalled = false

        fun backUpCurrent() {
            if (finalFile.exists()) {
                require(finalFile.renameTo(backup))
                databaseBackedUp = true
            }
            if (checksumFile.exists()) {
                require(checksumFile.renameTo(backupChecksum))
                checksumBackedUp = true
            }
        }

        fun restore() {
            if (databaseInstalled) finalFile.delete()
            if (checksumInstalled) checksumFile.delete()
            if (databaseBackedUp) {
                finalFile.delete()
                require(backup.renameTo(finalFile))
            }
            if (checksumBackedUp) {
                checksumFile.delete()
                require(backupChecksum.renameTo(checksumFile))
            }
            fsyncDirectory(storeDir)
        }

        fun discard() {
            backup.delete()
            backupChecksum.delete()
            fsyncDirectory(storeDir)
        }
    }

    companion object {
        private const val METADATA_ASSET = "dictionary_manifest.properties"
        private const val CONTENT_ID_LENGTH = 16
        private const val RETRY_BASE_MS = 5_000L
        private const val MAX_FAILURES = 5
        private const val MAX_CALLBACKS = 32
        private const val MIN_FREE_SPACE_AFTER_COPY = 16L * 1024L * 1024L
        private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "AddiyonDictionaryIo")
        }
    }
}
