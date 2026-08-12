package com.rebuildit.prestaflow.ui.reviews

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.rebuildit.prestaflow.domain.reviews.ReviewRejectionReason
import com.rebuildit.prestaflow.domain.reviews.model.Review
import com.rebuildit.prestaflow.ui.components.EmptyState
import com.rebuildit.prestaflow.ui.components.ErrorRow
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme

@Composable
fun ReviewsRoute(
    modifier: Modifier = Modifier,
    viewModel: ReviewsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()

    // Rattrapage : recharge la liste (et le compteur "en attente" de la pastille du shell) au
    // retour sur cet écran, cf. KDoc de ReviewsViewModel.onScreenResumed pour le contrat détaillé et
    // le pourquoi des DEUX déclencheurs (ReviewsRoute est monté dans le sous-onglet Avis de
    // ClientsTabsRoute, démonté quand un autre sous-onglet est sélectionné — cf. le même commentaire
    // dans ClientsScreen.ClientsRoute).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onScreenResumed()
    }
    LaunchedEffect(Unit) {
        viewModel.onScreenResumed()
    }

    ReviewsScreen(
        state = state,
        actionState = actionState,
        modifier = modifier,
        onRetry = viewModel::onRefresh,
        onRefresh = viewModel::onRefresh,
        onLoadMore = viewModel::onLoadMore,
        onPublish = viewModel::onPublish,
        onTrash = viewModel::onTrash,
        onReply = viewModel::onReply,
        onConsumeFeedback = viewModel::consumeActionFeedback,
    )
}

@Suppress("LongParameterList")
@Composable
fun ReviewsScreen(
    state: ReviewsUiState,
    modifier: Modifier = Modifier,
    actionState: ReviewActionState = ReviewActionState(),
    onRetry: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onPublish: (Long) -> Unit = {},
    onTrash: (Long, String) -> Unit = { _, _ -> },
    onReply: (Long, String) -> Unit = { _, _ -> },
    onConsumeFeedback: () -> Unit = {},
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionState.message, actionState.error) {
        val feedback = actionState.error ?: actionState.message
        if (feedback != null) {
            snackbarHostState.showSnackbar(feedback.resolve(context))
            onConsumeFeedback()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            ReviewsUiState.Loading -> LoadingState()
            is ReviewsUiState.Error ->
                EmptyState(
                    message = stringResource(R.string.reviews_list_error_title),
                    errorMessage = state.message.asString(),
                    onRefresh = onRetry,
                )
            is ReviewsUiState.Content ->
                ReviewsList(
                    state = state,
                    actionInProgress = actionState.inProgress,
                    onRefresh = onRefresh,
                    onLoadMore = onLoadMore,
                    onPublish = onPublish,
                    onTrash = onTrash,
                    onReply = onReply,
                )
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter)) { data ->
            Snackbar(snackbarData = data)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
private fun ReviewsList(
    state: ReviewsUiState.Content,
    actionInProgress: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onPublish: (Long) -> Unit,
    onTrash: (Long, String) -> Unit,
    onReply: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val errorMessage = state.error?.asString()

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

                if (state.reviews.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.reviews_list_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingM),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
                    ) {
                        items(state.reviews, key = { it.id }) { review ->
                            ReviewCard(
                                review = review,
                                actionInProgress = actionInProgress,
                                onPublish = { onPublish(review.id) },
                                onTrash = { reason -> onTrash(review.id, reason) },
                                onReply = { reply -> onReply(review.id, reply) },
                            )
                        }
                        if (state.hasNextPage) {
                            item {
                                ReviewsLoadMoreButton(isLoading = state.isLoadingMore, onClick = onLoadMore)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewsLoadMoreButton(
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

@Suppress("LongMethod")
@Composable
private fun ReviewCard(
    review: Review,
    actionInProgress: Boolean,
    onPublish: () -> Unit,
    onTrash: (String) -> Unit,
    onReply: (String) -> Unit,
) {
    var showTrashDialog by rememberSaveable { mutableStateOf(false) }
    var showReplyDialog by rememberSaveable { mutableStateOf(false) }

    if (showTrashDialog) {
        ReviewTrashDialog(
            onConfirm = { reason ->
                showTrashDialog = false
                onTrash(reason)
            },
            onDismiss = { showTrashDialog = false },
        )
    }
    if (showReplyDialog) {
        ReviewReplyDialog(
            initialValue = review.reply.orEmpty(),
            onConfirm = { reply ->
                showReplyDialog = false
                onReply(reply)
            },
            onDismiss = { showReplyDialog = false },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(Dimensions.cardPadding), verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(FULL_GRADE) { index ->
                    Icon(
                        imageVector = if (index < review.grade) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimensions.iconSizeSmall),
                    )
                }
            }

            if (!review.title.isNullOrBlank()) {
                Text(text = review.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(text = review.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

            Text(
                text =
                    if (review.productName != null) {
                        stringResource(R.string.reviews_author_for_product, review.authorName, review.productName)
                    } else {
                        review.authorName
                    },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!review.reply.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.reviews_existing_reply, review.reply),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Dimensions.spacingS),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
            ) {
                Button(onClick = onPublish, enabled = !actionInProgress, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.reviews_action_publish), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(onClick = { showReplyDialog = true }, enabled = !actionInProgress) {
                    Icon(Icons.Outlined.Reply, contentDescription = null, modifier = Modifier.size(Dimensions.iconSizeSmall))
                }
                OutlinedButton(onClick = { showTrashDialog = true }, enabled = !actionInProgress) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(Dimensions.iconSizeSmall))
                }
            }
        }
    }
}

private const val FULL_GRADE = 5

/**
 * Motif de rejet OBLIGATOIRE (article L111-7-2, ≥ [ReviewRejectionReason.MIN_LENGTH] caractères) —
 * SEULE porte d'entrée pour rejeter un avis dans cette UI : pas de swipe, pas de bouton direct
 * dans la liste (cf. [ReviewCard]). Le bouton de confirmation reste désactivé tant que le motif
 * n'est pas valide.
 */
@Composable
private fun ReviewTrashDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    val isValid = ReviewRejectionReason.isValid(reason)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reviews_trash_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)) {
                Text(
                    text = stringResource(R.string.reviews_trash_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.reviews_trash_dialog_placeholder)) },
                    isError = reason.isNotEmpty() && !isValid,
                    supportingText = {
                        Text(
                            stringResource(
                                R.string.reviews_trash_dialog_char_count,
                                reason.trim().length,
                                ReviewRejectionReason.MIN_LENGTH,
                            ),
                        )
                    },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim()) }, enabled = isValid) {
                Text(stringResource(R.string.reviews_trash_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reviews_trash_dialog_cancel))
            }
        },
    )
}

