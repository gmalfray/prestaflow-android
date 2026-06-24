package com.rebuildit.prestaflow.data.orders

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.remote.dto.OrderListCustomerDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListItemDto
import com.rebuildit.prestaflow.fakes.FakeOrderDao
import com.rebuildit.prestaflow.fakes.FakePrestaFlowApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires de [OrdersRepositoryImpl].
 *
 * Vérifie deux comportements critiques ayant causé des régressions :
 * 1. [refresh] purge la table Room AVANT l'upsert (évite que des commandes d'autres statuts
 *    persistent après un changement de filtre).
 * 2. Le paramètre [statusId] est bien transmis à l'API (filtre serveur effectif).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrdersRepositoryImplTest {
    private lateinit var fakeApi: FakePrestaFlowApi
    private lateinit var fakeDao: FakeOrderDao
    private lateinit var repository: OrdersRepositoryImpl

    @Before
    fun setUp() {
        fakeApi = FakePrestaFlowApi()
        fakeDao = FakeOrderDao()
        repository =
            OrdersRepositoryImpl(
                api = fakeApi,
                orderDao = fakeDao,
                networkErrorMapper = NetworkErrorMapper(),
                ioDispatcher = UnconfinedTestDispatcher(),
            )
    }

    // ─── Purge avant upsert ──────────────────────────────────────────────────

    @Test
    fun `refresh purge la table avant d inserer les nouvelles commandes`() =
        runTest {
            // Pré-remplir le DAO avec une commande
            fakeDao.upsertOrders(listOf(buildEntity(id = 99L)))
            fakeApi.ordersResponse = OrderListDto(orders = listOf(buildDto(id = 1L)))

            repository.refresh(forceRemote = true, statusId = null)

            // clear() doit avoir été appelé au moins une fois
            assertTrue("clear() doit être appelé avant upsert", fakeDao.clearCallCount >= 1)
            // L'ancienne commande (id=99) ne doit plus être présente
            val remaining = fakeDao.currentEntities()
            assertTrue("La commande précédente (id=99) ne doit plus être dans le DAO", remaining.none { it.id == 99L })
            assertTrue("La nouvelle commande (id=1) doit être présente", remaining.any { it.id == 1L })
        }

    @Test
    fun `refresh avec liste vide retournee par l API vide la table`() =
        runTest {
            fakeDao.upsertOrders(listOf(buildEntity(id = 1L), buildEntity(id = 2L)))
            fakeApi.ordersResponse = OrderListDto(orders = emptyList())

            repository.refresh(forceRemote = true, statusId = null)

            assertTrue("La table doit être vide si l'API renvoie une liste vide", fakeDao.currentEntities().isEmpty())
        }

    // ─── Transmission du statusId à l'API ────────────────────────────────────

    @Test
    fun `refresh transmet le statusId dans les filtres API`() =
        runTest {
            fakeApi.ordersResponse = OrderListDto(orders = emptyList())

            repository.refresh(forceRemote = true, statusId = 3)

            val filters = fakeApi.lastOrderFilters
            assertEquals("Le paramètre 'status' doit être '3'", "3", filters?.get("status"))
        }

    @Test
    fun `refresh sans statusId n inclut pas le parametre status dans l API`() =
        runTest {
            fakeApi.ordersResponse = OrderListDto(orders = emptyList())

            repository.refresh(forceRemote = true, statusId = null)

            val filters = fakeApi.lastOrderFilters
            assertTrue("Le paramètre 'status' ne doit pas être envoyé si statusId est null", !filters.orEmpty().containsKey("status"))
        }

    @Test
    fun `refresh envoie toujours sort et limit dans les filtres API`() =
        runTest {
            fakeApi.ordersResponse = OrderListDto(orders = emptyList())

            repository.refresh(forceRemote = true, statusId = null)

            val filters = fakeApi.lastOrderFilters.orEmpty()
            assertEquals("sort doit être '-date_add'", "-date_add", filters["sort"])
            assertEquals("limit doit être '50'", "50", filters["limit"])
        }

    // ─── Comportement en cas d'erreur réseau ─────────────────────────────────

    @Test(expected = RuntimeException::class)
    fun `refresh avec forceRemote true propage l exception reseau`() =
        runTest {
            fakeApi.ordersException = RuntimeException("Timeout")

            repository.refresh(forceRemote = true, statusId = null)
        }

    @Test
    fun `refresh avec forceRemote false n leve pas d exception en cas d erreur reseau`() =
        runTest {
            fakeApi.ordersException = RuntimeException("Timeout")
            // Ne doit pas lever d'exception
            repository.refresh(forceRemote = false, statusId = null)
        }

    // ─── Builders ────────────────────────────────────────────────────────────

    private fun buildDto(
        id: Long = 1L,
        reference: String = "REF00$id",
        status: String = "En préparation",
    ) = OrderListItemDto(
        id = id,
        reference = reference,
        status = status,
        totalPaid = 49.99,
        currency = "EUR",
        dateAdded = "2024-01-01T00:00:00+00:00",
        dateUpdated = "2024-01-02T00:00:00+00:00",
        customer = OrderListCustomerDto(id = 10L, firstName = "Alice", lastName = "Martin"),
        hasInvoice = false,
    )

    private fun buildEntity(
        id: Long = 1L,
        reference: String = "REF00$id",
    ) = com.rebuildit.prestaflow.data.local.entity.OrderEntity(
        id = id,
        reference = reference,
        status = "En préparation",
        totalPaid = 49.99,
        currency = "EUR",
        customerName = "Alice Martin",
        createdAtIso = "2024-01-01T00:00:00+00:00",
        updatedAtIso = "2024-01-02T00:00:00+00:00",
        hasInvoice = false,
        itemsJson = null,
        shippingJson = null,
    )
}
