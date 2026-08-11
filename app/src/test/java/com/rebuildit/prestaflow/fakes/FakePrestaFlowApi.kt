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
import com.rebuildit.prestaflow.data.remote.dto.GenerateLabelResponseDto
import com.rebuildit.prestaflow.data.remote.dto.OrderDetailResponseDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListDto
import com.rebuildit.prestaflow.data.remote.dto.OrderShippingUpdateRequestDto
import com.rebuildit.prestaflow.data.remote.dto.OrderStatusUpdateRequestDto
import com.rebuildit.prestaflow.data.remote.dto.OrderStatusesResponseDto
import com.rebuildit.prestaflow.data.remote.dto.ProductDetailResponseDto
import com.rebuildit.prestaflow.data.remote.dto.ProductListResponseDto
import com.rebuildit.prestaflow.data.remote.dto.ProductUpdateRequestDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewListResponseDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewPublishResponseDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewReplyRequestDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewReplyResponseDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewTrashRequestDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewTrashResponseDto
import com.rebuildit.prestaflow.data.remote.dto.SavReplyRequestDto
import com.rebuildit.prestaflow.data.remote.dto.SavReplyResponseDto
import com.rebuildit.prestaflow.data.remote.dto.SavStatusUpdateRequestDto
import com.rebuildit.prestaflow.data.remote.dto.SavThreadDetailResponseDto
import com.rebuildit.prestaflow.data.remote.dto.SavThreadListResponseDto
import com.rebuildit.prestaflow.data.remote.dto.ShopCapabilitiesDto
import com.rebuildit.prestaflow.data.remote.dto.StockUpdateRequestDto
import okhttp3.MultipartBody
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

    /** Si non null, [updateOrderStatus] lancera cette exception (au lieu de réussir silencieusement). */
    var updateOrderStatusException: Throwable? = null

    /** Appels reçus par [updateOrderStatus] : (orderId, status). */
    val updateOrderStatusCalls = mutableListOf<Pair<Long, String>>()

    override suspend fun updateOrderStatus(
        orderId: Long,
        body: OrderStatusUpdateRequestDto,
    ) {
        updateOrderStatusCalls += orderId to body.status
        updateOrderStatusException?.let { throw it }
    }

    override suspend fun updateOrderShipping(
        orderId: Long,
        body: OrderShippingUpdateRequestDto,
    ) = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getInvoicePdf(orderId: Long): Response<ResponseBody> =
        throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getShippingLabelPdf(orderId: Long): Response<ResponseBody> =
        throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun generateShippingLabel(orderId: Long): Response<GenerateLabelResponseDto> =
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

    override suspend fun updateProduct(
        productId: Long,
        body: ProductUpdateRequestDto,
    ): ProductDetailResponseDto = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun uploadProductImage(
        productId: Long,
        image: MultipartBody.Part,
    ): ProductDetailResponseDto = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun deleteProductImage(
        productId: Long,
        imageId: Long,
    ): ProductDetailResponseDto = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getDashboardMetrics(
        period: String?,
        from: String?,
        to: String?,
    ): DashboardMetricsDto = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getCustomerStats(): CustomerStatsDto = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getTopCustomers(limit: Int): CustomerListResponseDto =
        throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getCustomers(
        limit: Int?,
        offset: Int?,
        search: String?,
        sort: String?,
        createdFrom: String?,
        createdTo: String?,
    ): CustomerListResponseDto = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getCustomer(customerId: Long): CustomerDetailResponseDto =
        throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun registerDevice(body: DeviceRegistrationRequestDto) =
        throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun unregisterDevice(token: String) = throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun login(request: AuthRequestDto): AuthResponseDto = throw UnsupportedOperationException("Non utilisé dans ce test")

    /** Réponse renvoyée par [getCapabilities]. */
    var capabilitiesResponse: ShopCapabilitiesDto = ShopCapabilitiesDto()

    /** Si non null, [getCapabilities] lancera cette exception. */
    var capabilitiesException: Throwable? = null

    /** Nombre d'appels reçus par [getCapabilities]. */
    var getCapabilitiesCallCount: Int = 0

    override suspend fun getCapabilities(): ShopCapabilitiesDto {
        getCapabilitiesCallCount++
        capabilitiesException?.let { throw it }
        return capabilitiesResponse
    }

    override suspend fun getBaskets(abandonedSinceDays: Int?): CartListResponseDto =
        throw UnsupportedOperationException("Non utilisé dans ce test")

    override suspend fun getBasketById(cartId: Int): CartDetailResponseDto = throw UnsupportedOperationException("Non utilisé dans ce test")

    // ─── SAV ──────────────────────────────────────────────────────────────────

    var savThreadsResponse: SavThreadListResponseDto = SavThreadListResponseDto()
    var savThreadsException: Throwable? = null
    var lastSavThreadsFilters: Map<String, String>? = null

    override suspend fun getSavThreads(filters: Map<String, String>): SavThreadListResponseDto {
        lastSavThreadsFilters = filters
        savThreadsException?.let { throw it }
        return savThreadsResponse
    }

    var savThreadDetailResponse: SavThreadDetailResponseDto? = null
    var savThreadDetailException: Throwable? = null

    override suspend fun getSavThread(threadId: Long): SavThreadDetailResponseDto {
        savThreadDetailException?.let { throw it }
        return checkNotNull(savThreadDetailResponse) { "savThreadDetailResponse non configuré dans le fake" }
    }

    var updateSavThreadStatusException: Throwable? = null
    val updateSavThreadStatusCalls = mutableListOf<Pair<Long, String>>()

    override suspend fun updateSavThreadStatus(
        threadId: Long,
        body: SavStatusUpdateRequestDto,
    ) {
        updateSavThreadStatusCalls += threadId to body.status
        updateSavThreadStatusException?.let { throw it }
    }

    var replySavThreadResponse: SavReplyResponseDto? = null
    var replySavThreadException: Throwable? = null
    val replySavThreadCalls = mutableListOf<Pair<Long, String>>()

    override suspend fun replySavThread(
        threadId: Long,
        body: SavReplyRequestDto,
    ): SavReplyResponseDto {
        replySavThreadCalls += threadId to body.message
        replySavThreadException?.let { throw it }
        return checkNotNull(replySavThreadResponse) { "replySavThreadResponse non configuré dans le fake" }
    }

    // ─── Avis ─────────────────────────────────────────────────────────────────

    var reviewsResponse: ReviewListResponseDto = ReviewListResponseDto()
    var reviewsException: Throwable? = null

    override suspend fun getReviews(
        limit: Int?,
        offset: Int?,
    ): ReviewListResponseDto {
        reviewsException?.let { throw it }
        return reviewsResponse
    }

    var publishReviewResponse: ReviewPublishResponseDto? = null
    var publishReviewException: Throwable? = null

    override suspend fun publishReview(reviewId: Long): ReviewPublishResponseDto {
        publishReviewException?.let { throw it }
        return checkNotNull(publishReviewResponse) { "publishReviewResponse non configuré dans le fake" }
    }

    var trashReviewResponse: ReviewTrashResponseDto? = null
    var trashReviewException: Throwable? = null
    val trashReviewCalls = mutableListOf<Pair<Long, String>>()

    override suspend fun trashReview(
        reviewId: Long,
        body: ReviewTrashRequestDto,
    ): ReviewTrashResponseDto {
        trashReviewCalls += reviewId to body.reason
        trashReviewException?.let { throw it }
        return checkNotNull(trashReviewResponse) { "trashReviewResponse non configuré dans le fake" }
    }

    var replyReviewResponse: ReviewReplyResponseDto? = null
    var replyReviewException: Throwable? = null

    override suspend fun replyReview(
        reviewId: Long,
        body: ReviewReplyRequestDto,
    ): ReviewReplyResponseDto {
        replyReviewException?.let { throw it }
        return checkNotNull(replyReviewResponse) { "replyReviewResponse non configuré dans le fake" }
    }
}
