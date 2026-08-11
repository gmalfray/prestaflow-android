package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.sav.SavRepository
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
}
