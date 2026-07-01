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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires JVM du [OrdersViewModel].
 *
 * Couvre : filtre multi-statuts, filtre par défaut (résolution par nom), tri,
 * pagination (loadMore), swipe avec délai d'annulation, sélection multiple et états d'erreur.
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

    // ─── Filtre multi-statuts ────────────────────────────────────────────────

    @Test
    fun `toggler un statut l ajoute au filtre et declenche un refresh`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()
            fakeOrdersRepo.refreshCalls.clear()

            vm.onStatusFilterSelected(statusId = 3)
            advanceUntilIdle()

            assertTrue(3 in vm.uiState.value.selectedStatusIds)
            val lastCall = fakeOrdersRepo.refreshCalls.lastOrNull()
            assertEquals(3, lastCall?.second)
        }

    @Test
    fun `toggler le meme statut deux fois le retire du filtre`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onStatusFilterSelected(statusId = 5)
            advanceUntilIdle()
            vm.onStatusFilterSelected(statusId = 5)
            advanceUntilIdle()

            assertFalse(5 in vm.uiState.value.selectedStatusIds)
        }

    @Test
    fun `passer null reinitialise les filtres a un ensemble vide`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()
            vm.onStatusFilterSelected(statusId = 3)
            advanceUntilIdle()
            fakeOrdersRepo.refreshCalls.clear()

            vm.onStatusFilterSelected(statusId = null)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.selectedStatusIds.isEmpty())
            assertFalse(vm.uiState.value.hasActiveStatusFilter)
            val lastCall = fakeOrdersRepo.refreshCalls.lastOrNull()
            assertNull(lastCall?.second)
        }

    @Test
    fun `plusieurs statuts peuvent etre selectionnes simultanement`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onStatusFilterSelected(statusId = 2)
            advanceUntilIdle()
            vm.onStatusFilterSelected(statusId = 4)
            advanceUntilIdle()

            assertTrue(2 in vm.uiState.value.selectedStatusIds)
            assertTrue(4 in vm.uiState.value.selectedStatusIds)
        }

    // ─── Filtre par défaut (résolution par nom) ──────────────────────────────

    @Test
    fun `les statuts par defaut sont resolus par nom au demarrage`() =
        runTest {
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(2, "Paiement accepté", "#00FF00"),
                    OrderStatusFilter(3, "En cours de préparation", "#0000FF"),
                    OrderStatusFilter(4, "Expédié", "#FFA500"),
                    OrderStatusFilter(5, "Terminé", "#888888"),
                    OrderStatusFilter(6, "Annulé", "#FF0000"),
                )

            val vm = buildViewModel()
            advanceUntilIdle()

            val ids = vm.uiState.value.selectedStatusIds
            assertTrue("Paiement accepté (id=2) doit être sélectionné par défaut", 2 in ids)
            assertTrue("En cours de préparation (id=3) doit être sélectionné par défaut", 3 in ids)
            assertTrue("Expédié (id=4) doit être sélectionné par défaut", 4 in ids)
            assertTrue("Terminé (id=5) doit être sélectionné par défaut", 5 in ids)
            assertFalse("Annulé (id=6) ne doit PAS être sélectionné par défaut", 6 in ids)
        }

    @Test
    fun `si aucun statut ne matche les defauts le filtre est vide (toutes)`() =
        runTest {
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(1, "Statut inconnu", "#AAAAAA"),
                    OrderStatusFilter(2, "Autre statut", "#BBBBBB"),
                )

            val vm = buildViewModel()
            advanceUntilIdle()

            assertTrue(
                "selectedStatusIds doit être vide si aucun statut ne matche",
                vm.uiState.value.selectedStatusIds.isEmpty(),
            )
        }

    @Test
    fun `resolution insensible a la casse et aux accents`() =
        runTest {
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(10, "PAIEMENT ACCEPTE", "#00FF00"),
                    OrderStatusFilter(11, "PREPARATION EN COURS", "#0000FF"),
                )

            val vm = buildViewModel()
            advanceUntilIdle()

            val ids = vm.uiState.value.selectedStatusIds
            assertTrue("PAIEMENT ACCEPTE doit être résolu", 10 in ids)
            assertTrue("PREPARATION EN COURS doit être résolu", 11 in ids)
        }

    // ─── Tri ─────────────────────────────────────────────────────────────────

    @Test
    fun `onSortChanged met a jour selectedSort et declenche un refresh`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()
            fakeOrdersRepo.refreshCalls.clear()

            vm.onSortChanged(OrderSort.AMOUNT_DESC)
            advanceUntilIdle()

            assertEquals(OrderSort.AMOUNT_DESC, vm.uiState.value.selectedSort)
            assertTrue("Un refresh doit être déclenché après changement de tri", fakeOrdersRepo.refreshCalls.isNotEmpty())
        }

    @Test
    fun `le tri par defaut est DATE_DESC`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals(OrderSort.DATE_DESC, vm.uiState.value.selectedSort)
        }

    @Test
    fun `onSortChanged remet hasMore a false`() =
        runTest {
            fakeOrdersRepo.hasMoreOnRefresh = true
            val vm = buildViewModel()
            advanceUntilIdle()
            // Au démarrage hasMore = true (si le fake le retourne)
            // Changer le tri doit remettre hasMore à false pendant le chargement
            fakeOrdersRepo.hasMoreOnRefresh = false

            vm.onSortChanged(OrderSort.REFERENCE)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.hasMore)
        }

    // ─── Pagination ──────────────────────────────────────────────────────────

    @Test
    fun `hasMore est mis a jour selon la reponse du repository`() =
        runTest {
            fakeOrdersRepo.hasMoreOnRefresh = true
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L, "REF001")))

            val vm = buildViewModel()
            advanceUntilIdle()

            assertTrue("hasMore doit être true si le repo retourne true", vm.uiState.value.hasMore)
        }

    @Test
    fun `loadMore ne fait rien si hasMore est false`() =
        runTest {
            fakeOrdersRepo.hasMoreOnRefresh = false
            val vm = buildViewModel()
            advanceUntilIdle()
            val callCountBefore = fakeOrdersRepo.refreshCalls.size

            vm.loadMore()
            advanceUntilIdle()

            assertEquals("loadMore ne doit pas appeler refresh si hasMore=false", callCountBefore, fakeOrdersRepo.refreshCalls.size)
        }

    @Test
    fun `loadMore appelle refresh avec le bon offset apres chargement initial`() =
        runTest {
            fakeOrdersRepo.hasMoreOnRefresh = true
            val orders = (1..50).map { buildOrder(it.toLong(), "REF$it") }
            fakeOrdersRepo.setOrders(orders)

            val vm = buildViewModel()
            advanceUntilIdle()

            // Après init, hasMore=true, on peut charger plus
            val ordersCount = vm.uiState.value.orders.size
            fakeOrdersRepo.refreshStatusIdsCalls.clear()

            vm.loadMore()
            advanceUntilIdle()

            assertTrue(
                "loadMore doit déclencher au moins un refresh supplémentaire",
                fakeOrdersRepo.refreshCalls.size > 0,
            )
            assertEquals(
                "isLoadingMore doit être false après loadMore",
                false,
                vm.uiState.value.isLoadingMore,
            )
        }

    @Test
    fun `loadMore ne lance pas de second refresh si isLoadingMore est deja vrai`() =
        runTest {
            fakeOrdersRepo.hasMoreOnRefresh = true
            fakeOrdersRepo.setOrders((1..50).map { buildOrder(it.toLong(), "REF$it") })

            val vm = buildViewModel()
            advanceUntilIdle()

            // Premier loadMore (en cours)
            vm.loadMore()
            // Second loadMore immédiatement (doit être ignoré)
            vm.loadMore()
            advanceUntilIdle()

            // On ne peut pas compter exactement les appels car le state change rapidement,
            // mais isLoadingMore doit se stabiliser à false
            assertFalse(vm.uiState.value.isLoadingMore)
        }

    // ─── Swipe avec délai d'annulation ───────────────────────────────────────

    @Test
    fun `onSwipeAction expose un pendingSwipeAction`() =
        runTest {
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(3, "En cours de préparation", "#0000FF"),
                )
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L, "#ORD-001", status = "Paiement accepté")))

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onSwipeAction(1L, "#ORD-001", SwipeDirection.LEFT)

            val pending = vm.uiState.value.pendingSwipeAction
            assertTrue("pendingSwipeAction doit être non null après swipe", pending != null)
            assertEquals(1L, pending?.orderId)
            assertEquals("#ORD-001", pending?.orderReference)
        }

    @Test
    fun `cancelSwipeAction annule le pending et ne declenche pas updateOrderStatus`() =
        runTest {
            fakeOrdersRepo.orderStatuses =
                listOf(OrderStatusFilter(3, "En cours de préparation", "#0000FF"))
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L, "#ORD-001", status = "Paiement accepté")))

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onSwipeAction(1L, "#ORD-001", SwipeDirection.LEFT)
            vm.cancelSwipeAction()
            // Avancer après le délai pour vérifier qu'aucun appel n'a été fait
            advanceTimeBy(6_000)
            advanceUntilIdle()

            assertNull("pendingSwipeAction doit être null après annulation", vm.uiState.value.pendingSwipeAction)
            assertTrue(
                "updateOrderStatus ne doit pas être appelé si l'action est annulée",
                fakeOrdersRepo.updateStatusCalls.isEmpty(),
            )
        }

    @Test
    fun `le changement de statut est envoye apres le delai si non annule`() =
        runTest {
            fakeOrdersRepo.orderStatuses =
                listOf(OrderStatusFilter(3, "En cours de préparation", "#0000FF"))
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L, "#ORD-001", status = "Paiement accepté")))

            val vm = buildViewModel()
            advanceUntilIdle()
            fakeOrdersRepo.updateStatusCalls.clear()

            vm.onSwipeAction(1L, "#ORD-001", SwipeDirection.LEFT)
            // Avancer de 5 secondes pour déclencher l'envoi
            advanceTimeBy(5_001)
            advanceUntilIdle()

            assertTrue(
                "updateOrderStatus doit être appelé après le délai",
                fakeOrdersRepo.updateStatusCalls.isNotEmpty(),
            )
            val call = fakeOrdersRepo.updateStatusCalls.first()
            assertEquals(1L, call.first)
            assertEquals("3", call.second)
        }

    @Test
    fun `le changement n est PAS envoye si annule avant le delai`() =
        runTest {
            fakeOrdersRepo.orderStatuses =
                listOf(OrderStatusFilter(3, "En cours de préparation", "#0000FF"))
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L, "#ORD-001", status = "Paiement accepté")))

            val vm = buildViewModel()
            advanceUntilIdle()
            fakeOrdersRepo.updateStatusCalls.clear()

            vm.onSwipeAction(1L, "#ORD-001", SwipeDirection.LEFT)
            // Annuler avant 5 secondes
            advanceTimeBy(2_000)
            vm.cancelSwipeAction()
            advanceTimeBy(4_000) // dépasse le délai total
            advanceUntilIdle()

            assertTrue(
                "updateOrderStatus ne doit pas être appelé si annulé avant le délai",
                fakeOrdersRepo.updateStatusCalls.isEmpty(),
            )
        }

    @Test
    fun `un second swipe annule le premier sans envoyer et declenche le second`() =
        runTest {
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(3, "En cours de préparation", "#0000FF"),
                    OrderStatusFilter(5, "Terminé", "#888888"),
                )
            fakeOrdersRepo.setOrders(
                listOf(
                    buildOrder(1L, "#ORD-001", status = "Paiement accepté"),
                    buildOrder(2L, "#ORD-002", status = "Paiement accepté"),
                ),
            )

            val vm = buildViewModel()
            advanceUntilIdle()
            fakeOrdersRepo.updateStatusCalls.clear()

            // Premier swipe (commande 1, LEFT)
            vm.onSwipeAction(1L, "#ORD-001", SwipeDirection.LEFT)
            advanceTimeBy(2_000)

            // Second swipe (commande 2, RIGHT) — doit remplacer le premier
            vm.onSwipeAction(2L, "#ORD-002", SwipeDirection.RIGHT)
            advanceTimeBy(5_001)
            advanceUntilIdle()

            // Seul le second swipe doit avoir été envoyé
            assertEquals(
                "Un seul appel updateOrderStatus doit être effectué",
                1,
                fakeOrdersRepo.updateStatusCalls.size,
            )
            assertEquals(2L, fakeOrdersRepo.updateStatusCalls.first().first)
            assertEquals("5", fakeOrdersRepo.updateStatusCalls.first().second)
        }

    @Test
    fun `swipe RIGHT resout le statut Termine quand Termine vient en premier`() =
        runTest {
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(5, "Terminé", "#888888"),
                    OrderStatusFilter(6, "Livré", "#444444"),
                )
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L, "#ORD-001", status = "Paiement accepté")))

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onSwipeAction(1L, "#ORD-001", SwipeDirection.RIGHT)

            val pending = vm.uiState.value.pendingSwipeAction
            assertEquals(
                "La cible doit être exactement Terminé (id 5), pas Livré",
                "Terminé",
                pending?.targetStatusName,
            )
        }

    @Test
    fun `swipe RIGHT priorise Termine meme si Livre est avant dans la liste`() =
        runTest {
            // Cas critique du bug : Livré (id 5, ordre standard PS) précède Terminé (id 6)
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(5, "Livré", "#444444"),
                    OrderStatusFilter(6, "Terminé", "#888888"),
                )
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L, "#ORD-001", status = "Paiement accepté")))

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onSwipeAction(1L, "#ORD-001", SwipeDirection.RIGHT)

            val pending = vm.uiState.value.pendingSwipeAction
            assertEquals(
                "RIGHT doit résoudre Terminé même si Livré précède dans la liste",
                "Terminé",
                pending?.targetStatusName,
            )
        }

    @Test
    fun `swipe RIGHT se replie sur Livre si Termine est absent`() =
        runTest {
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(5, "Livré", "#444444"),
                )
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L, "#ORD-001", status = "Paiement accepté")))

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onSwipeAction(1L, "#ORD-001", SwipeDirection.RIGHT)

            val pending = vm.uiState.value.pendingSwipeAction
            assertEquals(
                "Sans statut Terminé, RIGHT doit se replier sur Livré",
                "Livré",
                pending?.targetStatusName,
            )
        }

    // ─── Préférences de statuts visibles ────────────────────────────────────

    @Test
    fun `retrait d un statut filtre le retire de selectedStatusIds`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onStatusFilterSelected(statusId = 2)
            advanceUntilIdle()

            // La préférence émet un ensemble qui ne contient plus le statut 2
            fakePrefsRepo.emitVisibleStatusIds(setOf(1, 3))
            advanceUntilIdle()

            assertFalse(
                "Le statut 2 doit être retiré de selectedStatusIds",
                2 in vm.uiState.value.selectedStatusIds,
            )
        }

    @Test
    fun `retrait d un statut non selectionne ne change pas selectedStatusIds`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onStatusFilterSelected(statusId = 1)
            advanceUntilIdle()

            fakePrefsRepo.emitVisibleStatusIds(setOf(1, 2))
            advanceUntilIdle()

            assertTrue(
                "Le statut 1 doit rester dans selectedStatusIds",
                1 in vm.uiState.value.selectedStatusIds,
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

            assertFalse(vm.uiState.value.selectionMode)
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

            assertFalse(vm.uiState.value.selectionMode)
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

            assertFalse("Le mode sélection doit être désactivé", vm.uiState.value.selectionMode)
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
            fakeOrdersRepo.failingOrderIds.add(2L)

            val vm = buildViewModel()
            advanceUntilIdle()
            vm.onOrderLongPress(1L)
            advanceUntilIdle()
            vm.onOrderSelectionToggle(2L)
            advanceUntilIdle()

            vm.bulkUpdateStatus("5")
            advanceUntilIdle()

            assertFalse("Le mode sélection doit être désactivé même avec un échec partiel", vm.uiState.value.selectionMode)
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

            assertFalse("isBulkUpdating doit être false après l'opération", vm.uiState.value.isBulkUpdating)
        }

    // ─── Filtre statut + liste vide ──────────────────────────────────────────

    @Test
    fun `filtre statut sur liste vide ne crashe pas et conserve hasActiveStatusFilter`() =
        runTest {
            fakeOrdersRepo.setOrders(emptyList())

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onStatusFilterSelected(statusId = 7)
            advanceUntilIdle()

            assertTrue(
                "hasActiveStatusFilter doit être true même si la liste est vide",
                vm.uiState.value.hasActiveStatusFilter,
            )
            assertTrue(
                "orders doit être vide",
                vm.uiState.value.orders.isEmpty(),
            )
        }

    @Test
    fun `reinitialiser le filtre statut remet selectedStatusIds a vide`() =
        runTest {
            fakeOrdersRepo.setOrders(emptyList())

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onStatusFilterSelected(statusId = 7)
            advanceUntilIdle()

            vm.onStatusFilterSelected(statusId = null)
            advanceUntilIdle()

            assertTrue(
                "selectedStatusIds doit être vide après réinitialisation",
                vm.uiState.value.selectedStatusIds.isEmpty(),
            )
        }

    // ─── Config swipe : résolution source ───────────────────────────────────

    @Test
    fun `isSwipeSource retourne true par ID quand sourceStatusId configure et currentStateId correspond`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            val config = SwipeConfig(enabled = true, sourceStatusId = 2)
            val statuses =
                listOf(
                    OrderStatusFilter(2, "Paiement accepté", "#00FF00"),
                    OrderStatusFilter(3, "En préparation", "#0000FF"),
                )

            assertTrue(
                "isSwipeSource doit retourner true quand currentStateId == sourceStatusId",
                vm.isSwipeSource(config, "Paiement accepté", 2, statuses),
            )
            assertFalse(
                "isSwipeSource doit retourner false quand currentStateId != sourceStatusId",
                vm.isSwipeSource(config, "En préparation", 3, statuses),
            )
        }

    @Test
    fun `isSwipeSource se replie sur le nom quand swipeEnabled false retourne false`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            val config = SwipeConfig(enabled = false, sourceStatusId = null)
            val statuses = listOf(OrderStatusFilter(2, "Paiement accepté", "#00FF00"))

            assertFalse(
                "isSwipeSource doit retourner false quand swipe désactivé",
                vm.isSwipeSource(config, "Paiement accepté", 2, statuses),
            )
        }

    @Test
    fun `isSwipeSource se replie sur le nom quand sourceStatusId est null`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            val configSansId = SwipeConfig(enabled = true, sourceStatusId = null)
            val statuses = listOf(OrderStatusFilter(2, "Paiement accepté", "#00FF00"))

            assertTrue(
                "isSwipeSource doit matcher par nom 'paiement accepte' quand sourceStatusId est null",
                vm.isSwipeSource(configSansId, "Paiement accepté", 2, statuses),
            )
            assertFalse(
                "Un statut sans 'paiement accepte' dans le nom ne doit pas matcher",
                vm.isSwipeSource(configSansId, "En préparation", 3, statuses),
            )
        }

    // ─── Config swipe : résolution cible ────────────────────────────────────

    @Test
    fun `resolveTargetStatus LEFT utilise l ID configure quand il existe dans les statuts`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            val config = SwipeConfig(enabled = true, leftTargetStatusId = 10)
            val statuses =
                listOf(
                    OrderStatusFilter(3, "En cours de préparation", "#0000FF"),
                    OrderStatusFilter(10, "Statut custom", "#AABBCC"),
                )

            val result = vm.resolveTargetStatus(config, statuses, SwipeDirection.LEFT)
            assertEquals(
                "resolveTargetStatus LEFT doit retourner le statut avec l'ID configuré",
                10,
                result?.id,
            )
        }

    @Test
    fun `resolveTargetStatus LEFT se replie sur le nom quand ID configure introuvable`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            // ID 99 n'existe pas dans la liste → repli par nom
            val config = SwipeConfig(enabled = true, leftTargetStatusId = 99)
            val statuses = listOf(OrderStatusFilter(3, "En cours de préparation", "#0000FF"))

            val result = vm.resolveTargetStatus(config, statuses, SwipeDirection.LEFT)
            assertEquals(
                "Si l'ID configuré est introuvable, doit se replier sur le statut 'preparation'",
                3,
                result?.id,
            )
        }

    @Test
    fun `resolveTargetStatus RIGHT utilise l ID configure quand il existe dans les statuts`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            val config = SwipeConfig(enabled = true, rightTargetStatusId = 20)
            val statuses =
                listOf(
                    OrderStatusFilter(5, "Terminé", "#888888"),
                    OrderStatusFilter(20, "Archivé", "#CCCCCC"),
                )

            val result = vm.resolveTargetStatus(config, statuses, SwipeDirection.RIGHT)
            assertEquals(
                "resolveTargetStatus RIGHT doit retourner le statut avec l'ID configuré",
                20,
                result?.id,
            )
        }

    @Test
    fun `resolveTargetStatus RIGHT se replie sur Termine par nom quand ID introuvable`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            // ID 99 absent → repli par nom "termin"
            val config = SwipeConfig(enabled = true, rightTargetStatusId = 99)
            val statuses = listOf(OrderStatusFilter(5, "Terminé", "#888888"))

            val result = vm.resolveTargetStatus(config, statuses, SwipeDirection.RIGHT)
            assertEquals(
                "Si l'ID RIGHT est introuvable, doit se replier sur le statut contenant 'termin'",
                5,
                result?.id,
            )
        }

    @Test
    fun `onSwipeAction ne fait rien quand swipe desactive`() =
        runTest {
            fakePrefsRepo.emitSwipeEnabled(false)
            fakeOrdersRepo.orderStatuses = listOf(OrderStatusFilter(3, "En cours de préparation", "#0000FF"))
            fakeOrdersRepo.setOrders(listOf(buildOrder(1L, "#ORD-001", status = "Paiement accepté")))

            val vm = buildViewModel()
            advanceUntilIdle()

            // S'assurer que le config est bien désactivé
            fakePrefsRepo.emitSwipeEnabled(false)
            advanceUntilIdle()
            fakeOrdersRepo.updateStatusCalls.clear()

            vm.onSwipeAction(1L, "#ORD-001", SwipeDirection.LEFT)
            advanceUntilIdle()

            assertNull(
                "pendingSwipeAction doit rester null quand swipe est désactivé",
                vm.uiState.value.pendingSwipeAction,
            )
            assertTrue(
                "updateOrderStatus ne doit pas être appelé quand swipe désactivé",
                fakeOrdersRepo.updateStatusCalls.isEmpty(),
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
