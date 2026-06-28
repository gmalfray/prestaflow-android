package com.rebuildit.prestaflow.data.orders

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.local.dao.OrderDao
import com.rebuildit.prestaflow.data.orders.mapper.toDomain
import com.rebuildit.prestaflow.data.orders.mapper.toEntity
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.data.remote.dto.ApiErrorBodyDto
import com.rebuildit.prestaflow.data.remote.dto.OrderShippingUpdateRequestDto
import com.rebuildit.prestaflow.data.remote.dto.OrderStatusUpdateRequestDto
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Instance Json réutilisée pour parser les body d'erreur du connecteur. */
private val errorBodyJson = Json { ignoreUnknownKeys = true }

@Singleton
class OrdersRepositoryImpl
    @Inject
    constructor(
        private val api: PrestaFlowApi,
        private val orderDao: OrderDao,
        private val networkErrorMapper: NetworkErrorMapper,
        private val ioDispatcher: CoroutineDispatcher,
    ) : OrdersRepository {
        override fun observeOrders(): Flow<List<Order>> =
            orderDao.observeOrders().map { entities ->
                entities.map { it.toDomain() }
            }

        override fun getOrder(orderId: Long): Flow<Order?> =
            orderDao.observeOrder(orderId).map { entity ->
                entity?.toDomain()
            }

        override suspend fun getOrderStatuses(): List<OrderStatusFilter> =
            withContext(ioDispatcher) {
                val response = api.getOrderStatuses()
                response.statuses.map { dto ->
                    OrderStatusFilter(id = dto.id, name = dto.name, color = dto.color)
                }
            }

        override suspend fun refresh(
            forceRemote: Boolean,
            statusId: Int?,
            dateFrom: String?,
            dateTo: String?,
        ) {
            withContext(ioDispatcher) {
                val filters =
                    buildMap {
                        put("sort", "-date_add")
                        put("limit", "50")
                        if (statusId != null) put("status", statusId.toString())
                        if (dateFrom != null) put("date_from", dateFrom)
                        if (dateTo != null) put("date_to", dateTo)
                    }
                val result = runCatching { api.getOrders(filters) }
                result.fold(
                    onSuccess = { payload ->
                        // Vider la table avant l'upsert pour que la liste Room reflète
                        // exactement le résultat de l'API (filtré ou non) — sans quoi les
                        // commandes d'autres statuts restent visibles après un changement de filtre.
                        orderDao.clear()
                        val entities = payload.orders.map { it.toEntity() }
                        orderDao.upsertOrders(entities)
                    },
                    onFailure = { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        if (forceRemote) throw error
                    },
                )
            }
        }

        override suspend fun refreshOrder(orderId: Long) {
            withContext(ioDispatcher) {
                val result = runCatching { api.getOrder(orderId) }
                result.fold(
                    onSuccess = { payload ->
                        val entity = payload.order.toEntity()
                        orderDao.upsertOrders(listOf(entity))
                    },
                    onFailure = { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        throw error
                    },
                )
            }
        }

        override suspend fun updateOrderStatus(
            orderId: Long,
            status: String,
        ) {
            withContext(ioDispatcher) {
                runCatching {
                    api.updateOrderStatus(orderId, OrderStatusUpdateRequestDto(status = status))
                }.fold(
                    onSuccess = { refreshOrder(orderId) },
                    onFailure = { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        throw error
                    },
                )
            }
        }

        override suspend fun updateOrderShipping(
            orderId: Long,
            trackingNumber: String,
            carrierId: Long?,
        ) {
            withContext(ioDispatcher) {
                runCatching {
                    api.updateOrderShipping(
                        orderId,
                        OrderShippingUpdateRequestDto(
                            trackingNumber = trackingNumber,
                            carrierId = carrierId,
                        ),
                    )
                }.fold(
                    onSuccess = { refreshOrder(orderId) },
                    onFailure = { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        throw error
                    },
                )
            }
        }

        override suspend fun downloadInvoicePdf(orderId: Long): ByteArray? =
            withContext(ioDispatcher) {
                val response = api.getInvoicePdf(orderId)
                when {
                    response.isSuccessful -> response.body()?.bytes()
                    response.code() == 404 -> null
                    else -> {
                        val msg = "Erreur HTTP ${response.code()} lors du téléchargement de la facture #$orderId"
                        Timber.w(msg)
                        error(msg)
                    }
                }
            }

        override suspend fun downloadShippingLabel(orderId: Long): ByteArray? =
            withContext(ioDispatcher) {
                val response = api.getShippingLabelPdf(orderId)
                when {
                    response.isSuccessful -> response.body()?.bytes()
                    response.code() == 404 -> null
                    else -> {
                        val msg = "Erreur HTTP ${response.code()} lors du téléchargement du bordereau #$orderId"
                        Timber.w(msg)
                        error(msg)
                    }
                }
            }

        override suspend fun generateShippingLabel(orderId: Long) {
            withContext(ioDispatcher) {
                val response = runCatching { api.generateShippingLabel(orderId) }.getOrElse { error ->
                    Timber.w(networkErrorMapper.map(error).toString())
                    throw error
                }
                when {
                    response.isSuccessful -> {
                        // Recharge la commande pour mettre à jour le n° de suivi et hasShippingLabel dans Room
                        refreshOrder(orderId)
                    }
                    else -> {
                        val errorMsg = parseGenerateLabelError(response.code(), response.errorBody()?.string())
                        Timber.w("Génération étiquette commande #$orderId — $errorMsg")
                        error(errorMsg)
                    }
                }
            }
        }

        /**
         * Traduit le code HTTP et le body d'erreur JSON en message lisible.
         * Le body peut être null si le serveur ne renvoie pas de contenu.
         */
        private fun parseGenerateLabelError(
            code: Int,
            errorBody: String?,
        ): String =
            when (code) {
                404 -> "Commande introuvable"
                422 -> "Génération dispo uniquement pour Colissimo"
                501 -> "Contrat transporteur non configuré"
                502 -> {
                    val connectorMessage =
                        runCatching {
                            errorBody
                                ?.takeIf { it.isNotBlank() }
                                ?.let { body ->
                                    errorBodyJson.decodeFromString<ApiErrorBodyDto>(body).message
                                }
                        }.getOrNull()
                    if (!connectorMessage.isNullOrBlank()) {
                        "Erreur transporteur : $connectorMessage"
                    } else {
                        "Erreur du service transporteur"
                    }
                }
                else -> "Erreur HTTP $code lors de la génération de l'étiquette"
            }
    }
