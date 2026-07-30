package com.addiyon.keyboard.suggestion

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SQLiteLanguageStoreInstrumentedTest {
    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var testRoot: File
    private lateinit var context: Context

    @Before
    fun createIsolatedStoreDirectory() {
        testRoot = File(targetContext.cacheDir, "sqlite-store-contract-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        context = IsolatedStoreContext(targetContext, File(testRoot, "no-backup"))
    }

    @After
    fun removeIsolatedStoreDirectory() {
        testRoot.deleteRecursively()
    }

    @Test
    fun concurrentCallbacksShareOneInstallAndReleaseReloadsTheStore() {
        val store = newStore()
        val callbacks = AtomicInteger()
        val ready = CountDownLatch(CALLBACK_COUNT)

        repeat(CALLBACK_COUNT) {
            store.loadAsync {
                callbacks.incrementAndGet()
                ready.countDown()
            }
        }

        assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(CALLBACK_COUNT, callbacks.get())
        assertTrue(store.isReady)
        assertEquals(1, store.databaseOrNull()?.rawQuery("SELECT 1", null)?.use {
            assertTrue(it.moveToFirst())
            it.getInt(0)
        })

        store.release()
        assertFalse(store.isReady)
        val reloaded = CountDownLatch(1)
        store.loadAsync(reloaded::countDown)
        assertTrue(reloaded.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertTrue(store.isReady)
        store.release()
    }

    @Test
    fun badChecksumIsAtomicallyReinstalledAndInterruptedBackupIsRestored() {
        val metadata = targetContext.assets.open(METADATA_ASSET)
            .use(DictionaryAssetMetadata::read)
        val asset = metadata.asset(ASSET_NAME)
        val store = newStore()
        awaitLoad(store)
        store.release()

        val database = installedDatabase(metadata.schemaVersion, asset.sha256)
        val checksum = File(database.parentFile, "${database.name}.sha256")
        assertTrue(database.isFile)
        assertTrue(checksum.isFile)
        checksum.writeText("invalid")

        val repaired = newStore()
        awaitLoad(repaired)
        assertTrue(repaired.isReady)
        repaired.release()
        assertEquals(asset.sha256, checksum.readText().trim())

        val backup = File(database.parentFile, "${database.name}.previous")
        val backupChecksum = File(checksum.parentFile, "${checksum.name}.previous")
        assertTrue(database.renameTo(backup))
        assertTrue(checksum.renameTo(backupChecksum))

        val restored = newStore()
        awaitLoad(restored)
        assertTrue(restored.isReady)
        restored.release()
        assertTrue(database.isFile)
        assertTrue(checksum.isFile)
        assertFalse(backup.exists())
        assertFalse(backupChecksum.exists())
    }

    @Test
    fun insufficientSpaceFailsTerminallyWithoutPublishingADatabase() {
        val store = SQLiteLanguageStore(
            context = context,
            assetName = ASSET_NAME,
            isLowRam = true,
            usableSpace = { 0L }
        )

        awaitLoad(store)

        assertFalse(store.isLoading)
        assertFalse(store.isReady)
        assertEquals(null, store.databaseOrNull())
        store.release()
    }

    private fun newStore(): SQLiteLanguageStore =
        SQLiteLanguageStore(
            context = context,
            assetName = ASSET_NAME,
            isLowRam = true
        )

    private fun awaitLoad(store: SQLiteLanguageStore) {
        val completed = CountDownLatch(1)
        store.loadAsync(completed::countDown)
        assertTrue(completed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
    }

    private fun installedDatabase(schemaVersion: Int, sha256: String): File =
        File(
            File(context.noBackupFilesDir, "dictionaries"),
            "english-v$schemaVersion-${sha256.take(CONTENT_ID_LENGTH)}.db"
        )

    private class IsolatedStoreContext(
        base: Context,
        private val isolatedNoBackupDir: File
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getNoBackupFilesDir(): File {
            isolatedNoBackupDir.mkdirs()
            return isolatedNoBackupDir
        }
    }

    private companion object {
        const val ASSET_NAME = "english.db"
        const val METADATA_ASSET = "dictionary_manifest.properties"
        const val CONTENT_ID_LENGTH = 16
        const val CALLBACK_COUNT = 8
        const val TIMEOUT_SECONDS = 45L
    }
}
