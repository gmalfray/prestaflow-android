package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.dashboard.DashboardWidgetsRepository
import com.rebuildit.prestaflow.domain.dashboard.model.AbandonedCartsSummary
import com.rebuildit.prestaflow.domain.dashboard.model.AcquisitionChannels
import com.rebuildit.prestaflow.domain.dashboard.model.CustomerRetention
import com.rebuildit.prestaflow.domain.dashboard.model.FreeConversionCohorts
import com.rebuildit.prestaflow.domain.dashboard.model.LatestOrder
import com.rebuildit.prestaflow.domain.dashboard.model.MonthlyTurnover
import com.rebuildit.prestaflow.domain.dashboard.model.PendingReviewsCount
import com.rebuildit.prestaflow.domain.dashboard.model.ReviewsSummary
import com.rebuildit.prestaflow.domain.dashboard.model.SalesByUniverse
import com.rebuildit.prestaflow.domain.dashboard.model.TopProduct

/** Fake de [DashboardWidgetsRepository] : compte les appels et peut simuler un échec ou l'absence d'avis. */
class FakeDashboardWidgetsRepository : DashboardWidgetsRepository {
    var latestOrders: List<LatestOrder> = emptyList()
    var carts = AbandonedCartsSummary(count = 0, totalTaxIncl = 0.0)
    var reviews: PendingReviewsCount? = PendingReviewsCount(count = 0, hasMore = false)
    var topProducts: List<TopProduct> = emptyList()
    var salesByUniverse: SalesByUniverse? = SalesByUniverse(emptyList(), 0, 0f)
    var customerRetention: CustomerRetention? = CustomerRetention(0, 0, 0.0, null, 0, 0)
    var acquisitionChannels: AcquisitionChannels? = AcquisitionChannels(emptyList(), 30)
    var freeCohorts: FreeConversionCohorts? = FreeConversionCohorts(emptyList())
    var monthlyTurnover: MonthlyTurnover? = MonthlyTurnover(emptyList())
    var reviewsSummary: ReviewsSummary? = ReviewsSummary(0, 0, null, 0, 0, null, null)
    var failure: Throwable? = null

    var monthlyTurnoverCalls = 0
    var reviewsSummaryCalls = 0
    var lastMonthlyMonths: Int? = null
    var lastReviewsSummaryRange: Pair<String?, String?>? = null

    var customerRetentionCalls = 0
    var acquisitionChannelsCalls = 0
    var freeCohortsCalls = 0
    var lastRetentionRange: Pair<String?, String?>? = null
    var lastChannelsWindowDays: Int? = null
    var lastCohortsMonths: Int? = null

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

    override suspend fun getCustomerRetention(
        dateFrom: String?,
        dateTo: String?,
    ): CustomerRetention? {
        customerRetentionCalls++
        lastRetentionRange = dateFrom to dateTo
        failure?.let { throw it }
        return customerRetention
    }

    override suspend fun getAcquisitionChannels(
        dateFrom: String?,
        dateTo: String?,
        windowDays: Int,
    ): AcquisitionChannels? {
        acquisitionChannelsCalls++
        lastChannelsWindowDays = windowDays
        failure?.let { throw it }
        return acquisitionChannels
    }

    override suspend fun getFreeConversionCohorts(months: Int): FreeConversionCohorts? {
        freeCohortsCalls++
        lastCohortsMonths = months
        failure?.let { throw it }
        return freeCohorts
    }

    override suspend fun getMonthlyTurnover(months: Int): MonthlyTurnover? {
        monthlyTurnoverCalls++
        lastMonthlyMonths = months
        failure?.let { throw it }
        return monthlyTurnover
    }

    override suspend fun getReviewsSummary(
        dateFrom: String?,
        dateTo: String?,
    ): ReviewsSummary? {
        reviewsSummaryCalls++
        lastReviewsSummaryRange = dateFrom to dateTo
        failure?.let { throw it }
        return reviewsSummary
    }
}
