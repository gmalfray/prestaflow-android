package com.rebuildit.prestaflow.ui.auth

import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.auth.AuthFailure
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.auth.AuthResult
import com.rebuildit.prestaflow.domain.auth.ShopUrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import kotlin.text.Charsets

@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val shopUrlValidator: ShopUrlValidator,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AuthUiState())
        val uiState: StateFlow<AuthUiState> = _uiState

        fun onShopUrlChanged(value: String) {
            val shopUrlError = validateUrlRealTime(value)
            _uiState.update {
                it.copy(
                    shopUrl = value,
                    shopUrlError = shopUrlError,
                    formError = null,
                    showModuleNotInstalledGuide = false,
                )
            }
        }

        /**
         * Validation en temps réel de l'URL boutique, non-bloquante :
         * - URL vide ou préfixe seul (« https:// ») → pas d'erreur (saisie en cours)
         * - URL avec schéma http → erreur HTTPS requise
         * - URL syntaxiquement invalide mais suffisamment longue → erreur malformée
         * - URL valide → pas d'erreur
         */
        private fun validateUrlRealTime(value: String): UiText? {
            if (value.isBlank()) return null
            // Ne pas pénaliser la saisie du schéma seul
            if (value == "http://" || value == "https://") return null
            // Pas encore de schéma → pas d'erreur (l'utilisateur tape encore)
            if (!value.contains("://")) return null
            return when (shopUrlValidator.validate(value)) {
                is ShopUrlValidator.Result.Valid -> null
                ShopUrlValidator.Result.Invalid.Empty -> null
                ShopUrlValidator.Result.Invalid.NonHttps ->
                    UiText.FromResources(R.string.auth_error_shop_url_https)
                ShopUrlValidator.Result.Invalid.Malformed -> {
                    // N'afficher l'erreur de malformation que si le host semble suffisamment saisi
                    val afterScheme = value.substringAfter("://")
                    if (afterScheme.length >= 5) {
                        UiText.FromResources(R.string.auth_error_shop_url_malformed)
                    } else {
                        null
                    }
                }
            }
        }

        fun onApiKeyChanged(value: String) {
            _uiState.update { it.copy(apiKey = value, formError = null, showModuleNotInstalledGuide = false) }
        }

        fun onQrScanned(rawValue: String) {
            val result = parseQrContent(rawValue.trim())
            if (result == null) {
                _uiState.update { it.copy(formError = UiText.FromResources(R.string.auth_error_qr_invalid)) }
                return
            }

            val (shopUrl, apiKey) = result
            val validation = shopUrlValidator.validate(shopUrl)
            val normalizedUrl =
                when (validation) {
                    is ShopUrlValidator.Result.Valid -> validation.normalizedUrl
                    is ShopUrlValidator.Result.Invalid -> {
                        val message =
                            when (validation) {
                                ShopUrlValidator.Result.Invalid.Empty -> UiText.FromResources(R.string.auth_error_shop_url_empty)
                                ShopUrlValidator.Result.Invalid.Malformed -> UiText.FromResources(R.string.auth_error_shop_url_malformed)
                                ShopUrlValidator.Result.Invalid.NonHttps -> UiText.FromResources(R.string.auth_error_shop_url_https)
                            }
                        _uiState.update { it.copy(shopUrlError = message, formError = null) }
                        return
                    }
                }

            _uiState.update {
                it.copy(
                    shopUrl = normalizedUrl,
                    apiKey = apiKey,
                    formError = null,
                    shopUrlError = null,
                )
            }
        }

        fun onQrScanCancelled() {
            _uiState.update { it.copy(formError = UiText.FromResources(R.string.auth_error_qr_cancelled)) }
        }

        fun submit() {
            val current = _uiState.value
            val validation = shopUrlValidator.validate(current.shopUrl)
            val normalizedUrl =
                when (validation) {
                    is ShopUrlValidator.Result.Valid -> validation.normalizedUrl
                    is ShopUrlValidator.Result.Invalid -> {
                        val message =
                            when (validation) {
                                ShopUrlValidator.Result.Invalid.Empty -> UiText.FromResources(R.string.auth_error_shop_url_empty)
                                ShopUrlValidator.Result.Invalid.Malformed -> UiText.FromResources(R.string.auth_error_shop_url_malformed)
                                ShopUrlValidator.Result.Invalid.NonHttps -> UiText.FromResources(R.string.auth_error_shop_url_https)
                            }
                        _uiState.update { it.copy(shopUrlError = message) }
                        return
                    }
                }

            if (current.apiKey.isBlank()) {
                _uiState.update { it.copy(formError = UiText.FromResources(R.string.auth_error_api_key_empty)) }
                return
            }

            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, formError = null, shopUrlError = null) }

                when (val result = authRepository.login(normalizedUrl, current.apiKey)) {
                    AuthResult.Success -> _uiState.update { it.copy(isLoading = false, shopUrl = normalizedUrl) }
                    is AuthResult.Failure -> handleFailure(result.reason)
                }
            }
        }

        private fun handleFailure(failure: AuthFailure) {
            val message =
                when (failure) {
                    is AuthFailure.InvalidShopUrl ->
                        when (failure.reason) {
                            ShopUrlValidator.Result.Invalid.Empty -> UiText.FromResources(R.string.auth_error_shop_url_empty)
                            ShopUrlValidator.Result.Invalid.Malformed -> UiText.FromResources(R.string.auth_error_shop_url_malformed)
                            ShopUrlValidator.Result.Invalid.NonHttps -> UiText.FromResources(R.string.auth_error_shop_url_https)
                        }
                    AuthFailure.ModuleNotInstalled ->
                        UiText.FromResources(R.string.auth_error_module_not_installed)
                    is AuthFailure.HostUnreachable -> failure.message
                    is AuthFailure.Network -> failure.message
                    is AuthFailure.Unknown -> failure.message
                }

            val showGuideAction = failure is AuthFailure.ModuleNotInstalled

            _uiState.update {
                val shopUrlError = if (failure is AuthFailure.InvalidShopUrl) message else null
                it.copy(
                    isLoading = false,
                    formError = if (shopUrlError == null) message else null,
                    shopUrlError = shopUrlError,
                    showModuleNotInstalledGuide = showGuideAction,
                )
            }
        }

        @Suppress("ReturnCount") // Parsing défensif QR code : early-returns sur chaque étape de décodage/validation
        private fun parseQrContent(raw: String): Pair<String, String>? {
            if (raw.isBlank()) return null

            val jsonString =
                if (raw.startsWith("prestaflow://", ignoreCase = true)) {
                    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
                    val encoded = uri.getQueryParameter("data") ?: return null
                    val decoded = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrElse { return null }
                    String(decoded, Charsets.UTF_8)
                } else {
                    raw
                }

            val json = runCatching { JSONObject(jsonString) }.getOrNull() ?: return null
            val shopUrl = json.optString("shopUrl")
            val apiKey = json.optString("apiKey")
            if (shopUrl.isNullOrBlank() || apiKey.isNullOrBlank()) {
                return null
            }

            return shopUrl to apiKey
        }
    }

data class AuthUiState(
    val shopUrl: String = "",
    val apiKey: String = "",
    val isLoading: Boolean = false,
    val formError: UiText? = null,
    val shopUrlError: UiText? = null,
    /**
     * Vrai quand la connexion a échoué avec un 404, indiquant que le module n'est pas installé.
     * L'écran affiche alors une action secondaire vers le guide d'installation.
     */
    val showModuleNotInstalledGuide: Boolean = false,
) {
    val isSubmitEnabled: Boolean
        get() = shopUrl.isNotBlank() && apiKey.isNotBlank() && !isLoading
}
