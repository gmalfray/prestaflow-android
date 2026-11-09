package com.rebuildit.prestaflow.data.dashboard

import com.rebuildit.prestaflow.data.remote.dto.BestSellerDto
import com.rebuildit.prestaflow.data.remote.dto.BestSellersResponseDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListCustomerDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListItemDto
import com.rebuildit.prestaflow.data.remote.dto.PaginationDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewListResponseDto
import com.rebuildit.prestaflow.data.remote.dto.SalesByUniversResponseDto
import com.rebuildit.prestaflow.domain.carts.model.CartSummary
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
}
