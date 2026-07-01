package com.rebuildit.prestaflow.ui.orders

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.print.InvoicePrinter
import com.rebuildit.prestaflow.core.print.PrintMode
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderItem
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import com.rebuildit.prestaflow.ui.auth.PortraitCaptureActivity
import com.rebuildit.prestaflow.ui.components.AvatarInitials
import com.rebuildit.prestaflow.ui.components.OrderStatusBadge
import com.rebuildit.prestaflow.ui.components.formatCurrency
import com.rebuildit.prestaflow.ui.components.formatTimestamp
import com.rebuildit.prestaflow.ui.orders.components.StatusPickerDialog
import com.rebuildit.prestaflow.ui.settings.ThermalPrinterViewModel
import com.rebuildit.prestaflow.ui.theme.Dimensions
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

// Route : câblage des callbacks d'impression (facture/bordereau/thermique) + gestion des permissions
@Suppress("LongMethod")
@Composable
fun OrderDetailRoute(
    onBackClick: () -> Unit,
    onProductClick: (Long) -> Unit = {},
    viewModel: OrderDetailViewModel = hiltViewModel(),
    thermalViewModel: ThermalPrinterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val availableStatuses by viewModel.availableStatuses.collectAsStateWithLifecycle()
    val savedDevice by thermalViewModel.savedDevice.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingPrintOrder by remember { mutableStateOf<Order?>(null) }
    // Commande en attente d'impression thermique, le temps d'obtenir les permissions Bluetooth
    var pendingThermalOrder by remember { mutableStateOf<Order?>(null) }

    // Lance l'impression thermique du bordereau (permissions déjà vérifiées en amont)
    val startThermalPrint: (Order, String) -> Unit = { order, macAddress ->
        viewModel.fetchShippingLabelPdf { pdfBytes ->
            viewModel.reportFeedback(message = com.rebuildit.prestaflow.core.ui.UiText.Dynamic(context.getString(R.string.order_detail_thermal_connecting)))
            scope.launch {
                viewModel.printOnThermalPrinter(
                    context = context,
                    pdfBytes = pdfBytes,
                    macAddress = macAddress,
                    reference = order.reference,
                )
            }
        }
    }

    // Permissions Bluetooth (API 31+) — la lib ESC/POS appelle cancelDiscovery() qui exige
    // BLUETOOTH_SCAN en plus de BLUETOOTH_CONNECT. On les demande ensemble, puis on relance
    // l'impression de la commande mise en attente une fois tout accordé.
    val btPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            val order = pendingThermalOrder
            pendingThermalOrder = null
            val allGranted = BT_RUNTIME_PERMISSIONS.all { results[it] == true }
            val device = savedDevice
            if (allGranted && order != null && device != null) {
                startThermalPrint(order, device.address)
            } else if (!allGranted) {
                viewModel.reportFeedback(error = com.rebuildit.prestaflow.core.ui.UiText.FromResources(R.string.order_detail_thermal_bt_permission_denied))
            }
        }

    // Impression du bordereau : route automatiquement vers l'imprimante thermique si une est
    // configurée, sinon vers l'impression système (PrintManager / PDF).
    val printShippingLabel: (Order) -> Unit = { order ->
        val device = savedDevice
        if (device == null) {
            viewModel.fetchShippingLabelPdf { pdfBytes ->
                InvoicePrinter.print(
                    context = context,
                    pdfBytesList = listOf(pdfBytes),
                    jobName = "Bordereau ${order.reference}",
                    mode = PrintMode.ONE_PER_PAGE,
                )
            }
        } else {
            when {
                !isBluetooth(context) ->
                    viewModel.reportFeedback(error = com.rebuildit.prestaflow.core.ui.UiText.Dynamic(context.getString(R.string.order_detail_thermal_bt_disabled)))
                !hasBtPermissions(context) -> {
                    pendingThermalOrder = order
                    btPermissionLauncher.launch(BT_RUNTIME_PERMISSIONS)
                }
                else -> startThermalPrint(order, device.address)
            }
        }
    }

    if (pendingPrintOrder != null) {
        val order = pendingPrintOrder!!
        PrintModeDialog(
            onDismiss = { pendingPrintOrder = null },
            onModeSelected = { mode ->
                pendingPrintOrder = null
                viewModel.fetchInvoicePdf { pdfBytes ->
                    InvoicePrinter.print(
                        context = context,
                        pdfBytesList = listOf(pdfBytes),
                        jobName = "Facture ${order.reference}",
                        mode = mode,
                    )
                }
            },
        )
    }

    OrderDetailScreen(
        state = state,
        actionState = actionState,
        availableStatuses = availableStatuses,
        onBackClick = onBackClick,
        onProductClick = onProductClick,
        onUpdateStatus = viewModel::updateStatus,
        onUpdateTracking = viewModel::updateTracking,
        onConsumeFeedback = viewModel::consumeActionFeedback,
        onPrintInvoice = { order -> pendingPrintOrder = order },
        onPrintShippingLabel = printShippingLabel,
        onShareShippingLabel = { order ->
            viewModel.fetchShippingLabelPdf { pdfBytes ->
                shareShippingLabelPdf(context, pdfBytes, order.reference)
            }
        },
        onGenerateLabel = viewModel::onGenerateLabel,
    )
}

