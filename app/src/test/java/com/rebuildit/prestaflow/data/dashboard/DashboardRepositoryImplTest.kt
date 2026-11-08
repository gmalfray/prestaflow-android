package com.rebuildit.prestaflow.data.dashboard

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.local.dao.DashboardDao
import com.rebuildit.prestaflow.data.local.entity.DashboardMetricEntity
import com.rebuildit.prestaflow.data.remote.dto.ChartPointDto
import com.rebuildit.prestaflow.data.remote.dto.DashboardMetricsDto
import com.rebuildit.prestaflow.data.remote.dto.FreeConversionDto
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.fakes.FakePrestaFlowApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DashboardRepositoryImplTest {
    private class InMemoryDao : DashboardDao {
        val rows = mutableMapOf<String, MutableStateFlow<DashboardMetricEntity?>>()

        override fun observeByPeriod(period: String): Flow<DashboardMetricEntity?> = rows.getOrPut(period) { MutableStateFlow(null) }

        override suspend fun upsert(entity: DashboardMetricEntity) {
            rows.getOrPut(entity.period) { MutableStateFlow(null) }.value = entity
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val api = FakePrestaFlowApi()
    private val dao = InMemoryDao()
    private val repo =
        DashboardRepositoryImpl(
            api = api,
            dashboardDao = dao,
            networkErrorMapper = NetworkErrorMapper(),
            ioDispatcher = UnconfinedTestDispatcher(),
            json = json,
        )

    @Test
    fun `refresh mappe les champs offertes et N-1 du connecteur vers le domaine`() =
        runTest {
            api.dashboardMetrics =
                DashboardMetricsDto(
                    turnover = 300.0,
                    ordersCount = 12,
                    customersCount = 4,
                    productsCount = 9,
                    chart =
                        listOf(
                            ChartPointDto("L", orders = 5, customers = 1, turnover = 100.0, ordersFree = 2, turnoverPrevious = 80.0),
                            ChartPointDto("M", orders = 7, customers = 3, turnover = 200.0, ordersFree = 0, turnoverPrevious = 150.0),
                        ),
                    ordersFreeCount = 2,
                )
            repo.refresh(DashboardPeriod.WEEK, forceRemote = true)

            val snapshot = repo.observeDashboard(DashboardPeriod.WEEK).first()
            assertNotNull(snapshot)
            assertEquals(2, snapshot!!.ordersFreeCount)
            assertEquals(2, snapshot.chart[0].ordersFree)
            assertEquals(80.0, snapshot.chart[0].turnoverPrevious, 0.0)
            assertEquals(150.0, snapshot.chart[1].turnoverPrevious, 0.0)
            assertEquals(30.0, snapshot.avgCartPaid, 0.0001) // 300 / (12 - 2) ; sinon 25
        }

    @Test
    fun `un connecteur ancien sans les nouveaux champs se desérialise avec les valeurs par défaut`() {
        val dto =
            json.decodeFromString(
                DashboardMetricsDto.serializer(),
                """{"turnover":10.0,"orders_count":2,"customers_count":1,"products_count":3,
                    "chart":[{"label":"L","orders":2,"customers":1,"turnover":10.0}]}""",
            )
        assertEquals(0, dto.ordersFreeCount)
        assertEquals(0, dto.chart[0].ordersFree)
        assertEquals(0.0, dto.chart[0].turnoverPrevious, 0.0)
    }

    @Test
    fun `un cache chart_json écrit avant la v17 se relit sans erreur`() =
        runTest {
            dao.upsert(
                DashboardMetricEntity(
                    period = DashboardPeriod.WEEK.name,
                    turnover = 10.0,
                    ordersCount = 2,
                    customersCount = 1,
                    productsCount = 3,
                    lastUpdatedIso = "2026-01-01T00:00:00Z",
                    chartJson = """[{"label":"L","orders":2,"customers":1,"turnover":10.0,"new_customers":1}]""",
                ),
            )
            val snapshot = repo.observeDashboard(DashboardPeriod.WEEK).first()!!
            assertEquals(1, snapshot.chart.size)
            assertEquals(0, snapshot.ordersFreeCount)
            assertEquals(0, snapshot.chart[0].ordersFree)
        }

    @Test
    fun `getFreeConversion mappe la réponse du connecteur`() =
        runTest {
            api.freeConversion = FreeConversionDto(freeFirstCustomers = 40, paidFirstCustomers = 100, converted = 10, rate = 25.0)
            val result = repo.getFreeConversion()
            assertEquals(40, result.freeFirstCustomers)
            assertEquals(100, result.paidFirstCustomers)
            assertEquals(10, result.converted)
            assertEquals(25.0, result.rate, 0.0)
        }
}
