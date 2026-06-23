package com.rebuildit.prestaflow.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.ProductStock
import com.rebuildit.prestaflow.domain.products.model.StockFilter
import com.rebuildit.prestaflow.ui.components.EmptyState
import com.rebuildit.prestaflow.ui.components.ErrorRow
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.components.SearchField
import com.rebuildit.prestaflow.ui.components.SectionHeader
import com.rebuildit.prestaflow.ui.components.ShopSwitcherChip
import com.rebuildit.prestaflow.ui.settings.ShopsViewModel
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import java.text.NumberFormat

/**
 * Seuil de stock faible local (fallback) — utilisé quand le backend ne fournit pas
 * `stock.is_low`. Si l'API retourne `is_low`, ce seuil est ignoré.
 */
private const val LOW_STOCK_THRESHOLD_FALLBACK = 5

@Composable
fun ProductsRoute(
    onProductClick: (Long) -> Unit = {},
    onAddShop: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProductsViewModel = hiltViewModel(),
    shopsViewModel: ShopsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connections by shopsViewModel.connections.collectAsStateWithLifecycle()
    ProductsScreen(
        modifier = modifier,
        state = state,
        connections = connections,
        onRefresh = viewModel::onRefresh,
        onProductClick = onProductClick,
        onQueryChange = viewModel::onQueryChange,
        onSwitchShop = shopsViewModel::switchShop,
        onAddShop = onAddShop,
        onStockFilterSelected = viewModel::onStockFilterSelected,
    )
}

@Composable
fun ProductsScreen(
    state: ProductsUiState,
    onRefresh: () -> Unit,
    onProductClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    connections: List<ShopConnection> = emptyList(),
    onQueryChange: (String) -> Unit = {},
    onSwitchShop: (String) -> Unit = {},
    onAddShop: () -> Unit = {},
    onStockFilterSelected: (StockFilter) -> Unit = {},
) {
    val errorMessage = state.error?.asString()

    when {
        state.isLoading && state.products.isEmpty() -> LoadingState(modifier)
        state.products.isEmpty() ->
            EmptyState(
                message = stringResource(R.string.products_list_empty),
                modifier = modifier,
                errorMessage = errorMessage,
                onRefresh = onRefresh,
            )
        else ->
            ProductList(
                modifier = modifier,
                products = state.visibleProducts,
                totalCount = state.products.size,
                lowStockCount =
                    state.products.count {
                        it.stock.isLow || it.stock.quantity <= LOW_STOCK_THRESHOLD_FALLBACK
                    },
                query = state.query,
                onQueryChange = onQueryChange,
                isRefreshing = state.isRefreshing,
                errorMessage = errorMessage,
                connections = connections,
                onRefresh = onRefresh,
                onProductClick = onProductClick,
                onSwitchShop = onSwitchShop,
                onAddShop = onAddShop,
                stockFilter = state.stockFilter,
                onStockFilterSelected = onStockFilterSelected,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
private fun ProductList(
    modifier: Modifier,
    products: List<Product>,
    totalCount: Int,
    lowStockCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    isRefreshing: Boolean,
    errorMessage: String?,
    connections: List<ShopConnection>,
    onRefresh: () -> Unit,
    onProductClick: (Long) -> Unit,
    onSwitchShop: (String) -> Unit,
    onAddShop: () -> Unit,
    stockFilter: StockFilter = StockFilter.ALL,
    onStockFilterSelected: (StockFilter) -> Unit = {},
) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance() }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        PullToRefreshBox(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (errorMessage != null) {
                    ErrorRow(message = errorMessage, onRefresh = onRefresh)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            horizontal = Dimensions.screenEdgeMargin,
                            vertical = Dimensions.spacingL,
                        ),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
                ) {
                    // KPI stats : total produits + stock faible
                    item {
                        ProductsStatsRow(
                            totalCount = totalCount,
                            lowStockCount = lowStockCount,
                        )
                    }

                    item { Spacer(modifier = Modifier.height(Dimensions.spacingXs)) }

                    // Sélecteur de boutique
                    if (connections.isNotEmpty()) {
                        item {
                            ShopSwitcherChip(
                                connections = connections,
                                onSwitch = onSwitchShop,
                                onAddShop = onAddShop,
                            )
                        }
                    }

                    // En-tête de section
                    item {
                        SectionHeader(
                            title = stringResource(R.string.products_list_section_title),
                        )
                    }

                    // Champ de recherche
                    item {
                        SearchField(
                            query = query,
                            onQueryChange = onQueryChange,
                            placeholder = stringResource(R.string.products_search_placeholder),
                        )
                    }

                    // Chips de filtre par état de stock
                    item {
                        StockFilterBar(
                            selected = stockFilter,
                            onSelected = onStockFilterSelected,
                        )
                    }

                    if (products.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.list_no_results, query),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Dimensions.spacingM),
                            )
                        }
                    } else {
                        // Carte conteneur avec toutes les lignes produit
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
                                    products.forEachIndexed { index, product ->
                                        ProductRow(
                                            product = product,
                                            currencyFormatter = currencyFormatter,
                                            onClick = { onProductClick(product.id) },
                                        )
                                        if (index < products.lastIndex) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.surfaceContainer,
                                                thickness = 1.dp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } // fin PullToRefreshBox
    }
}

