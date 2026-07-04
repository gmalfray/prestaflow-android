package com.rebuildit.prestaflow.data.orders

import com.rebuildit.prestaflow.core.network.ApiEndpointManager
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
import com.rebuildit.prestaflow.domain.sync.SyncQueueRepository
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

private const val HTTP_NOT_FOUND = 404
private const val HTTP_UNPROCESSABLE_ENTITY = 422
private const val HTTP_NOT_IMPLEMENTED = 501
private const val HTTP_BAD_GATEWAY = 502

@Singleton
@Suppress("LongParameterList") // Repository Hilt : dépendances API, DAO, mapper d'erreurs, dispatcher, queue sync et endpoint
class OrdersRepositoryImpl
    @Inject
    constructor(
        private val api: PrestaFlowApi,
        private val orderDao: OrderDao,
        private val networkErrorMapper: NetworkErrorMapper,
        private val ioDispatcher: CoroutineDispatcher,
        private val syncQueueRepository: SyncQueueRepository,
        private val endpointManager: ApiEndpointManager,
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
            statusIds: Set<Int>,
            sort: String,
            dateFrom: String?,
            dateTo: String?,
            offset: Int,
            limit: Int,
            search: String?,
        ): Boolean =
            withContext(ioDispatcher) {
                val filters =
                    buildMap {
                        put("sort", sort)
                        put("limit", limit.toString())
                        put("offset", offset.toString())
                        // Filtre multi-statuts : CSV d'IDs (param `statuses=2,3,4`)
                        if (statusIds.isNotEmpty()) put("statuses", statusIds.joinToString(","))
                        if (dateFrom != null) put("date_from", dateFrom)
                        if (dateTo != null) put("date_to", dateTo)
                        // Recherche côté serveur (référence + nom/prénom client) : le connecteur fait
                        // le LIKE sur toute la base, pas seulement les commandes déjà chargées.
                        if (!search.isNullOrBlank()) put("search", search.trim())
                    }
                val result = runCatching { api.getOrders(filters) }
                result.fold(
                    onSuccess = { payload ->
                        // Première page : vider la table pour que Room reflète exactement
                        // le résultat de l'API. Pages suivantes : upsert sans clear (accumulation).
                        if (offset == 0) orderDao.clear()
                        // position = rang global (offset de page + index) → préserve l'ordre du
                        // serveur (le tri choisi) à la relecture Room, y compris en pagination.
                        val entities = payload.orders.mapIndexed { index, dto -> dto.toEntity(position = offset + index) }
                        orderDao.upsertOrders(entities)
                        // hasMore : la page était pleine → il y a probablement des suivantes
                        payload.orders.size >= limit
                    },
                    onFailure = { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        if (forceRemote) throw error
                        false
                    },
                )
            }

        override suspend fun refreshOrder(orderId: Long) {
            withContext(ioDispatcher) {
                val result = runCatching { api.getOrder(orderId) }
                result.fold(
                    onSuccess = { payload ->
                        // Rafraîchissement du détail : on préserve la position existante dans la liste
                        // (sinon l'upsert REPLACE remettrait la commande en tête).
                        val position = orderDao.getPosition(orderId) ?: 0
                        val entity = payload.order.toEntity(position = position)
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
                        enqueueStatusUpdateRetry(orderId, status)
                        throw error
                    },
                )
            }
        }

        /**
         * Enfile un retry offline du changement de statut échoué (file `pending_sync`, rejouée
         * par `SyncWorker` contre la boutique active AU MOMENT DE L'ÉCHEC — cf. FIX "file offline
         * rejouée contre la mauvaise boutique"). Best-effort : un échec d'enfilement ne doit
         * jamais masquer l'erreur d'origine remontée à l'appelant.
         */
        private suspend fun enqueueStatusUpdateRetry(
            orderId: Long,
            status: String,
        ) {
            val shopUrl = endpointManager.getStoredShopUrl()
            if (shopUrl.isNullOrBlank()) return
            runCatching {
                syncQueueRepository.enqueue(
                    endpoint = "orders/$orderId/status",
                    method = "PATCH",
                    payloadJson =
                        errorBodyJson.encodeToString(
                            OrderStatusUpdateRequestDto.serializer(),
                            OrderStatusUpdateRequestDto(status = status),
                        ),
                    shopUrl = shopUrl,
                    resourceType = "order",
                    resourceId = orderId,
                )
            }.onFailure { Timber.w(it, "Impossible d'enfiler le retry de statut pour la commande $orderId") }
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
                    response.code() == HTTP_NOT_FOUND -> null
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
                    response.code() == HTTP_NOT_FOUND -> null
                    else -> {
                        val msg = "Erreur HTTP ${response.code()} lors du téléchargement du bordereau #$orderId"
                        Timber.w(msg)
                        error(msg)
                    }
                }
            }

        override suspend fun generateShippingLabel(orderId: Long) {
            withContext(ioDispatcher) {
                val response =
                    runCatching { api.generateShippingLabel(orderId) }.getOrElse { error ->
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
                HTTP_NOT_FOUND -> "Commande introuvable"
                HTTP_UNPROCESSABLE_ENTITY -> "Génération dispo uniquement pour Colissimo"
                HTTP_NOT_IMPLEMENTED -> "Contrat transporteur non configuré"
                HTTP_BAD_GATEWAY -> {
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
