package com.rebuildit.prestaflow.data.dashboard

import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.data.remote.dto.AcquisitionChannelsResponseDto
import com.rebuildit.prestaflow.data.remote.dto.BestSellerDto
import com.rebuildit.prestaflow.data.remote.dto.CustomerRetentionDto
import com.rebuildit.prestaflow.data.remote.dto.FreeConversionCohortsResponseDto
import com.rebuildit.prestaflow.data.remote.dto.MonthlyTurnoverResponseDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListItemDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewsSummaryResponseDto
import com.rebuildit.prestaflow.data.remote.dto.SalesByUniversResponseDto
import com.rebuildit.prestaflow.domain.carts.CartsRepository
import com.rebuildit.prestaflow.domain.dashboard.DashboardWidgetsRepository
import com.rebuildit.prestaflow.domain.dashboard.model.AbandonedCartsSummary
import com.rebuildit.prestaflow.domain.dashboard.model.AcquisitionChannel
import com.rebuildit.prestaflow.domain.dashboard.model.AcquisitionChannels
import com.rebuildit.prestaflow.domain.dashboard.model.CustomerRetention
import com.rebuildit.prestaflow.domain.dashboard.model.FreeConversionCohort
import com.rebuildit.prestaflow.domain.dashboard.model.FreeConversionCohorts
import com.rebuildit.prestaflow.domain.dashboard.model.LatestOrder
import com.rebuildit.prestaflow.domain.dashboard.model.MonthlyTurnover
import com.rebuildit.prestaflow.domain.dashboard.model.MonthlyTurnoverPoint
import com.rebuildit.prestaflow.domain.dashboard.model.PendingReviewsCount
import com.rebuildit.prestaflow.domain.dashboard.model.ReviewsSummary
import com.rebuildit.prestaflow.domain.dashboard.model.SalesByUniverse
import com.rebuildit.prestaflow.domain.dashboard.model.SavSummary
import com.rebuildit.prestaflow.domain.dashboard.model.TopProduct
import com.rebuildit.prestaflow.domain.dashboard.model.UniverseSales
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

private const val HTTP_CONFLICT = 409
private const val HTTP_NOT_FOUND = 404
private const val PERCENT = 100.0
private const val DEFAULT_ATTRIBUTION_WINDOW_DAYS = 30

/** Maximum accepté par `GET /reviews` ; au-delà, le compteur est un minorant. */
private const val REVIEWS_SCAN_LIMIT = 100

/** Le connecteur renvoie une ligne par déclinaison : on sur-échantillonne avant d'agréger par produit. */
private const val BEST_SELLERS_OVERSAMPLING = 3

