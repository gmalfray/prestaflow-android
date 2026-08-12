package com.rebuildit.prestaflow.ui.sav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.sav.model.SavMessage
import com.rebuildit.prestaflow.domain.sav.model.SavMessageAuthor
import com.rebuildit.prestaflow.domain.sav.model.SavThreadDetail
import com.rebuildit.prestaflow.domain.sav.model.SavThreadStatus
import com.rebuildit.prestaflow.ui.components.NotFoundState
import com.rebuildit.prestaflow.ui.components.formatTimestamp
import com.rebuildit.prestaflow.ui.sav.components.SavStatusPickerDialog
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SavThreadDetailRoute(
    onBackClick: () -> Unit,
    viewModel: SavThreadDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()

    SavThreadDetailScreen(
        state = state,
        actionState = actionState,
        onBackClick = onBackClick,
        onRetry = viewModel::onRetry,
        onStatusChange = viewModel::updateStatus,
        onSendReply = viewModel::sendReply,
        onConsumeFeedback = viewModel::consumeActionFeedback,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList", "LongMethod")
@Composable
fun SavThreadDetailScreen(
    state: SavThreadDetailUiState,
    onBackClick: () -> Unit,
    actionState: SavThreadActionState = SavThreadActionState(),
    onRetry: () -> Unit = {},
    onStatusChange: (SavThreadStatus) -> Unit = {},
    onSendReply: (String) -> Unit = {},
    onConsumeFeedback: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showStatusDialog by rememberSaveable { mutableStateOf(false) }
    // Réponse en cours de rédaction, en attente de confirmation explicite avant envoi
    // (⚠️ appeler onSendReply DÉCLENCHE l'envoi d'un vrai e-mail — jamais implicite).
    var pendingReply by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(actionState.message, actionState.error) {
        val feedback = actionState.error ?: actionState.message
        if (feedback != null) {
            snackbarHostState.showSnackbar(feedback.resolve(context))
            onConsumeFeedback()
        }
    }

    val successState = state as? SavThreadDetailUiState.Success

    if (showStatusDialog && successState != null) {
        SavStatusPickerDialog(
            currentStatus = successState.detail.thread.status,
            onConfirm = { status ->
                showStatusDialog = false
                onStatusChange(status)
            },
            onDismiss = { showStatusDialog = false },
        )
    }

    val pending = pendingReply
    if (pending != null && successState != null) {
        SavReplyConfirmDialog(
            message = pending,
            customerEmail = successState.detail.thread.customerEmail,
            onConfirm = {
                onSendReply(pending)
                pendingReply = null
            },
            onDismiss = { pendingReply = null },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(successState?.detail?.thread?.customerName ?: stringResource(R.string.sav_thread_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
                actions = {
                    if (successState != null) {
                        IconButton(onClick = { showStatusDialog = true }, enabled = !actionState.inProgress) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.sav_thread_change_status_content_description),
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (state) {
                SavThreadDetailUiState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                SavThreadDetailUiState.Error ->
                    NotFoundState(
                        message = stringResource(R.string.sav_thread_error),
                        onBackClick = onRetry,
                    )
                is SavThreadDetailUiState.Success ->
                    SavThreadDetailContent(
                        detail = state.detail,
                        actionInProgress = actionState.inProgress,
                        onSendReply = { pendingReply = it },
                    )
            }
        }
    }
}

@Composable
private fun SavThreadDetailContent(
    detail: SavThreadDetail,
    actionInProgress: Boolean,
    onSendReply: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }

    Column(modifier = Modifier.fillMaxSize()) {
        // En-tête : statut + commande liée
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimensions.screenEdgeMargin),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SavStatusBadge(status = detail.thread.status)
            if (detail.thread.orderReference != null) {
                Text(
                    text = stringResource(R.string.sav_thread_order_reference, detail.thread.orderReference),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingS),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        ) {
            itemsIndexed(detail.messages, key = { _, message -> message.id }) { _, message ->
                SavMessageBubble(message = message, dateFormatter = dateFormatter)
            }
        }

        // Composer de réponse — n'envoie RIEN directement : ouvre la confirmation explicite
        // (cf. SavThreadDetailScreen § pendingReply). Aucun brouillon auto-expédié.
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimensions.screenEdgeMargin),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.sav_thread_reply_placeholder)) },
                enabled = !actionInProgress,
            )
            IconButton(
                onClick = {
                    val trimmed = draft.trim()
                    if (trimmed.isNotEmpty()) {
                        onSendReply(trimmed)
                        draft = ""
                    }
                },
                enabled = !actionInProgress && draft.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = stringResource(R.string.sav_thread_reply_send_content_description),
                )
            }
        }
    }
}

