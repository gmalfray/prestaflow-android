package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.dashboard.DashboardWidgetsRepository
import com.rebuildit.prestaflow.domain.dashboard.model.AbandonedCartsSummary
import com.rebuildit.prestaflow.domain.dashboard.model.LatestOrder
import com.rebuildit.prestaflow.domain.dashboard.model.PendingReviewsCount
import com.rebuildit.prestaflow.domain.dashboard.model.SalesByUniverse
import com.rebuildit.prestaflow.domain.dashboard.model.TopProduct

/** Fake de [DashboardWidgetsRepository] : compte les appels et peut simuler un échec ou l'absence d'avis. */
class FakeDashboardWidgetsRepository : DashboardWidgetsRepository {
    var latestOrders: List<LatestOrder> = emptyList()
    var carts = AbandonedCartsSummary(count = 0, totalTaxIncl = 0.0)
    var reviews: PendingReviewsCount? = PendingReviewsCount(count = 0, hasMore = false)
    var topProducts: List<TopProduct> = emptyList()
    var salesByUniverse: SalesByUniverse? = SalesByUniverse(emptyList(), 0, 0f)
    var failure: Throwable? = null

    var latestOrdersCalls = 0
    var cartsCalls = 0
    var reviewsCalls = 0
    var topProductsCalls = 0
    var salesByUniverseCalls = 0
    var lastSalesByUniverseRange: Pair<String?, String?>? = null
    var lastTopProductsRange: Pair<String?, String?>? = null

    override suspend fun getLatestOrders(limit: Int): List<LatestOrder> {
        latestOrdersCalls++
        failure?.let { throw it }
        return latestOrders.take(limit)
    }

    override suspend fun getAbandonedCarts(sinceDays: Int): AbandonedCartsSummary {
        cartsCalls++
        failure?.let { throw it }
        return carts
    }

    override suspend fun getPendingReviews(): PendingReviewsCount? {
        reviewsCalls++
        failure?.let { throw it }
        return reviews
    }

    override suspend fun getTopProducts(
        limit: Int,
        dateFrom: String?,
        dateTo: String?,
    ): List<TopProduct> {
        topProductsCalls++
        lastTopProductsRange = dateFrom to dateTo
        failure?.let { throw it }
        return topProducts.take(limit)
    }

    override suspend fun getSalesByUnivers(
        dateFrom: String?,
        dateTo: String?,
    ): SalesByUniverse? {
        salesByUniverseCalls++
        lastSalesByUniverseRange = dateFrom to dateTo
        failure?.let { throw it }
        return salesByUniverse
    }
}
