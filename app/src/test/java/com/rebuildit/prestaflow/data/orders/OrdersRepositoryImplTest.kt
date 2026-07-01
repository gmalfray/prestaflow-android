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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires de [OrdersRepositoryImpl].
 *
 * Vérifie :
 * 1. [refresh] purge la table Room AVANT l'upsert (offset=0) — évite la persistance
 *    de commandes d'autres statuts après un changement de filtre.
 * 2. Les params `statuses` (CSV) et `sort` sont bien transmis à l'API.
 * 3. Pagination : `offset > 0` → pas de purge (accumulation).
 * 4. hasMore est calculé selon la taille de la réponse vs la limite.
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

    // ─── Purge avant upsert (offset=0) ──────────────────────────────────────

    @Test
    fun `refresh offset=0 purge la table avant d inserer les nouvelles commandes`() =
        runTest {
            fakeDao.upsertOrders(listOf(buildEntity(id = 99L)))
            fakeApi.ordersResponse = OrderListDto(orders = listOf(buildDto(id = 1L)))

            repository.refresh(forceRemote = true, offset = 0)

            assertTrue("clear() doit être appelé avant upsert", fakeDao.clearCallCount >= 1)
            val remaining = fakeDao.currentEntities()
            assertTrue("La commande précédente (id=99) ne doit plus être dans le DAO", remaining.none { it.id == 99L })
            assertTrue("La nouvelle commande (id=1) doit être présente", remaining.any { it.id == 1L })
        }

    @Test
    fun `refresh offset=0 avec liste vide retournee par l API vide la table`() =
        runTest {
            fakeDao.upsertOrders(listOf(buildEntity(id = 1L), buildEntity(id = 2L)))
            fakeApi.ordersResponse = OrderListDto(orders = emptyList())

            repository.refresh(forceRemote = true, offset = 0)

            assertTrue("La table doit être vide si l'API renvoie une liste vide", fakeDao.currentEntities().isEmpty())
        }

    // ─── Pagination (offset > 0) ─────────────────────────────────────────────

    @Test
    fun `refresh offset superieur a 0 n efface pas la table existante`() =
        runTest {
            fakeDao.upsertOrders(listOf(buildEntity(id = 1L)))
            fakeApi.ordersResponse = OrderListDto(orders = listOf(buildDto(id = 2L)))

            repository.refresh(forceRemote = true, offset = 50)

            val remaining = fakeDao.currentEntities()
            assertTrue("La commande id=1 doit rester (pas de clear sur offset>0)", remaining.any { it.id == 1L })
            assertTrue("La nouvelle commande id=2 doit être ajoutée", remaining.any { it.id == 2L })
        }

    @Test
    fun `hasMore est true si la reponse est pleine`() =
        runTest {
            val orders = (1..50).map { buildDto(id = it.toLong()) }
            fakeApi.ordersResponse = OrderListDto(orders = orders)

            val hasMore = repository.refresh(forceRemote = true, limit = 50)

            assertTrue("hasMore doit être true si la réponse est pleine (50 = limit)", hasMore)
        }

    @Test
    fun `hasMore est false si la reponse est inferieure a la limite`() =
        runTest {
            fakeApi.ordersResponse = OrderListDto(orders = listOf(buildDto(id = 1L), buildDto(id = 2L)))

            val hasMore = repository.refresh(forceRemote = true, limit = 50)

            assertFalse("hasMore doit être false si la réponse est incomplète", hasMore)
        }

    // ─── Transmission des filtres à l'API ────────────────────────────────────

    @Test
    fun `refresh transmet les statusIds dans le parametre statuses CSV`() =
        runTest {
            fakeApi.ordersResponse = OrderListDto(orders = emptyList())

            repository.refresh(forceRemote = true, statusIds = setOf(3))

            val filters = fakeApi.lastOrderFilters
            assertEquals("Le paramètre 'statuses' doit contenir l'ID", "3", filters?.get("statuses"))
        }

    @Test
    fun `refresh avec plusieurs statusIds les envoie en CSV`() =
        runTest {
            fakeApi.ordersResponse = OrderListDto(orders = emptyList())

            repository.refresh(forceRemote = true, statusIds = setOf(2, 3, 4))

            val filters = fakeApi.lastOrderFilters
            val statuses = filters?.get("statuses") ?: ""
            val ids = statuses.split(",").map { it.trim() }.toSet()
            assertEquals(setOf("2", "3", "4"), ids)
        }

    @Test
    fun `refresh sans statusIds n inclut pas le parametre statuses`() =
        runTest {
            fakeApi.ordersResponse = OrderListDto(orders = emptyList())

            repository.refresh(forceRemote = true, statusIds = emptySet())

            val filters = fakeApi.lastOrderFilters
            assertFalse(
                "Le paramètre 'statuses' ne doit pas être envoyé si statusIds est vide",
                filters.orEmpty().containsKey("statuses"),
            )
        }

    @Test
    fun `refresh envoie toujours sort et limit dans les filtres API`() =
        runTest {
            fakeApi.ordersResponse = OrderListDto(orders = emptyList())

            repository.refresh(forceRemote = true, sort = "date_desc", limit = 50)

            val filters = fakeApi.lastOrderFilters.orEmpty()
            assertEquals("sort doit correspondre au paramètre passé", "date_desc", filters["sort"])
            assertEquals("limit doit être '50'", "50", filters["limit"])
        }

    @Test
    fun `refresh transmet le tri custom`() =
        runTest {
            fakeApi.ordersResponse = OrderListDto(orders = emptyList())

            repository.refresh(forceRemote = true, sort = "total_desc")

            val filters = fakeApi.lastOrderFilters.orEmpty()
            assertEquals("total_desc", filters["sort"])
        }

    // ─── Comportement en cas d'erreur réseau ─────────────────────────────────

    @Test(expected = RuntimeException::class)
    fun `refresh avec forceRemote true propage l exception reseau`() =
        runTest {
            fakeApi.ordersException = RuntimeException("Timeout")

            repository.refresh(forceRemote = true)
        }

    @Test
    fun `refresh avec forceRemote false n leve pas d exception en cas d erreur reseau`() =
        runTest {
            fakeApi.ordersException = RuntimeException("Timeout")
            // Ne doit pas lever d'exception
            repository.refresh(forceRemote = false)
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
        statusColor = null,
        currentStateId = 0,
    )
}
