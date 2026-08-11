package com.rebuildit.prestaflow.ui.reviews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.ui.components.EmptyState
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme

@Composable
fun ReviewsRoute(
    modifier: Modifier = Modifier,
    viewModel: ReviewsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReviewsScreen(state = state, modifier = modifier)
}

/**
 * Squelette de l'écran Avis : uniquement les 3 états chargement/vide/erreur (cf. [ReviewsUiState]).
 * La file de modération, la publication et le rejet motivé (obligatoire — L111-7-2) arrivent avec
 * le lot Avis proprement dit.
 */
@Composable
fun ReviewsScreen(
    state: ReviewsUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    when (state) {
        ReviewsUiState.Loading -> LoadingState(modifier)
        ReviewsUiState.Empty -> EmptyState(message = stringResource(R.string.reviews_list_empty), modifier = modifier)
        is ReviewsUiState.Error ->
            EmptyState(
                message = stringResource(R.string.reviews_list_error_title),
                errorMessage = state.message.asString(),
                onRefresh = onRetry,
                modifier = modifier,
            )
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Avis — chargement")
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewReviewsLoading() {
    PrestaFlowTheme { ReviewsScreen(state = ReviewsUiState.Loading) }
}

@Preview(showBackground = true, name = "Avis — vide")
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewReviewsEmpty() {
    PrestaFlowTheme { ReviewsScreen(state = ReviewsUiState.Empty) }
}

@Preview(showBackground = true, name = "Avis — erreur")
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewReviewsError() {
    PrestaFlowTheme {
        ReviewsScreen(state = ReviewsUiState.Error(UiText.Dynamic("Connexion à la boutique impossible.")))
    }
}
