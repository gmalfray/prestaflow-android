package com.rebuildit.prestaflow.ui.sav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.util.ScreenResumeRefreshGuard
import com.rebuildit.prestaflow.domain.sav.SavRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Liste des fils SAV (`GET /sav`, paginée — 481 fils dont 97 ouverts mesurés en prod, cf. étude
 * `rebuild-it/docs/app-avis-sav.md`). Le chargement initial est sans filtre : le connecteur trie
 * déjà « non-clos d'abord » par défaut.
 */
@HiltViewModel
class SavViewModel
    @Inject
    constructor(
        private val savRepository: SavRepository,
        private val networkErrorMapper: NetworkErrorMapper,
        private val resumeRefreshGuard: ScreenResumeRefreshGuard,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SavUiState>(SavUiState.Loading)
        val uiState: StateFlow<SavUiState> = _uiState.asStateFlow()

        private var loadJob: Job? = null
        private var nextOffset = 0

        init {
            load(filter = SavStatusFilter.ALL, resetPage = true)
        }

        fun onRefresh() {
            val filter = currentContent()?.filter ?: SavStatusFilter.ALL
            load(filter = filter, resetPage = true)
        }

        fun onFilterChange(filter: SavStatusFilter) {
            if (filter == currentContent()?.filter) return
            load(filter = filter, resetPage = true)
        }

        /**
         * Rattrapage au retour sur l'écran SAV (cf. KDoc de
         * [com.rebuildit.prestaflow.ui.orders.OrdersViewModel.onScreenResumed] pour le contrat
         * général implémenté par [resumeRefreshGuard]). Recharge la 1ʳᵉ page avec le filtre de
         * statut déjà actif (conserve [SavUiState.Content.filter]).
         *
         * Aucun contrôle de scope ici : cet écran n'est atteignable QUE si le jeton porte
         * `sav.read` (cf. [com.rebuildit.prestaflow.ui.clients.ClientsSection.visibleSections],
         * seul point d'entrée vers `SavRoute`/ce ViewModel) — si le scope venait à disparaître en
         * cours de session, le sous-onglet SAV disparaîtrait AVANT qu'un retour sur cet écran ne
         * soit possible, donc avant que [onScreenResumed] ne puisse être appelée.
         *
         * Rafraîchit AUSSI [SavRepository.toProcessCount] (pastille de l'onglet Clients, cf.
         * [com.rebuildit.prestaflow.ui.root.RootViewModel.clientsBadgeCount]) : sans ce second
         * appel, un fil traité par quelqu'un d'autre pendant qu'on était sur un autre onglet
         * ferait disparaître ce fil de CETTE liste au retour, tout en laissant la pastille du
         * shell afficher l'ancien total — liste et pastille se contrediraient. Même throttle que
         * la liste (un seul et même [resumeRefreshGuard]) : pas d'appel réseau supplémentaire à
         * chaque aller-retour rapide entre onglets.
         */
        fun onScreenResumed() {
            val busy =
                when (val state = _uiState.value) {
                    is SavUiState.Loading -> true
                    is SavUiState.Content -> state.isRefreshing
                    is SavUiState.Error -> false
                }
            if (!resumeRefreshGuard.shouldRefresh(isBusy = busy)) return
            load(filter = currentContent()?.filter ?: SavStatusFilter.ALL, resetPage = true)
            viewModelScope.launch { savRepository.refreshToProcessCount() }
        }

        fun onLoadMore() {
            val state = currentContent() ?: return
            if (!state.hasNextPage || state.isLoadingMore) return
            load(filter = state.filter, resetPage = false)
        }

        private fun currentContent(): SavUiState.Content? = _uiState.value as? SavUiState.Content

        private fun load(
            filter: SavStatusFilter,
            resetPage: Boolean,
        ) {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    val previous = currentContent()

                    _uiState.value =
                        when {
                            resetPage && previous != null ->
                                previous.copy(filter = filter, isRefreshing = true, error = null)
                            resetPage -> SavUiState.Loading
                            previous != null -> previous.copy(isLoadingMore = true)
                            else -> return@launch
                        }
                    if (resetPage) nextOffset = 0

                    runCatching {
                        savRepository.fetchThreads(
                            status = filter.threadStatus,
                            offset = if (resetPage) 0 else nextOffset,
                        )
                    }.onSuccess { page ->
                        nextOffset = page.nextOffset
                        val merged = if (resetPage) page.threads else previous?.threads.orEmpty() + page.threads
                        _uiState.value =
                            SavUiState.Content(
                                threads = merged,
                                filter = filter,
                                hasNextPage = page.hasNext,
                                isLoadingMore = false,
                                isRefreshing = false,
                                error = null,
                            )
                        // Repère du throttle de onScreenResumed : seul un rechargement COMPLET
                        // (resetPage) compte comme "réussi" — loadMore ne le remet pas à zéro.
                        if (resetPage) resumeRefreshGuard.markRefreshSucceeded()
                    }.onFailure { error ->
                        Timber.w(error, "Échec du chargement des fils SAV (filter=$filter, resetPage=$resetPage)")
                        val mapped = networkErrorMapper.map(error)
                        _uiState.value =
                            if (previous != null) {
                                previous.copy(filter = filter, isLoadingMore = false, isRefreshing = false, error = mapped)
                            } else {
                                SavUiState.Error(mapped)
                            }
                    }
                }
        }
    }
