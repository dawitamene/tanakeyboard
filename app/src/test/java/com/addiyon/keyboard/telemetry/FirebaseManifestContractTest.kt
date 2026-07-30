package com.addiyon.keyboard.telemetry

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseManifestContractTest {
    @Test
    fun manifestDefaultsCollectionOffAndRemovesAdvertisingPermissions() {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(resourceFile("AndroidManifest.xml"))
        val android = "http://schemas.android.com/apk/res/android"
        val tools = "http://schemas.android.com/tools"
        val permissions = document.getElementsByTagName("uses-permission")
        val permissionNodes = (0 until permissions.length).map(permissions::item)
        val names = permissionNodes.map {
            it.attributes.getNamedItemNS(android, "name").nodeValue
        }.toSet()

        assertTrue("android.permission.INTERNET" in names)
        val features = document.getElementsByTagName("uses-feature")
        val microphoneFeature = (0 until features.length)
            .map(features::item)
            .single {
                it.attributes.getNamedItemNS(android, "name").nodeValue ==
                    "android.hardware.microphone"
            }
        assertEquals(
            "false",
            microphoneFeature.attributes.getNamedItemNS(android, "required").nodeValue
        )
        setOf(
            "com.google.android.gms.permission.AD_ID",
            "android.permission.ACCESS_ADSERVICES_AD_ID",
            "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
            "android.permission.ACCESS_ADSERVICES_TOPICS"
        ).forEach { denied ->
            val node = permissionNodes.single {
                it.attributes.getNamedItemNS(android, "name").nodeValue == denied
            }
            assertEquals("remove", node.attributes.getNamedItemNS(tools, "node").nodeValue)
        }

        val metadata = document.getElementsByTagName("meta-data")
        val values = (0 until metadata.length)
            .map(metadata::item)
            .associate {
                it.attributes.getNamedItemNS(android, "name").nodeValue to
                    it.attributes.getNamedItemNS(android, "value")?.nodeValue
            }
        assertEquals("false", values["firebase_analytics_collection_enabled"])
        assertEquals("false", values["firebase_crashlytics_collection_enabled"])
        assertEquals("false", values["google_analytics_adid_collection_enabled"])
        assertEquals(
            "false",
            values["google_analytics_default_allow_ad_personalization_signals"]
        )
    }

    @Test
    fun consentPreferencesAreExcludedFromBackupAndTransfer() {
        val backup = resourceFile("res/xml/backup_rules.xml").readText()
        val extraction = resourceFile("res/xml/data_extraction_rules.xml").readText()

        assertBackupAllowlist(backup, expectedCopies = 1)
        assertBackupAllowlist(extraction, expectedCopies = 2)
    }

    private fun assertBackupAllowlist(content: String, expectedCopies: Int) {
        assertEquals(expectedCopies, Regex(
            "<include domain=\"sharedpref\" path=\"addiyon_keyboard_prefs.xml\""
        ).findAll(content).count())
        assertEquals(expectedCopies, Regex(
            "<include domain=\"sharedpref\" path=\"addiyon_language_prefs.xml\""
        ).findAll(content).count())
        assertTrue(!content.contains("addiyon_telemetry_prefs.xml"))
    }

    private fun resourceFile(relativePath: String): File =
        listOf(
            File("src/main/$relativePath"),
            File("app/src/main/$relativePath")
        ).first(File::exists)
}
