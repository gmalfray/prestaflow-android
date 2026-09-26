package com.rebuildit.prestaflow.ui.carts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.carts.model.CartDetail
import com.rebuildit.prestaflow.domain.carts.model.CartProduct
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.components.NotFoundState
import com.rebuildit.prestaflow.ui.components.formatCurrency
import com.rebuildit.prestaflow.ui.components.formatTimestamp
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun CartDetailRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CartDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CartDetailScreen(
        modifier = modifier,
        state = state,
        onBackClick = onBackClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod") // Composable d'écran scaffold avec états loading/error/success distincts
@Composable
fun CartDetailScreen(
    state: CartDetailUiState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backDesc = stringResource(R.string.content_description_back)
    val retryDesc = stringResource(R.string.content_description_retry)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.cart?.let { stringResource(R.string.carts_detail_title) }
                            ?: stringResource(R.string.carts_detail_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backDesc,
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.error != null && state.cart == null -> {
                Column(
                    modifier =
                        Modifier
                            .padding(padding)
                            .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.error.asString(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.semantics { contentDescription = retryDesc },
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                    }
                }
            }
            state.cart != null ->
                CartDetailContent(
                    cart = state.cart,
                    modifier = Modifier.padding(padding),
                )
            else ->
                NotFoundState(
                    message = stringResource(R.string.carts_detail_not_found),
                    modifier = Modifier.padding(padding),
                    onBackClick = onBackClick,
                )
        }
    }
}

@Suppress("LongMethod") // Composable contenu panier : sections produits, totaux et résumé client
@Composable
private fun CartDetailContent(
    cart: CartDetail,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
    val totalText =
        remember(cart.totalTaxIncl, cart.currencyIso) {
            formatCurrency(cart.totalTaxIncl, cart.currencyIso)
        }
    val updatedAt =
        remember(cart.updatedAtIso) {
            formatTimestamp(cart.updatedAtIso, dateFormatter)
        }
    // F — Calcule le vrai nombre d'articles depuis les lignes produits (itemsCount peut valoir
    // 0 si le backend ne remplit pas ce champ dans la réponse détail).
    val realItemsCount =
        remember(cart.products) {
            if (cart.products.isNotEmpty()) cart.products.sumOf { it.quantity } else cart.itemsCount
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // En-tête panier
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = cart.customerName.ifBlank { stringResource(R.string.carts_customer_guest) },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (cart.customerEmail != null) {
                    Text(
                        text = cart.customerEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                // weight(fill=false) sur les deux : à fontScale élevé, ni le total ni le compte
                // d'articles ne peuvent déborder hors de la carte.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = stringResource(R.string.carts_total_incl, totalText),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = stringResource(R.string.carts_items_count, realItemsCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier =
                            Modifier
                                .weight(1f, fill = false)
                                .padding(start = 4.dp),
                    )
                }
                if (updatedAt != null) {
                    Text(
                        text = updatedAt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text =
                        if (cart.hasOrder) {
                            stringResource(R.string.carts_has_order)
                        } else {
                            stringResource(R.string.carts_abandoned)
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (cart.hasOrder) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )
            }
        }

        // Produits
        if (cart.products.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                        text = stringResource(R.string.carts_products_section),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    cart.products.forEachIndexed { index, product ->
                        CartProductRow(product = product, currencyIso = cart.currencyIso)
                        if (index < cart.products.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartProductRow(
    product: CartProduct,
    currencyIso: String,
) {
    val totalText =
        remember(product.totalTaxIncl, currencyIso) {
            formatCurrency(product.totalTaxIncl, currencyIso)
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (!product.reference.isNullOrBlank()) {
                Text(
                    text = product.reference,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.order_detail_qty_label, product.quantity),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = totalText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

/**
 * Preuve visuelle du fix fontScale : le total (TTC) et le compte d'articles, en en-tête, ne
 * doivent pas déborder hors de la carte à fontScale 1.5 puis 2.0.
 */
@Preview(showBackground = true, name = "Panier — détail")
@Preview(showBackground = true, fontScale = 1.5f, name = "Panier — détail — fontScale 1.5")
@Preview(showBackground = true, fontScale = 2f, name = "Panier — détail — fontScale 2.0")
@Composable
private fun PreviewCartDetail() {
    PrestaFlowTheme {
        CartDetailScreen(
            state =
                CartDetailUiState(
                    cart =
                        CartDetail(
                            id = 1,
                            customerName = "Camille Martin",
                            customerEmail = "camille.martin@example.com",
                            currencyIso = "EUR",
                            totalTaxIncl = 1234.56,
                            totalTaxExcl = 1028.80,
                            itemsCount = 3,
                            hasOrder = false,
                            createdAtIso = "2026-06-19T14:20:00Z",
                            updatedAtIso = "2026-06-19T14:20:00Z",
                            products =
                                listOf(
                                    CartProduct(
                                        productId = 1,
                                        name = "Pelote de laine mérinos",
                                        reference = "REF-001",
                                        quantity = 2,
                                        totalTaxIncl = 25.0,
                                        imageUrl = null,
                                    ),
                                ),
                        ),
                    isLoading = false,
                ),
            onBackClick = {},
        )
    }
}
