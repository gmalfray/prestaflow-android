package com.rebuildit.prestaflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.auth.AuthFailure
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.auth.AuthResult
import com.rebuildit.prestaflow.domain.auth.QrCodeParser
import com.rebuildit.prestaflow.domain.auth.ShopUrlValidator
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShopsViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val shopUrlValidator: ShopUrlValidator,
    ) : ViewModel() {
        val connections: StateFlow<List<ShopConnection>> =
            authRepository.connections.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = authRepository.connections.value,
            )

        private val _addState = MutableStateFlow(AddShopUiState())
        val addState: StateFlow<AddShopUiState> = _addState.asStateFlow()

        fun switchShop(id: String) {
            viewModelScope.launch { authRepository.switchActiveConnection(id) }
        }

        fun removeShop(id: String) {
            viewModelScope.launch { authRepository.removeConnection(id) }
        }

        fun showAddDialog() {
            _addState.value = AddShopUiState(visible = true)
        }

        fun dismissAddDialog() {
            _addState.value = AddShopUiState(visible = false)
        }

        fun onUrlChange(value: String) = _addState.update { it.copy(shopUrl = value, error = null) }

        fun onKeyChange(value: String) = _addState.update { it.copy(apiKey = value, error = null) }

        fun onLabelChange(value: String) = _addState.update { it.copy(label = value, error = null) }

        /**
         * Traite le contenu brut d'un QR code scanné depuis le dialog d'ajout de boutique.
         *
         * En cas de succès : pré-remplit les champs URL et clé API (l'utilisateur peut vérifier
         * et valider avant de soumettre). En cas d'échec : pose une erreur visible dans le dialog.
         * Scan annulé (null/vide) : ne fait rien.
         */
        fun onQrScanned(rawValue: String) {
            val result = QrCodeParser.parse(rawValue.trim())
            if (result == null) {
                _addState.update { it.copy(error = UiText.FromResources(R.string.auth_error_qr_invalid)) }
                return
            }

            val (shopUrl, apiKey) = result
            val normalizedUrl =
                when (val validation = shopUrlValidator.validate(shopUrl)) {
                    is ShopUrlValidator.Result.Valid -> validation.normalizedUrl
                    is ShopUrlValidator.Result.Invalid -> shopUrl // pas de normalisation possible, on laisse l'URL brute
                }

            _addState.update {
                it.copy(
                    shopUrl = normalizedUrl,
                    apiKey = apiKey,
                    error = null,
                )
            }
        }

        fun submitAdd() {
            val current = _addState.value
            if (current.shopUrl.isBlank() || current.apiKey.isBlank()) {
                _addState.update { it.copy(error = UiText.FromResources(R.string.shops_add_error_required)) }
                return
            }
            viewModelScope.launch {
                _addState.update { it.copy(loading = true, error = null) }
                when (val result = authRepository.addConnection(current.shopUrl, current.apiKey, current.label)) {
                    AuthResult.Success -> _addState.value = AddShopUiState(visible = false)
                    is AuthResult.Failure -> {
                        val message =
                            when (val reason = result.reason) {
                                is AuthFailure.InvalidShopUrl ->
                                    UiText.FromResources(R.string.auth_error_shop_url_malformed)
                                AuthFailure.ModuleNotInstalled ->
                                    UiText.FromResources(R.string.auth_error_module_not_installed)
                                is AuthFailure.HostUnreachable -> reason.message
                                is AuthFailure.Network -> reason.message
                                is AuthFailure.Unknown -> reason.message
                            }
                        _addState.update { it.copy(loading = false, error = message) }
                    }
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5000L
        }
    }

data class AddShopUiState(
    val visible: Boolean = false,
    val shopUrl: String = "",
    val apiKey: String = "",
    val label: String = "",
    val loading: Boolean = false,
    val error: UiText? = null,
)
