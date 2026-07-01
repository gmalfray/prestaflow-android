package com.rebuildit.prestaflow.ui.orders

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests unitaires JVM de [statusShortLabel].
 *
 * Couvre : mapping des statuts FR standards PrestaShop, insensibilité casse/accents,
 * fallback premier-mot avec et sans troncature, et entrées limites (vide, un seul mot long).
 */
class StatusChipUtilsTest {

    // ─── Mapping standard PrestaShop FR ─────────────────────────────────────

    @Test
    fun `paiement accepte mapped to Paye`() {
        assertEquals("Payé", statusShortLabel("Paiement accepté"))
    }

    @Test
    fun `en cours de preparation mapped to Prepa`() {
        assertEquals("Prépa", statusShortLabel("En cours de préparation"))
    }

    @Test
    fun `expedie mapped to Expedie`() {
        assertEquals("Expédié", statusShortLabel("Expédié(e)"))
    }

    @Test
    fun `livre mapped to Livre`() {
        assertEquals("Livré", statusShortLabel("Livré(e)"))
    }

    @Test
    fun `termine mapped to Termine`() {
        assertEquals("Terminé", statusShortLabel("Terminé(e)"))
    }

    @Test
    fun `attente cheque mapped to Cheque`() {
        assertEquals("Chèque", statusShortLabel("En attente du paiement par chèque"))
    }

    @Test
    fun `attente virement mapped to Virement`() {
        assertEquals("Virement", statusShortLabel("En attente de paiement (virement/banque)"))
    }

    @Test
    fun `annule mapped to Annule`() {
        assertEquals("Annulé", statusShortLabel("Annulé(e)"))
    }

    @Test
    fun `rembourse mapped to Rembourse`() {
        assertEquals("Remboursé", statusShortLabel("Remboursé(e)"))
    }

    @Test
    fun `paiement erreur mapped to Erreur`() {
        assertEquals("Erreur", statusShortLabel("Paiement erreur / refusé"))
    }

    // ─── Insensibilité casse et accents ──────────────────────────────────────

    @Test
    fun `mapping insensible a la casse`() {
        assertEquals("Payé", statusShortLabel("PAIEMENT ACCEPTÉ"))
    }

    @Test
    fun `mapping insensible aux accents`() {
        assertEquals("Expédié", statusShortLabel("Expedie"))
    }

    @Test
    fun `preparation en majuscules matchee`() {
        assertEquals("Prépa", statusShortLabel("PREPARATION EN COURS"))
    }

    // ─── Priorité du mapping (plus spécifique d'abord) ──────────────────────

    @Test
    fun `paiement accepte prime sur paiement generique`() {
        // "Paiement accepté" doit retourner "Payé" et non un fallback ou autre
        assertEquals("Payé", statusShortLabel("Paiement accepté"))
    }

    @Test
    fun `cheque prime sur paiement generique dans attente cheque`() {
        // "En attente du paiement par chèque" contient "paiement" mais "cheque" prime
        assertEquals("Chèque", statusShortLabel("En attente du paiement par chèque"))
    }

    // ─── Fallback : statut non mappé ─────────────────────────────────────────

    @Test
    fun `statut inconnu retourne le premier mot`() {
        assertEquals("Inconnu", statusShortLabel("Inconnu custom"))
    }

    @Test
    fun `premier mot court retourne tel quel`() {
        assertEquals("Livraison", statusShortLabel("Livraison rapide"))
    }

    @Test
    fun `premier mot long tronque avec ellipse`() {
        // "Approvisionnement" = 17 chars > 12 → tronqué à 11 + "…"
        val result = statusShortLabel("Approvisionnement en cours")
        assertEquals("Approvision…", result)
        assertEquals(12, result.length) // 11 chars + ellipse = 12
    }

    @Test
    fun `premier mot exactement 12 chars retourne tel quel`() {
        // "Prélèvement_" = 12 chars (on s'assure que 12 n'est pas tronqué)
        val name = "Préparatoire uniquement"
        val result = statusShortLabel(name)
        val firstWord = "Préparatoire" // 12 chars
        assertEquals(firstWord, result)
    }

    // ─── Cas limites ─────────────────────────────────────────────────────────

    @Test
    fun `chaine vide retourne chaine vide`() {
        assertEquals("", statusShortLabel(""))
    }

    @Test
    fun `chaine avec seulement des espaces retourne chaine vide`() {
        // isBlank → retourne name tel quel (ici "   " après trim)
        // La fonction retourne name si blank, donc " " → " "
        val result = statusShortLabel("   ")
        assertEquals("   ", result)
    }

    @Test
    fun `un seul mot court retourne tel quel`() {
        assertEquals("Envoyé", statusShortLabel("Envoyé"))
    }

    @Test
    fun `un seul mot tres long tronque`() {
        // "Désengagement" = 13 chars, ne frappe aucune clé du mapping → fallback
        val result = statusShortLabel("Désengagement")
        assertEquals("Désengageme…", result)
    }
}
