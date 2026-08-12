package com.rebuildit.prestaflow.data.push

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Vérifie que les URI de deep link construites pour le tap d'une notification (cf.
 * `build*DeepLinkIntent` dans [PrestaFlowFirebaseMessagingService]) sont exactement celles
 * déclarées comme `navDeepLink` dans `PrestaFlowNavGraph` et comme intent-filters dans le
 * manifeste — c'est ce qui détermine la destination ouverte au tap.
 */
class PrestaFlowFirebaseMessagingServiceDeepLinkTest {
    @Test
    fun `un push commande ouvre le detail de la commande avec le flag fromNotification`() {
        assertEquals("prestaflow://orders/42?fromNotification=true", orderDeepLinkUriString(42L))
    }

    @Test
    fun `un push stock faible ouvre la fiche produit`() {
        assertEquals("prestaflow://products/7", productDeepLinkUriString(7L))
    }

    @Test
    fun `un push message SAV ouvre le fil concerne`() {
        assertEquals("prestaflow://sav/99", savThreadDeepLinkUriString(99L))
    }

    @Test
    fun `un push avis a moderer ouvre le sous-onglet Avis de Clients`() {
        assertEquals("prestaflow://clients?section=reviews", reviewsDeepLinkUriString())
    }
}
