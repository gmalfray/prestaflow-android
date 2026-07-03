package com.rebuildit.prestaflow.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.products.model.Combination
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.ProductStock
import com.rebuildit.prestaflow.ui.components.SearchField
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme

/**
 * Bottom sheet du flux "scan code-barres → ajustement stock" déclenché depuis l'écran Produits.
 * Rendu conditionnel piloté par [ProductScanUiState] : chargement, aucun résultat, choix parmi
 * plusieurs résultats, fiche stock compacte (+/- et validation), ou confirmation de succès.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // Bottom sheet orchestrant plusieurs sous-états du flux de scan
@Composable
fun ProductScanSheet(
    state: ProductScanUiState,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onScanAgain: () -> Unit,
    onSelectProduct: (Product) -> Unit,
    onBackToResults: () -> Unit,
    onSelectCombination: (Combination) -> Unit,
    onQuantityChange: (String) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onConfirm: () -> Unit,
    onStartAssociation: () -> Unit,
    onCancelAssociation: () -> Unit,
    onAssociationQueryChange: (String) -> Unit,
    onSelectAssociationProduct: (Product) -> Unit,
    onSelectAssociationCombination: (Combination) -> Unit,
    onCancelAssociationCombinationChoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.screenEdgeMargin)
                    .padding(bottom = Dimensions.spacingXl),
        ) {
            ProductScanSheetBody(
                state = state,
                onDismiss = onDismiss,
                onScanAgain = onScanAgain,
                onSelectProduct = onSelectProduct,
                onBackToResults = onBackToResults,
                onSelectCombination = onSelectCombination,
                onQuantityChange = onQuantityChange,
                onIncrement = onIncrement,
                onDecrement = onDecrement,
                onConfirm = onConfirm,
                onStartAssociation = onStartAssociation,
                onCancelAssociation = onCancelAssociation,
                onAssociationQueryChange = onAssociationQueryChange,
                onSelectAssociationProduct = onSelectAssociationProduct,
                onSelectAssociationCombination = onSelectAssociationCombination,
                onCancelAssociationCombinationChoice = onCancelAssociationCombinationChoice,
            )
        }
    }
}

/**
 * Dispatch des sous-états du flux de scan, extrait de [ProductScanSheet] pour rester sous les
 * seuils detekt (longueur/complexité) : chaque état de [ProductScanUiState] pointe vers un unique
 * contenu, cf. KDoc de [ProductScanViewModel].
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod") // Dispatch 1:1 avec les états de ProductScanUiState
@Composable
private fun ProductScanSheetBody(
    state: ProductScanUiState,
    onDismiss: () -> Unit,
    onScanAgain: () -> Unit,
    onSelectProduct: (Product) -> Unit,
    onBackToResults: () -> Unit,
    onSelectCombination: (Combination) -> Unit,
    onQuantityChange: (String) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onConfirm: () -> Unit,
    onStartAssociation: () -> Unit,
    onCancelAssociation: () -> Unit,
    onAssociationQueryChange: (String) -> Unit,
    onSelectAssociationProduct: (Product) -> Unit,
    onSelectAssociationCombination: (Combination) -> Unit,
    onCancelAssociationCombinationChoice: () -> Unit,
) {
    when {
        state.submitSuccess -> ScanSuccessContent(onScanAgain = onScanAgain, onClose = onDismiss)
        state.isLoading -> ScanLoadingContent()
        state.isAssociating && state.associationCombinationChoices.isNotEmpty() ->
            CombinationChoiceContent(
                title = stringResource(R.string.products_scan_association_combination_title),
                productName = state.associationPendingProduct?.name.orEmpty(),
                choices = state.associationCombinationChoices,
                isSubmitting = state.isAssociationSubmitting,
                errorMessage = state.associationError?.asString(),
                onChoose = onSelectAssociationCombination,
                onBack = onCancelAssociationCombinationChoice,
            )
        state.isAssociating ->
            AssociationSearchContent(
                query = state.associationQuery,
                results = state.associationResults,
                isSearching = state.isAssociationSearching,
                isSubmitting = state.isAssociationSubmitting,
                errorMessage = state.associationError?.asString(),
                onQueryChange = onAssociationQueryChange,
                onSelectProduct = onSelectAssociationProduct,
                onCancel = onCancelAssociation,
            )
        state.notFound ->
            ScanNotFoundContent(
                code = state.scannedCode,
                onScanAgain = onScanAgain,
                onStartAssociation = onStartAssociation,
            )
        state.needsCombinationChoice ->
            CombinationChoiceContent(
                title = stringResource(R.string.products_scan_combination_choice_title),
                productName = state.results.firstOrNull()?.name.orEmpty(),
                choices = state.combinationChoices,
                isSubmitting = false,
                errorMessage = null,
                onChoose = onSelectCombination,
                onBack = null,
            )
        state.selectedProduct != null ->
            StockAdjustContent(
                product = state.selectedProduct,
                quantityInput = state.quantityInput,
                isSubmitting = state.isSubmitting,
                errorMessage = state.error?.asString(),
                backLabel = stockAdjustBackLabel(state),
                onBackToResults = onBackToResults,
                onQuantityChange = onQuantityChange,
                onIncrement = onIncrement,
                onDecrement = onDecrement,
                onConfirm = onConfirm,
                onCancel = onDismiss,
            )
        state.error != null ->
            ScanErrorContent(message = state.error.asString(), onScanAgain = onScanAgain)
        state.hasMultipleResults ->
            ScanResultsListContent(results = state.results, onSelectProduct = onSelectProduct)
        else -> ScanLoadingContent()
    }
}

/**
 * Libellé du bouton "retour" de [StockAdjustContent] selon d'où vient la sélection courante :
 * liste de produits distincts, sélecteur de déclinaison, ou aucun (résultat direct).
 */
