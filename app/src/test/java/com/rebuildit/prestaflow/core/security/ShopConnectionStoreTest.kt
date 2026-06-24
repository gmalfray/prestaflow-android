package com.rebuildit.prestaflow.core.security

import com.rebuildit.prestaflow.domain.auth.model.AuthToken
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.fakes.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires de [ShopConnectionStore].
 *
 * Vérifie les round-trips read/write, la gestion de l'id actif, le clear
 * et la résilience face à un JSON corrompu.
 *
 * Pas de Robolectric : on injecte un [FakeSharedPreferences] en mémoire.
 */
class ShopConnectionStoreTest {
    private lateinit var prefs: FakeSharedPreferences
    private lateinit var store: ShopConnectionStore

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        store = ShopConnectionStore(prefs)
    }

    // ─── Round-trip read/write ────────────────────────────────────────────────

    @Test
    fun `read retourne une liste vide si aucune connexion n a ete persistee`() {
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun `write puis read retourne la meme connexion`() {
        val connection = buildConnection("https://shop.test", "Ma boutique")

        store.write(listOf(connection))

        val result = store.read()
        assertEquals(1, result.size)
        assertEquals("https://shop.test", result[0].id)
        assertEquals("https://shop.test", result[0].shopUrl)
        assertEquals("Ma boutique", result[0].label)
        assertEquals("jwt-token-abc", result[0].token.value)
        assertEquals("api-key-xyz", result[0].apiKey)
    }

    @Test
    fun `write persiste plusieurs connexions et read les retourne toutes`() {
        val conn1 = buildConnection("https://shop1.test", "Boutique 1")
        val conn2 = buildConnection("https://shop2.test", "Boutique 2")
        val conn3 = buildConnection("https://shop3.test", "Boutique 3")

        store.write(listOf(conn1, conn2, conn3))

        val result = store.read()
        assertEquals(3, result.size)
        assertEquals(setOf("https://shop1.test", "https://shop2.test", "https://shop3.test"), result.map { it.id }.toSet())
    }

    @Test
    fun `write remplace completement la liste precedente`() {
        store.write(listOf(buildConnection("https://ancienne.test", "Ancienne")))
        store.write(listOf(buildConnection("https://nouvelle.test", "Nouvelle")))

        val result = store.read()
        assertEquals(1, result.size)
        assertEquals("https://nouvelle.test", result[0].id)
    }

    @Test
    fun `write avec liste vide supprime toutes les connexions`() {
        store.write(listOf(buildConnection("https://shop.test", "Boutique")))

        store.write(emptyList())

        assertTrue(store.read().isEmpty())
    }

    @Test
    fun `les scopes et expiresAt du token sont preserves apres round-trip`() {
        val token =
            AuthToken(
                value = "scoped-token",
                expiresAtEpochMillis = 9_999_999_999L,
                scopes = listOf("orders", "products", "customers"),
            )
        val connection =
            ShopConnection(
                id = "https://shop.test",
                shopUrl = "https://shop.test",
                label = "Boutique",
                token = token,
                apiKey = "key",
            )

        store.write(listOf(connection))

        val result = store.read().first()
        assertEquals(9_999_999_999L, result.token.expiresAtEpochMillis)
        assertEquals(listOf("orders", "products", "customers"), result.token.scopes)
    }

    @Test
    fun `un token sans expiresAt est preserve avec expiresAtEpochMillis null`() {
        val token = AuthToken(value = "token-sans-expiry", expiresAtEpochMillis = null)
        val connection =
            ShopConnection(
                id = "https://shop.test",
                shopUrl = "https://shop.test",
                label = "Boutique",
                token = token,
                apiKey = "key",
            )

        store.write(listOf(connection))

        assertNull(store.read().first().token.expiresAtEpochMillis)
    }

    // ─── getActiveId / setActiveId ────────────────────────────────────────────

    @Test
    fun `getActiveId retourne null si aucun id actif n a ete pose`() {
        assertNull(store.getActiveId())
    }

    @Test
    fun `setActiveId puis getActiveId retourne le meme id`() {
        store.setActiveId("https://shop.test")

        assertEquals("https://shop.test", store.getActiveId())
    }

    @Test
    fun `setActiveId avec null efface l id actif`() {
        store.setActiveId("https://shop.test")
        store.setActiveId(null)

        assertNull(store.getActiveId())
    }

    @Test
    fun `setActiveId ecrase l id actif precedent`() {
        store.setActiveId("https://shop1.test")
        store.setActiveId("https://shop2.test")

        assertEquals("https://shop2.test", store.getActiveId())
    }

    // ─── clear ────────────────────────────────────────────────────────────────

    @Test
    fun `clear supprime toutes les connexions et l id actif`() {
        store.write(listOf(buildConnection("https://shop.test", "Boutique")))
        store.setActiveId("https://shop.test")

        store.clear()

        assertTrue(store.read().isEmpty())
        assertNull(store.getActiveId())
    }

    @Test
    fun `clear est idempotent sur un store deja vide`() {
        store.clear()
        store.clear()

        assertTrue(store.read().isEmpty())
        assertNull(store.getActiveId())
    }

    // ─── Résilience au JSON corrompu ──────────────────────────────────────────

    @Test
    fun `read retourne liste vide si le JSON persisté est corrompu`() {
        prefs.edit().putString("shop_connections", "{ json: invalide [[[").apply()

        val result = store.read()

        assertTrue("Un JSON corrompu doit produire une liste vide, pas un crash", result.isEmpty())
    }

    @Test
    fun `read retourne liste vide si le JSON persisté est un objet seul au lieu d une liste`() {
        prefs.edit().putString("shop_connections", """{"id":"x","shopUrl":"x","label":"x","token":"t"}""").apply()

        val result = store.read()

        assertTrue("Un objet seul (non-liste) doit produire une liste vide", result.isEmpty())
    }

    @Test
    fun `read retourne liste vide si la valeur persistée est une chaine vide`() {
        prefs.edit().putString("shop_connections", "").apply()

        val result = store.read()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `read tolere les champs inconnus dans le JSON stocke`() {
        // Simule une version future du format qui ajoute des champs inconnus
        val jsonAvecChampsSupp =
            """[{"id":"https://shop.test","shopUrl":"https://shop.test",""" +
                """"label":"Boutique","token":"tok","champ_futur":"valeur_inconnue"}]"""
        prefs.edit().putString("shop_connections", jsonAvecChampsSupp).apply()

        val result = store.read()

        assertEquals("Les champs inconnus doivent être ignorés (ignoreUnknownKeys)", 1, result.size)
        assertEquals("https://shop.test", result[0].id)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun buildConnection(
        shopUrl: String,
        label: String,
        apiKey: String = "api-key-xyz",
    ) = ShopConnection(
        id = shopUrl,
        shopUrl = shopUrl,
        label = label,
        token = AuthToken(value = "jwt-token-abc", expiresAtEpochMillis = Long.MAX_VALUE),
        apiKey = apiKey,
    )
}
