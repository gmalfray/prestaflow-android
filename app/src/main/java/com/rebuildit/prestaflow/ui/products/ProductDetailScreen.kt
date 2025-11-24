package com.rebuildit.prestaflow.ui.products

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.products.model.Product
import java.text.NumberFormat

@Composable
fun ProductDetailRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProductDetailScreen(
        modifier = modifier,
        state = state,
        onBackClick = onBackClick,
        onUpdatePrice = viewModel::onUpdatePrice,
        onUpdateStock = viewModel::onUpdateStock,
        onToggleStatus = viewModel::onToggleStatus,
        onClearError = viewModel::clearError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    state: ProductDetailUiState,
    onBackClick: () -> Unit,
    onUpdatePrice: (Double) -> Unit,
    onUpdateStock: (Int) -> Unit,
    onToggleStatus: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val errorMessage = state.error?.asString()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.product?.name ?: stringResource(R.string.product_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = {
            if (errorMessage != null) {
                Snackbar(
                    action = {
                        TextButton(onClick = onClearError) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                ) {
                    Text(errorMessage)
                }
            }
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingContent(Modifier.padding(padding))
            state.product != null -> ProductContent(
                modifier = Modifier.padding(padding),
                product = state.product,
                isUpdating = state.isUpdating,
                onUpdatePrice = onUpdatePrice,
                onUpdateStock = onUpdateStock,
                onToggleStatus = onToggleStatus
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ProductContent(
    product: Product,
    isUpdating: Boolean,
    onUpdatePrice: (Double) -> Unit,
    onUpdateStock: (Int) -> Unit,
    onToggleStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance() }
    var priceText by remember(product.price) { mutableStateOf(product.price.toString()) }
    var stockText by remember(product.stock.quantity) { mutableStateOf(product.stock.quantity.toString()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Image
        if (product.images.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = product.images.first().url,
                    contentDescription = product.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }

        // Product Info Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.headlineSmall
                )
                if (product.reference.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.product_reference_label, product.reference),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Price Edit Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.product_price_section),
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.product_price_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUpdating
                )
                Button(
                    onClick = {
                        priceText.toDoubleOrNull()?.let { onUpdatePrice(it) }
                    },
                    enabled = !isUpdating && priceText.toDoubleOrNull() != null,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.product_update_price))
                }
            }
        }

        // Stock Edit Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.product_stock_section),
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedTextField(
                    value = stockText,
                    onValueChange = { stockText = it },
                    label = { Text(stringResource(R.string.product_stock_quantity_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUpdating
                )
                Button(
                    onClick = {
                        stockText.toIntOrNull()?.let { onUpdateStock(it) }
                    },
                    enabled = !isUpdating && stockText.toIntOrNull() != null,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.product_update_stock))
                }
            }
        }

        // Status Toggle Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.product_status_section),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(
                            if (product.active) R.string.products_status_active
                            else R.string.products_status_inactive
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = product.active,
                    onCheckedChange = { onToggleStatus() },
                    enabled = !isUpdating
                )
            }
        }

        if (isUpdating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