@Composable
private fun stockAdjustBackLabel(state: ProductScanUiState): String? =
    when {
        state.results.size > 1 -> stringResource(R.string.products_scan_back_to_results)
        state.combinationChoices.isNotEmpty() -> stringResource(R.string.products_scan_back_to_combinations)
        else -> null
    }

@Composable
private fun ScanLoadingContent() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.spacingXl),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(Dimensions.iconSizeMedium))
        Spacer(modifier = Modifier.size(Dimensions.spacingM))
        Text(
            text = stringResource(R.string.products_scan_loading),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ScanNotFoundContent(
    code: String?,
    onScanAgain: () -> Unit,
    onStartAssociation: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.spacingL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
    ) {
        Text(
            text = stringResource(R.string.products_scan_not_found),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!code.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.products_scan_barcode_label, code),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
            OutlinedButton(onClick = onStartAssociation) {
                Text(stringResource(R.string.products_scan_associate_action))
            }
            Button(onClick = onScanAgain) {
                Text(stringResource(R.string.products_scan_scan_again))
            }
        }
    }
}

/**
 * Recherche produit affichée quand un scan n'a matché aucun résultat : l'utilisateur cherche
 * puis sélectionne le produit auquel associer le code scanné (PATCH ean13 + enchaînement fiche
 * stock, piloté par le ViewModel).
 */
@Suppress("LongParameterList") // Contenu de recherche : chaque paramètre pilote une action distincte
@Composable
private fun AssociationSearchContent(
    query: String,
    results: List<Product>,
    isSearching: Boolean,
    isSubmitting: Boolean,
    errorMessage: String?,
    onQueryChange: (String) -> Unit,
    onSelectProduct: (Product) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.spacingM),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
    ) {
        Text(
            text = stringResource(R.string.products_scan_associate_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.products_search_placeholder),
        )

        AssociationResultsContent(
            query = query,
            results = results,
            isSearching = isSearching,
            isSubmitting = isSubmitting,
            onSelectProduct = onSelectProduct,
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(Dimensions.iconSizeMedium))
            } else {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.products_scan_cancel))
                }
            }
        }
    }
}

@Composable
private fun AssociationResultsContent(
    query: String,
    results: List<Product>,
    isSearching: Boolean,
    isSubmitting: Boolean,
    onSelectProduct: (Product) -> Unit,
) {
    when {
        isSearching ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.spacingM),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(Dimensions.iconSizeMedium))
            }
        results.isEmpty() && query.isNotBlank() ->
            Text(
                text = stringResource(R.string.products_scan_associate_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        else ->
            Column {
                results.forEachIndexed { index, product ->
                    ScanResultRow(
                        product = product,
                        onClick = { if (!isSubmitting) onSelectProduct(product) },
                    )
                    if (index < results.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                    }
                }
            }
    }
}

@Composable
private fun ScanErrorContent(
    message: String,
    onScanAgain: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.spacingL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Button(onClick = onScanAgain) {
            Text(stringResource(R.string.products_scan_scan_again))
        }
    }
}

@Composable
private fun ScanResultsListContent(
    results: List<Product>,
    onSelectProduct: (Product) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.products_scan_multiple_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = Dimensions.spacingM),
        )
        results.forEachIndexed { index, product ->
            ScanResultRow(product = product, onClick = { onSelectProduct(product) })
            if (index < results.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }
        }
    }
}

@Composable
private fun ScanResultRow(
    product: Product,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = Dimensions.spacingS),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScanThumbnail(imageUrl = product.images.firstOrNull()?.url, contentDescription = product.name)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = product.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = product.reference,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Sélecteur "Quelle déclinaison ?" affiché quand un scan matche un unique produit à ≥2
 * déclinaisons sans en désigner une précisément, ou quand l'association d'un code-barres doit
 * choisir sur quelle déclinaison le poser. [onBack] est optionnel (absent pour le flux de scan,
 * qui repart plutôt via [onBackToResults] depuis la fiche stock).
 */