/**
 * Permissions Bluetooth runtime requises (API 31+) pour se connecter et imprimer sur
 * l'imprimante thermique. [android.Manifest.permission.BLUETOOTH_SCAN] est nécessaire car
 * la bibliothèque ESC/POS appelle `BluetoothAdapter.cancelDiscovery()` lors de la connexion.
 */
private val BT_RUNTIME_PERMISSIONS =
    arrayOf(
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.BLUETOOTH_SCAN,
    )

/**
 * Écrit les octets PDF dans le répertoire cache, puis déclenche un Intent de partage
 * via [FileProvider] (ouvre le sélecteur d'apps : Munbyn Print, lecteur PDF, e-mail…).
 */
private fun shareShippingLabelPdf(
    context: android.content.Context,
    pdfBytes: ByteArray,
    reference: String,
) {
    val file = java.io.File(context.cacheDir, "bordereau_$reference.pdf")
    file.writeBytes(pdfBytes)
    val uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    val chooser = Intent.createChooser(intent, context.getString(R.string.order_detail_share_chooser_title))
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/** Retourne vrai si le Bluetooth est disponible et activé sur l'appareil. */
private fun isBluetooth(context: android.content.Context): Boolean {
    val bm = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
    return bm?.adapter?.isEnabled == true
}

/**
 * Retourne vrai si toutes les permissions Bluetooth runtime ([BT_RUNTIME_PERMISSIONS]) sont
 * accordées. Sur API < 31, ces permissions n'existent pas — retourne toujours vrai.
 */
private fun hasBtPermissions(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return true
    return BT_RUNTIME_PERMISSIONS.all {
        context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

@OptIn(ExperimentalMaterial3Api::class)
// Composable écran : paramètres = callbacks requis, longueur inhérente à la mise en page Compose
@Suppress("LongParameterList", "LongMethod")
@Composable
fun OrderDetailScreen(
    state: OrderDetailUiState,
    actionState: OrderActionState = OrderActionState(),
    availableStatuses: List<OrderStatusFilter> = emptyList(),
    onBackClick: () -> Unit,
    onProductClick: (Long) -> Unit = {},
    onUpdateStatus: (String) -> Unit = {},
    onUpdateTracking: (String) -> Unit = {},
    onConsumeFeedback: () -> Unit = {},
    onPrintInvoice: (Order) -> Unit = {},
    onPrintShippingLabel: (Order) -> Unit = {},
    onShareShippingLabel: (Order) -> Unit = {},
    onGenerateLabel: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val backDesc = stringResource(R.string.content_description_back)
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(actionState.message, actionState.error) {
        val feedback = actionState.error ?: actionState.message
        if (feedback != null) {
            snackbarHostState.showSnackbar(feedback.resolve(context))
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
                        isGeneratingLabel = actionState.isGeneratingLabel,
                        availableStatuses = availableStatuses,
                        onProductClick = onProductClick,
                        onUpdateStatus = onUpdateStatus,
                        onUpdateTracking = onUpdateTracking,
                        onPrintInvoice = { onPrintInvoice(state.order) },
                        onPrintShippingLabel = { onPrintShippingLabel(state.order) },
                        onShareShippingLabel = { onShareShippingLabel(state.order) },
                        onGenerateLabel = onGenerateLabel,
                    )
                }
            }
        }
    }
}

