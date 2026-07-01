package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderShipping
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

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

    /** Liste des paires (forceRemote, statusId du premier statut sélectionné) reçues par [refresh]. */
    val refreshCalls = mutableListOf<Pair<Boolean, Int?>>()

    /** Liste des jeux de statusIds reçus lors de chaque appel à [refresh]. */
    val refreshStatusIdsCalls = mutableListOf<Set<Int>>()

    /** Retourne [hasMore] sur le prochain appel à [refresh]. */
    var hasMoreOnRefresh = false

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

    // Flow réactif : réagit aux mises à jour de _ordersFlow (ex. après generateShippingLabel)
    override fun getOrder(orderId: Long): Flow<Order?> = _ordersFlow.map { orders -> orders.find { it.id == orderId } }

    var orderStatuses: List<OrderStatusFilter> = emptyList()

    override suspend fun getOrderStatuses(): List<OrderStatusFilter> = orderStatuses

    override suspend fun refresh(
        forceRemote: Boolean,
        statusIds: Set<Int>,
        sort: String,
        dateFrom: String?,
        dateTo: String?,
        offset: Int,
        limit: Int,
    ): Boolean {
        refreshCalls += Pair(forceRemote, statusIds.firstOrNull())
        refreshStatusIdsCalls += statusIds
        if (shouldThrowOnRefresh) throw refreshException
        return hasMoreOnRefresh
    }

    override suspend fun refreshOrder(orderId: Long) = Unit

    override suspend fun updateOrderStatus(
        orderId: Long,
        status: String,
    ) {
        updateStatusCalls += Pair(orderId, status)
        if (shouldThrowOnUpdateStatus || orderId in failingOrderIds) throw updateStatusException
    }

    /** Appels reçus par [updateOrderShipping] : (orderId, trackingNumber). */
    val updateShippingCalls = mutableListOf<Pair<Long, String>>()

    /** Si vrai, [updateOrderShipping] lance une exception. */
    var shouldThrowOnUpdateShipping = false
    var updateShippingException: Throwable = RuntimeException("Erreur réseau simulée update shipping")

    override suspend fun updateOrderShipping(
        orderId: Long,
        trackingNumber: String,
        carrierId: Long?,
    ) {
        updateShippingCalls += Pair(orderId, trackingNumber)
        if (shouldThrowOnUpdateShipping) throw updateShippingException
    }

    override suspend fun downloadInvoicePdf(orderId: Long): ByteArray? = null

    override suspend fun downloadShippingLabel(orderId: Long): ByteArray? = null

    /** Appels reçus par [generateShippingLabel] : orderId. */
    val generateLabelCalls = mutableListOf<Long>()

    /** Si vrai, [generateShippingLabel] lance une exception. */
    var shouldThrowOnGenerateLabel = false
    var generateLabelException: Throwable = RuntimeException("Génération dispo uniquement pour Colissimo")

    /**
     * N° de suivi à placer sur la commande après génération.
     * Si non null et [generateLabelUpdatesOrder] = true, met à jour [_ordersFlow].
     */
    var generateLabelTrackingNumber: String? = "8R01234567890"

    /** Si vrai, met à jour la commande dans [_ordersFlow] pour simuler le refreshOrder. */
    var generateLabelUpdatesOrder: Boolean = true

    override suspend fun generateShippingLabel(orderId: Long) {
        generateLabelCalls += orderId
        if (shouldThrowOnGenerateLabel) throw generateLabelException
        if (generateLabelUpdatesOrder) {
            val currentOrders = _ordersFlow.value.toMutableList()
            val idx = currentOrders.indexOfFirst { it.id == orderId }
            if (idx >= 0) {
                val order = currentOrders[idx]
                currentOrders[idx] = order.copy(
                    hasShippingLabel = true,
                    shipping = order.shipping?.copy(trackingNumber = generateLabelTrackingNumber)
                        ?: OrderShipping(carrierName = "", trackingNumber = generateLabelTrackingNumber),
                )
                _ordersFlow.value = currentOrders
            }
        }
    }
}
