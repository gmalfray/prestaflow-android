package com.rebuildit.prestaflow.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.components.NotFoundState
import com.rebuildit.prestaflow.ui.theme.Dimensions

@Composable
fun ProductEditRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            viewModel.consumeSaveSuccess()
            onBackClick()
        }
    }

    ProductEditScreen(
        modifier = modifier,
        state = state,
        onBackClick = onBackClick,
        onNameChange = viewModel::onNameChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onDescriptionShortChange = viewModel::onDescriptionShortChange,
        onReferenceChange = viewModel::onReferenceChange,
        onPriceChange = viewModel::onPriceChange,
        onActiveChange = viewModel::onActiveChange,
        onSaveClick = viewModel::onSave,
        onClearError = viewModel::clearError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // Composable d'écran : chaque paramètre est un callback de champ d'édition distinct
@Composable
fun ProductEditScreen(
    state: ProductEditUiState,
    onBackClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDescriptionShortChange: (String) -> Unit,
    onReferenceChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = state.error?.asString()
    val backDesc = stringResource(R.string.content_description_back)

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
                title = { Text(stringResource(R.string.product_edit_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.productNotFound ->
                NotFoundState(
                    message = stringResource(R.string.product_not_found),
                    modifier = Modifier.padding(padding),
                    onBackClick = onBackClick,
                )
            else ->
                ProductEditForm(
                    modifier = Modifier.padding(padding),
                    state = state,
                    onNameChange = onNameChange,
                    onDescriptionChange = onDescriptionChange,
                    onDescriptionShortChange = onDescriptionShortChange,
                    onReferenceChange = onReferenceChange,
                    onPriceChange = onPriceChange,
                    onActiveChange = onActiveChange,
                    onSaveClick = onSaveClick,
                )
        }
    }
}

@Suppress("LongParameterList", "LongMethod") // Formulaire d'édition : un callback par champ + états dérivés
@Composable
private fun ProductEditForm(
    state: ProductEditUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDescriptionShortChange: (String) -> Unit,
    onReferenceChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingM),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimensions.cardCornerRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.product_edit_name_label)) },
                    isError = state.nameError,
                    supportingText = {
                        if (state.nameError) Text(stringResource(R.string.product_edit_name_error))
                    },
                    singleLine = true,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.reference,
                    onValueChange = onReferenceChange,
                    label = { Text(stringResource(R.string.product_edit_reference_label)) },
                    isError = state.referenceError,
                    supportingText = {
                        if (state.referenceError) Text(stringResource(R.string.product_edit_reference_error))
                    },
                    singleLine = true,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.priceText,
                    onValueChange = onPriceChange,
                    label = { Text(stringResource(R.string.product_edit_price_label)) },
                    isError = state.priceError,
                    supportingText = {
                        if (state.priceError) Text(stringResource(R.string.product_edit_price_error))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.descriptionShort,
                    onValueChange = onDescriptionShortChange,
                    label = { Text(stringResource(R.string.product_edit_description_short_label)) },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    label = { Text(stringResource(R.string.product_edit_description_label)) },
                    minLines = 5,
                    maxLines = 10,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.product_edit_active_label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = state.active,
                        onCheckedChange = onActiveChange,
                        enabled = !state.isSaving,
                    )
                }
            }
        }

        Button(
            onClick = onSaveClick,
            enabled = state.canSave,
            modifier = Modifier.align(Alignment.End),
            shape = RoundedCornerShape(50),
        ) {
            Text(stringResource(R.string.product_edit_save_button))
        }

        if (state.isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
