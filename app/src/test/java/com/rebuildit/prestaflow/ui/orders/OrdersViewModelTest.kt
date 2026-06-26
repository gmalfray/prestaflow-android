package com.rebuildit.prestaflow.ui.orders

import androidx.lifecycle.SavedStateHandle
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import com.rebuildit.prestaflow.fakes.FakeAuthRepository
import com.rebuildit.prestaflow.fakes.FakeOrdersPreferencesRepository
import com.rebuildit.prestaflow.fakes.FakeOrdersRepository
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
 * Tests unitaires JVM du [OrdersViewModel].
 *
 * Vérifie la logique de filtre par statut, la recherche locale, et le comportement
 * lors du retrait d'un statut de la préférence de visibilité.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeOrdersRepo: FakeOrdersRepository
    private lateinit var fakePrefsRepo: FakeOrdersPreferencesRepository
    private lateinit var fakeAuthRepo: FakeAuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeOrdersRepo = FakeOrdersRepository()
        fakePrefsRepo = FakeOrdersPreferencesRepository()
        fakeAuthRepo = FakeAuthRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(periodValue: String? = null): OrdersViewModel =
        OrdersViewModel(
            savedStateHandle = SavedStateHandle(if (periodValue != null) mapOf("period" to periodValue) else emptyMap()),
            ordersRepository = fakeOrdersRepo,
            ordersPreferencesRepository = fakePrefsRepo,
            networkErrorMapper = NetworkErrorMapper(),
            authRepository = fakeAuthRepo,
        )

    // ─── Filtre par statut ───────────────────────────────────────────────────

    @Test
    fun `selectionner un statut declenche un refresh avec ce statusId`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()
            fakeOrdersRepo.refreshCalls.clear()

            vm.onStatusFilterSelected(statusId = 3)
            advanceUntilIdle()

            val lastCall = fakeOrdersRepo.refreshCalls.lastOrNull()
            assertEquals(3, lastCall?.second)
        }

    @Test
    fun `selectionner un statut met a jour selectedStatusId dans l etat`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onStatusFilterSelected(statusId = 5)
            advanceUntilIdle()

            assertEquals(5, vm.uiState.value.selectedStatusId)
        }

    @Test
    fun `selectionner null retire le filtre et passe statusId null au refresh`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()
            vm.onStatusFilterSelected(statusId = 3)
            advanceUntilIdle()
            fakeOrdersRepo.refreshCalls.clear()

            vm.onStatusFilterSelected(statusId = null)
            advanceUntilIdle()

            assertNull(vm.uiState.value.selectedStatusId)
            val lastCall = fakeOrdersRepo.refreshCalls.lastOrNull()
            assertNull(lastCall?.second)
        }

    // ─── Préférences de statuts visibles ────────────────────────────────────

    @Test
    fun `retrait d un statut de la preference retombe sur Toutes si c etait le statut selectionne`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            // Sélectionner le statut 2 comme filtre actif
            vm.onStatusFilterSelected(statusId = 2)
            advanceUntilIdle()

            // La préférence émet un ensemble qui ne contient plus le statut 2
            fakePrefsRepo.emitVisibleStatusIds(setOf(1, 3))
            advanceUntilIdle()

            assertNull(
                "selectedStatusId doit revenir à null quand le statut sélectionné disparaît des visibles",
                vm.uiState.value.selectedStatusId,
            )
        }

    @Test
    fun `retrait d un statut non selectionne ne change pas selectedStatusId`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onStatusFilterSelected(statusId = 1)
            advanceUntilIdle()

            // Retirer le statut 3 (pas le statut actif 1)
            fakePrefsRepo.emitVisibleStatusIds(setOf(1, 2))
            advanceUntilIdle()

            assertEquals(
                "selectedStatusId ne doit pas changer si le statut retiré n'était pas actif",
                1,
                vm.uiState.value.selectedStatusId,
            )
        }

    @Test
    fun `visibleStatusIds null dans les preferences affiche tous les statuts disponibles`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            fakePrefsRepo.emitVisibleStatusIds(null)
            advanceUntilIdle()

            assertNull(vm.uiState.value.visibleStatusIds)
        }

    // ─── Recherche locale ────────────────────────────────────────────────────

    @Test
    fun `onQueryChange met a jour query dans l etat`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onQueryChange("dupont")
            advanceUntilIdle()

            assertEquals("dupont", vm.uiState.value.query)
        }

    @Test
    fun `visibleOrders filtre les commandes par nom de client apres onQueryChange`() =
        runTest {
            fakeOrdersRepo.setOrders(
                listOf(
                    buildOrder(1L, "REF001", customerName = "Alice Martin"),
                    buildOrder(2L, "REF002", customerName = "Bob Dupont"),
                ),
            )

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onQueryChange("alice")
            advanceUntilIdle()

            val visible = vm.uiState.value.visibleOrders
            assertEquals(1, visible.size)
            assertEquals("REF001", visible.first().reference)
        }

    // ─── Chargement des statuts disponibles ─────────────────────────────────

    @Test
    fun `les statuts disponibles sont charges au demarrage`() =
        runTest {
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(1, "Paiement accepté", "#00FF00"),
                    OrderStatusFilter(2, "En préparation", "#0000FF"),
                )

            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals(2, vm.uiState.value.availableStatuses.size)
        }

    // ─── État d'erreur ───────────────────────────────────────────────────────

    @Test
    fun `un echec de refresh avec notifyOnError vrai expose une erreur dans l etat`() =
        runTest {
            fakeOrdersRepo.shouldThrowOnRefresh = true

            val vm = buildViewModel()
            advanceUntilIdle()

            // Refresh explicite avec notification d'erreur
            vm.onRefresh()
            advanceUntilIdle()

            assertTrue(
                "L'état doit contenir une erreur après un refresh échoué avec notifyOnError=true",
                vm.uiState.value.error != null,
            )
        }

    // ─── Sélection multiple ──────────────────────────────────────────────────

    @Test
    fun `appui long sur une commande avec facture active le mode selection`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L, "REF001", hasInvoice = true)))

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onOrderLongPress(1L)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.selectionMode)
            assertTrue(vm.uiState.value.selectedOrderIds.contains(1L))
        }

    @Test
    fun `appui long sur une commande sans facture n active pas le mode selection`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L, "REF001", hasInvoice = false)))

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onOrderLongPress(1L)
            advanceUntilIdle()

            assertTrue(!vm.uiState.value.selectionMode)
        }

    @Test
    fun `cancelSelection quitte le mode selection et vide les ids selectionnes`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L, "REF001", hasInvoice = true)))

            val vm = buildViewModel()
            advanceUntilIdle()
            vm.onOrderLongPress(1L)
            advanceUntilIdle()

            vm.cancelSelection()
            advanceUntilIdle()

            assertTrue(!vm.uiState.value.selectionMode)
            assertTrue(vm.uiState.value.selectedOrderIds.isEmpty())
        }

    // ─── Mise à jour de statut en lot ────────────────────────────────────────

    @Test
    fun `bulkUpdateStatus appelle updateOrderStatus pour chaque commande selectionnee`() =
        runTest {
            fakeOrdersRepo.setOrders(
                listOf(
                    buildOrder(1L, "REF001", hasInvoice = true),
                    buildOrder(2L, "REF002", hasInvoice = true),
                ),
            )
            val vm = buildViewModel()
            advanceUntilIdle()

            // Sélectionner les deux commandes
            vm.onOrderLongPress(1L)
            advanceUntilIdle()
            vm.onOrderSelectionToggle(2L)
            advanceUntilIdle()

            vm.bulkUpdateStatus("5")
            advanceUntilIdle()

            val calledIds = fakeOrdersRepo.updateStatusCalls.map { it.first }
            assertTrue("La commande 1 doit être mise à jour", 1L in calledIds)
            assertTrue("La commande 2 doit être mise à jour", 2L in calledIds)
            assertEquals("Le statut envoyé doit être '5'", "5", fakeOrdersRepo.updateStatusCalls.first().second)
        }

    @Test
    fun `bulkUpdateStatus quitte le mode selection apres succes`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L, "REF001", hasInvoice = true)))
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onOrderLongPress(1L)
            advanceUntilIdle()

            vm.bulkUpdateStatus("3")
            advanceUntilIdle()

            assertTrue("Le mode sélection doit être désactivé", !vm.uiState.value.selectionMode)
            assertTrue("Les IDs sélectionnés doivent être vidés", vm.uiState.value.selectedOrderIds.isEmpty())
        }

    @Test
    fun `bulkUpdateStatus emet un snackbar de succes`() =
        runTest {
            fakeOrdersRepo.setOrders(
                listOf(
                    buildOrder(1L, "REF001", hasInvoice = true),
                    buildOrder(2L, "REF002", hasInvoice = true),
                ),
            )
            val vm = buildViewModel()
            advanceUntilIdle()
            vm.onOrderLongPress(1L)
            advanceUntilIdle()
            vm.onOrderSelectionToggle(2L)
            advanceUntilIdle()

            vm.bulkUpdateStatus("5")
            advanceUntilIdle()

            val snackbar = vm.uiState.value.bulkSnackbar
            assertTrue(
                "Le snackbar doit mentionner le nombre de succès",
                snackbar != null && snackbar.contains("2"),
            )
        }

    @Test
    fun `bulkUpdateStatus gere les echecs partiels sans planter`() =
        runTest {
            fakeOrdersRepo.setOrders(
                listOf(
                    buildOrder(1L, "REF001", hasInvoice = true),
                    buildOrder(2L, "REF002", hasInvoice = true),
                ),
            )
            // La commande 2 échoue
            fakeOrdersRepo.failingOrderIds.add(2L)

            val vm = buildViewModel()
            advanceUntilIdle()
            vm.onOrderLongPress(1L)
            advanceUntilIdle()
            vm.onOrderSelectionToggle(2L)
            advanceUntilIdle()

            // Ne doit pas lancer d'exception
            vm.bulkUpdateStatus("5")
            advanceUntilIdle()

            // L'opération se termine quand même et sort du mode sélection
            assertTrue("Le mode sélection doit être désactivé même avec un échec partiel", !vm.uiState.value.selectionMode)
            val snackbar = vm.uiState.value.bulkSnackbar
            assertTrue("Le snackbar doit mentionner un échec", snackbar != null && snackbar.contains("1"))
        }

    @Test
    fun `bulkUpdateStatus ne fait rien si la selection est vide`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()
            fakeOrdersRepo.updateStatusCalls.clear()

            vm.bulkUpdateStatus("5")
            advanceUntilIdle()

            assertTrue("Aucun appel updateOrderStatus ne doit avoir eu lieu", fakeOrdersRepo.updateStatusCalls.isEmpty())
        }

    @Test
    fun `isBulkUpdating est faux apres la fin de la mise a jour`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L, "REF001", hasInvoice = true)))
            val vm = buildViewModel()
            advanceUntilIdle()
            vm.onOrderLongPress(1L)
            advanceUntilIdle()

            vm.bulkUpdateStatus("5")
            advanceUntilIdle()

            assertTrue("isBulkUpdating doit être false après l'opération", !vm.uiState.value.isBulkUpdating)
        }

    // ─── Filtre statut + liste vide ──────────────────────────────────────────

    @Test
    fun `filtre statut sur liste vide ne crashe pas et conserve selectedStatusId`() =
        runTest {
            // Aucune commande retournée par le repo (statut sans résultat)
            fakeOrdersRepo.setOrders(emptyList())

            val vm = buildViewModel()
            advanceUntilIdle()

            // Sélectionner un statut → refresh → liste vide
            vm.onStatusFilterSelected(statusId = 7)
            advanceUntilIdle()

            assertEquals(
                "selectedStatusId doit être conservé même si la liste est vide",
                7,
                vm.uiState.value.selectedStatusId,
            )
            assertTrue(
                "orders doit être vide",
                vm.uiState.value.orders.isEmpty(),
            )
        }

    @Test
    fun `reinitialiser le filtre statut remet selectedStatusId a null`() =
        runTest {
            fakeOrdersRepo.setOrders(emptyList())

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onStatusFilterSelected(statusId = 7)
            advanceUntilIdle()

            // Réinitialisation via onStatusFilterSelected(null) (action bouton "Réinitialiser")
            vm.onStatusFilterSelected(statusId = null)
            advanceUntilIdle()

            assertNull(
                "selectedStatusId doit être null après réinitialisation",
                vm.uiState.value.selectedStatusId,
            )
        }

    // ─── Builders ────────────────────────────────────────────────────────────

    private fun buildOrder(
        id: Long,
        reference: String,
        customerName: String = "Client Test",
        status: String = "En préparation",
        hasInvoice: Boolean = false,
    ) = Order(
        id = id,
        reference = reference,
        status = status,
        totalPaid = 49.99,
        currency = "EUR",
        customerName = customerName,
        createdAtIso = "2024-01-01T00:00:00+00:00",
        updatedAtIso = "2024-01-02T00:00:00+00:00",
        hasInvoice = hasInvoice,
    )
}
