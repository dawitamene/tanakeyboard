package com.addiyon.keyboard

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeMetadataContractTest {
    @Test
    fun systemExposesOnlyImplicitAmharicSubtype() {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(resourceFile("xml/method.xml"))
        val subtypes = document.getElementsByTagName("subtype")

        assertEquals(1, subtypes.length)
        val subtype = subtypes.item(0)
        val android = "http://schemas.android.com/apk/res/android"
        assertEquals("am-ET", subtype.attributes.getNamedItemNS(android, "languageTag").nodeValue)
        assertEquals(
            "@string/subtype_amharic",
            subtype.attributes.getNamedItemNS(android, "label").nodeValue
        )
        assertEquals(
            "true",
            subtype.attributes.getNamedItemNS(
                android,
                "overridesImplicitlyEnabledSubtype"
            ).nodeValue
        )
        assertEquals(
            "true",
            subtype.attributes.getNamedItemNS(android, "isAsciiCapable").nodeValue
        )
    }

    @Test
    fun frameworkAmharicLabelIsAlwaysWrittenInAmharic() {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(resourceFile("values/strings.xml"))
        val strings = document.getElementsByTagName("string")
        val matches = (0 until strings.length)
            .map(strings::item)
            .filter { it.attributes.getNamedItem("name").nodeValue == "subtype_amharic" }

        assertEquals(1, matches.size)
        assertEquals("አማርኛ", matches.single().textContent)
        assertEquals("false", matches.single().attributes.getNamedItem("translatable").nodeValue)
        assertFalse(resourceFile("values/strings.xml").readText().contains("subtype_english"))
    }

    private fun resourceFile(relativePath: String): File =
        listOf(
            File("src/main/res/$relativePath"),
            File("app/src/main/res/$relativePath")
        ).first(File::exists)
}