@Singleton
class DashboardWidgetsRepositoryImpl
    @Inject
    constructor(
        private val api: PrestaFlowApi,
        private val cartsRepository: CartsRepository,
        private val ioDispatcher: CoroutineDispatcher,
    ) : DashboardWidgetsRepository {
        override suspend fun getLatestOrders(limit: Int): List<LatestOrder> =
            withContext(ioDispatcher) {
                val filters = mapOf("sort" to "date_desc", "limit" to limit.toString(), "offset" to "0")
                api.getOrders(filters).orders.take(limit).map { it.toLatestOrder() }
            }

        override suspend fun getAbandonedCarts(sinceDays: Int): AbandonedCartsSummary =
            withContext(ioDispatcher) {
                val open = cartsRepository.getCarts(abandonedSinceDays = sinceDays).filterNot { it.hasOrder }
                AbandonedCartsSummary(count = open.size, totalTaxIncl = open.sumOf { it.totalTaxIncl })
            }

        override suspend fun getPendingReviews(): PendingReviewsCount? =
            withContext(ioDispatcher) {
                try {
                    val response = api.getReviews(limit = REVIEWS_SCAN_LIMIT, offset = 0)
                    val pagination = response.pagination
                    val total = pagination?.total
                    if (total != null) {
                        PendingReviewsCount(count = total, hasMore = false)
                    } else {
                        PendingReviewsCount(
                            count = response.reviews.size,
                            hasMore = pagination?.hasNext == true || response.reviews.size >= REVIEWS_SCAN_LIMIT,
                        )
                    }
                } catch (error: HttpException) {
                    if (error.code() == HTTP_CONFLICT) null else throw error
                }
            }

        override suspend fun getTopProducts(
            limit: Int,
            dateFrom: String?,
            dateTo: String?,
        ): List<TopProduct> =
            withContext(ioDispatcher) {
                api.getBestSellers(limit = limit * BEST_SELLERS_OVERSAMPLING, dateFrom = dateFrom, dateTo = dateTo)
                    .products
                    .aggregateByProduct()
                    .take(limit)
            }

        override suspend fun getSalesByUnivers(
            dateFrom: String?,
            dateTo: String?,
        ): SalesByUniverse? =
            withContext(ioDispatcher) {
                nullIfUnavailable { api.getSalesByUnivers(dateFrom = dateFrom, dateTo = dateTo).toDomain() }
            }

        override suspend fun getCustomerRetention(
            dateFrom: String?,
            dateTo: String?,
        ): CustomerRetention? =
            withContext(ioDispatcher) {
                nullIfUnavailable { api.getCustomerRetention(dateFrom = dateFrom, dateTo = dateTo).toDomain() }
            }

        override suspend fun getAcquisitionChannels(
            dateFrom: String?,
            dateTo: String?,
            windowDays: Int,
        ): AcquisitionChannels? =
            withContext(ioDispatcher) {
                nullIfUnavailable {
                    api.getAcquisitionChannels(dateFrom = dateFrom, dateTo = dateTo, windowDays = windowDays).toDomain(windowDays)
                }
            }

        override suspend fun getFreeConversionCohorts(months: Int): FreeConversionCohorts? =
            withContext(ioDispatcher) {
                nullIfUnavailable { api.getFreeConversionCohorts(months = months).toDomain() }
            }

        override suspend fun getMonthlyTurnover(months: Int): MonthlyTurnover? =
            withContext(ioDispatcher) {
                nullIfUnavailable { api.getMonthlyTurnover(months = months).toDomain() }
            }

        override suspend fun getReviewsSummary(
            dateFrom: String?,
            dateTo: String?,
        ): ReviewsSummary? =
            withContext(ioDispatcher) {
                nullIfUnavailable { api.getReviewsSummary(dateFrom = dateFrom, dateTo = dateTo).toDomain() }
            }
    }

/** Endpoint absent côté boutique (connecteur pas à jour : 404 ou 409) : `null` ; toute autre erreur est propagée. */
private inline fun <T : Any> nullIfUnavailable(block: () -> T): T? =
    try {
        block()
    } catch (error: HttpException) {
        if (error.code() == HTTP_NOT_FOUND || error.code() == HTTP_CONFLICT) null else throw error
    }

/** Le taux du connecteur prime ; à défaut il est recalculé depuis les effectifs. */
internal fun CustomerRetentionDto.toDomain(): CustomerRetention =
    CustomerRetention(
        payingCustomers = payingCustomers,
        repeatCustomers = repeatCustomers,
        repeatRatePercent =
            repeatRate ?: if (payingCustomers > 0) repeatCustomers * PERCENT / payingCustomers else 0.0,
        medianDaysBetweenOrders = medianDaysBetweenOrders,
        newCustomers = newCustomers,
        recurringCustomers = recurringCustomers,
    )

