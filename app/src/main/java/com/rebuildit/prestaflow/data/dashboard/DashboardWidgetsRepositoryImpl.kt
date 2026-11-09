package com.rebuildit.prestaflow.data.dashboard

import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.data.remote.dto.BestSellerDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListItemDto
import com.rebuildit.prestaflow.data.remote.dto.SalesByUniversResponseDto
import com.rebuildit.prestaflow.domain.carts.CartsRepository
import com.rebuildit.prestaflow.domain.dashboard.DashboardWidgetsRepository
import com.rebuildit.prestaflow.domain.dashboard.model.AbandonedCartsSummary
import com.rebuildit.prestaflow.domain.dashboard.model.LatestOrder
import com.rebuildit.prestaflow.domain.dashboard.model.PendingReviewsCount
import com.rebuildit.prestaflow.domain.dashboard.model.SalesByUniverse
import com.rebuildit.prestaflow.domain.dashboard.model.TopProduct
import com.rebuildit.prestaflow.domain.dashboard.model.UniverseSales
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

private const val HTTP_CONFLICT = 409
private const val HTTP_NOT_FOUND = 404

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
                try {
                    api.getSalesByUnivers(dateFrom = dateFrom, dateTo = dateTo).toDomain()
                } catch (error: HttpException) {
                    if (error.code() == HTTP_NOT_FOUND || error.code() == HTTP_CONFLICT) null else throw error
                }
            }
    }

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
