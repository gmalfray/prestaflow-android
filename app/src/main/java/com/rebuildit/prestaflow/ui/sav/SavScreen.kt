package com.rebuildit.prestaflow.ui.sav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.sav.model.SavThread
import com.rebuildit.prestaflow.domain.sav.model.SavThreadStatus
import com.rebuildit.prestaflow.ui.components.AvatarInitials
import com.rebuildit.prestaflow.ui.components.EmptyState
import com.rebuildit.prestaflow.ui.components.ErrorRow
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.components.formatTimestamp
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SavRoute(
    onThreadClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SavViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Rattrapage : recharge la liste (et le compteur "à traiter" de la pastille du shell) au retour
    // sur cet écran, cf. KDoc de SavViewModel.onScreenResumed pour le contrat détaillé et le
    // pourquoi des DEUX déclencheurs (SavRoute est monté dans le sous-onglet SAV de
    // ClientsTabsRoute, démonté quand un autre sous-onglet est sélectionné — cf. le même commentaire
    // dans ClientsScreen.ClientsRoute).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onScreenResumed()
    }
    LaunchedEffect(Unit) {
        viewModel.onScreenResumed()
    }

    SavScreen(
        state = state,
        modifier = modifier,
        onRetry = viewModel::onRefresh,
        onRefresh = viewModel::onRefresh,
        onFilterChange = viewModel::onFilterChange,
        onLoadMore = viewModel::onLoadMore,
        onThreadClick = onThreadClick,
    )
}

