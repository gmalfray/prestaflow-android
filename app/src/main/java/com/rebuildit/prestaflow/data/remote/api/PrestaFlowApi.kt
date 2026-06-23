package com.rebuildit.prestaflow.data.remote.api

import com.rebuildit.prestaflow.data.remote.dto.AuthRequestDto
import com.rebuildit.prestaflow.data.remote.dto.AuthResponseDto
import com.rebuildit.prestaflow.data.remote.dto.CartDetailResponseDto
import com.rebuildit.prestaflow.data.remote.dto.CartListResponseDto
import com.rebuildit.prestaflow.data.remote.dto.CustomerDetailResponseDto
import com.rebuildit.prestaflow.data.remote.dto.CustomerListResponseDto
import com.rebuildit.prestaflow.data.remote.dto.DashboardMetricsDto
import com.rebuildit.prestaflow.data.remote.dto.DeviceRegistrationRequestDto
import com.rebuildit.prestaflow.data.remote.dto.OrderDetailResponseDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListDto
import com.rebuildit.prestaflow.data.remote.dto.OrderShippingUpdateRequestDto
import com.rebuildit.prestaflow.data.remote.dto.OrderStatusUpdateRequestDto
import com.rebuildit.prestaflow.data.remote.dto.OrderStatusesResponseDto
import com.rebuildit.prestaflow.data.remote.dto.ProductDetailResponseDto
import com.rebuildit.prestaflow.data.remote.dto.ProductListResponseDto
import com.rebuildit.prestaflow.data.remote.dto.StockUpdateRequestDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Streaming

interface PrestaFlowApi {
    @POST("connector/login")
    suspend fun login(
        @Body request: AuthRequestDto,
    ): AuthResponseDto

    @GET("orders/statuses")
    suspend fun getOrderStatuses(): OrderStatusesResponseDto

    @GET("orders")
    suspend fun getOrders(
        @QueryMap filters: Map<String, @JvmSuppressWildcards String>,
    ): OrderListDto

    @GET("orders/{id}")
    suspend fun getOrder(
        @Path("id") orderId: Long,
    ): OrderDetailResponseDto

    @PATCH("orders/{id}/status")
    suspend fun updateOrderStatus(
        @Path("id") orderId: Long,
        @Body body: OrderStatusUpdateRequestDto,
    )

    @PATCH("orders/{id}/shipping")
    suspend fun updateOrderShipping(
        @Path("id") orderId: Long,
        @Body body: OrderShippingUpdateRequestDto,
    )

    /**
     * Télécharge la facture PDF d'une commande.
     * Retourne 404 si la commande n'a pas de facture générée.
     * `@Streaming` évite de bufferiser le PDF entier en mémoire avant de le lire.
     */
    @Streaming
    @GET("orders/{id}/invoice")
    suspend fun getInvoicePdf(
        @Path("id") orderId: Long,
    ): Response<ResponseBody>

    @GET("products")
    suspend fun getProducts(
        @QueryMap filters: Map<String, @JvmSuppressWildcards String> = emptyMap(),
    ): ProductListResponseDto

    @GET("products/{id}")
    suspend fun getProduct(
        @Path("id") productId: Long,
    ): ProductDetailResponseDto

    @PATCH("products/{id}/stock")
    suspend fun updateProductStock(
        @Path("id") productId: Long,
        @Body body: StockUpdateRequestDto,
    )

    @GET("dashboard/metrics")
    suspend fun getDashboardMetrics(
        @Query("period") period: String,
    ): DashboardMetricsDto

    @GET("customers/top")
    suspend fun getTopCustomers(
        @Query("limit") limit: Int,
    ): CustomerListResponseDto

    @GET("customers/{id}")
    suspend fun getCustomer(
        @Path("id") customerId: Long,
    ): CustomerDetailResponseDto

    @POST("notifications/devices")
    suspend fun registerDevice(
        @Body body: DeviceRegistrationRequestDto,
    )

    @DELETE("notifications/devices/{token}")
    suspend fun unregisterDevice(
        @Path(value = "token", encoded = true) token: String,
    )

    @GET("baskets")
    suspend fun getBaskets(
        @Query("abandoned_since_days") abandonedSinceDays: Int? = null,
    ): CartListResponseDto

    @GET("baskets/{id}")
    suspend fun getBasketById(
        @Path("id") cartId: Int,
    ): CartDetailResponseDto
}
