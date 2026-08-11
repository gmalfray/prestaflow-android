package com.rebuildit.prestaflow.ui.reviews

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lot socle : [ReviewsViewModel] démarre en état vide (pas de logique métier câblée, cf.
 * [ReviewsUiState] et étude `rebuild-it/docs/app-avis-sav.md`).
 */
class ReviewsViewModelTest {
    @Test
    fun `l etat initial est vide, pas un chargement sans fin`() {
        val viewModel = ReviewsViewModel()

        assertEquals(ReviewsUiState.Empty, viewModel.uiState.value)
    }
}
