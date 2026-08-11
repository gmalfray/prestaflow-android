package com.rebuildit.prestaflow.ui.sav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
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
