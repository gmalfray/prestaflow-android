package com.rebuildit.prestaflow.ui.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.core.util.ScreenResumeRefreshGuard
import com.rebuildit.prestaflow.domain.reviews.ReviewRejectionReason
import com.rebuildit.prestaflow.domain.reviews.ReviewsRepository
import com.rebuildit.prestaflow.domain.reviews.model.Review
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * File de modération des avis (`GET /reviews`, paginée). Volume faible en prod (cf. étude
 * `rebuild-it/docs/app-avis-sav.md` § « Ce que disent les données ») — la pagination reste par
 * cohérence avec le reste de l'app, pas par nécessité mesurée.
 */
@HiltViewModel
class ReviewsViewModel
    @Inject
    constructor(
        private val reviewsRepository: ReviewsRepository,
        private val networkErrorMapper: NetworkErrorMapper,
        private val resumeRefreshGuard: ScreenResumeRefreshGuard,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<ReviewsUiState>(ReviewsUiState.Loading)
        val uiState: StateFlow<ReviewsUiState> = _uiState.asStateFlow()

        private val _actionState = MutableStateFlow(ReviewActionState())
        val actionState: StateFlow<ReviewActionState> = _actionState.asStateFlow()

        private var loadJob: Job? = null
        private var nextOffset = 0

        init {
            load(resetPage = true)
        }

        fun onRefresh() = load(resetPage = true)

        /**
         * Rattrapage au retour sur l'écran Avis (cf. KDoc de
         * [com.rebuildit.prestaflow.ui.orders.OrdersViewModel.onScreenResumed] pour le contrat
         * général implémenté par [resumeRefreshGuard]).
         *
         * Aucun contrôle de capacité/scope ici : cet écran n'est atteignable QUE si la capacité
         * `reviews` est vraie ET si le jeton porte `reviews.moderate` (cf.
         * [com.rebuildit.prestaflow.ui.clients.ClientsSection.visibleSections], seul point d'entrée
         * vers `ReviewsRoute`/ce ViewModel) — si l'une des deux venait à disparaître en cours de
         * session, le sous-onglet Avis disparaîtrait AVANT qu'un retour sur cet écran ne soit
         * possible.
         *
         * Rafraîchit AUSSI [ReviewsRepository.pendingReviewCount] (pastille de l'onglet Clients, cf.
         * [com.rebuildit.prestaflow.ui.root.RootViewModel.clientsBadgeCount]) : sans ce second
         * appel, un avis modéré ailleurs pendant qu'on était sur un autre onglet disparaîtrait de
         * CETTE liste au retour sans que la pastille du shell ne se mette à jour — liste et
         * pastille se contrediraient. Même throttle que la liste (un seul [resumeRefreshGuard]).
         */
        fun onScreenResumed() {
            val busy =
                when (val state = _uiState.value) {
                    is ReviewsUiState.Loading -> true
                    is ReviewsUiState.Content -> state.isRefreshing
                    is ReviewsUiState.Error -> false
                }
            if (!resumeRefreshGuard.shouldRefresh(isBusy = busy)) return
            load(resetPage = true)
            viewModelScope.launch { reviewsRepository.refreshPendingCount() }
        }

        fun onLoadMore() {
            val state = currentContent() ?: return
            if (!state.hasNextPage || state.isLoadingMore) return
            load(resetPage = false)
        }

        fun onPublish(reviewId: Long) {
            viewModelScope.launch {
                _actionState.update { it.copy(inProgress = true, error = null) }
                runCatching { reviewsRepository.publish(reviewId) }
                    .onSuccess {
                        removeFromQueue(reviewId)
                        _actionState.update {
                            it.copy(inProgress = false, message = UiText.FromResources(R.string.reviews_action_published))
                        }
                    }.onFailure { error ->
                        _actionState.update {
                            it.copy(inProgress = false, error = UiText.Dynamic(error.message ?: "Échec de la publication"))
                        }
                    }
            }
        }

        /**
         * ⚠️ Motif OBLIGATOIRE (≥ [ReviewRejectionReason.MIN_LENGTH] caractères, article
         * L111-7-2) — le bouton de confirmation dans l'UI ne doit être actif que si
         * [ReviewRejectionReason.isValid] est vrai ; ce ViewModel revalide malgré tout avant
         * l'appel réseau (aucun geste de rejet rapide ne doit pouvoir contourner la règle).
         */
        fun onTrash(
            reviewId: Long,
            reason: String,
        ) {
            if (!ReviewRejectionReason.isValid(reason)) return
            viewModelScope.launch {
                _actionState.update { it.copy(inProgress = true, error = null) }
                runCatching { reviewsRepository.trash(reviewId, reason) }
                    .onSuccess { result ->
                        removeFromQueue(reviewId)
                        val feedback =
                            if (result.authorNotified) {
                                R.string.reviews_action_trashed
                            } else {
                                // Mise en corbeille réussie mais e-mail de motif non envoyé (adresse
                                // absente) — pas un échec de l'action, cf. doc ReviewTrashResult.
                                R.string.reviews_action_trashed_no_email
                            }
                        _actionState.update { it.copy(inProgress = false, message = UiText.FromResources(feedback)) }
                    }.onFailure { error ->
                        _actionState.update {
                            it.copy(inProgress = false, error = UiText.Dynamic(error.message ?: "Échec de la mise à la corbeille"))
                        }
                    }
            }
        }

        fun onReply(
            reviewId: Long,
            reply: String,
        ) {
            val trimmed = reply.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                _actionState.update { it.copy(inProgress = true, error = null) }
                runCatching { reviewsRepository.reply(reviewId, trimmed) }
                    .onSuccess { updated ->
                        updateInQueue(updated)
                        _actionState.update {
                            it.copy(inProgress = false, message = UiText.FromResources(R.string.reviews_action_replied))
                        }
                    }.onFailure { error ->
                        _actionState.update {
                            it.copy(inProgress = false, error = UiText.Dynamic(error.message ?: "Échec de l'envoi de la réponse"))
                        }
                    }
            }
        }

        fun consumeActionFeedback() {
            _actionState.update { it.copy(message = null, error = null) }
        }

        private fun currentContent(): ReviewsUiState.Content? = _uiState.value as? ReviewsUiState.Content

        private fun removeFromQueue(reviewId: Long) {
            val content = currentContent() ?: return
            _uiState.value = content.copy(reviews = content.reviews.filterNot { it.id == reviewId })
        }

        private fun updateInQueue(review: Review) {
            val content = currentContent() ?: return
            _uiState.value = content.copy(reviews = content.reviews.map { if (it.id == review.id) review else it })
        }

        private fun load(resetPage: Boolean) {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    val previous = currentContent()
                    _uiState.value =
                        when {
                            resetPage && previous != null -> previous.copy(isRefreshing = true, error = null)
                            resetPage -> ReviewsUiState.Loading
                            previous != null -> previous.copy(isLoadingMore = true)
                            else -> return@launch
                        }
                    if (resetPage) nextOffset = 0

                    runCatching {
                        reviewsRepository.fetchPendingReviews(offset = if (resetPage) 0 else nextOffset)
                    }.onSuccess { page ->
                        nextOffset = page.nextOffset
                        val merged = if (resetPage) page.reviews else previous?.reviews.orEmpty() + page.reviews
                        _uiState.value =
                            ReviewsUiState.Content(
                                reviews = merged,
                                hasNextPage = page.hasNext,
                                isLoadingMore = false,
                                isRefreshing = false,
                                error = null,
                            )
                        // Repère du throttle de onScreenResumed : seul un rechargement COMPLET
                        // (resetPage) compte comme "réussi" — loadMore ne le remet pas à zéro.
                        if (resetPage) resumeRefreshGuard.markRefreshSucceeded()
                    }.onFailure { error ->
                        Timber.w(error, "Échec du chargement des avis en modération")
                        val mapped = networkErrorMapper.map(error)
                        _uiState.value =
                            if (previous != null) {
                                previous.copy(isLoadingMore = false, isRefreshing = false, error = mapped)
                            } else {
                                ReviewsUiState.Error(mapped)
                            }
                    }
                }
        }
    }
