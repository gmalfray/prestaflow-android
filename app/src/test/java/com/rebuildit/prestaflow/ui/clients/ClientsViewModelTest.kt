package com.rebuildit.prestaflow.ui.clients

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.domain.clients.model.Client
import com.rebuildit.prestaflow.domain.clients.model.ClientStats
import com.rebuildit.prestaflow.fakes.FakeAuthRepository
import com.rebuildit.prestaflow.fakes.FakeClientsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires JVM du [ClientsViewModel].
 *
 * Points vérifiés :
 * - [ClientStats.total] et [ClientStats.newThisMonth] viennent de [fetchStats]
 *   (endpoint customers/stats), pas d'un calcul local.
 * - La recherche filtre localement sur le nom complet et l'e-mail.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeClientsRepo: FakeClientsRepository
    private lateinit var fakeAuthRepo: FakeAuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeClientsRepo = FakeClientsRepository()
        fakeAuthRepo = FakeAuthRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): ClientsViewModel =
        ClientsViewModel(
            clientsRepository = fakeClientsRepo,
            networkErrorMapper = NetworkErrorMapper(),
            authRepository = fakeAuthRepo,
        )

    // ─── Stats depuis l'API ──────────────────────────────────────────────────

    @Test
    fun `stats total vient de fetchStats pas d un comptage local`() = runTest {
        // Seulement 1 client en local, mais fetchStats retourne 150
        fakeClientsRepo.setClients(listOf(buildClient(1L)))
        fakeClientsRepo.fetchStatsResult = ClientStats(total = 150, newThisMonth = 12)

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(
            "stats.total doit venir de fetchStats (150), pas du count local (1)",
            150,
            vm.uiState.value.stats?.total,
        )
    }

    @Test
    fun `stats newThisMonth vient de fetchStats`() = runTest {
        fakeClientsRepo.fetchStatsResult = ClientStats(total = 150, newThisMonth = 7)

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(7, vm.uiState.value.stats?.newThisMonth)
    }

    @Test
    fun `stats est null si fetchStats retourne null`() = runTest {
        fakeClientsRepo.fetchStatsResult = null

        val vm = buildViewModel()
        advanceUntilIdle()

        assertNull(
            "stats doit être null si fetchStats retourne null (erreur réseau)",
            vm.uiState.value.stats,
        )
    }

    // ─── Recherche locale ────────────────────────────────────────────────────

    @Test
    fun `visibleClients filtre par nom complet insensible a la casse`() = runTest {
        fakeClientsRepo.setClients(
            listOf(
                buildClient(1L, firstName = "Alice", lastName = "Martin", email = "alice@test.fr"),
                buildClient(2L, firstName = "Bob", lastName = "Dupont", email = "bob@test.fr"),
            ),
        )

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onQueryChange("alice")
        advanceUntilIdle()

        val visible = vm.uiState.value.visibleClients
        assertEquals(1, visible.size)
        assertEquals("Alice", visible.first().firstName)
    }

    @Test
    fun `visibleClients filtre par email insensible a la casse`() = runTest {
        fakeClientsRepo.setClients(
            listOf(
                buildClient(1L, firstName = "Alice", lastName = "Martin", email = "alice@test.fr"),
                buildClient(2L, firstName = "Bob", lastName = "Dupont", email = "bob@example.com"),
            ),
        )

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onQueryChange("EXAMPLE")
        advanceUntilIdle()

        val visible = vm.uiState.value.visibleClients
        assertEquals(1, visible.size)
        assertEquals("Bob", visible.first().firstName)
    }

    @Test
    fun `visibleClients retourne tous les clients si query est vide`() = runTest {
        fakeClientsRepo.setClients(
            listOf(buildClient(1L), buildClient(2L), buildClient(3L)),
        )

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.visibleClients.size)
    }

    // ─── État d'erreur ───────────────────────────────────────────────────────

    @Test
    fun `un echec de refreshTopClients avec onRefresh expose une erreur dans l etat`() = runTest {
        fakeClientsRepo.shouldThrowOnRefresh = true

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onRefresh()
        advanceUntilIdle()

        assertTrue(
            "L'état doit contenir une erreur après onRefresh() échoué",
            vm.uiState.value.error != null,
        )
    }

    // ─── Builders ────────────────────────────────────────────────────────────

    private fun buildClient(
        id: Long,
        firstName: String = "Client",
        lastName: String = "Test$id",
        email: String = "client$id@test.fr",
    ) = Client(
        id = id,
        firstName = firstName,
        lastName = lastName,
        email = email,
        ordersCount = 3,
        totalSpent = 150.0,
        lastOrderAtIso = null,
        orders = emptyList(),
    )
}
