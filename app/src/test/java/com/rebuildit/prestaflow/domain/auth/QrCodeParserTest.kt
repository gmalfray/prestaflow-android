package com.rebuildit.prestaflow.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

/**
 * Tests unitaires de [QrCodeParser].
 *
 * Couvre les deux formats de QR code PrestaFlow :
 * - URI profonde `prestaflow://…?data=<base64(json)>`
 * - JSON brut `{"shopUrl":"…","apiKey":"…"}`
 *
 * Et les cas d'erreur : contenu vide, JSON invalide, champs manquants, base64 corrompu.
 */
class QrCodeParserTest {

    // ─── Format JSON brut ─────────────────────────────────────────────────────

    @Test
    fun `json brut valide retourne shopUrl et apiKey`() {
        val raw = """{"shopUrl":"https://mabouttique.fr","apiKey":"abc-def"}"""
        val result = QrCodeParser.parse(raw)
        assertNotNull("Le parse doit réussir pour un JSON valide", result)
        assertEquals("https://mabouttique.fr", result!!.first)
        assertEquals("abc-def", result.second)
    }

    @Test
    fun `json brut avec champs supplementaires est accepte`() {
        val raw = """{"shopUrl":"https://shop.test","apiKey":"key1","extra":"ignored"}"""
        val result = QrCodeParser.parse(raw)
        assertNotNull(result)
        assertEquals("https://shop.test", result!!.first)
        assertEquals("key1", result.second)
    }

    @Test
    fun `json brut sans shopUrl retourne null`() {
        val raw = """{"apiKey":"abc-def"}"""
        assertNull("shopUrl manquant → null", QrCodeParser.parse(raw))
    }

    @Test
    fun `json brut sans apiKey retourne null`() {
        val raw = """{"shopUrl":"https://shop.test"}"""
        assertNull("apiKey manquant → null", QrCodeParser.parse(raw))
    }

    @Test
    fun `json brut avec shopUrl vide retourne null`() {
        val raw = """{"shopUrl":"","apiKey":"abc-def"}"""
        assertNull("shopUrl vide → null", QrCodeParser.parse(raw))
    }

    @Test
    fun `json brut avec apiKey vide retourne null`() {
        val raw = """{"shopUrl":"https://shop.test","apiKey":""}"""
        assertNull("apiKey vide → null", QrCodeParser.parse(raw))
    }

    @Test
    fun `contenu vide retourne null`() {
        assertNull("Contenu vide → null", QrCodeParser.parse(""))
    }

    @Test
    fun `contenu blanc retourne null`() {
        assertNull("Contenu blanc → null", QrCodeParser.parse("   "))
    }

    @Test
    fun `texte arbitraire non json retourne null`() {
        assertNull("Texte non-JSON → null", QrCodeParser.parse("bonjour"))
    }

    // ─── Format URI profonde prestaflow:// ────────────────────────────────────

    @Test
    fun `uri profonde valide retourne shopUrl et apiKey`() {
        val json = """{"shopUrl":"https://mabouttique.fr","apiKey":"xyz-789"}"""
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        val raw = "prestaflow://connect?data=$encoded"

        val result = QrCodeParser.parse(raw)
        assertNotNull("URI prestaflow valide → parse doit réussir", result)
        assertEquals("https://mabouttique.fr", result!!.first)
        assertEquals("xyz-789", result.second)
    }

    @Test
    fun `uri profonde sans parametre data retourne null`() {
        val raw = "prestaflow://connect"
        assertNull("URI sans 'data' → null", QrCodeParser.parse(raw))
    }

    @Test
    fun `uri profonde avec base64 corrompu retourne null`() {
        val raw = "prestaflow://connect?data=!!!not_base64!!!"
        assertNull("Base64 corrompu → null", QrCodeParser.parse(raw))
    }

    @Test
    fun `uri profonde avec json invalide dans data retourne null`() {
        val encoded = Base64.getEncoder().encodeToString("pas_du_json".toByteArray())
        val raw = "prestaflow://connect?data=$encoded"
        assertNull("Base64 décode mais JSON invalide → null", QrCodeParser.parse(raw))
    }

    @Test
    fun `uri profonde case insensitive prestaflow`() {
        val json = """{"shopUrl":"https://shop.test","apiKey":"key"}"""
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        val raw = "PRESTAFLOW://connect?data=$encoded"
        val result = QrCodeParser.parse(raw)
        assertNotNull("Le préfixe prestaflow:// est insensible à la casse", result)
    }
}
