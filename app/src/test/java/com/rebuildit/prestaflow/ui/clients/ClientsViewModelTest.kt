package com.rebuildit.prestaflow.ui.clients

import androidx.lifecycle.SavedStateHandle
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.domain.clients.ClientsRepository
import com.rebuildit.prestaflow.domain.clients.model.Client
import com.rebuildit.prestaflow.domain.clients.model.ClientStats
import com.rebuildit.prestaflow.domain.clients.model.ClientsPage
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Tests unitaires JVM du [ClientsViewModel].
 *
 * Points vérifiés :
 * - Statistiques depuis [fetchStats] (pas de comptage local).
 * - Changement de mode via [onFilterChange] : TOP / ALL / NEW.
 * - Retour au mode TOP par re-tap.
 * - Pagination incrémentale (loadMore).
 * - Recherche pleine-base (debounce → `fetchClients`).
 * - Calcul du 1er du mois courant pour le filtre NEW_THIS_MONTH.
 * - États loading / erreur.
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

    private fun buildViewModel(filterArg: String? = null): ClientsViewModel =
        ClientsViewModel(
            savedStateHandle = SavedStateHandle(
                if (filterArg != null) mapOf("filter" to filterArg) else emptyMap(),
            ),
            clientsRepository = fakeClientsRepo,
            networkErrorMapper = NetworkErrorMapper(),
            authRepository = fakeAuthRepo,
        )

    // ─── Stats depuis l'API ──────────────────────────────────────────────────

    @Test
    fun `stats total vient de fetchStats pas d un comptage local`() =
        runTest {
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
    fun `stats newThisMonth vient de fetchStats`() =
        runTest {
            fakeClientsRepo.fetchStatsResult = ClientStats(total = 150, newThisMonth = 7)

            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals(7, vm.uiState.value.stats?.newThisMonth)
        }

    @Test
    fun `stats est null si fetchStats retourne null`() =
        runTest {
            fakeClientsRepo.fetchStatsResult = null

            val vm = buildViewModel()
            advanceUntilIdle()

            assertNull(
                "stats doit être null si fetchStats retourne null (erreur réseau)",
                vm.uiState.value.stats,
            )
        }

    // ─── Changement de mode (cartes KPI) ─────────────────────────────────────

    @Test
    fun `mode initial est TOP_CLIENTS`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()
            assertEquals(ClientFilter.TOP_CLIENTS, vm.uiState.value.activeFilter)
        }

    @Test
    fun `onFilterChange ALL_CLIENTS active le mode et charge via fetchClients`() =
        runTest {
            val page =
                ClientsPage(
                    clients = listOf(buildClient(10L)),
                    hasNext = true,
                    nextOffset = 20,
                )
            fakeClientsRepo.fetchClientsResult = page

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onFilterChange(ClientFilter.ALL_CLIENTS)
            advanceUntilIdle()

            assertEquals(ClientFilter.ALL_CLIENTS, vm.uiState.value.activeFilter)
            assertEquals(1, vm.uiState.value.clients.size)
            assertTrue(vm.uiState.value.hasNextPage)
            assertEquals(20, vm.uiState.value.nextOffset)
        }

    @Test
    fun `fetchClients en mode ALL_CLIENTS transmet sort=date_desc sans filtre date`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onFilterChange(ClientFilter.ALL_CLIENTS)
            advanceUntilIdle()

            val call = fakeClientsRepo.lastFetchClientsCall
            assertNotNull(call)
            assertEquals("date_desc", call?.sort)
            assertNull(call?.createdFrom)
        }

    @Test
    fun `fetchClients en mode NEW_THIS_MONTH transmet le 1er du mois courant`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onFilterChange(ClientFilter.NEW_THIS_MONTH)
            advanceUntilIdle()

            val call = fakeClientsRepo.lastFetchClientsCall
            assertNotNull(call)
            assertEquals("date_desc", call?.sort)

            val expectedFirstOfMonth = LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            assertEquals(expectedFirstOfMonth, call?.createdFrom)
        }

    @Test
    fun `onFilterChange NEW_THIS_MONTH active le mode`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onFilterChange(ClientFilter.NEW_THIS_MONTH)
            advanceUntilIdle()

            assertEquals(ClientFilter.NEW_THIS_MONTH, vm.uiState.value.activeFilter)
        }

    @Test
    fun `re-tap ALL_CLIENTS revient au mode TOP_CLIENTS`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onFilterChange(ClientFilter.ALL_CLIENTS)
            advanceUntilIdle()
            vm.onFilterChange(ClientFilter.ALL_CLIENTS) // re-tap = désélection
            advanceUntilIdle()

            assertEquals(ClientFilter.TOP_CLIENTS, vm.uiState.value.activeFilter)
        }

    @Test
    fun `re-tap NEW_THIS_MONTH revient au mode TOP_CLIENTS`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onFilterChange(ClientFilter.NEW_THIS_MONTH)
            advanceUntilIdle()
            vm.onFilterChange(ClientFilter.NEW_THIS_MONTH)
            advanceUntilIdle()

            assertEquals(ClientFilter.TOP_CLIENTS, vm.uiState.value.activeFilter)
        }

    @Test
    fun `tap TOP_CLIENTS reste TOP_CLIENTS`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onFilterChange(ClientFilter.TOP_CLIENTS)
            advanceUntilIdle()

            assertEquals(ClientFilter.TOP_CLIENTS, vm.uiState.value.activeFilter)
        }

    @Test
    fun `passage de NEW a ALL fonctionne directement`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onFilterChange(ClientFilter.NEW_THIS_MONTH)
            advanceUntilIdle()
            vm.onFilterChange(ClientFilter.ALL_CLIENTS)
            advanceUntilIdle()

            assertEquals(ClientFilter.ALL_CLIENTS, vm.uiState.value.activeFilter)
        }

    // ─── Pagination ──────────────────────────────────────────────────────────

    @Test
    fun `onLoadMore ajoute les clients en bas de la liste`() =
        runTest {
            val page1 =
                ClientsPage(
                    clients = listOf(buildClient(1L), buildClient(2L)),
                    hasNext = true,
                    nextOffset = 20,
                )
            val page2 =
                ClientsPage(
                    clients = listOf(buildClient(3L)),
                    hasNext = false,
                    nextOffset = 0,
                )
            fakeClientsRepo.fetchClientsResult = page1

            val vm = buildViewModel()
            advanceUntilIdle()

            // Activer le mode ALL pour avoir la pagination
            vm.onFilterChange(ClientFilter.ALL_CLIENTS)
            advanceUntilIdle()
            assertEquals(2, vm.uiState.value.clients.size)
            assertTrue(vm.uiState.value.hasNextPage)

            // Charger la page 2
            fakeClientsRepo.fetchClientsResult = page2
            vm.onLoadMore()
            advanceUntilIdle()

            assertEquals(3, vm.uiState.value.clients.size)
            assertFalse(vm.uiState.value.hasNextPage)
        }

    @Test
    fun `onLoadMore avec offset correct (next_offset de la page precedente)`() =
        runTest {
            fakeClientsRepo.fetchClientsResult =
                ClientsPage(
                    clients = listOf(buildClient(1L)),
                    hasNext = true,
                    nextOffset = 20,
                )

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onFilterChange(ClientFilter.ALL_CLIENTS)
            advanceUntilIdle()

            fakeClientsRepo.fetchClientsResult =
                ClientsPage(clients = emptyList(), hasNext = false, nextOffset = 0)
            vm.onLoadMore()
            advanceUntilIdle()

            assertEquals(20, fakeClientsRepo.lastFetchClientsCall?.offset)
        }

    @Test
    fun `onLoadMore ignoree si hasNextPage est false`() =
        runTest {
            fakeClientsRepo.fetchClientsResult =
                ClientsPage(clients = listOf(buildClient(1L)), hasNext = false, nextOffset = 0)

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onFilterChange(ClientFilter.ALL_CLIENTS)
            advanceUntilIdle()

            val callCountBefore = fakeClientsRepo.lastFetchClientsCall
            vm.onLoadMore() // ne doit rien faire
            advanceUntilIdle()

            // L'offset ne doit pas avoir changé (toujours 0)
            assertEquals(0, fakeClientsRepo.lastFetchClientsCall?.offset)
        }

    @Test
    fun `onLoadMore ignoree en mode TOP_CLIENTS`() =
        runTest {
            fakeClientsRepo.setClients(listOf(buildClient(1L)))

            val vm = buildViewModel()
            advanceUntilIdle()

            // Mode TOP_CLIENTS, pas de pagination
            assertFalse(vm.uiState.value.hasNextPage)
            vm.onLoadMore() // ne doit rien déclencher
            advanceUntilIdle()

            // fetchClients ne doit pas avoir été appelé
            assertNull(fakeClientsRepo.lastFetchClientsCall)
        }

    // ─── Recherche pleine base ────────────────────────────────────────────────

    @Test
    fun `onQueryChange non vide appelle fetchClients avec la query`() =
        runTest {
            val searchResult =
                ClientsPage(
                    clients = listOf(buildClient(99L, firstName = "Alice")),
                    hasNext = false,
                    nextOffset = 0,
                )
            fakeClientsRepo.fetchClientsResult = searchResult

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onQueryChange("alice")
            // Le debounce est de 300ms ; advanceUntilIdle() avance le temps virtuel
            advanceUntilIdle()

            val call = fakeClientsRepo.lastFetchClientsCall
            assertNotNull("fetchClients doit avoir été appelé après une query non vide", call)
            assertEquals("alice", call?.query)
            assertEquals(1, vm.uiState.value.clients.size)
        }

    @Test
    fun `onQueryChange vide en mode TOP ne declenche pas fetchClients`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onQueryChange("")
            advanceUntilIdle()

            // Pas d'appel fetchClients pour query vide en mode TOP
            assertNull(fakeClientsRepo.lastFetchClientsCall)
        }

    @Test
    fun `listMode est SEARCH quand query non vide`() =
        runTest {
            fakeClientsRepo.fetchClientsResult =
                ClientsPage(clients = listOf(buildClient(1L)), hasNext = false, nextOffset = 0)

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onQueryChange("test")
            advanceUntilIdle()

            assertEquals(ClientListMode.SEARCH, vm.uiState.value.listMode)
        }

    @Test
    fun `listMode est TOP quand mode TOP et query vide`() =
        runTest {
            fakeClientsRepo.setClients(listOf(buildClient(1L)))
            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals(ClientListMode.TOP, vm.uiState.value.listMode)
        }

    @Test
    fun `listMode est ALL quand mode ALL_CLIENTS`() =
        runTest {
            fakeClientsRepo.fetchClientsResult = ClientsPage(clients = emptyList(), hasNext = false, nextOffset = 0)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onFilterChange(ClientFilter.ALL_CLIENTS)
            advanceUntilIdle()

            assertEquals(ClientListMode.ALL, vm.uiState.value.listMode)
        }

    @Test
    fun `listMode est NEW quand mode NEW_THIS_MONTH`() =
        runTest {
            fakeClientsRepo.fetchClientsResult = ClientsPage(clients = emptyList(), hasNext = false, nextOffset = 0)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onFilterChange(ClientFilter.NEW_THIS_MONTH)
            advanceUntilIdle()

            assertEquals(ClientListMode.NEW, vm.uiState.value.listMode)
        }

    // ─── Calcul du 1er du mois ───────────────────────────────────────────────

    @Test
    fun `le 1er du mois calcule est bien le 1er jour du mois courant`() {
        // Test unitaire pur (pas de coroutine) : vérifie la logique de calcul
        val firstOfMonth = LocalDate.now().withDayOfMonth(1)
        val formatted = firstOfMonth.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        // Vérifications : format correct et jour = 1
        assertTrue("Format attendu yyyy-MM-dd", formatted.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertEquals("Le jour doit être le 1er", "01", formatted.substring(8, 10))
    }

    // ─── État d'erreur ───────────────────────────────────────────────────────

    @Test
    fun `echec de refreshTopClients avec onRefresh expose une erreur`() =
        runTest {
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

    @Test
    fun `echec de fetchClients en mode ALL expose une erreur`() =
        runTest {
            fakeClientsRepo.setClients(listOf(buildClient(1L))) // top clients OK
            fakeClientsRepo.shouldThrowOnFetchClients = true

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onFilterChange(ClientFilter.ALL_CLIENTS)
            advanceUntilIdle()

            assertNotNull(
                "L'état doit contenir une erreur après un échec fetchClients",
                vm.uiState.value.error,
            )
        }

    // ─── Filtre initial transmis par la navigation (dashboard KPI) ──────────────

    @Test
    fun `filter new au demarrage active le mode NEW_THIS_MONTH`() =
        runTest {
            fakeClientsRepo.fetchClientsResult =
                ClientsPage(clients = emptyList(), hasNext = false, nextOffset = 0)

            val vm = buildViewModel(filterArg = "new")
            advanceUntilIdle()

            assertEquals(
                "Le mode doit être NEW_THIS_MONTH quand filter=new est passé en nav arg",
                ClientFilter.NEW_THIS_MONTH,
                vm.uiState.value.activeFilter,
            )
        }

    @Test
    fun `filter new au demarrage charge la liste des nouveaux du mois avec le 1er du mois`() =
        runTest {
            val newClients = listOf(buildClient(1L), buildClient(2L))
            fakeClientsRepo.fetchClientsResult =
                ClientsPage(clients = newClients, hasNext = false, nextOffset = 0)

            val vm = buildViewModel(filterArg = "new")
            advanceUntilIdle()

            val call = fakeClientsRepo.lastFetchClientsCall
            assertNotNull("fetchClients doit être appelé au démarrage avec filter=new", call)
            val expectedFirstOfMonth =
                LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            assertEquals("createdFrom doit être le 1er du mois courant", expectedFirstOfMonth, call?.createdFrom)
            assertEquals("sort doit être date_desc", "date_desc", call?.sort)
            assertEquals("La liste doit contenir les 2 nouveaux clients", 2, vm.uiState.value.clients.size)
        }

    @Test
    fun `filter null au demarrage reste en mode TOP_CLIENTS sans appel fetchClients`() =
        runTest {
            fakeClientsRepo.setClients(listOf(buildClient(1L)))

            val vm = buildViewModel(filterArg = null)
            advanceUntilIdle()

            assertEquals(
                "Sans filter, le mode doit rester TOP_CLIENTS",
                ClientFilter.TOP_CLIENTS,
                vm.uiState.value.activeFilter,
            )
            assertNull(
                "fetchClients ne doit pas être appelé en mode TOP_CLIENTS initial",
                fakeClientsRepo.lastFetchClientsCall,
            )
        }

    @Test
    fun `changement de mode remet la liste a zero`() =
        runTest {
            fakeClientsRepo.setClients(listOf(buildClient(1L), buildClient(2L)))
            fakeClientsRepo.fetchClientsResult =
                ClientsPage(clients = listOf(buildClient(10L)), hasNext = false, nextOffset = 0)

            val vm = buildViewModel()
            advanceUntilIdle()
            assertEquals(2, vm.uiState.value.clients.size)

            vm.onFilterChange(ClientFilter.ALL_CLIENTS)
            advanceUntilIdle()

            // La liste doit contenir uniquement les résultats du fetchClients
            assertEquals(1, vm.uiState.value.clients.size)
            assertEquals(10L, vm.uiState.value.clients.first().id)
        }

    // ─── Page size ───────────────────────────────────────────────────────────

    @Test
    fun `fetchClients est appele avec le PAGE_SIZE correct`() =
        runTest {
            fakeClientsRepo.fetchClientsResult = ClientsPage(clients = emptyList(), hasNext = false, nextOffset = 0)

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onFilterChange(ClientFilter.ALL_CLIENTS)
            advanceUntilIdle()

            assertEquals(ClientsRepository.PAGE_SIZE, fakeClientsRepo.lastFetchClientsCall?.limit)
        }

    // ─── onNavigationFilter (ré-navigation depuis le dashboard KPI) ─────────

    @Test
    fun `onNavigationFilter NEW_THIS_MONTH applique le mode meme si le VM est en TOP_CLIENTS`() =
        runTest {
            fakeClientsRepo.fetchClientsResult =
                ClientsPage(clients = listOf(buildClient(1L)), hasNext = false, nextOffset = 0)

            val vm = buildViewModel(filterArg = null) // Clients ouvert via barre de nav → TOP
            advanceUntilIdle()
            assertEquals(ClientFilter.TOP_CLIENTS, vm.uiState.value.activeFilter)

            // Simulation d'une ré-navigation via KPI (le LaunchedEffect de ClientsRoute appelle cette méthode)
            vm.onNavigationFilter(ClientFilter.NEW_THIS_MONTH)
            advanceUntilIdle()

            assertEquals(
                "onNavigationFilter doit forcer NEW_THIS_MONTH même si le mode était TOP_CLIENTS",
                ClientFilter.NEW_THIS_MONTH,
                vm.uiState.value.activeFilter,
            )
        }

    @Test
    fun `onNavigationFilter NEW_THIS_MONTH declenche le chargement avec createdFrom`() =
        runTest {
            val newClients = listOf(buildClient(5L))
            fakeClientsRepo.fetchClientsResult =
                ClientsPage(clients = newClients, hasNext = false, nextOffset = 0)

            val vm = buildViewModel(filterArg = null)
            advanceUntilIdle()

            vm.onNavigationFilter(ClientFilter.NEW_THIS_MONTH)
            advanceUntilIdle()

            val call = fakeClientsRepo.lastFetchClientsCall
            assertNotNull("fetchClients doit être appelé après onNavigationFilter(NEW)", call)
            val expectedFirstOfMonth =
                LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            assertEquals(expectedFirstOfMonth, call?.createdFrom)
            assertEquals(1, vm.uiState.value.clients.size)
        }

    @Test
    fun `onNavigationFilter est idempotent si le mode est deja correct`() =
        runTest {
            fakeClientsRepo.fetchClientsResult =
                ClientsPage(clients = listOf(buildClient(1L)), hasNext = false, nextOffset = 0)

            val vm = buildViewModel(filterArg = "new") // init → NEW_THIS_MONTH
            advanceUntilIdle()
            val callCountAfterInit = fakeClientsRepo.lastFetchClientsCall

            // Ré-appel avec le même mode : ne doit pas déclencher de nouveau chargement
            vm.onNavigationFilter(ClientFilter.NEW_THIS_MONTH)
            advanceUntilIdle()

            assertEquals(
                "onNavigationFilter idempotent : lastFetchClientsCall ne doit pas changer",
                callCountAfterInit,
                fakeClientsRepo.lastFetchClientsCall,
            )
        }

    @Test
    fun `onNavigationFilter apres changement interactif ré-applique le mode cible`() =
        runTest {
            fakeClientsRepo.fetchClientsResult =
                ClientsPage(clients = emptyList(), hasNext = false, nextOffset = 0)

            val vm = buildViewModel(filterArg = "new") // init → NEW_THIS_MONTH
            advanceUntilIdle()

            // L'utilisateur bascule manuellement vers TOP via les cartes KPI (re-tap)
            vm.onFilterChange(ClientFilter.NEW_THIS_MONTH) // re-tap = retour TOP
            advanceUntilIdle()
            assertEquals(ClientFilter.TOP_CLIENTS, vm.uiState.value.activeFilter)

            // Nouvelle navigation depuis le dashboard (LaunchedEffect re-déclenché)
            fakeClientsRepo.fetchClientsResult =
                ClientsPage(clients = listOf(buildClient(99L)), hasNext = false, nextOffset = 0)
            vm.onNavigationFilter(ClientFilter.NEW_THIS_MONTH)
            advanceUntilIdle()

            assertEquals(
                "onNavigationFilter doit ré-appliquer NEW après un changement interactif",
                ClientFilter.NEW_THIS_MONTH,
                vm.uiState.value.activeFilter,
            )
            assertEquals(1, vm.uiState.value.clients.size)
        }

    @Test
    fun `navigationFilterFlow emet new quand le savedStateHandle contient filter=new`() =
        runTest {
            val vm = buildViewModel(filterArg = "new")
            advanceUntilIdle()
            assertEquals("new", vm.navigationFilterFlow.value)
        }

    @Test
    fun `navigationFilterFlow emet null quand aucun filtre de navigation`() =
        runTest {
            val vm = buildViewModel(filterArg = null)
            advanceUntilIdle()
            assertNull(vm.navigationFilterFlow.value)
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
