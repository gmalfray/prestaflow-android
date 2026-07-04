package com.rebuildit.prestaflow.localization

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Garde-fou i18n : le défaut (`values/`, FR) et chaque langue cible doivent exposer exactement
 * les mêmes clés (0 MissingTranslation) et les mêmes placeholders `%n$s`/`%n$d` dans le même ordre.
 */
class LocalizationParityTest {
    // Langues cibles (v0.40.0) : EN, ES, DE, IT, PT, NL. Le défaut (values/) est le FR.
    private val targetLocales = listOf("en", "es", "de", "it", "pt", "nl")

    @Test
    fun `all target locales have the same string keys as the default (fr) locale`() {
        val default = loadStrings(resolveResource("src/main/res/values/strings.xml"))
        targetLocales.forEach { locale ->
            val translated = loadStrings(resolveResource("src/main/res/values-$locale/strings.xml"))
            assertEquals(
                "values-$locale/strings.xml should contain the same string keys as the default (fr) locale",
                default.keys,
                translated.keys,
            )
        }
    }

    @Test
    fun `all target locales preserve the default locale placeholders`() {
        val placeholderRegex = Regex("""%(\d+)\$[sd]""")
        val default = loadStrings(resolveResource("src/main/res/values/strings.xml"))
        targetLocales.forEach { locale ->
            val translated = loadStrings(resolveResource("src/main/res/values-$locale/strings.xml"))
            default.forEach { (key, defaultValue) ->
                val defaultPlaceholders = placeholderRegex.findAll(defaultValue).map { it.groupValues[1] }.sorted().toList()
                val translatedValue = translated[key].orEmpty()
                val translatedPlaceholders = placeholderRegex.findAll(translatedValue).map { it.groupValues[1] }.sorted().toList()
                assertEquals(
                    "values-$locale/strings.xml key '$key' should keep the same placeholders as the default locale",
                    defaultPlaceholders,
                    translatedPlaceholders,
                )
            }
        }
    }

    private fun loadStrings(file: File): Map<String, String> {
        val builder =
            DocumentBuilderFactory.newInstance().apply {
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

    private fun resolveResource(relativePath: String): File {
        val candidates =
            listOf(
                File(relativePath),
                File("app/$relativePath"),
                File("../$relativePath"),
                File("../app/$relativePath"),
            ).map { it.absoluteFile.normalize() }

        return candidates.firstOrNull { it.exists() }
            ?: error("Unable to locate resource file for path '$relativePath' from ${File(".").absolutePath}")
    }
}