// Composable contenu de détail : chaque bloc est une card métier distincte, extraction nuirait à la lisibilité
@Suppress("LongMethod", "LongParameterList")
@Composable
fun OrderDetailContent(
    order: Order,
    actionInProgress: Boolean = false,
    isGeneratingLabel: Boolean = false,
    availableStatuses: List<OrderStatusFilter> = emptyList(),
    onProductClick: (Long) -> Unit = {},
    onUpdateStatus: (String) -> Unit = {},
    onUpdateTracking: (String) -> Unit = {},
    onPrintInvoice: () -> Unit = {},
    onPrintShippingLabel: () -> Unit = {},
    onShareShippingLabel: () -> Unit = {},
    onGenerateLabel: () -> Unit = {},
) {
    var showStatusDialog by remember { mutableStateOf(false) }
    var showTrackingDialog by remember { mutableStateOf(false) }
    var pendingScannedTracking by remember { mutableStateOf<String?>(null) }

    val scanTrackingPrompt = stringResource(R.string.order_detail_scan_tracking_prompt)
    val scanLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            if (result != null && !result.contents.isNullOrBlank()) {
                pendingScannedTracking = extractTrackingNumber(result.contents)
            }
        }

    if (showStatusDialog) {
        // Trouver l'id du statut courant pour la pré-sélection (match sur le nom)
        val currentStatusId =
            availableStatuses.firstOrNull { it.name.equals(order.status, ignoreCase = true) }?.id
        StatusPickerDialog(
            statuses = availableStatuses,
            currentStatusId = currentStatusId,
            onConfirm = { statusId ->
                showStatusDialog = false
                onUpdateStatus(statusId)
            },
            onDismiss = { showStatusDialog = false },
        )
    }

    if (showTrackingDialog) {
        TrackingInputDialog(
            initialValue = order.shipping?.trackingNumber.orEmpty(),
            scannedValue = pendingScannedTracking,
            onScanBarcode = {
                scanLauncher.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(
                            // Formats 1D — étiquettes Colissimo colis, codes-barres classiques
                            ScanOptions.CODE_128,
                            ScanOptions.CODE_39,
                            ScanOptions.ITF,
                            ScanOptions.CODE_93,
                            ScanOptions.EAN_13,
                            // Formats 2D — DataMatrix La Poste (Lettre suivie, Courrier suivi),
                            // QR code (certains transporteurs alternatifs)
                            ScanOptions.DATA_MATRIX,
                            ScanOptions.QR_CODE,
                        )
                        .setPrompt(scanTrackingPrompt)
                        .setBeepEnabled(true)
                        .setBarcodeImageEnabled(false)
                        .setOrientationLocked(false)
                        .setCaptureActivity(PortraitCaptureActivity::class.java),
                )
            },
            onConfirm = { tracking ->
                showTrackingDialog = false
                pendingScannedTracking = null
                onUpdateTracking(tracking)
            },
            onDismiss = {
                showTrackingDialog = false
                pendingScannedTracking = null
            },
        )
    }

    val editStatusDesc = stringResource(R.string.order_detail_status_edit_content_description)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OrderStatusBadge(
                            status = order.status,
                            statusColor = order.statusColor,
                        )
                        IconButton(
                            onClick = { showStatusDialog = true },
                            enabled = !actionInProgress,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = editStatusDesc,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Dimensions.spacingM))
                Text(
                    text = formatTimestamp(order.createdAtIso, dateFormatter) ?: order.createdAtIso,
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
            val editTrackingDesc = stringResource(R.string.order_detail_tracking_edit_content_description)
            SectionCard(
                title = stringResource(R.string.order_detail_shipping_section),
                icon = Icons.Outlined.LocalShipping,
                headerAction = {
                    IconButton(
                        onClick = { showTrackingDialog = true },
                        enabled = !actionInProgress,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = editTrackingDesc,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
                    OrderItemRow(
                        item = item,
                        currency = order.currency,
                        onProductClick = onProductClick,
                    )
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

        // Bouton génération étiquette Colissimo — visible uniquement si pas encore d'étiquette
        if (!order.hasShippingLabel) {
            val generateLabelDesc = stringResource(R.string.order_detail_generate_label_content_description)
            Button(
                onClick = onGenerateLabel,
                enabled = !actionInProgress,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = generateLabelDesc },
                shape = RoundedCornerShape(50),
            ) {
                if (isGeneratingLabel) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimensions.iconSizeSmall),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Label,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.iconSizeSmall),
                    )
                }
                Spacer(modifier = Modifier.size(Dimensions.spacingS))
                Text(stringResource(R.string.order_detail_generate_label))
            }
        }

        // Bouton impression facture — visible uniquement si has_invoice
        if (order.hasInvoice) {
            Button(
                onClick = onPrintInvoice,
                enabled = !actionInProgress,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Print,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.iconSizeSmall),
                )
                Spacer(modifier = Modifier.size(Dimensions.spacingS))
                Text(stringResource(R.string.order_detail_print_invoice))
            }
        }

        // Boutons bordereau — visibles uniquement si has_shipping_label
        if (order.hasShippingLabel) {
            // Impression du bordereau : route vers l'imprimante thermique si configurée,
            // sinon vers l'impression système (PrintManager / PDF). Décision dans la Route.
            OutlinedButton(
                onClick = onPrintShippingLabel,
                enabled = !actionInProgress,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalShipping,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.iconSizeSmall),
                )
                Spacer(modifier = Modifier.size(Dimensions.spacingS))
                Text(stringResource(R.string.order_detail_print_shipping_label))
            }

            // Partage du PDF (ouvrir avec Munbyn Print, visionneuse PDF, etc.)
            val shareDesc = stringResource(R.string.order_detail_share_shipping_label_content_description)
            OutlinedButton(
                onClick = onShareShippingLabel,
                enabled = !actionInProgress,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = shareDesc },
                shape = RoundedCornerShape(50),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.iconSizeSmall),
                )
                Spacer(modifier = Modifier.size(Dimensions.spacingS))
                Text(stringResource(R.string.order_detail_share_shipping_label))
            }
        }
    }
}

