package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.sav.SavRepository
import com.rebuildit.prestaflow.domain.sav.model.SavReplyResult
import com.rebuildit.prestaflow.domain.sav.model.SavThreadDetail
import com.rebuildit.prestaflow.domain.sav.model.SavThreadStatus
import com.rebuildit.prestaflow.domain.sav.model.SavThreadsPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Fake en mémoire de [SavRepository]. */
class FakeSavRepository(
    initialUnreadCount: Int = 0,
) : SavRepository {
    private val _unreadThreadCount = MutableStateFlow(initialUnreadCount)
    override val unreadThreadCount: StateFlow<Int> = _unreadThreadCount

    fun emitUnreadCount(count: Int) {
        _unreadThreadCount.value = count
    }

    /** Valeur émise dans [unreadThreadCount] au prochain [refreshUnreadCount] réussi. */
    var nextRefreshUnreadCount: Int? = null
    var shouldThrowOnRefreshUnreadCount = false
    var refreshUnreadCountCallCount = 0

    override suspend fun refreshUnreadCount() {
        refreshUnreadCountCallCount++
        if (shouldThrowOnRefreshUnreadCount) return // best-effort : ne remonte jamais l'erreur à l'appelant
        nextRefreshUnreadCount?.let { _unreadThreadCount.value = it }
    }

    var fetchThreadsResult: SavThreadsPage = SavThreadsPage(threads = emptyList(), hasNext = false, nextOffset = 0)
    var shouldThrowOnFetchThreads = false
    var lastFetchThreadsCall: FetchThreadsCall? = null

    override suspend fun fetchThreads(
        status: SavThreadStatus?,
        limit: Int,
        offset: Int,
    ): SavThreadsPage {
        lastFetchThreadsCall = FetchThreadsCall(status = status, limit = limit, offset = offset)
        if (shouldThrowOnFetchThreads) throw RuntimeException("Erreur réseau fetchThreads simulée")
        return fetchThreadsResult
    }

    var fetchThreadResult: SavThreadDetail? = null
    var shouldThrowOnFetchThread = false

    override suspend fun fetchThread(threadId: Long): SavThreadDetail {
        if (shouldThrowOnFetchThread) throw RuntimeException("Erreur réseau fetchThread simulée")
        return checkNotNull(fetchThreadResult) { "fetchThreadResult non configuré dans le fake" }
    }

    var shouldThrowOnUpdateStatus = false
    val updateStatusCalls = mutableListOf<Pair<Long, SavThreadStatus>>()

    override suspend fun updateThreadStatus(
        threadId: Long,
        status: SavThreadStatus,
    ) {
        updateStatusCalls.add(threadId to status)
        if (shouldThrowOnUpdateStatus) throw RuntimeException("Erreur réseau updateThreadStatus simulée")
    }

    var replyResult: SavReplyResult? = null
    var shouldThrowOnReply = false
    val replyCalls = mutableListOf<Pair<Long, String>>()

    override suspend fun replyToThread(
        threadId: Long,
        message: String,
    ): SavReplyResult {
        replyCalls.add(threadId to message)
        if (shouldThrowOnReply) throw RuntimeException("Erreur réseau replyToThread simulée")
        return checkNotNull(replyResult) { "replyResult non configuré dans le fake" }
    }

    data class FetchThreadsCall(
        val status: SavThreadStatus?,
        val limit: Int,
        val offset: Int,
    )
}
