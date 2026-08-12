package com.addiyon.keyboard

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeMetadataContractTest {
    private val android = "http://schemas.android.com/apk/res/android"

    @Test
    fun addiyonUsesBilingualImeNameWithoutSystemInputModes() {
        val subtypes = subtypeAttributes("apps/addiyon")

        assertTrue(subtypes.isEmpty())
        assertEquals(
            "Addiyon Keyboard - አዲዮን ኪቦርድ",
            stringResource("apps/addiyon", "ime_name")
        )
        val method = projectRoot.resolve("apps/addiyon/src/main/res/xml/method.xml").readText()
        assertFalse(method.contains("<subtype"))
        assertFalse(method.contains("am-ET"))
        assertFalse(method.contains("en-US"))
        assertFalse(method.contains("om-ET"))
    }

    @Test
    fun textRevampExposesOnlyEnglishSubtype() {
        val subtypes = subtypeAttributes("apps/textrevamp")

        assertEquals(listOf("en-US"), subtypes.map { it.languageTag })
        assertEquals(listOf("@string/subtype_english"), subtypes.map { it.label })
        assertTrue(subtypes.single().isAsciiCapable)
        val method = projectRoot.resolve("apps/textrevamp/src/main/res/xml/method.xml").readText()
        assertFalse(method.contains("am-ET"))
        assertFalse(method.contains("om-ET"))
    }

    private fun subtypeAttributes(module: String): List<SubtypeAttributes> {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(projectRoot.resolve("$module/src/main/res/xml/method.xml"))
        val nodes = document.getElementsByTagName("subtype")
        return (0 until nodes.length).map { index ->
            val attributes = nodes.item(index).attributes
            SubtypeAttributes(
                languageTag = attributes.getNamedItemNS(android, "languageTag").nodeValue,
                label = attributes.getNamedItemNS(android, "label").nodeValue,
                isAsciiCapable = attributes.getNamedItemNS(android, "isAsciiCapable").nodeValue == "true"
            )
        }
    }

    private fun stringResource(module: String, name: String): String {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(projectRoot.resolve("$module/src/main/res/values/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length)
            .map(nodes::item)
            .single { it.attributes.getNamedItem("name").nodeValue == name }
            .textContent
    }

    private val projectRoot: File = generateSequence(
        File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
    ) { it.parentFile }.first { it.resolve("settings.gradle.kts").isFile }

    private data class SubtypeAttributes(
        val languageTag: String,
        val label: String,
        val isAsciiCapable: Boolean
    )
}
