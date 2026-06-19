package com.rebuildit.prestaflow.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderItem
import com.rebuildit.prestaflow.ui.components.AvatarInitials
import com.rebuildit.prestaflow.ui.components.OrderStatusBadge
import com.rebuildit.prestaflow.ui.components.formatCurrency
import com.rebuildit.prestaflow.ui.components.formatTimestamp
import com.rebuildit.prestaflow.ui.theme.Dimensions
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun OrderDetailRoute(
    onBackClick: () -> Unit,
    viewModel: OrderDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    OrderDetailScreen(
        state = state,
        actionState = actionState,
        onBackClick = onBackClick,
        onUpdateStatus = viewModel::updateStatus,
        onUpdateTracking = viewModel::updateTracking,
        onConsumeFeedback = viewModel::consumeActionFeedback,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
// Composable écran : paramètres = callbacks requis, longueur inhérente à la mise en page Compose
@Suppress("LongParameterList", "LongMethod")
@Composable
fun OrderDetailScreen(
    state: OrderDetailUiState,
    actionState: OrderActionState = OrderActionState(),
    onBackClick: () -> Unit,
    onUpdateStatus: (String) -> Unit = {},
    onUpdateTracking: (String) -> Unit = {},
    onConsumeFeedback: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val backDesc = stringResource(R.string.content_description_back)

    LaunchedEffect(actionState.message, actionState.error) {
        val feedback = actionState.error ?: actionState.message
        if (feedback != null) {
            snackbarHostState.showSnackbar(feedback)
            onConsumeFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.order_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backDesc,
                        )
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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when (state) {
                OrderDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                OrderDetailUiState.Error -> {
                    val retryDesc = stringResource(R.string.content_description_retry)
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.order_detail_error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.semantics { contentDescription = retryDesc },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                            )
                        }
                    }
                }

                is OrderDetailUiState.Success -> {
                    OrderDetailContent(
                        order = state.order,
                        actionInProgress = actionState.inProgress,
                        onUpdateStatus = onUpdateStatus,
                        onUpdateTracking = onUpdateTracking,
                    )
                }
            }
        }
    }
}

@Suppress("LongMethod") // Composable contenu de détail : chaque bloc est une card métier distincte, extraction nuirait à la lisibilité
@Composable
fun OrderDetailContent(
    order: Order,
    actionInProgress: Boolean = false,
    onUpdateStatus: (String) -> Unit = {},
    onUpdateTracking: (String) -> Unit = {},
) {
    var showStatusDialog by remember { mutableStateOf(false) }
    var showTrackingDialog by remember { mutableStateOf(false) }

    if (showStatusDialog) {
        TextInputDialog(
            title = stringResource(R.string.order_detail_change_status),
            label = stringResource(R.string.order_detail_status_label),
            initialValue = order.status,
            confirmLabel = stringResource(R.string.order_detail_status_update),
            cancelLabel = stringResource(R.string.order_detail_cancel),
            onConfirm = {
                showStatusDialog = false
                onUpdateStatus(it)
            },
            onDismiss = { showStatusDialog = false },
        )
    }

    if (showTrackingDialog) {
        TextInputDialog(
            title = stringResource(R.string.order_detail_tracking_title),
            label = stringResource(R.string.order_detail_tracking_label),
            initialValue = order.shipping?.trackingNumber.orEmpty(),
            confirmLabel = stringResource(R.string.order_detail_tracking_save),
            cancelLabel = stringResource(R.string.order_detail_cancel),
            onConfirm = {
                showTrackingDialog = false
                onUpdateTracking(it)
            },
            onDismiss = { showTrackingDialog = false },
        )
    }

    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingM),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
    ) {
        // Header Card : référence + statut + date + avatar client
        SoftCard {
            Column(modifier = Modifier.padding(Dimensions.cardPadding)) {
                // Référence + badge statut
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.order_detail_reference_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = order.reference,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    OrderStatusBadge(status = order.status)
                }
                Spacer(modifier = Modifier.height(Dimensions.spacingM))
                Text(
                    text = formatTimestamp(order.updatedAtIso, dateFormatter) ?: order.updatedAtIso,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Customer Card : avatar + nom + email (si disponible)
        SectionCard(
            title = stringResource(R.string.order_detail_customer_section),
            icon = Icons.Outlined.Person,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarInitials(name = order.customerName.ifBlank { order.reference })
                Text(
                    text = order.customerName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Shipping Card
        if (order.shipping != null) {
            SectionCard(
                title = stringResource(R.string.order_detail_shipping_section),
                icon = Icons.Outlined.LocalShipping,
            ) {
                Text(
                    text = order.shipping.carrierName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (order.shipping.trackingNumber != null) {
                    Text(
                        text = stringResource(R.string.order_detail_tracking_display, order.shipping.trackingNumber),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        // Items Card
        if (order.items.isNotEmpty()) {
            SectionCard(
                title = stringResource(R.string.order_detail_items_section, order.items.size),
                icon = Icons.Outlined.ShoppingBag,
            ) {
                order.items.forEach { item ->
                    OrderItemRow(item = item, currency = order.currency)
                    if (item != order.items.last()) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        // Totals Card — fond surface, total en primary bold
        SoftCard {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Dimensions.cardPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.order_detail_total_paid),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatCurrency(order.totalPaid, order.currency),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Actions — boutons pill Stitch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        ) {
            OutlinedButton(
                onClick = { showStatusDialog = true },
                enabled = !actionInProgress,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
            ) {
                Text(stringResource(R.string.order_detail_change_status))
            }
            Button(
                onClick = { showTrackingDialog = true },
                enabled = !actionInProgress,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
            ) {
                Text(stringResource(R.string.order_detail_tracking_button))
            }
        }
    }
}

// Dialog générique : titres, labels et callbacks distincts, non fusionnables sans perdre la clarté
@Suppress("LongParameterList")
@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel)
            }
        },
    )
}

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    SoftCard {
        Column(modifier = Modifier.padding(Dimensions.cardPadding)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimensions.iconSizeMedium),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(Dimensions.spacingM))
            content()
        }
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    colors: CardColors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = { content() },
    )
}

// StatusBadge conservé pour compatibilité — délègue vers OrderStatusBadge (SharedComponents)
@Composable
fun StatusBadge(status: String) {
    com.rebuildit.prestaflow.ui.components.OrderStatusBadge(status = status)
}

@Composable
fun OrderItemRow(
    item: OrderItem,
    currency: String,
) {
    val qtyLabel = stringResource(R.string.order_detail_qty_label, item.quantity)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = qtyLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatCurrency(item.price, currency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
