package com.rebuildit.prestaflow.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.core.util.ScreenResumeRefreshGuard
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.products.ProductsRepository
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.StockFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Debounce (ms) avant de déclencher une recherche API sur changement de query. */
private const val SEARCH_DEBOUNCE_MS = 300L

@OptIn(FlowPreview::class)
@HiltViewModel
class ProductsViewModel
    @Inject
    constructor(
        private val productsRepository: ProductsRepository,
        private val networkErrorMapper: NetworkErrorMapper,
        private val authRepository: AuthRepository,
        private val resumeRefreshGuard: ScreenResumeRefreshGuard,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ProductsUiState())
        val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

        init {
            observeProducts()
            refresh(forceRemote = true, notifyOnError = false)
            refreshLowStockCount()
            refreshCatalogTotal()
            observeActiveShopSwitch()
            observeSearchQuery()
        }

        fun onRefresh() {
            refresh(forceRemote = true, notifyOnError = true)
            refreshLowStockCount()
            refreshCatalogTotal()
        }

        /**
         * Rattrapage au retour sur l'écran Produits (cf. KDoc de
         * [com.rebuildit.prestaflow.ui.orders.OrdersViewModel.onScreenResumed] pour le contrat
         * général implémenté par [resumeRefreshGuard]). Recharge la liste avec le filtre de stock et
         * la recherche déjà en place ([refresh] les relit depuis [_uiState] courant), silencieux en
         * cas d'échec. Rafraîchit aussi les 2 compteurs KPI (stock faible, total catalogue) pour
         * qu'ils ne restent pas figés pendant que la liste, elle, se met à jour.
         */
        fun onScreenResumed() {
            if (!resumeRefreshGuard.shouldRefresh(isBusy = _uiState.value.isRefreshing)) return
            refresh(forceRemote = true, notifyOnError = false)
            refreshLowStockCount()
            refreshCatalogTotal()
        }

        /**
         * Récupère le total serveur des produits en stock faible (KPI en tête d'écran), indépendamment
         * du filtre actif et de la pagination locale. Silencieux en cas d'échec : on garde la valeur
         * courante (à défaut, l'écran retombe sur une approximation locale). Cf.
         * [ProductsRepository.countByStock].
         */
        private fun refreshLowStockCount() {
            viewModelScope.launch {
                val count = productsRepository.countByStock(StockFilter.LOW_STOCK.stockParam)
                if (count != null) {
                    _uiState.update { it.copy(lowStockTotal = count) }
                }
            }
        }

        /**
         * Récupère le total serveur du catalogue COMPLET (actifs + inactifs, cf.
         * [ProductsRepository.countByStock] appelé sans filtre) pour le KPI « Total produits » en
         * tête d'écran. Volontairement indépendant du filtre actif : sélectionner « Inactifs » (ou
         * tout autre filtre) ne doit pas faire chuter ce chiffre au sous-ensemble filtré — sinon le
         * libellé « Total produits » deviendrait trompeur. Silencieux en cas d'échec (on garde la
         * valeur courante, avec repli sur le total du filtre actif tant qu'aucune réponse n'est
         * arrivée, cf. [ProductsUiState.catalogTotal]).
         */
        private fun refreshCatalogTotal() {
            viewModelScope.launch {
                val count = productsRepository.countByStock(null)
                if (count != null) {
                    _uiState.update { it.copy(catalogTotal = count) }
                }
            }
        }

        fun onQueryChange(query: String) {
            _uiState.update { it.copy(query = query) }
        }

        /** Sélectionne un filtre de stock puis recharge la liste depuis le serveur. */
        fun onStockFilterSelected(filter: StockFilter) {
            _uiState.update { it.copy(stockFilter = filter) }
            refresh(forceRemote = true, notifyOnError = true)
        }

        private fun observeActiveShopSwitch() {
            viewModelScope.launch {
                authRepository.connections
                    .map { list -> list.firstOrNull { it.isActive }?.id }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect {
                        _uiState.update { current ->
                            current.copy(
                                products = emptyList(),
                                totalCount = 0,
                                isLoading = true,
                                error = null,
                                stockFilter = StockFilter.ALL,
                                lowStockTotal = null,
                                catalogTotal = null,
                            )
                        }
                        refresh(forceRemote = true, notifyOnError = true)
                        refreshLowStockCount()
                        refreshCatalogTotal()
                    }
            }
        }

        /**
         * Observe les changements de query avec un debounce de 300 ms pour déclencher
         * une recherche API sans spammer le serveur à chaque frappe.
         */
        private fun observeSearchQuery() {
            viewModelScope.launch {
                _uiState
                    .map { it.query }
                    .distinctUntilChanged()
                    .drop(1) // ignore la valeur initiale vide
                    .debounce(SEARCH_DEBOUNCE_MS)
                    .collect {
                        refresh(forceRemote = true, notifyOnError = false)
                    }
            }
        }

        private fun observeProducts() {
            viewModelScope.launch {
                productsRepository.observeProducts().collect { products ->
                    _uiState.update { current ->
                        current.copy(
                            products = products,
                            // Ne quitte l'état de chargement que si des produits sont arrivés, OU
                            // si le refresh réseau initial a déjà tranché. Sinon, la 1ère émission
                            // Room (cache vide au 1er lancement ou après changement de boutique)
                            // ferait flasher l'état "vide" avant la vraie réponse réseau.
                            isLoading = current.isLoading && products.isEmpty(),
                            isRefreshing = false,
                            error = if (products.isNotEmpty()) null else current.error,
                        )
                    }
                }
            }
        }

        private fun refresh(
            forceRemote: Boolean,
            notifyOnError: Boolean,
        ) {
            viewModelScope.launch {
                _uiState.update { current ->
                    current.copy(
                        isRefreshing = true,
                        isLoading = current.products.isEmpty(),
                        error = if (notifyOnError) null else current.error,
                    )
                }

                val filter = _uiState.value.stockFilter
                val search = _uiState.value.query.takeIf { it.isNotBlank() }
                runCatching { productsRepository.refresh(forceRemote, filter.stockParam, filter.activeParam, search) }
                    .onFailure { error ->
                        Timber.w(error, "Failed to refresh products")
                        _uiState.update { current ->
                            val mapped = networkErrorMapper.map(error)
                            current.copy(
                                isRefreshing = false,
                                isLoading = current.products.isEmpty(),
                                error = if (notifyOnError) mapped else current.error,
                            )
                        }
                    }
                    .onSuccess { total ->
                        _uiState.update { current ->
                            current.copy(
                                isRefreshing = false,
                                isLoading = current.products.isEmpty(),
                                error = null,
                                totalCount = total ?: current.totalCount,
                            )
                        }
                        resumeRefreshGuard.markRefreshSucceeded()
                    }
            }
        }
    }

data class ProductsUiState(
    val products: List<Product> = emptyList(),
    /**
     * Total réel rapporté par l'API (tient compte des filtres actifs et de la recherche).
     * Vaut 0 tant que le premier refresh n'a pas abouti.
     */
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: UiText? = null,
    val query: String = "",
    /** Filtre de stock actif. */
    val stockFilter: StockFilter = StockFilter.ALL,
    /**
     * Total serveur des produits en stock faible (KPI en tête), stable et indépendant du filtre
     * actif / de la pagination. `null` tant que le compteur n'a pas répondu → l'écran affiche alors
     * une approximation locale.
     */
    val lowStockTotal: Int? = null,
    /**
     * Total serveur du catalogue COMPLET (actifs + inactifs), indépendant du filtre actif — sert au
     * KPI « Total produits ». `null` tant que le compteur n'a pas répondu → l'écran retombe alors sur
     * [totalCount] (total du filtre actif) en attendant.
     */
    val catalogTotal: Int? = null,
) {
    /**
     * La recherche est déléguée à l'API : [products] contient déjà les résultats filtrés
     * par le serveur. Pas de filtrage local supplémentaire.
     */
    val visibleProducts: List<Product> get() = products
}
