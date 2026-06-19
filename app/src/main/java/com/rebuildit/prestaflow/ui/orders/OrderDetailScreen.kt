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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderItem
import com.rebuildit.prestaflow.domain.orders.model.OrderShipping
import java.text.NumberFormat
import java.util.Currency

@Composable
fun OrderDetailRoute(
    onBackClick: () -> Unit,
    viewModel: OrderDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    OrderDetailScreen(
        state = state,
        actionState = actionState,
        onBackClick = onBackClick,
        onUpdateStatus = viewModel::updateStatus,
        onUpdateTracking = viewModel::updateTracking,
        onConsumeFeedback = viewModel::consumeActionFeedback
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    state: OrderDetailUiState,
    actionState: OrderActionState = OrderActionState(),
    onBackClick: () -> Unit,
    onUpdateStatus: (String) -> Unit = {},
    onUpdateTracking: (String) -> Unit = {},
    onConsumeFeedback: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }

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
                title = { Text("Order Details") }, // Should be localized
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (state) {
                OrderDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                OrderDetailUiState.Error -> {
                    Text(
                        text = "Error loading order",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is OrderDetailUiState.Success -> {
                    OrderDetailContent(
                        order = state.order,
                        actionInProgress = actionState.inProgress,
                        onUpdateStatus = onUpdateStatus,
                        onUpdateTracking = onUpdateTracking
                    )
                }
            }
        }
    }
}

@Composable
fun OrderDetailContent(
    order: Order,
    actionInProgress: Boolean = false,
    onUpdateStatus: (String) -> Unit = {},
    onUpdateTracking: (String) -> Unit = {}
) {
    var showStatusDialog by remember { mutableStateOf(false) }
    var showTrackingDialog by remember { mutableStateOf(false) }

    if (showStatusDialog) {
        TextInputDialog(
            title = "Change status",
            label = "Status",
            initialValue = order.status,
            confirmLabel = "Update",
            onConfirm = {
                showStatusDialog = false
                onUpdateStatus(it)
            },
            onDismiss = { showStatusDialog = false }
        )
    }

    if (showTrackingDialog) {
        TextInputDialog(
            title = "Tracking number",
            label = "Tracking number",
            initialValue = order.shipping?.trackingNumber.orEmpty(),
            confirmLabel = "Save",
            onConfirm = {
                showTrackingDialog = false
                onUpdateTracking(it)
            },
            onDismiss = { showTrackingDialog = false }
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Card
        SoftCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = order.reference,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatusBadge(status = order.status)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = order.updatedAtIso, // Format this properly
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Customer Card
        SectionCard(
            title = "Customer",
            icon = Icons.Outlined.Person
        ) {
            Text(
                text = order.customerName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            // Add email if available in model
        }

        // Shipping Card
        if (order.shipping != null) {
            SectionCard(
                title = "Shipping",
                icon = Icons.Outlined.LocalShipping
            ) {
                Text(
                    text = order.shipping.carrierName,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (order.shipping.trackingNumber != null) {
                    Text(
                        text = "Tracking: ${order.shipping.trackingNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Items Card
        if (order.items.isNotEmpty()) {
            SectionCard(
                title = "Items (${order.items.size})",
                icon = Icons.Outlined.ShoppingBag
            ) {
                order.items.forEach { item ->
                    OrderItemRow(item = item, currency = order.currency)
                    if (item != order.items.last()) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        // Totals Card
        SoftCard(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Paid",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = formatCurrency(order.totalPaid, order.currency),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { showStatusDialog = true },
                enabled = !actionInProgress,
                modifier = Modifier.weight(1f)
            ) {
                Text("Change status")
            }
            Button(
                onClick = { showTrackingDialog = true },
                enabled = !actionInProgress,
                modifier = Modifier.weight(1f)
            ) {
                Text("Tracking")
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
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
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    SoftCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    ),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp), // Soft corners
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = { content() }
    )
}

@Composable
fun StatusBadge(status: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun OrderItemRow(item: OrderItem, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Qty: ${item.quantity}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatCurrency(item.price, currency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatCurrency(amount: Double, currencyCode: String): String {
    val formatter = NumberFormat.getCurrencyInstance()
    runCatching { formatter.currency = Currency.getInstance(currencyCode) }
    return formatter.format(amount)
}
