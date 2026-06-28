package com.rebuildit.prestaflow.data.remote.api

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

    /**
     * Télécharge le bordereau de transport PDF d'une commande.
     * Retourne 404 si la commande n'a pas de bordereau disponible (transporteur non géré,
     * fichier absent, URL expirée).
     * `@Streaming` évite de bufferiser le PDF entier en mémoire avant de le lire.
     */
    @Streaming
    @GET("orders/{id}/shipping-label")
    suspend fun getShippingLabelPdf(
        @Path("id") orderId: Long,
    ): Response<ResponseBody>

    /**
     * Génère l'étiquette Colissimo pour la commande [orderId] via le webservice transporteur.
     * 200 = étiquette déjà existante (idempotent) ; 201 = nouvellement générée.
     * Erreurs : 404 not_found, 422 carrier_not_supported, 501 generation_not_configured,
     * 502 carrier_webservice_error (+ message).
     * On utilise [Response] pour accéder au code et au body d'erreur sans relancer d'exception.
     */
    @POST("orders/{id}/shipping-label")
    suspend fun generateShippingLabel(
        @Path("id") orderId: Long,
    ): Response<GenerateLabelResponseDto>

    @GET("products")
    suspend fun getProducts(
        @QueryMap filters: Map<String, @JvmSuppressWildcards String> = emptyMap(),
        @Query("search") search: String? = null,
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
        @Query("period") period: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): DashboardMetricsDto

    @GET("customers/stats")
    suspend fun getCustomerStats(): CustomerStatsDto

    @GET("customers/top")
    suspend fun getTopCustomers(
        @Query("limit") limit: Int,
    ): CustomerListResponseDto

    /**
     * Liste paginée et filtrable de tous les clients (`GET /customers`).
     *
     * Les filtres de date suivent le format PHP de tableau d'URL : `filter[created_from]`.
     * Retrofit encode les crochets en `%5B`/`%5D` — PHP les décode correctement via `$_GET`.
     *
     * @param limit Nombre de clients par page (défaut serveur : 20).
     * @param offset Décalage depuis lequel commencer (pour la pagination).
     * @param search Recherche full-text sur nom/prénom/email (LIKE côté SQL).
     * @param sort Ordre de tri : `date_desc` (défaut), `date_asc`, `orders_desc`, `spent_desc`.
     * @param createdFrom Borne inférieure de `date_add` (format `YYYY-MM-DD`).
     * @param createdTo Borne supérieure de `date_add` (format `YYYY-MM-DD`).
     */
    @GET("customers")
    suspend fun getCustomers(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("search") search: String? = null,
        @Query("sort") sort: String? = null,
        @Query("filter[created_from]") createdFrom: String? = null,
        @Query("filter[created_to]") createdTo: String? = null,
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