// ─── KPI stats produits ───────────────────────────────────────────────────────

@Composable
private fun ProductsStatsRow(
    totalCount: Int,
    lowStockCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.gutter),
    ) {
        // Total produits
        StatCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.products_stats_total),
            value = totalCount.toString(),
        )
        // Stock faible
        StatCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.products_stats_low_stock),
            value = lowStockCount.toString(),
            isAlert = lowStockCount > 0,
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isAlert: Boolean = false,
) {
    val bgColor =
        if (isAlert) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (isAlert) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                if (isAlert) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color =
                    if (isAlert) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
    }
}

// ─── Ligne produit ─────────────────────────────────────────────────────────────

@Composable
private fun ProductRow(
    product: Product,
    currencyFormatter: NumberFormat,
    onClick: () -> Unit,
) {
    val priceText = remember(product.price) { currencyFormatter.format(product.price) }
    // Priorité à is_low fourni par le backend ; fallback local si le backend n'envoie pas le champ
    val isLowStock = product.stock.isLow || product.stock.quantity <= LOW_STOCK_THRESHOLD_FALLBACK
    val stockText = stringResource(R.string.products_stock_label, product.stock.quantity)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = stringResource(R.string.products_action_open),
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics { role = Role.Button }
                .padding(Dimensions.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Miniature produit (48dp, radius 8dp)
        ProductThumbnail(
            imageUrl = product.images.firstOrNull()?.url,
            contentDescription = product.name,
        )

        // Contenu
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = priceText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Badge stock faible ou affichage stock normal
                if (isLowStock) {
                    StockBadge(text = stockText, isLow = true)
                } else {
                    Text(
                        text = stockText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductThumbnail(
    imageUrl: String?,
    contentDescription: String,
) {
    val shape = RoundedCornerShape(Dimensions.chipCornerRadius)
    if (imageUrl != null) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
        )
    } else {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StockBadge(
    text: String,
    isLow: Boolean,
) {
    val bgColor = if (isLow) Color(0xFFFFDAD6) else MaterialTheme.colorScheme.surfaceContainer
    val textColor =
        if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Dimensions.chipCornerRadius))
                .background(bgColor)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
        )
    }
}

// ─── Barre de filtres par état de stock ───────────────────────────────────────

/**
 * Ligne horizontale scrollable de chips pour filtrer les produits par état de stock :
 * Tous / En stock / Rupture / Stock faible.
 */
@Composable
private fun StockFilterBar(
    selected: StockFilter,
    onSelected: (StockFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(com.rebuildit.prestaflow.ui.theme.Dimensions.spacingS),
    ) {
        StockFilter.entries.forEach { filter ->
            val label =
                when (filter) {
                    StockFilter.ALL -> stringResource(R.string.products_filter_all)
                    StockFilter.IN_STOCK -> stringResource(R.string.products_filter_in_stock)
                    StockFilter.OUT_OF_STOCK -> stringResource(R.string.products_filter_out_of_stock)
                    StockFilter.LOW_STOCK -> stringResource(R.string.products_filter_low_stock)
                }
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(label) },
            )
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Produits — liste")
@Composable
private fun PreviewProductsList() {
    PrestaFlowTheme {
        ProductsScreen(
            state =
                ProductsUiState(
                    products =
                        listOf(
                            Product(
                                id = 1L,
                                name = "Boutons Céramique Beige",
                                reference = "BTN-001",
                                price = 12.50,
                                active = true,
                                stock = ProductStock(quantity = 45),
                                images = emptyList(),
                                updatedAt = "2026-06-19T10:00:00Z",
                            ),
                            Product(
                                id = 2L,
                                name = "Fil Coton Bio \"Sauge\"",
                                reference = "FIL-042",
                                price = 4.20,
                                active = true,
                                stock = ProductStock(quantity = 4),
                                images = emptyList(),
                                updatedAt = "2026-06-18T10:00:00Z",
                            ),
                        ),
                    isLoading = false,
                    isRefreshing = false,
                ),
            onRefresh = {},
            onProductClick = {},
        )
    }
}
