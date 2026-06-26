package com.rebuildit.prestaflow.ui.orders

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter

/**
 * Layout deux colonnes pour tablette (WindowWidthSizeClass.Expanded).
 *
 * Approche choisie : Row à deux panneaux conditionnée par la taille de fenêtre,
 * sans ListDetailPaneScaffold de material3-adaptive.
 *
 * Raison : material3-adaptive (1.1.x) n'est pas inclus dans le Compose BOM 2025.01
 * et son API est encore en @ExperimentalMaterial3AdaptiveApi. Intégrer une alpha
 * séparée aurait requis des exclusions de versions et complexifié le NavGraph
 * existant (qui gère déjà la route orders/{orderId} pour le single-pane).
 * Un Row simple est suffisant, lisible et facilement maintenable.
 *
 * Fonctionnement :
 * - Le panneau gauche (40 % de la largeur) affiche la liste des commandes.
 * - Le panneau droit (60 %) affiche le détail de la commande sélectionnée,
 *   ou un placeholder si aucune commande n'est sélectionnée.
 * - [OrderDetailPaneViewModel] est scoped au composable parent via hiltViewModel()
 *   et reçoit l'orderId sélectionné via [OrderDetailPaneViewModel.selectOrder].
 */
@Suppress("LongMethod") // Two-pane layout : Row + deux panneaux, longueur inhérente au rendu adaptatif
@Composable
fun OrdersTwoPaneRoute(
    ordersViewModel: OrdersViewModel = hiltViewModel(),
    detailViewModel: OrderDetailPaneViewModel = hiltViewModel(),
) {
    val ordersUiState by ordersViewModel.uiState.collectAsStateWithLifecycle()
    val detailUiState by detailViewModel.uiState.collectAsStateWithLifecycle()
    val actionState by detailViewModel.actionState.collectAsStateWithLifecycle()
    val availableStatuses by detailViewModel.availableStatuses.collectAsStateWithLifecycle()

    Row(modifier = Modifier.fillMaxSize()) {
        // Panneau gauche : liste des commandes (40 %)
        Surface(
            modifier =
                Modifier
                    .weight(0.4f)
                    .fillMaxHeight(),
            color = MaterialTheme.colorScheme.background,
        ) {
            OrdersScreen(
                uiState = ordersUiState,
                onRefresh = { forceRemote -> ordersViewModel.refresh(forceRemote, notifyOnError = true) },
                onOrderClick = { orderId -> detailViewModel.selectOrder(orderId) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        VerticalDivider(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(1.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        // Panneau droit : détail commande (60 %)
        Surface(
            modifier =
                Modifier
                    .weight(0.6f)
                    .fillMaxHeight(),
            color = MaterialTheme.colorScheme.background,
        ) {
            OrdersDetailPane(
                state = detailUiState,
                actionState = actionState,
                availableStatuses = availableStatuses,
                onUpdateStatus = detailViewModel::updateStatus,
                onUpdateTracking = detailViewModel::updateTracking,
                onConsumeFeedback = detailViewModel::consumeActionFeedback,
            )
        }
    }
}

@Composable
private fun OrdersDetailPane(
    state: OrderDetailUiState,
    actionState: OrderActionState,
    availableStatuses: List<OrderStatusFilter>,
    onUpdateStatus: (String) -> Unit,
    onUpdateTracking: (String) -> Unit,
    onConsumeFeedback: () -> Unit,
) {
    when (state) {
        OrderDetailUiState.Loading -> {
            // Aucune commande sélectionnée : placeholder d'invite
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.orders_detail_pane_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OrderDetailUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.order_detail_error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        is OrderDetailUiState.Success -> {
            LaunchedEffect(actionState.message, actionState.error) {
                // Le snackbar n'est pas disponible dans le panneau droit sans Scaffold dédié.
                // Le feedback est consommé silencieusement ; une amélioration future
                // pourrait afficher un toast ou un chip d'état dans le panneau.
                if (actionState.message != null || actionState.error != null) {
                    onConsumeFeedback()
                }
            }
            OrderDetailContent(
                order = state.order,
                actionInProgress = actionState.inProgress,
                availableStatuses = availableStatuses,
                onUpdateStatus = onUpdateStatus,
                onUpdateTracking = onUpdateTracking,
            )
        }
    }
}
