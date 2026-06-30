package com.rebuildit.prestaflow.core.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.Inflater

/**
 * Tests unitaires (JVM pur) de [ThermalLabelPrinter].
 *
 * Couvre les invariants indépendants d'Android : constantes de géométrie et conformité du flux
 * zlib utilisé par la commande TSPL `BITMAP ...,3,...`. La construction du bitmap monochrome et
 * du job TSPL complet dépend d'`android.graphics.Bitmap` (non instanciable en JUnit pur) et est
 * validée au test matériel.
 */
class ThermalLabelPrinterTest {
    @Test
    fun `resolution est 8 dots par mm (203 dpi)`() {
        assertEquals(8, ThermalLabelPrinter.DOTS_PER_MM)
    }

    @Test
    fun `largeur d impression de 816 dots correspond a 102 octets par ligne et 102 mm`() {
        assertEquals(816, ThermalLabelPrinter.PRINT_WIDTH_DOTS)
        // 816 dots / 8 bits = 102 octets/ligne (param widthBytes de BITMAP)
        assertEquals(102, (ThermalLabelPrinter.PRINT_WIDTH_DOTS + 7) / 8)
        // 816 dots / 8 dots-par-mm = 102 mm (param SIZE)
        assertEquals(102, ThermalLabelPrinter.PRINT_WIDTH_DOTS / ThermalLabelPrinter.DOTS_PER_MM)
    }

    @Test
    fun `deflateZlib produit un flux zlib valide qui se decompresse a l identique`() {
        val input = ByteArray(62016) { (it * 31 % 256).toByte() }

        val compressed = ThermalLabelPrinter.deflateZlib(input)

        // En-tête zlib standard (0x78), comme attendu par le firmware (BITMAP mode 3)
        assertEquals(0x78.toByte(), compressed[0])
        // Round-trip : la décompression doit redonner exactement l'entrée
        val inflater = Inflater()
        inflater.setInput(compressed)
        val out = ByteArray(input.size)
        val n = inflater.inflate(out)
        inflater.end()
        assertEquals(input.size, n)
        assertTrue(input.contentEquals(out))
    }

    @Test
    fun `deflateZlib reduit la taille d un contenu majoritairement uniforme`() {
        // Une étiquette est surtout blanche → très compressible
        val mostlyWhite = ByteArray(62016) { 0xFF.toByte() }

        val compressed = ThermalLabelPrinter.deflateZlib(mostlyWhite)

        assertTrue(
            "Le flux compressé (${compressed.size}) doit être bien plus petit que l'original (${mostlyWhite.size})",
            compressed.size < mostlyWhite.size / 10,
        )
    }
}