@Suppress("LongParameterList") // Sélecteur réutilisé par 2 flux (scan et association) : chaque paramètre pilote une action distincte
@Composable
private fun CombinationChoiceContent(
    title: String,
    productName: String,
    choices: List<Combination>,
    isSubmitting: Boolean,
    errorMessage: String?,
    onChoose: (Combination) -> Unit,
    onBack: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.spacingM),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (productName.isNotBlank()) {
            Text(text = productName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column {
            choices.forEachIndexed { index, combination ->
                CombinationChoiceRow(
                    combination = combination,
                    onClick = { if (!isSubmitting) onChoose(combination) },
                )
                if (index < choices.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                }
            }
        }
        if (errorMessage != null) {
            Text(text = errorMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        if (isSubmitting) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.size(Dimensions.iconSizeMedium))
            }
        } else if (onBack != null) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.products_scan_combination_back))
            }
        }
    }
}

@Composable
private fun CombinationChoiceRow(
    combination: Combination,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = Dimensions.spacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = combination.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(R.string.products_scan_combination_stock_row, combination.quantity),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("LongParameterList") // Fiche stock : chaque paramètre pilote une action distincte du formulaire
@Composable
private fun StockAdjustContent(
    product: Product,
    quantityInput: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    backLabel: String?,
    onBackToResults: () -> Unit,
    onQuantityChange: (String) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val canConfirm = quantityInput.toIntOrNull() != null && !isSubmitting

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.spacingM),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
    ) {
        if (backLabel != null) {
            TextButton(onClick = onBackToResults) {
                Text(backLabel)
            }
        }

        ScanProductHeader(product = product)

        QuantityStepper(
            quantityInput = quantityInput,
            isSubmitting = isSubmitting,
            onQuantityChange = onQuantityChange,
            onIncrement = onIncrement,
            onDecrement = onDecrement,
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !isSubmitting) {
                Text(stringResource(R.string.products_scan_cancel))
            }
            Spacer(modifier = Modifier.size(Dimensions.spacingS))
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(Dimensions.iconSizeMedium))
            } else {
                Button(onClick = onConfirm, enabled = canConfirm) {
                    Text(stringResource(R.string.products_scan_confirm))
                }
            }
        }
    }
}

@Composable
private fun ScanProductHeader(product: Product) {
    val matchedCombination = product.matchedCombination
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScanThumbnail(imageUrl = product.images.firstOrNull()?.url, contentDescription = product.name)
        Column {
            Text(text = product.name, style = MaterialTheme.typography.titleMedium)
            if (product.reference.isNotBlank()) {
                Text(
                    text = product.reference,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (matchedCombination != null) {
                // Produit à déclinaisons (ex. pelotes de laine) : le scan a matché une COMBINAISON
                // précise, dont le stock est distinct de celui du produit parent — on l'affiche
                // explicitement pour éviter toute confusion sur ce qui va être mis à jour.
                Text(
                    text = stringResource(R.string.products_scan_combination_label, matchedCombination.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.products_scan_current_stock, product.scannedQuantity),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuantityStepper(
    quantityInput: String,
    isSubmitting: Boolean,
    onQuantityChange: (String) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val decrementDesc = stringResource(R.string.products_scan_decrement_content_description)
    val incrementDesc = stringResource(R.string.products_scan_increment_content_description)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDecrement, enabled = !isSubmitting) {
            Icon(imageVector = Icons.Filled.Remove, contentDescription = decrementDesc)
        }
        OutlinedTextField(
            value = quantityInput,
            onValueChange = onQuantityChange,
            label = { Text(stringResource(R.string.products_scan_quantity_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            enabled = !isSubmitting,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onIncrement, enabled = !isSubmitting) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = incrementDesc)
        }
    }
}

@Composable
private fun ScanSuccessContent(
    onScanAgain: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.spacingL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimensions.iconContainerSize),
        )
        Text(
            text = stringResource(R.string.products_scan_success),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
            OutlinedButton(onClick = onClose) {
                Text(stringResource(R.string.products_scan_close))
            }
            Button(onClick = onScanAgain) {
                Text(stringResource(R.string.products_scan_another))
            }
        }
    }
}

@Composable
private fun ScanThumbnail(
    imageUrl: String?,
    contentDescription: String,
) {
    val shape = RoundedCornerShape(Dimensions.chipCornerRadius)
    if (imageUrl != null) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
        )
    } else {
        Surface(
            modifier = Modifier.size(48.dp).clip(shape),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = "?", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

private fun previewProduct() =
    Product(
        id = 1L,
        name = "Boutons Céramique Beige",
        reference = "BTN-001",
        price = 12.50,
        active = true,
        stock = ProductStock(quantity = 45),
        images = emptyList(),
        updatedAt = "2026-06-19T10:00:00Z",
        ean13 = "3401234567890",
    )

@Preview(showBackground = true, name = "Scan — fiche stock")
@Composable
private fun PreviewStockAdjustContent() {
    PrestaFlowTheme {
        Surface {
            StockAdjustContent(
                product = previewProduct(),
                quantityInput = "50",
                isSubmitting = false,
                errorMessage = null,
                backLabel = null,
                onBackToResults = {},
                onQuantityChange = {},
                onIncrement = {},
                onDecrement = {},
                onConfirm = {},
                onCancel = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Scan — non trouvé")
@Composable
private fun PreviewNotFound() {
    PrestaFlowTheme {
        Surface {
            ScanNotFoundContent(code = "3401234567890", onScanAgain = {}, onStartAssociation = {})
        }
    }
}
