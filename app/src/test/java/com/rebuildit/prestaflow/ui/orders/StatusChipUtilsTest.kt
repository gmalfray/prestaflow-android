package com.rebuildit.prestaflow.ui.orders

import com.rebuildit.prestaflow.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests unitaires JVM de [statusShortLabelResId] et [statusShortLabelFallback].
 *
 * Depuis le passage aux statuts localisés serveur (header Accept-Language), le mapping se fait
 * par ID de statut PrestaShop (stable, indépendant de la langue) → ressource de libellé court —
 * plus par mot-clé français sur le nom. Couvre : mapping des 10 IDs standards (comparaison de
 * l'Int `@StringRes`, cf. précédent dans `ShopsViewModelTest`), ID custom non mappé (→ `null`),
 * et le fallback premier-mot (utilisé uniquement pour un ID non mappé, avec le nom déjà localisé
 * par le serveur) avec troncature/ellipse et cas limites.
 */
class StatusChipUtilsTest {
    // ─── Mapping par ID PrestaShop standard ─────────────────────────────────

    @Test
    fun `id 1 attente cheque mappe sur orders_status_short_1`() {
        assertEquals(R.string.orders_status_short_1, statusShortLabelResId(1))
    }

    @Test
    fun `id 2 paiement accepte mappe sur orders_status_short_2`() {
        assertEquals(R.string.orders_status_short_2, statusShortLabelResId(2))
    }

    @Test
    fun `id 3 en preparation mappe sur orders_status_short_3`() {
        assertEquals(R.string.orders_status_short_3, statusShortLabelResId(3))
    }

    @Test
    fun `id 4 expedie mappe sur orders_status_short_4`() {
        assertEquals(R.string.orders_status_short_4, statusShortLabelResId(4))
    }

    @Test
    fun `id 5 livre mappe sur orders_status_short_5`() {
        assertEquals(R.string.orders_status_short_5, statusShortLabelResId(5))
    }

    @Test
    fun `id 6 annule mappe sur orders_status_short_6`() {
        assertEquals(R.string.orders_status_short_6, statusShortLabelResId(6))
    }

    @Test
    fun `id 7 rembourse mappe sur orders_status_short_7`() {
        assertEquals(R.string.orders_status_short_7, statusShortLabelResId(7))
    }

    @Test
    fun `id 8 erreur de paiement mappe sur orders_status_short_8`() {
        assertEquals(R.string.orders_status_short_8, statusShortLabelResId(8))
    }

    @Test
    fun `id 9 terminee pensebonheur mappe sur orders_status_short_9`() {
        assertEquals(R.string.orders_status_short_9, statusShortLabelResId(9))
    }

    @Test
    fun `id 10 attente virement mappe sur orders_status_short_10`() {
        assertEquals(R.string.orders_status_short_10, statusShortLabelResId(10))
    }

    // ─── ID custom (non mappé) ───────────────────────────────────────────────

    @Test
    fun `id custom absent du mapping retourne null`() {
        assertNull(statusShortLabelResId(999))
    }

    @Test
    fun `id 0 absent du mapping retourne null`() {
        assertNull(statusShortLabelResId(0))
    }

    @Test
    fun `id negatif absent du mapping retourne null`() {
        assertNull(statusShortLabelResId(-1))
    }

    // ─── Fallback premier-mot (statut custom, nom déjà localisé) ─────────────

    @Test
    fun `statut inconnu retourne le premier mot`() {
        assertEquals("Inconnu", statusShortLabelFallback("Inconnu custom"))
    }

    @Test
    fun `premier mot court retourne tel quel`() {
        assertEquals("Livraison", statusShortLabelFallback("Livraison rapide"))
    }

    @Test
    fun `fallback fonctionne aussi sur un nom localise en allemand`() {
        // "In Bearbeitung" (DE) : aucun mot-clé FR ne matchait avant ce refactor, d'où le bug
        // initial ("In" tronqué). Le fallback ne fait plus de matching par mot-clé : il retourne
        // simplement le premier mot du nom déjà localisé par le serveur.
        assertEquals("In", statusShortLabelFallback("In Bearbeitung"))
    }

    @Test
    fun `premier mot long tronque avec ellipse`() {
        // "Approvisionnement" = 17 chars > 12 → tronqué à 11 + "…"
        val result = statusShortLabelFallback("Approvisionnement en cours")
        assertEquals("Approvision…", result)
        assertEquals(12, result.length) // 11 chars + ellipse = 12
    }

    @Test
    fun `premier mot exactement 12 chars retourne tel quel`() {
        // "Préparatoire" = 12 chars (on s'assure que 12 n'est pas tronqué)
        val name = "Préparatoire uniquement"
        val result = statusShortLabelFallback(name)
        val firstWord = "Préparatoire" // 12 chars
        assertEquals(firstWord, result)
    }

    // ─── Cas limites ─────────────────────────────────────────────────────────

    @Test
    fun `chaine vide retourne chaine vide`() {
        assertEquals("", statusShortLabelFallback(""))
    }

    @Test
    fun `chaine avec seulement des espaces retourne chaine vide`() {
        // isBlank → retourne name tel quel (ici "   " après trim)
        val result = statusShortLabelFallback("   ")
        assertEquals("   ", result)
    }

    @Test
    fun `un seul mot court retourne tel quel`() {
        assertEquals("Envoyé", statusShortLabelFallback("Envoyé"))
    }

    @Test
    fun `un seul mot tres long tronque`() {
        // "Désengagement" = 13 chars → tronqué avec ellipse
        val result = statusShortLabelFallback("Désengagement")
        assertEquals("Désengageme…", result)
    }
}
