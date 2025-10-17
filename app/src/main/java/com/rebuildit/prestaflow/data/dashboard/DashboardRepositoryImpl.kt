package com.rebuildit.prestaflow.data.dashboard

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.local.dao.DashboardDao
import com.rebuildit.prestaflow.data.local.entity.DashboardMetricEntity
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.data.remote.dto.ChartPointDto
import com.rebuildit.prestaflow.domain.dashboard.DashboardRepository
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardChartPoint
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

@Singleton
class DashboardRepositoryImpl @Inject constructor(
    private val api: PrestaFlowApi,
    private val dashboardDao: DashboardDao,
    private val networkErrorMapper: NetworkErrorMapper,
    private val ioDispatcher: CoroutineDispatcher,
    private val json: Json
) : DashboardRepository {

    private val chartSerializer = ListSerializer(ChartPointDto.serializer())

    override fun observeDashboard(period: DashboardPeriod): Flow<DashboardSnapshot?> =
        dashboardDao.observeByPeriod(period.name).map { entity -> entity?.toDomain(period) }

    override suspend fun refresh(period: DashboardPeriod, forceRemote: Boolean) {
        withContext(ioDispatcher) {
            val result = runCatching { api.getDashboardMetrics(period.queryValue) }
            result.fold(
                onSuccess = { payload ->
                    val entity = DashboardMetricEntity(
                        period = period.name,
                        turnover = payload.turnover,
                        ordersCount = payload.ordersCount,
                        customersCount = payload.customersCount,
                        productsCount = payload.productsCount,
                        lastUpdatedIso = java.time.Instant.now().toString(),
                        chartJson = json.encodeToString(chartSerializer, payload.chart)
                    )
                    dashboardDao.upsert(entity)
                },
                onFailure = { error ->
                    Timber.w(networkErrorMapper.map(error).toString())
                    if (forceRemote) throw error
                }
            )
        }
    }

    private fun DashboardMetricEntity.toDomain(period: DashboardPeriod): DashboardSnapshot {
        val chartPoints = runCatching {
            json.decodeFromString(chartSerializer, chartJson)
        }.getOrElse { emptyList() }
        return DashboardSnapshot(
            period = period,
            turnover = turnover,
            ordersCount = ordersCount,
            customersCount = customersCount,
            productsCount = productsCount,
            chart = chartPoints.map { it.toDomain() },
            lastUpdatedIso = lastUpdatedIso
        )
    }

    private fun ChartPointDto.toDomain(): DashboardChartPoint = DashboardChartPoint(
        label = label,
        orders = orders,
        customers = customers,
        turnover = turnover
    )
}
