package com.rebuildit.prestaflow.ui.orders

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests unitaires de [extractTrackingNumber] et [colissimoCheckDigit].
 */
class TrackingNumberExtractorTest {

    // ──────────────────────────────────────────────────────────────────────
    // Colissimo / Lettre Max — code-barres 1D (clé de contrôle recalculée)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `colissimo 1D recalcule la cle et renvoie le numero complet - exemple 1`() {
        // Brut du Code 128 : préfixe 5Y + serial 0052689628 (clé 6 absente du code) → 5Y00526896286
        assertEquals("5Y00526896286", extractTrackingNumber("%0055170115Y0052689628801250"))
    }

    @Test
    fun `colissimo 1D recalcule la cle et renvoie le numero complet - exemple 2`() {
        assertEquals("5Y00526896293", extractTrackingNumber("%0034610115Y0052689629801250"))
    }

    @Test
    fun `colissimo numero complet deja saisi reste identique`() {
        assertEquals("5Y00526896286", extractTrackingNumber("5Y00526896286"))
    }

    @Test
    fun `lettre max 1L recalcule correctement la cle`() {
        assertEquals("1L02607415232", extractTrackingNumber("1L02607415232"))
    }

    @Test
    fun `cle de controle colissimo sur les exemples reels`() {
        assertEquals(6, colissimoCheckDigit("0052689628"))
        assertEquals(3, colissimoCheckDigit("0052689629"))
        assertEquals(2, colissimoCheckDigit("0045434637"))
        assertEquals(9, colissimoCheckDigit("0045434638"))
    }

    // ──────────────────────────────────────────────────────────────────────
    // La Poste Lettre suivie — DataMatrix 2D (14 chiffres)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `lettre suivie extrait les 14 chiffres du DataMatrix - exemple 1`() {
        assertEquals("87001335318721", extractTrackingNumber("%000000087001335318721601250A18^edb7b43"))
    }

    @Test
    fun `lettre suivie extrait les 14 chiffres du DataMatrix - exemple 2`() {
        assertEquals("87001244100285", extractTrackingNumber("%000000087001244100285601250A18^6b3a602"))
    }

    @Test
    fun `lettre suivie numero imprime complet conserve`() {
        assertEquals("87001335318721H", extractTrackingNumber("SD : 8700133531872 1H"))
    }

    // ──────────────────────────────────────────────────────────────────────
    // Générique + cas dégénérés
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `numero numerique simple tel quel`() {
        assertEquals("1234567890123", extractTrackingNumber("1234567890123"))
    }

    @Test
    fun `entree vide retourne chaine vide sans exception`() {
        assertEquals("", extractTrackingNumber(""))
    }

    @Test
    fun `entree blanche retourne chaine vide sans exception`() {
        assertEquals("", extractTrackingNumber("   "))
    }

    @Test
    fun `entree sans motif reconnu retourne le brut nettoye`() {
        assertEquals("XYZ", extractTrackingNumber("XYZ"))
    }
}
