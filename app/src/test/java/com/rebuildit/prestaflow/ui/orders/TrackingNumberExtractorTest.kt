package com.rebuildit.prestaflow.ui.orders

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests unitaires de [extractTrackingNumber].
 *
 * Couvre les cas La Poste lettre suivie (DataMatrix 2D), les codes 1D Colissimo,
 * les payloads DataMatrix avec données supplémentaires et les entrées dégénérées.
 */
class TrackingNumberExtractorTest {

    // ──────────────────────────────────────────────────────────────────────
    // La Poste — numéro avec préfixe « SD : »
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `retourne le numero La Poste sans prefix SD avec espace`() {
        // Étiquette imprimée : "SD : 8700133531872 1H"
        assertEquals("87001335318721H", extractTrackingNumber("SD : 8700133531872 1H"))
    }

    @Test
    fun `retourne le numero La Poste sans prefix SD sans espace apres deux points`() {
        assertEquals("87001335318721H", extractTrackingNumber("SD: 8700133531872 1H"))
    }

    @Test
    fun `retourne le numero La Poste en majuscules avec prefix minuscule`() {
        assertEquals("87001335318721H", extractTrackingNumber("sd : 8700133531872 1h"))
    }

    // ──────────────────────────────────────────────────────────────────────
    // La Poste — numéro sans préfixe (affiché avec espace interne)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `retourne le numero La Poste sans prefixe avec espace interne`() {
        // "8700124410028 5T" — espace sépare les 13 chiffres des 2 chars
        assertEquals("87001244100285T", extractTrackingNumber("8700124410028 5T"))
    }

    @Test
    fun `retourne le numero La Poste sans prefixe ni espace`() {
        assertEquals("87001244100285T", extractTrackingNumber("87001244100285T"))
    }

    @Test
    fun `retourne le troisieme exemple La Poste`() {
        assertEquals("87001244100288N", extractTrackingNumber("8700124410028 8N"))
    }

    // ──────────────────────────────────────────────────────────────────────
    // Payload DataMatrix simulé (données structurées entourant le numéro)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `extrait le numero La Poste depuis un payload DataMatrix avec donnees supplementaires`() {
        // Simulation d'un payload propriétaire La Poste contenant d'autres champs
        val payload = "ID:87001244100285T;WT:200;SVC:LettreS"
        assertEquals("87001244100285T", extractTrackingNumber(payload))
    }

    @Test
    fun `extrait le numero La Poste depuis payload avec espaces autour`() {
        val payload = "REF 87001244100285T DATE 20240601"
        assertEquals("87001244100285T", extractTrackingNumber(payload))
    }

    // ──────────────────────────────────────────────────────────────────────
    // Codes 1D — Colissimo et autres transporteurs
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `retourne le numero Colissimo 1D tel quel`() {
        // "6A12345678901" — 1 lettre + 12 chiffres = 13 chars, pas 13 chiffres + 2 alnum
        assertEquals("6A12345678901", extractTrackingNumber("6A12345678901"))
    }

    @Test
    fun `retourne un numero CODE128 classique tel quel`() {
        // 13 chars entièrement numériques → ne matche pas "13 chiffres + 2 alnum" (pas assez long)
        // mais matche le generic 11–16
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
        // "XYZ" = 3 chars, ne matche ni La Poste ni generic (11–16)
        assertEquals("XYZ", extractTrackingNumber("XYZ"))
    }

    @Test
    fun `entree courte sans espace retourne le brut compacte`() {
        assertEquals("AB12", extractTrackingNumber("AB12"))
    }

    @Test
    fun `entree avec seulement des caracteres speciaux retourne le brut nettoye`() {
        assertEquals("---", extractTrackingNumber("---"))
    }
}
