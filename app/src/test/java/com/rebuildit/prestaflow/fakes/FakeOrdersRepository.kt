package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake en mémoire de [OrdersRepository] pour les tests JVM.
 *
 * - [ordersFlow] permet d'émettre des listes de commandes depuis le test.
 * - [refreshCalls] enregistre chaque appel à [refresh] (forceRemote, statusId).
 * - [shouldThrowOnRefresh] force un échec réseau si vrai.
 */
class FakeOrdersRepository : OrdersRepository {
    private val _ordersFlow = MutableStateFlow<List<Order>>(emptyList())

    fun setOrders(orders: List<Order>) {
        _ordersFlow.value = orders
    }

    /** Liste des paires (forceRemote, statusId) reçues par [refresh]. */
    val refreshCalls = mutableListOf<Pair<Boolean, Int?>>()

    var shouldThrowOnRefresh = false
    var refreshException: Throwable = RuntimeException("Erreur réseau simulée")

    /** Appels reçus par [updateOrderStatus] : (orderId, status). */
    val updateStatusCalls = mutableListOf<Pair<Long, String>>()

    /** Si vrai, [updateOrderStatus] lance une exception. */
    var shouldThrowOnUpdateStatus = false
    var updateStatusException: Throwable = RuntimeException("Erreur réseau simulée update status")

    /**
     * IDs pour lesquels [updateOrderStatus] doit échouer
     * (pour simuler des échecs partiels en mode batch).
     */
    val failingOrderIds = mutableSetOf<Long>()

    override fun observeOrders(): Flow<List<Order>> = _ordersFlow.asStateFlow()

    override fun getOrder(orderId: Long): Flow<Order?> = MutableStateFlow(_ordersFlow.value.find { it.id == orderId })

    var orderStatuses: List<OrderStatusFilter> = emptyList()

    override suspend fun getOrderStatuses(): List<OrderStatusFilter> = orderStatuses

    override suspend fun refresh(
        forceRemote: Boolean,
        statusId: Int?,
        dateFrom: String?,
        dateTo: String?,
    ) {
        refreshCalls += Pair(forceRemote, statusId)
        if (shouldThrowOnRefresh) throw refreshException
    }

    override suspend fun refreshOrder(orderId: Long) = Unit

    override suspend fun updateOrderStatus(
        orderId: Long,
        status: String,
    ) {
        updateStatusCalls += Pair(orderId, status)
        if (shouldThrowOnUpdateStatus || orderId in failingOrderIds) throw updateStatusException
    }

    override suspend fun updateOrderShipping(
        orderId: Long,
        trackingNumber: String,
        carrierId: Long?,
    ) = Unit

    override suspend fun downloadInvoicePdf(orderId: Long): ByteArray? = null

    override suspend fun downloadShippingLabel(orderId: Long): ByteArray? = null
}