@Composable
private fun SavMessageBubble(
    message: SavMessage,
    dateFormatter: DateTimeFormatter,
) {
    val isEmployee = message.author == SavMessageAuthor.EMPLOYEE
    val alignment = if (isEmployee) Alignment.End else Alignment.Start
    val bubbleColor = if (isEmployee) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest
    val textColor = if (isEmployee) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val authorLabel =
        when {
            isEmployee && !message.employeeName.isNullOrBlank() -> message.employeeName
            isEmployee -> stringResource(R.string.sav_thread_author_merchant)
            else -> stringResource(R.string.sav_thread_author_customer)
        }
    val date = formatTimestamp(message.dateAddedIso, dateFormatter)

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(Dimensions.cardCornerRadius),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
        ) {
            Column(modifier = Modifier.padding(Dimensions.cardPadding)) {
                Text(
                    text = authorLabel.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor.copy(alpha = 0.75f),
                )
                Text(
                    text = message.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
            }
        }
        if (date != null) {
            Text(
                text = date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

/**
 * Dernier garde-fou avant l'envoi : rappelle explicitement que c'est un e-mail réel à une vraie
 * cliente. Aucun bouton de rejet rapide ni de raccourci — l'utilisatrice doit lire l'adresse et
 * le contenu avant de confirmer.
 */
@Composable
private fun SavReplyConfirmDialog(
    message: String,
    customerEmail: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sav_thread_reply_confirm_title)) },
        text = {
            // Le connecteur émet TOUJOURS `customer.email` (chaîne vide si le fil n'a pas
            // d'adresse, jamais null) — tester la nullité seule afficherait « envoyé à  » avec
            // une adresse vide, alors que le connecteur répondrait `email_sent: false`. Cf.
            // SavService::formatThreadRow().
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
                Text(
                    text =
                        if (!customerEmail.isNullOrBlank()) {
                            stringResource(R.string.sav_thread_reply_confirm_recipient, customerEmail)
                        } else {
                            stringResource(R.string.sav_thread_reply_confirm_recipient_unknown)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.sav_thread_reply_confirm_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sav_thread_reply_confirm_cancel))
            }
        },
    )
}

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Fil SAV — détail")
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewSavThreadDetail() {
    PrestaFlowTheme {
        SavThreadDetailScreen(
            state =
                SavThreadDetailUiState.Success(
                    SavThreadDetail(
                        thread =
                            com.rebuildit.prestaflow.domain.sav.model.SavThread(
                                id = 154L,
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
                        messages =
                            listOf(
                                SavMessage(
                                    id = 512L,
                                    author = SavMessageAuthor.CUSTOMER,
                                    employeeName = null,
                                    message = "Bonjour, ma commande n'est toujours pas arrivée.",
                                    private = false,
                                    read = true,
                                    dateAddedIso = "2026-08-01 10:03:00",
                                ),
                                SavMessage(
                                    id = 513L,
                                    author = SavMessageAuthor.EMPLOYEE,
                                    employeeName = "Marina",
                                    message = "Bonjour, votre colis a été retardé, il arrive sous 48h.",
                                    private = false,
                                    read = true,
                                    dateAddedIso = "2026-08-01 14:10:00",
                                ),
                            ),
                    ),
                ),
            onBackClick = {},
        )
    }
}
