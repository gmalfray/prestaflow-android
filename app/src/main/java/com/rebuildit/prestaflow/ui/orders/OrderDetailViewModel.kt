package com.rebuildit.prestaflow.ui.orders

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.print.ThermalLabelPrinter
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class OrderDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val ordersRepository: OrdersRepository,
    ) : ViewModel() {
        private val orderId: Long = checkNotNull(savedStateHandle["orderId"])

        val uiState: StateFlow<OrderDetailUiState> =
            ordersRepository.getOrder(orderId)
                .map { order ->
                    if (order != null) {
                        OrderDetailUiState.Success(order)
                    } else {
                        OrderDetailUiState.Loading
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = OrderDetailUiState.Loading,
                )

        private val _actionState = MutableStateFlow(OrderActionState())
        val actionState: StateFlow<OrderActionState> = _actionState.asStateFlow()

        private val _availableStatuses = MutableStateFlow<List<OrderStatusFilter>>(emptyList())
        val availableStatuses: StateFlow<List<OrderStatusFilter>> = _availableStatuses.asStateFlow()

        init {
            viewModelScope.launch {
                runCatching {
                    ordersRepository.refreshOrder(orderId)
                }.onFailure { error ->
                    // Les données en cache (issues de la liste) restent affichées, mais
                    // sans articles/livraison : on remonte l'échec au lieu de l'avaler
                    // pour diagnostiquer (ex. endpoint détail du connecteur indisponible).
                    Timber.w(error, "Échec du chargement du détail commande #%d", orderId)
                    _actionState.update {
                        it.copy(error = UiText.Dynamic("Détail indisponible : affichage des données en cache"))
                    }
                }
            }
            loadStatuses()
        }

        /** Charge les statuts disponibles depuis l'API (silencieux en cas d'erreur). */
        private fun loadStatuses() {
            viewModelScope.launch {
                runCatching { ordersRepository.getOrderStatuses() }
                    .onSuccess { statuses -> _availableStatuses.value = statuses }
                    .onFailure { error ->
                        Timber.w(error, "Impossible de charger les statuts pour le détail commande")
                    }
            }
        }

        fun updateStatus(status: String) {
            val trimmed = status.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                _actionState.update { it.copy(inProgress = true, error = null) }
                runCatching {
                    ordersRepository.updateOrderStatus(orderId, trimmed)
                }.onSuccess {
                    _actionState.update { state ->
                        state.copy(inProgress = false, message = UiText.FromResources(R.string.order_detail_status_updated))
                    }
                }.onFailure { error ->
                    _actionState.update { state ->
                        state.copy(
                            inProgress = false,
                            error = UiText.Dynamic(error.message ?: "Update failed"),
                        )
                    }
                }
            }
        }

        fun updateTracking(trackingNumber: String) {
            val trimmed = trackingNumber.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                _actionState.update { it.copy(inProgress = true, error = null) }
                runCatching {
                    ordersRepository.updateOrderShipping(orderId, trimmed)
                }.onSuccess {
                    _actionState.update { state ->
                        state.copy(inProgress = false, message = UiText.FromResources(R.string.order_detail_tracking_updated))
                    }
                }.onFailure { error ->
                    _actionState.update { state ->
                        state.copy(
                            inProgress = false,
                            error = UiText.Dynamic(error.message ?: "Update failed"),
                        )
                    }
                }
            }
        }

        fun consumeActionFeedback() {
            _actionState.update { it.copy(message = null, error = null) }
        }

        /**
         * Permet au composable Route de remonter un message ou une erreur dans [actionState]
         * (ex. résultat d'une impression thermique).
         */
        fun reportFeedback(
            message: UiText? = null,
            error: UiText? = null,
        ) {
            _actionState.update { it.copy(message = message, error = error) }
        }

        /**
         * Imprime l'étiquette de transport sur l'imprimante thermique Bluetooth.
         *
         * Le rendu PDF → bitmap et l'envoi ESC/POS se font sur [Dispatchers.IO].
         * Le résultat (succès ou erreur) est remonté dans [actionState] sur le Main.
         *
         * @param context    Contexte Android (pour PdfRenderer et BluetoothManager).
         * @param pdfBytes   Octets du PDF de l'étiquette (déjà téléchargés).
         * @param macAddress Adresse MAC de l'imprimante thermique cible.
         * @param reference  Référence de la commande (pour les logs uniquement).
         */
        suspend fun printOnThermalPrinter(
            context: Context,
            pdfBytes: ByteArray,
            macAddress: String,
            reference: String,
        ) {
            runCatching {
                withContext(Dispatchers.IO) {
                    ThermalLabelPrinter.print(context, pdfBytes, macAddress)
                }
            }.onSuccess {
                Timber.i("Impression thermique réussie — commande %s", reference)
                _actionState.update {
                    it.copy(inProgress = false, message = UiText.FromResources(R.string.order_detail_thermal_print_success))
                }
            }.onFailure { error ->
                Timber.e(error, "Impression thermique échouée — commande %s", reference)
                _actionState.update {
                    it.copy(
                        inProgress = false,
                        error = UiText.Dynamic("Impression thermique échouée : ${error.message ?: "erreur inconnue"}"),
                    )
                }
            }
        }

        /**
         * Télécharge la facture PDF de la commande courante.
         * Le résultat (octets PDF ou null si absent) est émis via [actionState].
         */
        fun fetchInvoicePdf(onReady: (ByteArray) -> Unit) {
            viewModelScope.launch {
                _actionState.update { it.copy(inProgress = true, error = null) }
                runCatching {
                    ordersRepository.downloadInvoicePdf(orderId)
                }.onSuccess { bytes ->
                    _actionState.update { it.copy(inProgress = false) }
                    if (bytes != null) {
                        onReady(bytes)
                    } else {
                        _actionState.update {
                            it.copy(error = UiText.FromResources(R.string.order_detail_invoice_unavailable))
                        }
                    }
                }.onFailure { error ->
                    _actionState.update {
                        it.copy(inProgress = false, error = UiText.Dynamic(error.message ?: "Échec du téléchargement de la facture"))
                    }
                }
            }
        }

        /**
         * Télécharge le bordereau de transport PDF de la commande courante.
         * Le résultat (octets PDF ou null si absent) est émis via [actionState].
         */
        fun fetchShippingLabelPdf(onReady: (ByteArray) -> Unit) {
            viewModelScope.launch {
                _actionState.update { it.copy(inProgress = true, error = null) }
                runCatching {
                    ordersRepository.downloadShippingLabel(orderId)
                }.onSuccess { bytes ->
                    _actionState.update { it.copy(inProgress = false) }
                    if (bytes != null) {
                        onReady(bytes)
                    } else {
                        _actionState.update {
                            it.copy(error = UiText.FromResources(R.string.order_detail_shipping_label_unavailable))
                        }
                    }
                }.onFailure { error ->
                    _actionState.update {
                        it.copy(inProgress = false, error = UiText.Dynamic(error.message ?: "Échec du téléchargement du bordereau"))
                    }
                }
            }
        }

        /**
         * Génère l'étiquette Colissimo via le webservice transporteur.
         * Au succès, Room est mis à jour (refreshOrder) → le Flow [uiState] émet automatiquement
         * le nouvel état avec [Order.hasShippingLabel] = true et le n° de suivi mis à jour.
         * En cas d'erreur, un message explicite est émis via [actionState].
         */
        fun onGenerateLabel() {
            viewModelScope.launch {
                _actionState.update { it.copy(inProgress = true, isGeneratingLabel = true, error = null) }
                runCatching {
                    ordersRepository.generateShippingLabel(orderId)
                }.onSuccess {
                    _actionState.update {
                        it.copy(inProgress = false, isGeneratingLabel = false, message = UiText.FromResources(R.string.order_detail_label_generated))
                    }
                }.onFailure { error ->
                    _actionState.update {
                        it.copy(
                            inProgress = false,
                            isGeneratingLabel = false,
                            error = UiText.Dynamic(error.message ?: "Échec de la génération de l'étiquette"),
                        )
                    }
                }
            }
        }
    }

data class OrderActionState(
    val inProgress: Boolean = false,
    /** Vrai uniquement pendant l'appel à generateShippingLabel (spinner dans le bouton). */
    val isGeneratingLabel: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null,
)

sealed interface OrderDetailUiState {
    data object Loading : OrderDetailUiState

    data class Success(val order: Order) : OrderDetailUiState

    data object Error : OrderDetailUiState
}
