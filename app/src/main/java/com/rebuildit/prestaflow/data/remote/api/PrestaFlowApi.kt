package com.rebuildit.prestaflow.data.remote.api

import com.rebuildit.prestaflow.data.remote.dto.AuthRequestDto
import com.rebuildit.prestaflow.data.remote.dto.AuthResponseDto
import com.rebuildit.prestaflow.data.remote.dto.DashboardMetricsDto
import com.rebuildit.prestaflow.data.remote.dto.OrderDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListResponseDto
import com.rebuildit.prestaflow.data.remote.dto.ProductDto
import com.rebuildit.prestaflow.data.remote.dto.ProductListResponseDto
import com.rebuildit.prestaflow.data.remote.dto.ShippingUpdateRequestDto
import com.rebuildit.prestaflow.data.remote.dto.StockUpdateRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.QueryMap

interface PrestaFlowApi {

    @POST("connector/login")
    suspend fun login(@Body body: AuthRequestDto): AuthResponseDto

    @GET("orders")
    suspend fun getOrders(@QueryMap filters: Map<String, @JvmSuppressWildcards Any?>): OrderListResponseDto

    @GET("orders/{id}")
    suspend fun getOrder(@Path("id") id: Long): OrderDto

    @PATCH("orders/{id}/status")
    suspend fun updateOrderStatus(
        @Path("id") id: Long,
        @Body payload: Map<String, @JvmSuppressWildcards Any?>
    ): OrderDto

    @PATCH("orders/{id}/shipping")
    suspend fun updateOrderShipping(
        @Path("id") id: Long,
        @Body payload: ShippingUpdateRequestDto
    ): OrderDto

    @GET("products")
    suspend fun getProducts(@QueryMap filters: Map<String, @JvmSuppressWildcards Any?>): ProductListResponseDto

    @GET("products/{id}")
    suspend fun getProduct(@Path("id") id: Long): ProductDto

    @PATCH("products/{id}")
    suspend fun updateProduct(
        @Path("id") id: Long,
        @Body payload: Map<String, @JvmSuppressWildcards Any?>
    ): ProductDto

    @PATCH("products/{id}/stock")
    suspend fun updateStock(
        @Path("id") id: Long,
        @Body payload: StockUpdateRequestDto
    ): ProductDto

    @GET("dashboard/metrics")
    suspend fun getDashboardMetrics(@QueryMap filters: Map<String, @JvmSuppressWildcards Any?>): DashboardMetricsDto
}