@Composable
private fun ReviewReplyDialog(
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reply by rememberSaveable { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reviews_reply_dialog_title)) },
        text = {
            OutlinedTextField(
                value = reply,
                onValueChange = { reply = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.reviews_reply_dialog_placeholder)) },
                minLines = 3,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reply.trim()) }, enabled = reply.isNotBlank()) {
                Text(stringResource(R.string.reviews_reply_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reviews_reply_dialog_cancel))
            }
        },
    )
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
    PrestaFlowTheme { ReviewsScreen(state = ReviewsUiState.Content(reviews = emptyList())) }
}

@Preview(showBackground = true, name = "Avis — file de modération")
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewReviewsList() {
    PrestaFlowTheme {
        ReviewsScreen(
            state =
                ReviewsUiState.Content(
                    reviews =
                        listOf(
                            Review(
                                id = 812L,
                                productId = 305L,
                                productName = "Bougie parfumée Lavande",
                                authorName = "Julie M.",
                                authorEmail = "julie@example.com",
                                grade = 4,
                                title = "Très satisfaite",
                                content = "Odeur agréable, tient longtemps.",
                                verifiedBuyer = true,
                                validated = false,
                                deleted = false,
                                reply = null,
                                rejectionReason = null,
                                dateAddedIso = "2026-08-10 18:22:00",
                            ),
                        ),
                ),
        )
    }
}

@Preview(showBackground = true, name = "Avis — erreur")
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewReviewsError() {
    PrestaFlowTheme {
        ReviewsScreen(state = ReviewsUiState.Error(UiText.Dynamic("Connexion à la boutique impossible.")))
    }
}
