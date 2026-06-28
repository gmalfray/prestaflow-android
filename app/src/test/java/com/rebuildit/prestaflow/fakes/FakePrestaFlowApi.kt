package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.data.remote.dto.AuthRequestDto
import com.rebuildit.prestaflow.data.remote.dto.AuthResponseDto
import com.rebuildit.prestaflow.data.remote.dto.CartDetailResponseDto
import com.rebuildit.prestaflow.data.remote.dto.CartListResponseDto
import com.rebuildit.prestaflow.data.remote.dto.CustomerDetailResponseDto
import com.rebuildit.prestaflow.data.remote.dto.CustomerListResponseDto
import com.rebuildit.prestaflow.data.remote.dto.CustomerStatsDto
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

/**
 * Fake de [PrestaFlowApi] pour les tests de repository.
 * Toutes les méthodes non utilisées lèvent [UnsupportedOperationException] pour signaler
 * explicitement un appel inattendu dans un test.
 */
class FakePrestaFlowApi : PrestaFlowApi {
    /** Réponse renvoyée par [getOrders]. */
    var ordersResponse: OrderListDto = OrderListDto(orders = emptyList())

    /** Si non null, [getOrders] lancera cette exception. */
    var ordersException: Throwable? = null

    /** Filtres reçus par le dernier appel à [getOrders]. */
    var lastOrderFilters: Map<String, String>? = null

    override suspend fun getOrders(filters: Map<String, String>): OrderListDto {
        lastOrderFilters = filters
        ordersException?.let { throw it }
        return ordersResponse
    }

    override suspend fun getOrderStatuses(): OrderStatusesResponseDto = OrderStatusesResponseDto(statuses = emptyList())

    override suspend fun getOrder(orderId: Long): OrderDetailResponseDto = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun updateOrderStatus(
        orderId: Long,
        body: OrderStatusUpdateRequestDto,
    ) = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun updateOrderShipping(
        orderId: Long,
        body: OrderShippingUpdateRequestDto,
    ) = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getInvoicePdf(orderId: Long): Response<ResponseBody> =
        throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getShippingLabelPdf(orderId: Long): Response<ResponseBody> =
        throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getProducts(
        filters: Map<String, String>,
        search: String?,
    ): ProductListResponseDto = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getProduct(productId: Long): ProductDetailResponseDto =
        throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun updateProductStock(
        productId: Long,
        body: StockUpdateRequestDto,
    ) = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getDashboardMetrics(
        period: String?,
        from: String?,
        to: String?,
    ): DashboardMetricsDto = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getCustomerStats(): CustomerStatsDto = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getTopCustomers(limit: Int): CustomerListResponseDto =
        throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getCustomer(customerId: Long): CustomerDetailResponseDto =
        throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun registerDevice(body: DeviceRegistrationRequestDto) =
        throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun unregisterDevice(token: String) = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun login(request: AuthRequestDto): AuthResponseDto = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getBaskets(abandonedSinceDays: Int?): CartListResponseDto =
        throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getBasketById(cartId: Int): CartDetailResponseDto = throw UnsupportedOperationException("Non utilisé dans ce test")
}
