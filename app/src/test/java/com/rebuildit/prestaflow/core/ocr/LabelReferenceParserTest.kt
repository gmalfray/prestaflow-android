package com.rebuildit.prestaflow.core.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires JVM de la logique PURE de [LabelReferenceParser] (secours OCR du réappro stock,
 * cf. [com.rebuildit.prestaflow.ui.products.StockReplenishViewModel]) — aucune dépendance
 * Android/ML Kit, isolée du framework OCR.
 */
class LabelReferenceParserTest {
    @Test
    fun `une etiquette typique extrait les references en ignorant le bruit`() {
        val ocrText =
            """
            RICORUMI049
            RICORUMINILLI001
            LOT: BA1234
            FABRE
            PARTIE 2
            FAB 07/2026
            049
            """.trimIndent()

        val candidates = LabelReferenceParser.extractReferenceCandidates(ocrText)

        assertEquals(listOf("RICORUMI049", "RICORUMINILLI001", "049"), candidates)
    }

    @Test
    fun `un texte vide ou blanc ne renvoie aucun candidat`() {
        assertTrue(LabelReferenceParser.extractReferenceCandidates("").isEmpty())
        assertTrue(LabelReferenceParser.extractReferenceCandidates("   \n  ").isEmpty())
    }

    @Test
    fun `les jetons alphanumeriques sont priorises avant les jetons tout-chiffres`() {
        val ocrText =
            """
            123
            ABC456
            """.trimIndent()

        val candidates = LabelReferenceParser.extractReferenceCandidates(ocrText)

        assertEquals(listOf("ABC456", "123"), candidates)
    }

    @Test
    fun `les mots sans chiffre sont ecartes`() {
        val ocrText =
            """
            PELOTE DE LAINE
            BLEU
            """.trimIndent()

        assertTrue(LabelReferenceParser.extractReferenceCandidates(ocrText).isEmpty())
    }

    @Test
    fun `une ligne contenant un mot-bruit connu est entierement ecartee`() {
        val ocrText =
            """
            REF123
            LOT BA9999
            COMPOSITION 100 LAINE
            """.trimIndent()

        val candidates = LabelReferenceParser.extractReferenceCandidates(ocrText)

        assertEquals(listOf("REF123"), candidates)
    }

    @Test
    fun `une ligne ressemblant a une date est entierement ecartee`() {
        val ocrText =
            """
            REF123
            FABRIQUE LE 01/07/2026
            12/2026
            """.trimIndent()

        val candidates = LabelReferenceParser.extractReferenceCandidates(ocrText)

        assertEquals(listOf("REF123"), candidates)
    }

    @Test
    fun `les doublons sont deduplique en conservant le premier ordre d apparition`() {
        val ocrText =
            """
            REF123
            REF123
            REF456
            """.trimIndent()

        val candidates = LabelReferenceParser.extractReferenceCandidates(ocrText)

        assertEquals(listOf("REF123", "REF456"), candidates)
    }

    @Test
    fun `la casse d entree est normalisee`() {
        val candidates = LabelReferenceParser.extractReferenceCandidates("ricorumi049")

        assertEquals(listOf("RICORUMI049"), candidates)
    }

    @Test
    fun `un jeton d un seul caractere est ignore`() {
        val candidates = LabelReferenceParser.extractReferenceCandidates("A\n1\nAB12")

        assertEquals(listOf("AB12"), candidates)
    }
}
