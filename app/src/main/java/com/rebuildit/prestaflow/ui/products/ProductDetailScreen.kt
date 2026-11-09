package com.rebuildit.prestaflow.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.ProductImage
import com.rebuildit.prestaflow.domain.products.model.ProductStock
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.components.NotFoundState
import com.rebuildit.prestaflow.ui.components.euroCurrencyFormatter
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme

@Composable
fun ProductDetailRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEditClick: (Long) -> Unit = {},
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProductDetailScreen(
        modifier = modifier,
        state = state,
        onBackClick = onBackClick,
        onUpdatePrice = viewModel::onUpdatePrice,
        onUpdateStock = viewModel::onUpdateStock,
        onToggleStatus = viewModel::onToggleStatus,
        onClearError = viewModel::clearError,
        onEditClick = { state.product?.let { onEditClick(it.id) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // Composable d'écran : chaque paramètre est un callback d'action produit distinct
@Composable
fun ProductDetailScreen(
    state: ProductDetailUiState,
    onBackClick: () -> Unit,
    onUpdatePrice: (Double) -> Unit,
    onUpdateStock: (Int) -> Unit,
    onToggleStatus: () -> Unit,
    onClearError: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = state.error?.asString()
    val backDesc = stringResource(R.string.content_description_back)
    val editDesc = stringResource(R.string.product_detail_edit_content_description)

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onClearError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.product?.name ?: stringResource(R.string.product_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backDesc,
                        )
                    }
                },
                actions = {
                    if (state.product != null) {
                        IconButton(onClick = onEditClick) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = editDesc,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.product != null ->
                ProductContent(
                    modifier = Modifier.padding(padding),
                    product = state.product,
                    isUpdating = state.isUpdating,
                    onUpdatePrice = onUpdatePrice,
                    onUpdateStock = onUpdateStock,
                    onToggleStatus = onToggleStatus,
                )
            else ->
                NotFoundState(
                    message = stringResource(R.string.product_not_found),
                    modifier = Modifier.padding(padding),
                    onBackClick = onBackClick,
                )
        }
    }
}

private val SectionShape = RoundedCornerShape(Dimensions.cardCornerRadius)
private const val HERO_ASPECT_RATIO = 4f / 3f
private val HeroMaxHeight = 320.dp
private val ThumbnailSize = 56.dp

@Suppress("LongParameterList") // Contenu produit : actions prix, stock et statut
@Composable
private fun ProductContent(
    product: Product,
    isUpdating: Boolean,
    onUpdatePrice: (Double) -> Unit,
    onUpdateStock: (Int) -> Unit,
    onToggleStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingM),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingL),
    ) {
        ProductGallery(product)
        ProductIdentity(product)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(SectionShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            PriceSection(product, isUpdating, onUpdatePrice)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StockSection(product, isUpdating, onUpdateStock)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StatusSection(product, isUpdating, onToggleStatus)
        }
        if (isUpdating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ProductGallery(
    product: Product,
    modifier: Modifier = Modifier,
) {
    var selected by remember(product.id) { mutableIntStateOf(0) }
    val images = product.images
    val current = images.getOrNull(selected) ?: images.firstOrNull()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = HeroMaxHeight)
                    .aspectRatio(HERO_ASPECT_RATIO)
                    .clip(SectionShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (current != null) {
                SubcomposeAsyncImage(
                    model = current.url,
                    contentDescription = product.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    loading = { ImagePlaceholder() },
                    error = { ImagePlaceholder() },
                )
            } else {
                ImagePlaceholder()
            }
        }
        if (images.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
                itemsIndexed(images, key = { _, image -> image.id }) { index, image ->
                    val isSelected = index == selected
                    AsyncImage(
                        model = image.url,
                        contentDescription = product.name,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(ThumbnailSize)
                                .clip(RoundedCornerShape(Dimensions.chipCornerRadius))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color =
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                    shape = RoundedCornerShape(Dimensions.chipCornerRadius),
                                )
                                .clickable { selected = index },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.spacingXl + Dimensions.spacingL),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductIdentity(
    product: Product,
    modifier: Modifier = Modifier,
) {
    val price = remember { euroCurrencyFormatter() }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
        ) {
            if (product.active) {
                StatusChip(
                    text = stringResource(R.string.products_status_active),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                StatusChip(
                    text = stringResource(R.string.products_status_inactive),
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (product.stock.isLow) {
                StatusChip(
                    text = stringResource(R.string.products_stats_low_stock),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        Text(
            text = product.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (product.reference.isNotBlank()) {
            Text(
                text = stringResource(R.string.product_reference_label, product.reference),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = price.format(product.price),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun StatusChip(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .background(container, RoundedCornerShape(Dimensions.chipCornerRadius))
                .padding(horizontal = Dimensions.spacingS + Dimensions.spacingXs, vertical = Dimensions.spacingXs),
    )
}

@Composable
private fun EditSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Dimensions.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
private fun PriceSection(
    product: Product,
    isUpdating: Boolean,
    onUpdatePrice: (Double) -> Unit,
) {
    var priceText by remember(product.price) { mutableStateOf(product.price.toString()) }
    EditSection(title = stringResource(R.string.product_price_section)) {
        OutlinedTextField(
            value = priceText,
            onValueChange = { priceText = it },
            label = { Text(stringResource(R.string.product_price_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isUpdating,
        )
        Button(
            onClick = { priceText.toDoubleOrNull()?.let { onUpdatePrice(it) } },
            enabled = !isUpdating && priceText.toDoubleOrNull() != null,
            modifier = Modifier.align(Alignment.End),
            shape = RoundedCornerShape(50),
        ) {
            Text(stringResource(R.string.product_update_price))
        }
    }
}

@Composable
private fun StockSection(
    product: Product,
    isUpdating: Boolean,
    onUpdateStock: (Int) -> Unit,
) {
    var stockText by remember(product.stock.quantity) { mutableStateOf(product.stock.quantity.toString()) }
    EditSection(title = stringResource(R.string.product_stock_section)) {
        OutlinedTextField(
            value = stockText,
            onValueChange = { stockText = it },
            label = { Text(stringResource(R.string.product_stock_quantity_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isUpdating,
        )
        Button(
            onClick = { stockText.toIntOrNull()?.let { onUpdateStock(it) } },
            enabled = !isUpdating && stockText.toIntOrNull() != null,
            modifier = Modifier.align(Alignment.End),
            shape = RoundedCornerShape(50),
        ) {
            Text(stringResource(R.string.product_update_stock))
        }
    }
}

@Composable
private fun StatusSection(
    product: Product,
    isUpdating: Boolean,
    onToggleStatus: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.cardPadding, vertical = Dimensions.spacingS)
                .heightIn(min = Dimensions.minTouchTarget),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)) {
            Text(
                text = stringResource(R.string.product_status_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    stringResource(
                        if (product.active) R.string.products_status_active else R.string.products_status_inactive,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = product.active,
            onCheckedChange = { onToggleStatus() },
            enabled = !isUpdating,
        )
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

private fun previewDetailProduct(isLow: Boolean = true) =
    Product(
        id = 1L,
        name = "Pelote de coton recyclé Terracotta, très longue désignation de produit",
        reference = "BTN-001-ABCDEFGH",
        price = 1234.5,
        active = true,
        stock = ProductStock(quantity = 3, isLow = isLow),
        images = listOf(ProductImage(1L, "https://example.invalid/a.jpg"), ProductImage(2L, "https://example.invalid/b.jpg")),
        updatedAt = "2026-06-19T10:00:00Z",
    )

@Composable
private fun ProductDetailPreviewContent(product: Product = previewDetailProduct()) {
    PrestaFlowTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ProductContent(
                product = product,
                isUpdating = false,
                onUpdatePrice = {},
                onUpdateStock = {},
                onToggleStatus = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Fiche produit")
@Composable
private fun ProductDetailPreview() = ProductDetailPreviewContent()

@Preview(showBackground = true, name = "Fiche produit, sans image", heightDp = 900)
@Composable
private fun ProductDetailNoImagePreview() = ProductDetailPreviewContent(previewDetailProduct(false).copy(images = emptyList()))

@Preview(showBackground = true, name = "Fiche produit, police 1.5", fontScale = 1.5f, heightDp = 1400)
@Composable
private fun ProductDetailFontScale15Preview() = ProductDetailPreviewContent()

@Preview(showBackground = true, name = "Fiche produit, police 2.0", fontScale = 2.0f, heightDp = 1800)
@Composable
private fun ProductDetailFontScale20Preview() = ProductDetailPreviewContent()
