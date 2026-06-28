package com.rebuildit.prestaflow.core.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires JVM des constantes et de la logique pure de [ThermalLabelPrinter].
 *
 * Les opérations Android (PdfRenderer, BluetoothManager) ne sont pas testables sur JVM —
 * elles sont couvertes par tests d'intégration ou validées sur appareil réel.
 */
class ThermalLabelPrinterTest {
    // ─── Constantes matériel ─────────────────────────────────────────────────

    @Test
    fun `PRINTER_DPI est 203`() {
        assertEquals(203, ThermalLabelPrinter.PRINTER_DPI)
    }

    @Test
    fun `BITMAP_WIDTH_PX correspond à 4 pouces au PRINTER_DPI`() {
        // 4 " × 203 dpi = 812 px
        val expected = 4 * ThermalLabelPrinter.PRINTER_DPI
        assertEquals(expected, ThermalLabelPrinter.BITMAP_WIDTH_PX)
    }

    @Test
    fun `BITMAP_HEIGHT_PX correspond à 6 pouces au PRINTER_DPI`() {
        // 6 " × 203 dpi = 1218 px
        val expected = 6 * ThermalLabelPrinter.PRINTER_DPI
        assertEquals(expected, ThermalLabelPrinter.BITMAP_HEIGHT_PX)
    }

    @Test
    fun `PRINTER_WIDTH_MM est entre 100 et 110mm plausible pour 4 pouces`() {
        // 4" = 101.6 mm ; marge driver : 100–110 mm est raisonnable
        assertTrue(
            "La largeur doit être entre 100 et 110 mm pour une imprimante 4 pouces",
            ThermalLabelPrinter.PRINTER_WIDTH_MM in 100f..110f,
        )
    }

    @Test
    fun `PRINTER_CHARS_PER_LINE est positif`() {
        assertTrue(ThermalLabelPrinter.PRINTER_CHARS_PER_LINE > 0)
    }

    // ─── Ratio bitmap ─────────────────────────────────────────────────────────

    @Test
    fun `le ratio largeur-hauteur du bitmap correspond à une étiquette 4×6 pouces`() {
        // Ratio attendu : 4/6 = 0.667 ; on tolère ±1 px
        val ratio = ThermalLabelPrinter.BITMAP_WIDTH_PX.toDouble() / ThermalLabelPrinter.BITMAP_HEIGHT_PX
        val expected = 4.0 / 6.0
        assertEquals("Ratio bitmap doit être 4/6", expected, ratio, 0.005)
    }

    // ─── Taille bitmap minimale ───────────────────────────────────────────────

    @Test
    fun `BITMAP_WIDTH_PX est supérieur à 800 px (résolution minimale pour lisibilité)`() {
        assertTrue(
            "Largeur bitmap doit être ≥ 800 px pour lisibilité du code-barres",
            ThermalLabelPrinter.BITMAP_WIDTH_PX >= 800,
        )
    }
}
