package com.rebuildit.prestaflow.localization

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class LocalizationParityTest {

    @Test
    fun `english and french strings have matching keys`() {
        val projectDir = File(".").absoluteFile
        val english = loadStrings(File(projectDir, "app/src/main/res/values/strings.xml"))
        val french = loadStrings(File(projectDir, "app/src/main/res/values-fr/strings.xml"))
        assertEquals(
            english.keys,
            french.keys,
            "French translation file should contain the same string keys as the default locale"
        )
    }

    private fun loadStrings(file: File): Map<String, String> {
        val builder = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder()
        val doc = builder.parse(file)
        val resources = doc.documentElement
        val map = mutableMapOf<String, String>()
        val children = resources.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == "string") {
                val name = node.getAttribute("name")
                map[name] = node.textContent
            }
        }
        return map
    }
}
