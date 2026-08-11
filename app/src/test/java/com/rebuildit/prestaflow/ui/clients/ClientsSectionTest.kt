package com.rebuildit.prestaflow.ui.clients

import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [ClientsSection.visibleSections] — LE point à risque signalé par l'étude
 * `rebuild-it/docs/app-avis-sav.md` : une section « Avis » qui apparaîtrait alors que le module
 * `rbreviews` est absent de la boutique.
 */
class ClientsSectionTest {
    @Test
    fun `Avis est absent quand la capacite reviews est fausse`() {
        val sections = ClientsSection.visibleSections(ShopCapabilities(sav = true, reviews = false))

        assertFalse(
            "Avis ne doit apparaitre dans AUCUN cas si le module n'est pas installe",
            sections.contains(ClientsSection.REVIEWS),
        )
    }

    @Test
    fun `Avis est present quand la capacite reviews est vraie`() {
        val sections = ClientsSection.visibleSections(ShopCapabilities(sav = true, reviews = true))

        assertTrue(sections.contains(ClientsSection.REVIEWS))
    }

    @Test
    fun `Clients est toujours present quelles que soient les capacites`() {
        assertTrue(ClientsSection.visibleSections(ShopCapabilities(reviews = false)).contains(ClientsSection.CLIENTS))
        assertTrue(ClientsSection.visibleSections(ShopCapabilities(reviews = true)).contains(ClientsSection.CLIENTS))
    }

    @Test
    fun `SAV est toujours present meme si sav vaut false dans les capacites`() {
        // sav est nativement toujours vrai côté connecteur, mais l'app ne doit JAMAIS masquer
        // le SAV même si un payload malformé le renvoyait à false — cf. étude
        // « sav est toujours vrai : les fils clients sont natifs PrestaShop, aucun module requis ».
        val sections = ClientsSection.visibleSections(ShopCapabilities(sav = false, reviews = false))

        assertTrue(sections.contains(ClientsSection.SAV))
    }

    @Test
    fun `l ordre des sections visibles est stable (Clients puis SAV puis Avis)`() {
        val sections = ClientsSection.visibleSections(ShopCapabilities(sav = true, reviews = true))

        assertEquals(listOf(ClientsSection.CLIENTS, ClientsSection.SAV, ClientsSection.REVIEWS), sections)
    }

    @Test
    fun `sans le module avis seules Clients et SAV sont visibles`() {
        val sections = ClientsSection.visibleSections(ShopCapabilities(sav = true, reviews = false))

        assertEquals(listOf(ClientsSection.CLIENTS, ClientsSection.SAV), sections)
    }
}
