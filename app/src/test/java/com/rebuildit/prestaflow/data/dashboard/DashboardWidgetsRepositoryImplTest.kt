package com.rebuildit.prestaflow.data.dashboard

import com.rebuildit.prestaflow.data.remote.dto.AcquisitionChannelsResponseDto
import com.rebuildit.prestaflow.data.remote.dto.BestSellerDto
import com.rebuildit.prestaflow.data.remote.dto.BestSellersResponseDto
import com.rebuildit.prestaflow.data.remote.dto.CustomerRetentionDto
import com.rebuildit.prestaflow.data.remote.dto.FreeConversionCohortsResponseDto
import com.rebuildit.prestaflow.data.remote.dto.MonthlyTurnoverResponseDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListCustomerDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListItemDto
import com.rebuildit.prestaflow.data.remote.dto.PaginationDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewListResponseDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewsSummaryResponseDto
import com.rebuildit.prestaflow.data.remote.dto.SalesByUniversResponseDto
import com.rebuildit.prestaflow.domain.carts.model.CartSummary
import com.rebuildit.prestaflow.domain.dashboard.model.AcquisitionChannel
import com.rebuildit.prestaflow.fakes.FakeCartsRepository
import com.rebuildit.prestaflow.fakes.FakePrestaFlowApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.Response as OkHttpResponse

class DashboardWidgetsRepositoryImplTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val api = FakePrestaFlowApi()
    private val carts = FakeCartsRepository()
    private val repo = DashboardWidgetsRepositoryImpl(api, carts, UnconfinedTestDispatcher())

    private fun httpException(code: Int) =
        HttpException(
            Response.error<Any>(
                """{"error":"reviews_unavailable"}""".toResponseBody("application/json".toMediaTypeOrNull()),
                OkHttpResponse.Builder()
                    .request(Request.Builder().url("https://shop.test/api/reviews").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("Error")
                    .build(),
            ),
        )

    private fun review(id: Long) = ReviewDto(id = id)

    private fun cart(
        id: Int,
        total: Double,
        hasOrder: Boolean = false,
    ) = CartSummary(id, "Client $id", null, "EUR", total, 1, hasOrder, null, null)

    @Test
    fun `bestsellers - le DTO se désérialise et les déclinaisons sont agrégées par produit`() =
        runTest {
            val dto =
                json.decodeFromString(
                    BestSellersResponseDto.serializer(),
                    """{"products":[
                        {"product_id":7,"product_attribute_id":null,"name":"Fil","reference":"F1","quantity":3,
                         "total_tax_incl":30.0,"total_tax_excl":25.0},
                        {"product_id":9,"product_attribute_id":2,"name":null,"reference":"R9","quantity":9,
                         "total_tax_incl":90.0,"total_tax_excl":75.0},
                        {"product_id":7,"product_attribute_id":4,"name":"Fil","reference":"F1","quantity":4,
                         "total_tax_incl":44.0,"total_tax_excl":36.0}]}""",
                )
            assertEquals(3, dto.products.size)
            assertNull(dto.products[0].productAttributeId)

            api.bestSellers = dto
            val top = repo.getTopProducts(limit = 5, dateFrom = "2026-11-01 00:00:00", dateTo = "2026-11-09 23:59:59")

            assertEquals(Triple(15, "2026-11-01 00:00:00", "2026-11-09 23:59:59"), api.lastBestSellersQuery)
            assertEquals(listOf(9, 7), top.map { it.productId })
            assertEquals("R9", top[0].name) // nom absent → référence
            assertEquals(7, top[1].quantity)
            assertEquals(74.0, top[1].totalTaxIncl, 0.0001)
        }

    @Test
    fun `bestsellers - champs absents par défaut et limite respectée`() =
        runTest {
            api.bestSellers =
                BestSellersResponseDto(products = (1..10).map { BestSellerDto(productId = it, quantity = it) })
            val top = repo.getTopProducts(limit = 3, dateFrom = null, dateTo = null)
            assertEquals(listOf(10, 9, 8), top.map { it.productId })
        }

    @Test
    fun `dernières commandes - limite, tri récent d'abord et mapping`() =
        runTest {
            api.ordersResponse =
                OrderListDto(
                    orders =
                        listOf(
                            OrderListItemDto(
                                id = 5,
                                reference = "ABC",
                                status = "Payé",
                                totalPaid = 12.5,
                                currency = "EUR",
                                dateAdded = "2026-11-09 10:00:00",
                                customer = OrderListCustomerDto(1, "Ada", "Lovelace"),
                                statusColor = "#32CD32",
                            ),
                        ),
                )
            val orders = repo.getLatestOrders(limit = 5)

            assertEquals("date_desc", api.lastOrderFilters?.get("sort"))
            assertEquals("1", api.lastOrderFilters?.get("exclude_free"))
            assertEquals("5", api.lastOrderFilters?.get("limit"))
            assertEquals("Ada Lovelace", orders.single().customerName)
            assertEquals("#32CD32", orders.single().statusColor)
            assertEquals(12.5, orders.single().totalPaid, 0.0)
        }

    @Test
    fun `paniers - exclut ceux déjà commandés`() =
        runTest {
            carts.cartsResult = listOf(cart(1, 10.0), cart(2, 5.0), cart(3, 99.0, hasOrder = true))
            val summary = repo.getAbandonedCarts(sinceDays = 7)
            assertEquals(2, summary.count)
            assertEquals(15.0, summary.totalTaxIncl, 0.0)
        }

    @Test
    fun `avis - total de la pagination, sinon taille de la liste avec minorant`() =
        runTest {
            api.reviewsResponse =
                ReviewListResponseDto(reviews = listOf(review(1)), pagination = PaginationDto(total = 42))
            assertEquals(42, repo.getPendingReviews()?.count)
            assertFalse(repo.getPendingReviews()!!.hasMore)

            api.reviewsResponse =
                ReviewListResponseDto(reviews = listOf(review(1), review(2)), pagination = PaginationDto(hasNext = true))
            val partial = repo.getPendingReviews()!!
            assertEquals(2, partial.count)
            assertTrue(partial.hasMore)
        }

    @Test
    fun `avis - 409 module absent donne null, une autre erreur est propagée`() =
        runTest {
            api.reviewsException = httpException(409)
            assertNull(repo.getPendingReviews())

            api.reviewsException = httpException(500)
            val thrown = runCatching { repo.getPendingReviews() }.exceptionOrNull()
            assertTrue(thrown is HttpException)
        }

    private val universesJson =
        """{"universes":[
            {"key":"fils","is_digital":false,"ca_tax_incl":100.0,"ca_tax_excl":83.0,"quantity":10,"lines":8},
            {"key":"kits","is_digital":false,"ca_tax_incl":600.0,"ca_tax_excl":500.0,"quantity":30,"lines":25},
            {"key":"patrons_pdf","is_digital":true,"ca_tax_incl":250.0,"ca_tax_excl":208.0,"quantity":50,"lines":40},
            {"key":"ebooks","is_digital":true,"ca_tax_incl":50.0,"ca_tax_excl":41.0,"quantity":5,"lines":5},
            {"key":"livres","is_digital":false,"ca_tax_incl":0,"ca_tax_excl":0,"quantity":0,"lines":0},
            {"key":"autre","is_digital":false}],
            "free_patterns":{"quantity":899,"lines":891},
            "period":{"date_from":null,"date_to":null}}"""

    @Test
    fun `univers - le DTO se désérialise avec défauts et champs inconnus ignorés`() {
        val dto = json.decodeFromString(SalesByUniversResponseDto.serializer(), universesJson)
        assertEquals(6, dto.universes.size)
        assertEquals(600.0, dto.universes[1].caTaxIncl, 0.0)
        assertTrue(dto.universes[2].isDigital)
        assertEquals(0.0, dto.universes[5].caTaxIncl, 0.0)
        assertEquals(899, dto.freePatterns?.quantity)

        val minimal = json.decodeFromString(SalesByUniversResponseDto.serializer(), "{}")
        assertTrue(minimal.universes.isEmpty())
        assertNull(minimal.freePatterns)
    }

    @Test
    fun `univers - tri décroissant, exclusion des zéros et poids du numérique`() =
        runTest {
            api.salesByUnivers = json.decodeFromString(SalesByUniversResponseDto.serializer(), universesJson)
            val result = repo.getSalesByUnivers("2026-11-01 00:00:00", "2026-11-09 23:59:59")!!

            assertEquals("2026-11-01 00:00:00" to "2026-11-09 23:59:59", api.lastSalesByUniversQuery)
            assertEquals(listOf("kits", "patrons_pdf", "fils", "ebooks"), result.universes.map { it.key })
            assertEquals(1000.0, result.totalTaxIncl, 0.0001)
            assertEquals(0.3f, result.digitalShareRatio, 0.0001f)
            assertEquals(899, result.freePatternsQuantity)
            assertFalse(result.isEmpty)
            assertTrue(result.universes.first { it.key == "ebooks" }.isDigital)
        }

    @Test
    fun `univers - aucune vente donne un modèle vide et un poids nul`() =
        runTest {
            api.salesByUnivers =
                json.decodeFromString(
                    SalesByUniversResponseDto.serializer(),
                    """{"universes":[{"key":"kits","is_digital":false,"ca_tax_incl":0.0}],"free_patterns":null}""",
                )
            val result = repo.getSalesByUnivers(null, null)!!
            assertTrue(result.isEmpty)
            assertEquals(0f, result.digitalShareRatio, 0f)
            assertEquals(0, result.freePatternsQuantity)
        }

    @Test
    fun `univers - 404 ou 409 donne null, une autre erreur est propagée`() =
        runTest {
            api.salesByUniversException = httpException(404)
            assertNull(repo.getSalesByUnivers(null, null))
            api.salesByUniversException = httpException(409)
            assertNull(repo.getSalesByUnivers(null, null))
            api.salesByUniversException = httpException(500)
            assertTrue(runCatching { repo.getSalesByUnivers(null, null) }.exceptionOrNull() is HttpException)
        }

    @Test
    fun `fidélité - le DTO se désérialise, la médiane null est conservée`() =
        runTest {
            api.customerRetention =
                json.decodeFromString(
                    CustomerRetentionDto.serializer(),
                    """{"paying_customers":820,"repeat_customers":310,"repeat_rate":37.8,"median_days_between_orders":null,
                        "new_customers":510,"recurring_customers":310,"period":{"from":"x"}}""",
                )
            val result = repo.getCustomerRetention("2026-11-01 00:00:00", "2026-11-09 23:59:59")!!

            assertEquals("2026-11-01 00:00:00" to "2026-11-09 23:59:59", api.lastCustomerRetentionQuery)
            assertEquals(820, result.payingCustomers)
            assertEquals(37.8, result.repeatRatePercent, 0.0001)
            assertNull(result.medianDaysBetweenOrders)
            assertEquals(510, result.newCustomers)
            assertFalse(result.isEmpty)
        }

    @Test
    fun `fidélité - taux recalculé si absent, vide si aucun client payant`() {
        val recomputed = CustomerRetentionDto(payingCustomers = 200, repeatCustomers = 50).toDomain()
        assertEquals(25.0, recomputed.repeatRatePercent, 0.0001)
        val empty = json.decodeFromString(CustomerRetentionDto.serializer(), "{}").toDomain()
        assertEquals(0.0, empty.repeatRatePercent, 0.0)
        assertTrue(empty.isEmpty)
    }

    @Test
    fun `fidélité - 404 ou 409 donne null, une autre erreur est propagée`() =
        runTest {
            api.customerRetentionException = httpException(404)
            assertNull(repo.getCustomerRetention(null, null))
            api.customerRetentionException = httpException(409)
            assertNull(repo.getCustomerRetention(null, null))
            api.customerRetentionException = httpException(500)
            assertTrue(runCatching { repo.getCustomerRetention(null, null) }.exceptionOrNull() is HttpException)
        }

    @Test
    fun `canaux - tri décroissant, clés normalisées fusionnées, zéros écartés, parts`() =
        runTest {
            api.acquisitionChannels =
                json.decodeFromString(
                    AcquisitionChannelsResponseDto.serializer(),
                    """{"channels":[{"key":"direct","orders":44},{"key":"recherche","orders":100},
                        {"key":"Recherche","orders":18},{"key":"instagram","orders":38},{"key":"pinterest","orders":0},
                        {"key":"","orders":9}],"window_days":30,"period":{}}""",
                )
            val result = repo.getAcquisitionChannels("a", "b", 30)!!

            assertEquals(Triple<String?, String?, Int?>("a", "b", 30), api.lastAcquisitionChannelsQuery)
            assertEquals(listOf("recherche", "direct", "instagram"), result.channels.map { it.key })
            assertEquals(118, result.channels.first().orders)
            assertEquals(200, result.totalOrders)
            assertEquals(0.59f, result.shareOf(result.channels.first()), 0.0001f)
            assertEquals(30, result.windowDays)
            assertFalse(result.isEmpty)
        }

    @Test
    fun `canaux - réponse vide donne un modèle vide, 404 donne null`() =
        runTest {
            api.acquisitionChannels = json.decodeFromString(AcquisitionChannelsResponseDto.serializer(), "{}")
            val result = repo.getAcquisitionChannels(null, null, 30)!!
            assertTrue(result.isEmpty)
            assertEquals(0f, result.shareOf(AcquisitionChannel("x", 1)), 0f)
            assertEquals(30, result.windowDays)

            api.acquisitionChannelsException = httpException(404)
            assertNull(repo.getAcquisitionChannels(null, null, 30))
            api.acquisitionChannelsException = httpException(500)
            assertTrue(runCatching { repo.getAcquisitionChannels(null, null, 30) }.exceptionOrNull() is HttpException)
        }

    @Test
    fun `cohortes - mois récents d'abord et taux dérivés par effectif`() =
        runTest {
            api.freeConversionCohorts =
                json.decodeFromString(
                    FreeConversionCohortsResponseDto.serializer(),
                    """{"cohorts":[{"month":"2026-04","first_free_customers":55,"converted_30d":3,"converted_60d":5,"converted_90d":8},
                        {"month":"2026-05","first_free_customers":40,"converted_30d":1,"converted_60d":2,"converted_90d":3},
                        {"month":"2026-06","first_free_customers":0}],"period":{}}""",
                )
            val result = repo.getFreeConversionCohorts(12)!!

            assertEquals(12, api.lastFreeConversionCohortsMonths)
            assertEquals(listOf("2026-06", "2026-05", "2026-04"), result.cohorts.map { it.month })
            val may = result.cohorts[1]
            assertEquals(7.5, may.rate90Percent, 0.0001)
            assertEquals(5.0, may.rate60Percent, 0.0001)
            assertEquals(2.5, may.rate30Percent, 0.0001)
            assertEquals(0.0, result.cohorts[0].rate90Percent, 0.0)
        }

    @Test
    fun `cohortes - 404 ou 409 donne null, une autre erreur est propagée`() =
        runTest {
            api.freeConversionCohortsException = httpException(409)
            assertNull(repo.getFreeConversionCohorts(12))
            api.freeConversionCohortsException = httpException(404)
            assertNull(repo.getFreeConversionCohorts(12))
            api.freeConversionCohortsException = httpException(500)
            assertTrue(runCatching { repo.getFreeConversionCohorts(12) }.exceptionOrNull() is HttpException)
        }

    @Test
    fun `saisonnalité - série triée par mois, mois sans clé écarté, pic et total`() =
        runTest {
            api.monthlyTurnover =
                json.decodeFromString(
                    MonthlyTurnoverResponseDto.serializer(),
                    """{"months":[{"month":"2026-01","turnover":0.0,"orders":0},
                        {"month":"2025-12","turnover":1234.56,"orders":42},{"month":"","turnover":9.0},
                        {"month":"2026-02","turnover":1234.56,"orders":40}],"period":{"from":"2025-12-01","to":"2026-02-28"}}""",
                )
            val result = repo.getMonthlyTurnover(12)!!

            assertEquals(12, api.lastMonthlyTurnoverMonths)
            assertEquals(listOf("2025-12", "2026-01", "2026-02"), result.months.map { it.month })
            assertEquals(2469.12, result.total, 0.0001)
            assertEquals(listOf("2025-12", "2026-02"), result.peakMonths.map { it.month })
            assertFalse(result.isEmpty)
        }

    @Test
    fun `saisonnalité - aucune vente donne vide, 404 ou 409 donne null, autre erreur propagée`() =
        runTest {
            api.monthlyTurnover =
                json.decodeFromString(
                    MonthlyTurnoverResponseDto.serializer(),
                    """{"months":[{"month":"2026-01","turnover":0.0,"orders":0}]}""",
                )
            val empty = repo.getMonthlyTurnover(12)!!
            assertTrue(empty.isEmpty)
            assertTrue(empty.peakMonths.isEmpty())

            api.monthlyTurnoverException = httpException(404)
            assertNull(repo.getMonthlyTurnover(12))
            api.monthlyTurnoverException = httpException(409)
            assertNull(repo.getMonthlyTurnover(12))
            api.monthlyTurnoverException = httpException(500)
            assertTrue(runCatching { repo.getMonthlyTurnover(12) }.exceptionOrNull() is HttpException)
        }

    @Test
    fun `avis et SAV - réponse complète mappée, période transmise`() =
        runTest {
            api.reviewsSummary =
                json.decodeFromString(
                    ReviewsSummaryResponseDto.serializer(),
                    """{"total_reviews":2856,"native_reviews":16,"average_rating":4.88,"pending_reviews":0,"orders":6721,"review_rate":0.2,
                        "period":{},"sav":{"open_threads":5,"answered_threads":190}}""",
                )
            val result = repo.getReviewsSummary("2026-11-01 00:00:00", "2026-11-09 23:59:59")!!

            assertEquals("2026-11-01 00:00:00" to "2026-11-09 23:59:59", api.lastReviewsSummaryQuery)
            assertEquals(2856, result.totalReviews)
            assertEquals(16, result.nativeReviews)
            assertEquals(4.88, result.averageRating!!, 0.0001)
            assertEquals(0, result.pendingReviews)
            assertEquals(0.2, result.reviewRatePercent!!, 0.0001)
            assertEquals(5, result.sav!!.openThreads)
            assertEquals(190, result.sav!!.answeredThreads)
        }

    @Test
    fun `avis et SAV - note, taux et bloc SAV absents ou null restent null`() =
        runTest {
            api.reviewsSummary =
                json.decodeFromString(
                    ReviewsSummaryResponseDto.serializer(),
                    """{"total_reviews":0,"average_rating":null,"pending_reviews":2,"orders":0,"review_rate":null}""",
                )
            val result = repo.getReviewsSummary(null, null)!!
            assertNull(result.averageRating)
            assertNull(result.reviewRatePercent)
            assertEquals(0, result.nativeReviews)
            assertNull(result.sav)
            assertFalse(result.isEmpty)

            // Ancien connecteur : median_first_response_hours encore présent, ignoré sans erreur.
            api.reviewsSummary =
                json.decodeFromString(
                    ReviewsSummaryResponseDto.serializer(),
                    """{"total_reviews":1,"sav":{"open_threads":2,"median_first_response_hours":6.0,"answered_threads":3}}""",
                )
            assertEquals(2, repo.getReviewsSummary(null, null)!!.sav!!.openThreads)
        }

    @Test
    fun `avis et SAV - 409 reviews_unavailable donne null, 500 propagée`() =
        runTest {
            api.reviewsSummaryException = httpException(409)
            assertNull(repo.getReviewsSummary(null, null))
            api.reviewsSummaryException = httpException(404)
            assertNull(repo.getReviewsSummary(null, null))
            api.reviewsSummaryException = httpException(500)
            assertTrue(runCatching { repo.getReviewsSummary(null, null) }.exceptionOrNull() is HttpException)
        }
}
