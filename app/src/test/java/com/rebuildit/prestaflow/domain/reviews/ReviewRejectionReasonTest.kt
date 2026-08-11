package com.rebuildit.prestaflow.domain.reviews

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Règle légale (article L111-7-2) : le motif de rejet d'un avis est OBLIGATOIRE, au moins
 * [ReviewRejectionReason.MIN_LENGTH] caractères. Ces tests figent le seuil exact — le serveur
 * répond `422` en dessous (cf. `rebuild-connector` docs/api.md § reviews/{id}/trash), donc cette
 * règle DOIT rester alignée pour qu'aucun rejet valide côté app ne soit rejeté côté serveur, et
 * inversement.
 */
class ReviewRejectionReasonTest {
    @Test
    fun `un motif vide est invalide`() {
        assertFalse(ReviewRejectionReason.isValid(""))
    }

    @Test
    fun `un motif compose uniquement d espaces est invalide`() {
        assertFalse(ReviewRejectionReason.isValid("            "))
    }

    @Test
    fun `un motif de 9 caracteres est invalide`() {
        assertFalse(ReviewRejectionReason.isValid("123456789"))
    }

    @Test
    fun `un motif de 10 caracteres exactement est valide`() {
        assertTrue(ReviewRejectionReason.isValid("1234567890"))
    }

    @Test
    fun `un motif de plus de 10 caracteres est valide`() {
        assertTrue(ReviewRejectionReason.isValid("Contenu hors sujet, sans rapport avec le produit vendu."))
    }

    @Test
    fun `les espaces de bordure ne comptent pas dans la longueur`() {
        // 8 caractères utiles entourés d'espaces : sous le seuil une fois trimmé
        assertFalse(ReviewRejectionReason.isValid("   12345678   "))
    }

    @Test
    fun `un motif juste sous le seuil apres trim est invalide`() {
        // 9 caractères utiles après trim
        assertFalse(ReviewRejectionReason.isValid("  123456789  "))
    }
}
