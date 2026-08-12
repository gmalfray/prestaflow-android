package com.rebuildit.prestaflow.ui.clients

import com.rebuildit.prestaflow.domain.auth.model.AuthScopes
import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [ClientsSection.visibleSections] — LE point à risque signalé par l'étude
 * `rebuild-it/docs/app-avis-sav.md` (« capacité ≠ droit »), et le défaut vécu par Greg : un jeton
 * sans `sav.read` ne doit PAS laisser le sous-onglet SAV visible, même si la capacité SAV native
 * est toujours vraie — sinon l'ouvrir se heurte à un `403`.
 */
class ClientsSectionTest {
    private val bothScopes = setOf(AuthScopes.SAV_READ, AuthScopes.REVIEWS_MODERATE)

    // ─── Avis : capacité ET scope ───────────────────────────────────────────────

    @Test
    fun `Avis est absent quand la capacite reviews est fausse, meme avec le scope`() {
        val sections =
            ClientsSection.visibleSections(ShopCapabilities(sav = true, reviews = false), setOf(AuthScopes.REVIEWS_MODERATE))

        assertFalse(
            "Avis ne doit apparaitre dans AUCUN cas si le module n'est pas installe",
            sections.contains(ClientsSection.REVIEWS),
        )
    }

    @Test
    fun `Avis est absent quand le scope reviews moderate manque, meme si la capacite est vraie`() {
        val sections = ClientsSection.visibleSections(ShopCapabilities(sav = true, reviews = true), emptySet())

        assertFalse(
            "Une capacite vraie n'autorise rien si le jeton ne porte pas le scope — capacite != droit",
            sections.contains(ClientsSection.REVIEWS),
        )
    }

    @Test
    fun `Avis est present quand la capacite reviews est vraie ET le scope present`() {
        val sections = ClientsSection.visibleSections(ShopCapabilities(sav = true, reviews = true), bothScopes)

        assertTrue(sections.contains(ClientsSection.REVIEWS))
    }

    // ─── SAV : scope seul (capacité toujours vraie, non pertinente) ────────────

    @Test
    fun `SAV est absent quand le jeton ne porte pas sav read, meme si la capacite sav est vraie`() {
        // C'est EXACTEMENT le défaut vécu par Greg : capacité SAV toujours vraie (natif), mais
        // jeton sans sav.read → le sous-onglet ne doit PAS apparaître (sinon 403 à l'ouverture).
        val sections = ClientsSection.visibleSections(ShopCapabilities(sav = true, reviews = false), emptySet())

        assertFalse(sections.contains(ClientsSection.SAV))
    }

    @Test
    fun `SAV est present quand le jeton porte sav read`() {
        val sections = ClientsSection.visibleSections(ShopCapabilities(sav = true, reviews = false), setOf(AuthScopes.SAV_READ))

        assertTrue(sections.contains(ClientsSection.SAV))
    }

    @Test
    fun `SAV reste present meme si sav vaut false dans les capacites, tant que le scope est present`() {
        // sav est nativement toujours vrai côté connecteur (la capacité n'est pas vérifiée pour ce
        // sous-onglet) ; seul le scope du jeton détermine sa visibilité.
        val sections = ClientsSection.visibleSections(ShopCapabilities(sav = false, reviews = false), setOf(AuthScopes.SAV_READ))

        assertTrue(sections.contains(ClientsSection.SAV))
    }

    // ─── Clients : toujours présent ─────────────────────────────────────────────

    @Test
    fun `Clients est toujours present quelles que soient les capacites et les scopes`() {
        assertTrue(ClientsSection.visibleSections(ShopCapabilities(reviews = false), emptySet()).contains(ClientsSection.CLIENTS))
        assertTrue(ClientsSection.visibleSections(ShopCapabilities(reviews = true), bothScopes).contains(ClientsSection.CLIENTS))
    }

    // ─── Combinaisons et ordre ──────────────────────────────────────────────────

    @Test
    fun `aucun scope secondaire ne laisse que Clients`() {
        val sections = ClientsSection.visibleSections(ShopCapabilities(sav = true, reviews = true), emptySet())

        assertEquals(listOf(ClientsSection.CLIENTS), sections)
    }

    @Test
    fun `l ordre des sections visibles est stable (Clients puis SAV puis Avis)`() {
        val sections = ClientsSection.visibleSections(ShopCapabilities(sav = true, reviews = true), bothScopes)

        assertEquals(listOf(ClientsSection.CLIENTS, ClientsSection.SAV, ClientsSection.REVIEWS), sections)
    }

    @Test
    fun `sans le scope avis seules Clients et SAV sont visibles`() {
        val sections = ClientsSection.visibleSections(ShopCapabilities(sav = true, reviews = true), setOf(AuthScopes.SAV_READ))

        assertEquals(listOf(ClientsSection.CLIENTS, ClientsSection.SAV), sections)
    }

    @Test
    fun `sans le scope sav seules Clients et Avis sont visibles`() {
        val sections = ClientsSection.visibleSections(ShopCapabilities(sav = true, reviews = true), setOf(AuthScopes.REVIEWS_MODERATE))

        assertEquals(listOf(ClientsSection.CLIENTS, ClientsSection.REVIEWS), sections)
    }
}
