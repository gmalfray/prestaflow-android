package com.rebuildit.prestaflow.ui.sav

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
fun SavRoute(
    modifier: Modifier = Modifier,
    viewModel: SavViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SavScreen(state = state, modifier = modifier)
}

/**
 * Squelette de l'écran SAV : uniquement les 3 états chargement/vide/erreur (cf. [SavUiState]).
 * La liste des fils, leur détail et la réponse arrivent avec le lot SAV proprement dit.
 */
@Composable
fun SavScreen(
    state: SavUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    when (state) {
        SavUiState.Loading -> LoadingState(modifier)
        SavUiState.Empty -> EmptyState(message = stringResource(R.string.sav_list_empty), modifier = modifier)
        is SavUiState.Error ->
            EmptyState(
                message = stringResource(R.string.sav_list_error_title),
                errorMessage = state.message.asString(),
                onRefresh = onRetry,
                modifier = modifier,
            )
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "SAV — chargement")
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewSavLoading() {
    PrestaFlowTheme { SavScreen(state = SavUiState.Loading) }
}

@Preview(showBackground = true, name = "SAV — vide")
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewSavEmpty() {
    PrestaFlowTheme { SavScreen(state = SavUiState.Empty) }
}

@Preview(showBackground = true, name = "SAV — erreur")
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewSavError() {
    PrestaFlowTheme {
        SavScreen(state = SavUiState.Error(UiText.Dynamic("Connexion à la boutique impossible.")))
    }
}
