package com.rebuildit.prestaflow.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.UiText

@Composable
fun AuthRoute(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result == null || result.contents.isNullOrBlank()) {
            viewModel.onQrScanCancelled()
        } else {
            viewModel.onQrScanned(result.contents)
        }
    }

    val scanPrompt = stringResource(id = R.string.auth_scan_prompt)

    AuthScreen(
        modifier = modifier,
        state = uiState,
        onShopUrlChanged = viewModel::onShopUrlChanged,
        onApiKeyChanged = viewModel::onApiKeyChanged,
        onSubmit = viewModel::submit,
        onScanQr = {
            val options = ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(scanPrompt)
                .setBeepEnabled(false)
                .setBarcodeImageEnabled(false)
            scanLauncher.launch(options)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    state: AuthUiState,
    onShopUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onScanQr: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    Scaffold(modifier = modifier) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                val shopUrlLabel = stringResource(id = R.string.auth_field_shop_url)
                val shopUrlPlaceholder = stringResource(id = R.string.auth_field_shop_url_placeholder)
                val apiKeyLabel = stringResource(id = R.string.auth_field_api_key)
                val apiKeyPlaceholder = stringResource(id = R.string.auth_field_api_key_placeholder)
                val loadingDescription = stringResource(id = R.string.auth_loading)

                Text(
                    text = stringResource(id = R.string.auth_title),
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = stringResource(id = R.string.auth_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = shopUrlLabel },
                    value = state.shopUrl,
                    onValueChange = onShopUrlChanged,
                    label = { Text(text = shopUrlLabel) },
                    placeholder = { Text(text = shopUrlPlaceholder) },
                    singleLine = true,
                    isError = state.shopUrlError != null,
                    supportingText = {
                        val error = state.shopUrlError
                        if (error != null) {
                            ErrorText(error)
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = {
                        focusManager.moveFocus(FocusDirection.Down)
                    })
                )

                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = apiKeyLabel },
                    value = state.apiKey,
                    onValueChange = onApiKeyChanged,
                    label = { Text(text = apiKeyLabel) },
                    placeholder = { Text(text = apiKeyPlaceholder) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus(force = true)
                        if (state.isSubmitEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSubmit()
                        }
                    })
                )

                val formError = state.formError
                if (formError != null) {
                    ErrorText(formError)
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSubmit()
                    },
                    enabled = state.isSubmitEnabled
                ) {
                    Text(text = stringResource(id = R.string.auth_action_connect))
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onScanQr()
                    },
                    enabled = !state.isLoading
                ) {
                    Text(text = stringResource(id = R.string.auth_action_scan_qr))
                }

                TextButton(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(0.dp),
                    enabled = !state.isLoading,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onShopUrlChanged("https://")
                    }
                ) {
                    Text(text = stringResource(id = R.string.auth_action_prefill_https))
                }

                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 16.dp)
                            .semantics { contentDescription = loadingDescription }
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorText(message: UiText) {
    val text = when (message) {
        is UiText.Dynamic -> message.value
        is UiText.FromResources -> stringResource(id = message.resId, *message.args.toTypedArray())
    }

    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium
    )
}