/** Clés normalisées (trim + minuscules), doublons fusionnés, canaux à 0 commande écartés, tri décroissant. */
internal fun AcquisitionChannelsResponseDto.toDomain(requestedWindowDays: Int = DEFAULT_ATTRIBUTION_WINDOW_DAYS): AcquisitionChannels =
    AcquisitionChannels(
        channels =
            channels
                .filter { it.key.isNotBlank() }
                .groupBy { it.key.trim().lowercase() }
                .map { (key, rows) -> AcquisitionChannel(key = key, orders = rows.sumOf { it.orders }) }
                .filter { it.orders > 0 }
                .sortedByDescending { it.orders },
        windowDays = windowDays ?: requestedWindowDays,
    )

/** Mois sans clé écartés, tri chronologique (le format `yyyy-MM` se trie lexicalement). */
internal fun MonthlyTurnoverResponseDto.toDomain(): MonthlyTurnover =
    MonthlyTurnover(
        months =
            months
                .filter { it.month.isNotBlank() }
                .map { MonthlyTurnoverPoint(month = it.month, turnover = it.turnover, orders = it.orders) }
                .sortedBy { it.month },
    )

/** Note et taux d'avis restent null s'ils sont absents (rien n'est inventé) ; bloc SAV facultatif. */
internal fun ReviewsSummaryResponseDto.toDomain(): ReviewsSummary =
    ReviewsSummary(
        totalReviews = totalReviews,
        nativeReviews = nativeReviews,
        averageRating = averageRating,
        pendingReviews = pendingReviews,
        orders = orders,
        reviewRatePercent = reviewRate,
        sav =
            sav?.let {
                SavSummary(
                    openThreads = it.openThreads,
                    answeredThreads = it.answeredThreads,
                )
            },
    )

/** Mois les plus récents d'abord (le format `yyyy-MM` se trie lexicalement) ; cohortes sans mois écartées. */
internal fun FreeConversionCohortsResponseDto.toDomain(): FreeConversionCohorts =
    FreeConversionCohorts(
        cohorts =
            cohorts
                .filter { it.month.isNotBlank() }
                .map {
                    FreeConversionCohort(
                        month = it.month,
                        firstFreeCustomers = it.firstFreeCustomers,
                        converted30d = it.converted30d,
                        converted60d = it.converted60d,
                        converted90d = it.converted90d,
                    )
                }
                .sortedByDescending { it.month },
    )

/** Garde les univers au CA > 0, triés par CA décroissant ; poids du numérique = CA numérique / CA total. */
internal fun SalesByUniversResponseDto.toDomain(): SalesByUniverse {
    val sold =
        universes
            .filter { it.caTaxIncl > 0.0 }
            .map { UniverseSales(key = it.key, isDigital = it.isDigital, caTaxIncl = it.caTaxIncl, quantity = it.quantity) }
            .sortedByDescending { it.caTaxIncl }
    val total = sold.sumOf { it.caTaxIncl }
    val digital = sold.filter { it.isDigital }.sumOf { it.caTaxIncl }
    return SalesByUniverse(
        universes = sold,
        freePatternsQuantity = freePatterns?.quantity ?: 0,
        digitalShareRatio = if (total > 0.0) (digital / total).toFloat() else 0f,
    )
}

internal fun List<BestSellerDto>.aggregateByProduct(): List<TopProduct> =
    groupBy { it.productId }
        .map { (productId, rows) ->
            TopProduct(
                productId = productId,
                name = rows.firstNotNullOfOrNull { it.name?.takeIf(String::isNotBlank) } ?: rows.first().reference.orEmpty(),
                quantity = rows.sumOf { it.quantity },
                totalTaxIncl = rows.sumOf { it.totalTaxIncl },
            )
        }
        .sortedByDescending { it.quantity }

internal fun OrderListItemDto.toLatestOrder(): LatestOrder =
    LatestOrder(
        id = id,
        reference = reference,
        customerName = "${customer.firstName} ${customer.lastName}".trim(),
        totalPaid = totalPaid,
        currency = currency,
        status = status,
        statusColor = statusColor?.takeIf { it.isNotBlank() },
        createdAtIso = dateAdded,
    )
