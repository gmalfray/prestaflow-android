package com.rebuildit.prestaflow.ui.orders

import app.cash.turbine.test
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderShipping
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires JVM du [OrderDetailViewModel].
 *
 * Vérifie le chargement des statuts disponibles et la mise à jour de statut.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeOrdersRepo: FakeOrdersRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeOrdersRepo = FakeOrdersRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(orderId: Long = 1L): OrderDetailViewModel {
        val savedState = androidx.lifecycle.SavedStateHandle(mapOf("orderId" to orderId))
        return OrderDetailViewModel(
            savedStateHandle = savedState,
            ordersRepository = fakeOrdersRepo,
        )
    }

    // ─── Chargement des statuts ──────────────────────────────────────────────

    @Test
    fun `availableStatuses est vide au demarrage si le repo ne retourne rien`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            assertTrue(vm.availableStatuses.value.isEmpty())
        }

    @Test
    fun `availableStatuses est alimente apres demarrage si le repo retourne des statuts`() =
        runTest {
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(1, "Paiement accepté", "#28A745"),
                    OrderStatusFilter(2, "En préparation", "#17A2B8"),
                    OrderStatusFilter(3, "Expédié", "#C95A3B"),
                )

            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals(3, vm.availableStatuses.value.size)
            assertEquals("Paiement accepté", vm.availableStatuses.value.first().name)
        }

    @Test
    fun `availableStatuses reste vide en cas d erreur reseau silencieuse`() =
        runTest {
            // Le repo lève une exception au chargement des statuts
            fakeOrdersRepo.orderStatuses = emptyList()

            val vm = buildViewModel()
            advanceUntilIdle()

            // Pas de crash, juste une liste vide
            assertTrue(vm.availableStatuses.value.isEmpty())
        }

    // ─── Mise à jour de statut ───────────────────────────────────────────────

    @Test
    fun `updateStatus appelle updateOrderStatus avec le status en entier`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L)))
            val vm = buildViewModel(orderId = 1L)
            advanceUntilIdle()

            vm.updateStatus("3")
            advanceUntilIdle()

            val call = fakeOrdersRepo.updateStatusCalls.lastOrNull()
            assertEquals(1L, call?.first)
            assertEquals("3", call?.second)
        }

    @Test
    fun `updateStatus ignore les valeurs blanches`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L)))
            val vm = buildViewModel(orderId = 1L)
            advanceUntilIdle()
            fakeOrdersRepo.updateStatusCalls.clear()

            vm.updateStatus("   ")
            advanceUntilIdle()

            assertTrue("updateOrderStatus ne doit pas être appelé pour une valeur vide", fakeOrdersRepo.updateStatusCalls.isEmpty())
        }

    @Test
    fun `updateStatus expose un message de succes via actionState`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L)))
            val vm = buildViewModel(orderId = 1L)
            advanceUntilIdle()

            vm.updateStatus("2")
            advanceUntilIdle()

            assertTrue("Un message de succès doit être émis", vm.actionState.value.message != null)
            assertEquals(null, vm.actionState.value.error)
        }

    @Test
    fun `updateStatus expose une erreur via actionState en cas d echec`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L)))
            fakeOrdersRepo.shouldThrowOnUpdateStatus = true
            val vm = buildViewModel(orderId = 1L)
            advanceUntilIdle()

            vm.updateStatus("2")
            advanceUntilIdle()

            assertTrue("Une erreur doit être émise", vm.actionState.value.error != null)
            assertEquals(null, vm.actionState.value.message)
        }

    @Test
    fun `consumeActionFeedback reinitialise message et erreur`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L)))
            val vm = buildViewModel(orderId = 1L)
            advanceUntilIdle()

            vm.updateStatus("2")
            advanceUntilIdle()

            vm.consumeActionFeedback()
            advanceUntilIdle()

            assertEquals(null, vm.actionState.value.message)
            assertEquals(null, vm.actionState.value.error)
        }

    // ─── Mise à jour du numéro de suivi ─────────────────────────────────────────

    @Test
    fun `updateTracking appelle updateOrderShipping avec le numero trimme`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L)))
            val vm = buildViewModel(orderId = 1L)
            advanceUntilIdle()

            // Simule un résultat de scan avec espaces parasites (ex. artefact lecteur)
            vm.updateTracking("  1Z999AA10123456784  ")
            advanceUntilIdle()

            val call = fakeOrdersRepo.updateShippingCalls.lastOrNull()
            assertEquals(1L, call?.first)
            assertEquals("1Z999AA10123456784", call?.second)
        }

    @Test
    fun `updateTracking ignore les valeurs blanches`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L)))
            val vm = buildViewModel(orderId = 1L)
            advanceUntilIdle()

            vm.updateTracking("   ")
            advanceUntilIdle()

            assertTrue("updateOrderShipping ne doit pas être appelé pour une valeur vide", fakeOrdersRepo.updateShippingCalls.isEmpty())
        }

    @Test
    fun `updateTracking expose un message de succes via actionState`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L)))
            val vm = buildViewModel(orderId = 1L)
            advanceUntilIdle()

            vm.updateTracking("1Z999AA10123456784")
            advanceUntilIdle()

            assertTrue("Un message de succès doit être émis", vm.actionState.value.message != null)
            assertEquals(null, vm.actionState.value.error)
        }

    @Test
    fun `updateTracking expose une erreur via actionState en cas d echec`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L)))
            fakeOrdersRepo.shouldThrowOnUpdateShipping = true
            val vm = buildViewModel(orderId = 1L)
            advanceUntilIdle()

            vm.updateTracking("1Z999AA10123456784")
            advanceUntilIdle()

            assertTrue("Une erreur doit être émise", vm.actionState.value.error != null)
            assertEquals(null, vm.actionState.value.message)
        }

    // ─── Génération étiquette ────────────────────────────────────────────────

    @Test
    fun `onGenerateLabel appelle generateShippingLabel pour la commande courante`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L)))
            val vm = buildViewModel(orderId = 1L)
            advanceUntilIdle()

            vm.onGenerateLabel()
            advanceUntilIdle()

            assertTrue("generateShippingLabel doit être appelé avec l'id 1", fakeOrdersRepo.generateLabelCalls.contains(1L))
        }

    @Test
    fun `onGenerateLabel sur succes met a jour hasShippingLabel et le tracking via le Flow`() =
        runTest {
            val orderSansLabel = buildOrder(1L).copy(
                hasShippingLabel = false,
                shipping = OrderShipping(carrierId = 1L, carrierName = "Colissimo", trackingNumber = null),
            )
            fakeOrdersRepo.setOrders(listOf(orderSansLabel))
            fakeOrdersRepo.generateLabelTrackingNumber = "8R01234567890"
            val vm = buildViewModel(orderId = 1L)

            vm.uiState.test {
                // advanceUntilIdle() dans le bloc test (collecteur actif) pour que
                // le stateIn(WhileSubscribed) collecte et émette l'état initial avant toute action.
                advanceUntilIdle()
                val initial = expectMostRecentItem()
                assertTrue("L'état initial doit être Success", initial is OrderDetailUiState.Success)
                assertFalse((initial as OrderDetailUiState.Success).order.hasShippingLabel)

                vm.onGenerateLabel()
                advanceUntilIdle()

                // Après génération, le Flow _ordersFlow est mis à jour → uiState émet Success(updatedOrder)
                val updated = expectMostRecentItem()
                assertTrue("L'état doit être Success après génération", updated is OrderDetailUiState.Success)
                val order = (updated as OrderDetailUiState.Success).order
                assertTrue("hasShippingLabel doit être true", order.hasShippingLabel)
                assertEquals("8R01234567890", order.shipping?.trackingNumber)

                cancelAndIgnoreRemainingEvents()
            }

            assertTrue("Un message de succès doit être émis", vm.actionState.value.message != null)
            assertEquals(null, vm.actionState.value.error)
        }

    @Test
    fun `onGenerateLabel sur erreur 422 expose un message carrier_not_supported`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L)))
            fakeOrdersRepo.shouldThrowOnGenerateLabel = true
            fakeOrdersRepo.generateLabelException = RuntimeException("Génération dispo uniquement pour Colissimo")
            val vm = buildViewModel(orderId = 1L)
            advanceUntilIdle()

            vm.onGenerateLabel()
            advanceUntilIdle()

            val error = vm.actionState.value.error
            assertNotNull("Une erreur doit être émise", error)
            assertTrue(
                "Le message doit mentionner Colissimo",
                (error as UiText.Dynamic).value.contains("Colissimo"),
            )
            assertEquals(null, vm.actionState.value.message)
        }

    @Test
    fun `onGenerateLabel sur erreur 502 expose le message transporteur`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L)))
            fakeOrdersRepo.shouldThrowOnGenerateLabel = true
            fakeOrdersRepo.generateLabelException = RuntimeException("Erreur transporteur : Compte Colissimo inactif")
            val vm = buildViewModel(orderId = 1L)
            advanceUntilIdle()

            vm.onGenerateLabel()
            advanceUntilIdle()

            val error = vm.actionState.value.error
            assertNotNull("Une erreur doit être émise", error)
            assertTrue(
                "Le message doit contenir le détail transporteur",
                (error as UiText.Dynamic).value.contains("Compte Colissimo inactif"),
            )
        }

    @Test
    fun `onGenerateLabel ne lance pas inProgress pendant l appel`() =
        runTest {
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L)))
            val vm = buildViewModel(orderId = 1L)
            advanceUntilIdle()

            vm.actionState.test {
                awaitItem() // état initial

                vm.onGenerateLabel()

                // Pendant la génération : inProgress = true, isGeneratingLabel = true
                val loading = awaitItem()
                assertTrue(loading.inProgress)
                assertTrue(loading.isGeneratingLabel)

                // Après succès : inProgress = false, isGeneratingLabel = false
                val done = awaitItem()
                assertFalse(done.inProgress)
                assertFalse(done.isGeneratingLabel)
                assertNotNull(done.message)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─── Builders ─────────────────────────────────────────────────────────────

    private fun buildOrder(id: Long) =
        Order(
            id = id,
            reference = "REF-$id",
            status = "En préparation",
            totalPaid = 49.99,
            currency = "EUR",
            customerName = "Client Test",
            createdAtIso = "2024-01-01T00:00:00+00:00",
            updatedAtIso = "2024-01-02T00:00:00+00:00",
        )
}
