package com.rebuildit.prestaflow.ui.products

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.products.model.Product
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ProductsRoute(
    modifier: Modifier = Modifier,
    viewModel: ProductsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProductsScreen(
        modifier = modifier,
        state = state,
        onRefresh = viewModel::onRefresh
    )
}

@Composable
fun ProductsScreen(
    state: ProductsUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val errorMessage = state.error?.asString()

    when {
        state.isLoading && state.products.isEmpty() -> LoadingState(modifier)
        state.products.isEmpty() -> EmptyState(modifier, errorMessage, onRefresh)
        else -> ProductList(
            modifier = modifier,
            products = state.products,
            isRefreshing = state.isRefreshing,
            errorMessage = errorMessage,
            onRefresh = onRefresh
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier,
    errorMessage: String?,
    onRefresh: () -> Unit
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(id = R.string.products_list_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                IconButton(onClick = onRefresh) {
                    Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun ProductList(
    modifier: Modifier,
    products: List<Product>,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit
) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance() }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }

    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(visible = isRefreshing, enter = fadeIn(), exit = fadeOut()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (errorMessage != null) {
            ErrorRow(message = errorMessage, onRefresh = onRefresh)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(products, key = { it.id }) { product ->
                ProductCard(
                    product = product,
                    currencyFormatter = currencyFormatter,
                    dateFormatter = dateFormatter
                )
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: Product,
    currencyFormatter: NumberFormat,
    dateFormatter: DateTimeFormatter
) {
    val priceText = remember(product.priceTaxIncl) {
        currencyFormatter.format(product.priceTaxIncl)
    }
    val updatedAt = remember(product.lastUpdatedIso) {
        formatProductTimestamp(product.lastUpdatedIso, dateFormatter)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!product.sku.isNullOrBlank()) {
                Text(
                    text = product.sku,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = priceText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(
                        id = if (product.active) R.string.products_status_active else R.string.products_status_inactive
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (product.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = stringResource(id = R.string.products_stock_label, product.stockQuantity),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (updatedAt != null) {
                Text(
                    text = updatedAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatProductTimestamp(value: String?, formatter: DateTimeFormatter): String? {
    if (value.isNullOrBlank()) return null

    val zone = ZoneId.systemDefault()

    val fromInstant = runCatching { Instant.parse(value) }
        .map { instant -> instant.atZone(zone).format(formatter) }
    if (fromInstant.isSuccess) {
        return fromInstant.getOrThrow()
    }

    val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
    patterns.forEach { pattern ->
        runCatching {
            LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern))
        }.map { localDateTime ->
            return localDateTime.atZone(zone).format(formatter)
        }
    }

    return value
}

@Composable
private fun ErrorRow(message: String, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRefresh) {
            Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
        }
    }
}
