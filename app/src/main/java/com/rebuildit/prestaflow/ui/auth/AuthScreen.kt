package com.rebuildit.prestaflow.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.UiText

@Composable
fun AuthRoute(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScreen(
        modifier = modifier,
        state = uiState,
        onShopUrlChanged = viewModel::onShopUrlChanged,
        onApiKeyChanged = viewModel::onApiKeyChanged,
        onSubmit = viewModel::submit
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    state: AuthUiState,
    onShopUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                modifier = Modifier.fillMaxWidth(),
                value = state.shopUrl,
                onValueChange = onShopUrlChanged,
                label = { Text(text = stringResource(id = R.string.auth_field_shop_url)) },
                placeholder = { Text(text = stringResource(id = R.string.auth_field_shop_url_placeholder)) },
                singleLine = true,
                isError = state.shopUrlError != null,
                supportingText = state.shopUrlError?.let { error -> { ErrorText(error) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Uri
                )
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.apiKey,
                onValueChange = onApiKeyChanged,
                label = { Text(text = stringResource(id = R.string.auth_field_api_key)) },
                placeholder = { Text(text = stringResource(id = R.string.auth_field_api_key_placeholder)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            state.formError?.let { error -> ErrorText(error) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSubmit,
                enabled = state.isSubmitEnabled
            ) {
                Text(text = stringResource(id = R.string.auth_action_connect))
            }

            TextButton(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(0.dp),
                enabled = !state.isLoading,
                onClick = { onShopUrlChanged("https://") }
            ) {
                Text(text = stringResource(id = R.string.auth_action_prefill_https))
            }

            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 16.dp)
                )
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