/**
 * Dialogue Material3 permettant de choisir le mode d'impression avant de lancer le travail.
 * Deux options : deux par page (paysage) ou une par page (portrait).
 */
@Composable
fun PrintModeDialog(
    onDismiss: () -> Unit,
    onModeSelected: (PrintMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.print_mode_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
                TextButton(
                    onClick = { onModeSelected(PrintMode.TWO_UP) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.print_mode_two_up))
                }
                TextButton(
                    onClick = { onModeSelected(PrintMode.ONE_PER_PAGE) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.print_mode_one_per_page))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.print_mode_cancel))
            }
        },
    )
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

/**
 * Dialog de saisie/scan du numéro de suivi.
 *
 * Affiche un [OutlinedTextField] pré-rempli avec [initialValue]. L'icône caméra (trailing)
 * déclenche [onScanBarcode]. Quand [scannedValue] change (résultat ZXing), le champ est mis
 * à jour automatiquement — l'utilisateur peut encore éditer avant de confirmer.
 */
@Composable
private fun TrackingInputDialog(
    initialValue: String,
    scannedValue: String?,
    onScanBarcode: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    val scanDesc = stringResource(R.string.order_detail_scan_tracking_content_description)

    LaunchedEffect(scannedValue) {
        if (scannedValue != null) {
            value = scannedValue
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.order_detail_tracking_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.order_detail_tracking_label)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = onScanBarcode) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = scanDesc,
                        )
                    }
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
            ) {
                Text(stringResource(R.string.order_detail_tracking_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.order_detail_cancel))
            }
        },
    )
}

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    SoftCard {
        Column(modifier = Modifier.padding(Dimensions.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.weight(1f),
                )
                if (headerAction != null) {
                    headerAction()
                }
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
    onProductClick: (Long) -> Unit = {},
) {
    val qtyLabel = stringResource(R.string.order_detail_qty_label, item.quantity)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onProductClick(item.productId) },
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrderItemThumbnail(imageUrl = item.imageUrl, contentDescription = item.name)
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

/** Miniature de l'image produit pour une ligne d'article (ou placeholder neutre). */
@Composable
private fun OrderItemThumbnail(
    imageUrl: String?,
    contentDescription: String,
) {
    val shape = RoundedCornerShape(Dimensions.chipCornerRadius)
    val base =
        Modifier
            .size(48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    if (imageUrl != null) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = base,
        )
    } else {
        Box(modifier = base)
    }
}
