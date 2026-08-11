package com.rebuildit.prestaflow.ui.sav

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.sav.SavRepository
import com.rebuildit.prestaflow.domain.sav.model.SavThreadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Détail d'un fil SAV : lecture chronologique, changement de statut, réponse.
 *
 * ⚠️ [sendReply] envoie un VRAI e-mail à la cliente — cette méthode ne doit être appelée qu'APRÈS
 * une confirmation explicite de l'utilisatrice dans [SavThreadDetailScreen]. Ce ViewModel
 * lui-même n'implémente aucun garde-fou de confirmation : c'est l'UI qui porte cette
 * responsabilité (dialogue de confirmation avant tout appel à cette méthode), pour que l'appel
 * réseau reste le seul et unique déclencheur d'envoi, sans double-confirmation illusoire côté
 * ViewModel qui pourrait être contournée par un futur appelant.
 */
@HiltViewModel
class SavThreadDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val savRepository: SavRepository,
    ) : ViewModel() {
        private val threadId: Long = checkNotNull(savedStateHandle["threadId"])

        private val _uiState = MutableStateFlow<SavThreadDetailUiState>(SavThreadDetailUiState.Loading)
        val uiState: StateFlow<SavThreadDetailUiState> = _uiState.asStateFlow()

        private val _actionState = MutableStateFlow(SavThreadActionState())
        val actionState: StateFlow<SavThreadActionState> = _actionState.asStateFlow()

        init {
            load()
        }

        fun onRetry() = load()

        private fun load() {
            viewModelScope.launch {
                _uiState.value = SavThreadDetailUiState.Loading
                runCatching { savRepository.fetchThread(threadId) }
                    .onSuccess { detail -> _uiState.value = SavThreadDetailUiState.Success(detail) }
                    .onFailure { error ->
                        Timber.w(error, "Échec du chargement du fil SAV #%d", threadId)
                        _uiState.value = SavThreadDetailUiState.Error
                    }
            }
        }

        fun updateStatus(status: SavThreadStatus) {
            viewModelScope.launch {
                _actionState.update { it.copy(inProgress = true, error = null) }
                runCatching { savRepository.updateThreadStatus(threadId, status) }
                    .onSuccess {
                        _actionState.update {
                            it.copy(inProgress = false, message = UiText.FromResources(R.string.sav_thread_status_updated))
                        }
                        load()
                    }.onFailure { error ->
                        _actionState.update {
                            it.copy(inProgress = false, error = UiText.Dynamic(error.message ?: "Échec de la mise à jour du statut"))
                        }
                    }
            }
        }

        /**
         * ⚠️ Envoie un VRAI e-mail à la cliente. N'appeler qu'après confirmation explicite —
         * cf. Javadoc de la classe.
         */
        fun sendReply(message: String) {
            val trimmed = message.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                _actionState.update { it.copy(inProgress = true, error = null) }
                runCatching { savRepository.replyToThread(threadId, trimmed) }
                    .onSuccess { result ->
                        val feedback =
                            if (result.emailSent) {
                                UiText.FromResources(R.string.sav_thread_reply_sent)
                            } else {
                                // Le connecteur a accepté le message (thread mis à jour) mais n'a
                                // pas pu notifier la cliente (pas d'adresse exploitable) — ce n'est
                                // pas un échec de l'action elle-même, cf. doc SavReplyResult.
                                UiText.FromResources(R.string.sav_thread_reply_sent_no_email)
                            }
                        _actionState.update { it.copy(inProgress = false, message = feedback) }
                        load()
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
    }
