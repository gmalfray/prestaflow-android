package com.rebuildit.prestaflow.ui.orders

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests unitaires de [extractTrackingNumber].
 *
 * Cas couverts :
 * - Payload DataMatrix La Poste réel (`%<zéros><14 chiffres>...^<signature>`) → 14 chiffres.
 * - Numéro imprimé / saisi avec la lettre de contrôle → conservé tel quel (La Poste l'accepte).
 * - Codes 1D Colissimo et numéros simples.
 * - Entrées dégénérées (jamais d'exception).
 */
class TrackingNumberExtractorTest {

    // ──────────────────────────────────────────────────────────────────────
    // DataMatrix La Poste — payloads bruts RÉELS (décodés via Google Lens)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `extrait les 14 chiffres du payload DataMatrix La Poste - exemple 1`() {
        // Étiquette imprimée "SD : 8700133531872 1H" → suivi = 14 chiffres 87001335318721
        assertEquals("87001335318721", extractTrackingNumber("%000000087001335318721601250A18^edb7b43"))
    }

    @Test
    fun `extrait les 14 chiffres du payload DataMatrix La Poste - exemple 2`() {
        // Étiquette imprimée "SD : 8700124410028 5T"
        assertEquals("87001244100285", extractTrackingNumber("%000000087001244100285601250A18^6b3a602"))
    }

    @Test
    fun `extrait les 14 chiffres du payload DataMatrix La Poste - exemple 3`() {
        // Étiquette imprimée "SD : 8700124410028 8N"
        assertEquals("87001244100288", extractTrackingNumber("%000000087001244100288601250A18^9c1d4e5"))
    }

    // ──────────────────────────────────────────────────────────────────────
    // Numéro imprimé / saisi (avec la clé de contrôle facultative) — conservé
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `conserve le numero imprime complet avec prefixe SD et espace`() {
        // La Poste accepte 14 chiffres OU 15 avec la clé : on garde ce qui est fourni.
        assertEquals("87001335318721H", extractTrackingNumber("SD : 8700133531872 1H"))
    }

    @Test
    fun `conserve le numero imprime complet sans prefixe`() {
        assertEquals("87001244100285T", extractTrackingNumber("8700124410028 5T"))
    }

    @Test
    fun `met en majuscules un numero imprime en minuscules`() {
        assertEquals("87001335318721H", extractTrackingNumber("sd : 8700133531872 1h"))
    }

    // ──────────────────────────────────────────────────────────────────────
    // Codes 1D — Colissimo et numéros simples
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `retourne le numero Colissimo 1D tel quel`() {
        assertEquals("6A12345678901", extractTrackingNumber("6A12345678901"))
    }

    @Test
    fun `retourne un numero numerique simple tel quel`() {
        assertEquals("1234567890123", extractTrackingNumber("1234567890123"))
    }

    // ──────────────────────────────────────────────────────────────────────
    // Cas dégénérés — pas de crash, repli sur le brut nettoyé
    // ──────────────────────────────────────────────────────────────────────

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

    @Test
    fun `entree avec seulement des caracteres speciaux retourne le brut nettoye`() {
        assertEquals("---", extractTrackingNumber("---"))
    }
}