@Suppress("LongParameterList")
@Composable
fun SavScreen(
    state: SavUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onFilterChange: (SavStatusFilter) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onThreadClick: (Long) -> Unit = {},
) {
    when (state) {
        SavUiState.Loading -> LoadingState(modifier)
        is SavUiState.Error ->
            EmptyState(
                message = stringResource(R.string.sav_list_error_title),
                errorMessage = state.message.asString(),
                onRefresh = onRetry,
                modifier = modifier,
            )
        is SavUiState.Content ->
            SavThreadList(
                modifier = modifier,
                state = state,
                onRefresh = onRefresh,
                onFilterChange = onFilterChange,
                onLoadMore = onLoadMore,
                onThreadClick = onThreadClick,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList", "LongMethod")
@Composable
private fun SavThreadList(
    state: SavUiState.Content,
    onRefresh: () -> Unit,
    onFilterChange: (SavStatusFilter) -> Unit,
    onLoadMore: () -> Unit,
    onThreadClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val errorMessage = state.error?.asString()
    val dateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        PullToRefreshBox(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (errorMessage != null) {
                    ErrorRow(message = errorMessage, onRefresh = onRefresh)
                }

                SavFilterRow(selected = state.filter, onFilterChange = onFilterChange)

                if (state.threads.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.sav_list_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                horizontal = Dimensions.screenEdgeMargin,
                                vertical = Dimensions.spacingM,
                            ),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(Dimensions.cardCornerRadius),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            ) {
                                Column {
                                    state.threads.forEachIndexed { index, thread ->
                                        SavThreadRow(
                                            thread = thread,
                                            dateFormatter = dateFormatter,
                                            onClick = { onThreadClick(thread.id) },
                                        )
                                        if (index < state.threads.lastIndex) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.surfaceContainer,
                                                thickness = 1.dp,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (state.hasNextPage) {
                            item {
                                SavLoadMoreButton(isLoading = state.isLoadingMore, onClick = onLoadMore)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavLoadMoreButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(Dimensions.spacingM),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Button(
                onClick = onClick,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
            ) {
                Text(text = stringResource(R.string.clients_load_more), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SavFilterRow(
    selected: SavStatusFilter,
    onFilterChange: (SavStatusFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingS),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
    ) {
        item {
            SavFilterChip(
                label = stringResource(R.string.sav_filter_all),
                selected = selected == SavStatusFilter.ALL,
                onClick = { onFilterChange(SavStatusFilter.ALL) },
            )
        }
        item {
            SavFilterChip(
                label = stringResource(R.string.sav_filter_open),
                selected = selected == SavStatusFilter.OPEN,
                onClick = { onFilterChange(SavStatusFilter.OPEN) },
            )
        }
        item {
            SavFilterChip(
                label = stringResource(R.string.sav_filter_awaiting_customer),
                selected = selected == SavStatusFilter.AWAITING_CUSTOMER_REPLY,
                onClick = { onFilterChange(SavStatusFilter.AWAITING_CUSTOMER_REPLY) },
            )
        }
        item {
            SavFilterChip(
                label = stringResource(R.string.sav_filter_awaiting_merchant),
                selected = selected == SavStatusFilter.AWAITING_MERCHANT_REPLY,
                onClick = { onFilterChange(SavStatusFilter.AWAITING_MERCHANT_REPLY) },
            )
        }
        item {
            SavFilterChip(
                label = stringResource(R.string.sav_filter_closed),
                selected = selected == SavStatusFilter.CLOSED,
                onClick = { onFilterChange(SavStatusFilter.CLOSED) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    )
}

@Composable
private fun SavThreadRow(
    thread: SavThread,
    dateFormatter: DateTimeFormatter,
    onClick: () -> Unit,
) {
    val displayName = thread.customerName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.sav_thread_unknown_customer)
    val lastMessage = formatTimestamp(thread.lastMessageAtIso ?: thread.dateAddedIso, dateFormatter)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(Dimensions.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AvatarInitials(name = displayName)
            // `toProcess` et NON `unread` : le drapeau « non lu » de PrestaShop n'est posé que
            // lorsqu'un employé ouvre le fil dans la vue back-office. Cette boutique traite son
            // SAV par mail, il ne l'est donc jamais : 449 fils sur 481 en production, fils clos
            // et déjà répondus compris. Le point rouge apparaissait sur toutes les lignes et
            // n'identifiait plus rien.
            if (thread.toProcess) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (thread.orderReference != null) {
                Text(
                    text = stringResource(R.string.sav_thread_order_reference, thread.orderReference),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (lastMessage != null) {
                Text(
                    text = lastMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SavStatusBadge(status = thread.status)
    }
}

@Composable
fun SavStatusBadge(
    status: SavThreadStatus,
    modifier: Modifier = Modifier,
) {
    val (bg, fg, label) =
        when (status) {
            SavThreadStatus.OPEN ->
                Triple(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    stringResource(R.string.sav_status_open),
                )
            SavThreadStatus.AWAITING_CUSTOMER_REPLY ->
                Triple(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                    stringResource(R.string.sav_status_awaiting_customer),
                )
            SavThreadStatus.AWAITING_MERCHANT_REPLY ->
                Triple(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    stringResource(R.string.sav_status_awaiting_merchant),
                )
            SavThreadStatus.CLOSED ->
                Triple(
                    MaterialTheme.colorScheme.surfaceContainer,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    stringResource(R.string.sav_status_closed),
                )
        }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(Dimensions.chipCornerRadius))
                .background(bg)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = label, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
    PrestaFlowTheme { SavScreen(state = SavUiState.Content(threads = emptyList())) }
}

@Preview(showBackground = true, name = "SAV — liste")
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewSavList() {
    PrestaFlowTheme {
        SavScreen(
            state =
                SavUiState.Content(
                    threads =
                        listOf(
                            SavThread(
                                id = 1L,
                                status = SavThreadStatus.AWAITING_MERCHANT_REPLY,
                                unread = true,
                                toProcess = true,
                                customerId = 88L,
                                customerName = "Camille Martin",
                                customerEmail = "camille@example.com",
                                orderId = 4021L,
                                orderReference = "ABCDEF123",
                                lastMessageAtIso = "2026-08-09 16:42:00",
                                dateAddedIso = "2026-08-01 10:03:00",
                                dateUpdatedIso = "2026-08-09 16:42:00",
                            ),
                            SavThread(
                                id = 2L,
                                status = SavThreadStatus.CLOSED,
                                unread = false,
                                toProcess = false,
                                customerId = null,
                                customerName = null,
                                customerEmail = null,
                                orderId = null,
                                orderReference = null,
                                lastMessageAtIso = "2026-08-05 09:00:00",
                                dateAddedIso = "2026-08-05 09:00:00",
                                dateUpdatedIso = "2026-08-05 09:00:00",
                            ),
                        ),
                    hasNextPage = true,
                ),
        )
    }
}

@Preview(showBackground = true, name = "SAV — erreur")
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewSavError() {
    PrestaFlowTheme {
        SavScreen(state = SavUiState.Error(UiText.Dynamic("Connexion à la boutique impossible.")))
    }
}
