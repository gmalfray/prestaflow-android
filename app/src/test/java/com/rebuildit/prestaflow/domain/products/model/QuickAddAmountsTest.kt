package com.rebuildit.prestaflow.domain.products.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests unitaires de [normalizeQuickAddAmounts] — logique de bornage (1-5 boutons) et de
 * validation (entiers > 0) des préférences de boutons rapides de l'écran de réappro (Lot 2).
 */
class QuickAddAmountsTest {
    @Test
    fun `une liste deja valide n est pas modifiee`() {
        assertEquals(listOf(5, 10, 20), normalizeQuickAddAmounts(listOf(5, 10, 20)))
    }

    @Test
    fun `une liste vide retombe sur le defaut Lot 1`() {
        assertEquals(DEFAULT_QUICK_ADD_AMOUNTS, normalizeQuickAddAmounts(emptyList()))
        assertEquals(listOf(5, 10, 20), DEFAULT_QUICK_ADD_AMOUNTS)
    }

    @Test
    fun `les valeurs zero et negatives sont filtrees`() {
        assertEquals(listOf(5, 10), normalizeQuickAddAmounts(listOf(5, 0, 10, -3)))
    }

    @Test
    fun `si toutes les valeurs sont invalides retombe sur le defaut`() {
        assertEquals(DEFAULT_QUICK_ADD_AMOUNTS, normalizeQuickAddAmounts(listOf(0, -1, -5)))
    }

    @Test
    fun `plus de 5 boutons est borne a 5`() {
        val result = normalizeQuickAddAmounts(listOf(1, 2, 3, 4, 5, 6, 7))
        assertEquals(MAX_QUICK_ADD_BUTTONS, result.size)
        assertEquals(listOf(1, 2, 3, 4, 5), result)
    }

    @Test
    fun `un seul bouton reste valide (borne min)`() {
        assertEquals(listOf(7), normalizeQuickAddAmounts(listOf(7)))
    }

    @Test
    fun `les doublons sont conserves tels quels`() {
        // Pas de dédoublonnage exigé par le Lot 2 — un utilisateur peut vouloir 2 boutons identiques.
        assertEquals(listOf(5, 5, 10), normalizeQuickAddAmounts(listOf(5, 5, 10)))
    }
}
