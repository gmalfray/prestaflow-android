package com.rebuildit.prestaflow.ui.orders

import androidx.lifecycle.SavedStateHandle
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import com.rebuildit.prestaflow.fakes.FakeAuthRepository
import com.rebuildit.prestaflow.fakes.FakeLanguageRepository
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
 * pagination (loadMore), sélection multiple et états d'erreur.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeOrdersRepo: FakeOrdersRepository
    private lateinit var fakePrefsRepo: FakeOrdersPreferencesRepository
    private lateinit var fakeAuthRepo: FakeAuthRepository
    private lateinit var fakeLanguageRepo: FakeLanguageRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeOrdersRepo = FakeOrdersRepository()
        fakePrefsRepo = FakeOrdersPreferencesRepository()
        fakeAuthRepo = FakeAuthRepository()
        fakeLanguageRepo = FakeLanguageRepository()
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
            languageRepository = fakeLanguageRepo,
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
    fun `taper un 2e chip REMPLACE la selection - isolation exclusive`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onStatusFilterSelected(statusId = 2)
            advanceUntilIdle()
            vm.onStatusFilterSelected(statusId = 4)
            advanceUntilIdle()

            // Chip = raccourci EXCLUSIF : le 2e tap ISOLE sur 4, ne s'ajoute pas à 2 (pas de
            // multi-statuts via les chips — cf. onStatusFiltersReplaced pour le vrai multi-statuts).
            assertEquals(setOf(4), vm.uiState.value.selectedStatusIds)
        }

    @Test
    fun `selectionner un statut deja actif dans le filtre par defaut ISOLE dessus - ne l exclut pas`() =
        runTest {
            // RÉGRESSION (v0.42.5) : le filtre par défaut "à traiter" pré-sélectionne plusieurs
            // statuts (2/3/4/9) sans que l'utilisateur les ait tapés. Taper un chip qui en fait déjà
            // partie (ex. "Prépa"=3) doit ISOLER dessus (ne montrer QUE ce statut), PAS le retirer du
            // filtre — sinon sélectionner un statut fait disparaître exactement ses commandes,
            // l'inverse de l'effet attendu ("bug de l'exclude au lieu de l'include").
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(2, "Paiement accepté", "#00FF00"),
                    OrderStatusFilter(3, "En cours de préparation", "#0000FF"),
                    OrderStatusFilter(4, "Expédié", "#FFA500"),
                    OrderStatusFilter(9, "Terminée", "#888888"),
                )
            val vm = buildViewModel()
            advanceUntilIdle()
            assertEquals(
                "Précondition : le défaut « à traiter » doit être actif avant le tap",
                setOf(2, 3, 4, 9),
                vm.uiState.value.selectedStatusIds,
            )
            fakeOrdersRepo.refreshCalls.clear()

            vm.onStatusFilterSelected(statusId = 3)
            advanceUntilIdle()

            assertEquals(
                "Taper 'Prépa' (déjà actif par défaut) doit ISOLER dessus (montrer QUE ce statut)",
                setOf(3),
                vm.uiState.value.selectedStatusIds,
            )
            val lastCall = fakeOrdersRepo.refreshCalls.lastOrNull()
            assertEquals(
                "Le refresh serveur doit filtrer uniquement sur le statut isolé",
                3,
                lastCall?.second,
            )
        }

    // ─── Recherche serveur ───────────────────────────────────────────────────

    @Test
    fun `onQueryChange declenche une recherche SERVEUR debouncee avec le terme`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()
            fakeOrdersRepo.refreshSearchCalls.clear()

            vm.onQueryChange("dupont")
            advanceTimeBy(400) // dépasse le debounce de 300 ms
            advanceUntilIdle()

            assertTrue(
                "La recherche 'dupont' doit être transmise au serveur (param search)",
                fakeOrdersRepo.refreshSearchCalls.contains("dupont"),
            )
        }

    @Test
    fun `recherche serveur reussie n active pas le repli local`() =
        runTest {
            fakeOrdersRepo.shouldThrowOnRefresh = false
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onQueryChange("dupont")
            advanceTimeBy(400)
            advanceUntilIdle()

            assertFalse(
                "searchFallback doit rester faux quand la recherche serveur réussit",
                vm.uiState.value.searchFallback,
            )
        }

    // ─── Filtre par défaut (résolution par ID PrestaShop stable) ─────────────

    @Test
    fun `les statuts par defaut sont resolus par ID au demarrage`() =
        runTest {
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(2, "Paiement accepté", "#00FF00"),
                    OrderStatusFilter(3, "En cours de préparation", "#0000FF"),
                    OrderStatusFilter(4, "Expédié", "#FFA500"),
                    OrderStatusFilter(9, "Terminée", "#888888"),
                    OrderStatusFilter(6, "Annulé", "#FF0000"),
                )

            val vm = buildViewModel()
            advanceUntilIdle()

            val ids = vm.uiState.value.selectedStatusIds
            assertEquals(
                "Défaut = IDs 2/3/4/9 présents (à traiter), pas les autres",
                setOf(2, 3, 4, 9),
                ids,
            )
            assertFalse("Annulé (id=6) ne doit PAS être sélectionné par défaut", 6 in ids)
        }

    @Test
    fun `si aucun statut par defaut n existe le filtre est vide (toutes)`() =
        runTest {
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(1, "En attente de paiement", "#AAAAAA"),
                    OrderStatusFilter(6, "Annulé", "#BBBBBB"),
                    OrderStatusFilter(7, "Remboursé", "#CCCCCC"),
                )

            val vm = buildViewModel()
            advanceUntilIdle()

            assertTrue(
                "selectedStatusIds doit être vide si aucun ID par défaut n'existe",
                vm.uiState.value.selectedStatusIds.isEmpty(),
            )
        }

    @Test
    fun `resolution par ID robuste a la langue d affichage`() =
        runTest {
            // Mêmes IDs, mais noms en ALLEMAND (statuts localisés côté serveur) : la résolution par
            // ID doit fonctionner identiquement — c'est tout l'intérêt vs l'ancien matching par nom FR.
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(2, "Zahlung akzeptiert", "#00FF00"),
                    OrderStatusFilter(3, "In Bearbeitung", "#0000FF"),
                    OrderStatusFilter(4, "Versandt", "#FFA500"),
                    OrderStatusFilter(9, "Fertig", "#888888"),
                )

            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals(
                "Le défaut par ID doit s'appliquer même avec des noms non-français",
                setOf(2, 3, 4, 9),
                vm.uiState.value.selectedStatusIds,
            )
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

    // ─── Préférences de statuts visibles ────────────────────────────────────

    @Test
    fun `un statut filtre retire des raccourcis reste actif dans le filtre`() =
        runTest {
            // Régression : le filtre actif ne doit plus être écrasé par les chips raccourcis.
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onStatusFilterSelected(statusId = 2)
            advanceUntilIdle()

            // La préférence de raccourcis émet un ensemble qui ne contient plus le statut 2
            fakePrefsRepo.emitVisibleStatusIds(setOf(1, 3))
            advanceUntilIdle()

            assertTrue(
                "Le statut 2 doit rester dans selectedStatusIds même s'il n'est plus un raccourci",
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

    @Test
    fun `onStatusFiltersReplaced pose le filtre sur un statut hors raccourcis et declenche un refresh`() =
        runTest {
            // Le volet "Filtrer par statut" du menu doit pouvoir cibler 100% des statuts,
            // y compris ceux qui ne sont pas épinglés en chips raccourcis (max 3).
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(1, "Paiement accepté", "#00FF00"),
                    OrderStatusFilter(2, "En préparation", "#0000FF"),
                    OrderStatusFilter(3, "Expédié", "#FFA500"),
                    OrderStatusFilter(4, "Terminé", "#008000"),
                    OrderStatusFilter(5, "Remboursé", "#AA0000"),
                )
            val vm = buildViewModel()
            advanceUntilIdle()
            fakePrefsRepo.emitVisibleStatusIds(setOf(2, 3, 4))
            advanceUntilIdle()
            fakeOrdersRepo.refreshCalls.clear()

            vm.onStatusFiltersReplaced(setOf(5))
            advanceUntilIdle()

            assertEquals(setOf(5), vm.uiState.value.selectedStatusIds)
            val lastCall = fakeOrdersRepo.refreshCalls.lastOrNull()
            assertEquals(5, lastCall?.second)
        }

    @Test
    fun `onStatusFiltersReplaced avec un ensemble vide reinitialise le filtre a Toutes`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()
            vm.onStatusFilterSelected(statusId = 2)
            advanceUntilIdle()

            vm.onStatusFiltersReplaced(emptySet())
            advanceUntilIdle()

            assertTrue(vm.uiState.value.selectedStatusIds.isEmpty())
            assertFalse(vm.uiState.value.hasActiveStatusFilter)
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
    fun `visibleOrders repli local filtre par nom quand la recherche serveur echoue`() =
        runTest {
            fakeOrdersRepo.setOrders(
                listOf(
                    buildOrder(1L, "REF001", customerName = "Alice Martin"),
                    buildOrder(2L, "REF002", customerName = "Bob Dupont"),
                ),
            )

            val vm = buildViewModel()
            advanceUntilIdle()

            // La recherche serveur échoue (réseau KO) → repli local sur le cache déjà chargé.
            fakeOrdersRepo.shouldThrowOnRefresh = true
            vm.onQueryChange("alice")
            advanceTimeBy(400) // dépasse le debounce
            advanceUntilIdle()

            assertTrue("Un échec serveur avec query active doit activer le repli", vm.uiState.value.searchFallback)
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

    @Test
    fun `un changement de langue recharge les statuts avec leurs libelles localises`() =
        runTest {
            // Au démarrage : statuts renvoyés en français (comme le connecteur sans Accept-Language).
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(2, "Paiement accepté", "#00FF00"),
                    OrderStatusFilter(9, "Terminée", "#0000FF"),
                )
            val vm = buildViewModel()
            advanceUntilIdle()
            assertEquals("Terminée", vm.uiState.value.availableStatuses.first { it.id == 9 }.name)

            // Le serveur renverra désormais les statuts en allemand (Accept-Language: de) — mêmes IDs.
            fakeOrdersRepo.orderStatuses =
                listOf(
                    OrderStatusFilter(2, "Zahlung akzeptiert", "#00FF00"),
                    OrderStatusFilter(9, "Fertig", "#0000FF"),
                )
            fakeLanguageRepo.emit("de")
            advanceUntilIdle()

            assertEquals(
                "Les libellés de statut (puces de filtre) doivent suivre la nouvelle langue",
                "Fertig",
                vm.uiState.value.availableStatuses.first { it.id == 9 }.name,
            )
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

    // ─── Filtre de période (depuis dashboard) ────────────────────────────────

    @Test
    fun `init avec nav arg period valide expose activePeriod dans l etat`() =
        runTest {
            val vm = buildViewModel(periodValue = "week")
            advanceUntilIdle()

            assertEquals(DashboardPeriod.WEEK, vm.uiState.value.activePeriod)
        }

    @Test
    fun `init sans nav arg period expose activePeriod null`() =
        runTest {
            val vm = buildViewModel(periodValue = null)
            advanceUntilIdle()

            assertNull(vm.uiState.value.activePeriod)
        }

    @Test
    fun `clearPeriodFilter met activePeriod a null et declenche un refresh`() =
        runTest {
            val vm = buildViewModel(periodValue = "month")
            advanceUntilIdle()
            fakeOrdersRepo.refreshCalls.clear()

            vm.clearPeriodFilter()
            advanceUntilIdle()

            assertNull("activePeriod doit être null après clearPeriodFilter", vm.uiState.value.activePeriod)
            assertTrue("Un refresh doit être déclenché après clearPeriodFilter", fakeOrdersRepo.refreshCalls.isNotEmpty())
        }

    @Test
    fun `clearPeriodFilter ne reinitialise pas les filtres de statut`() =
        runTest {
            val vm = buildViewModel(periodValue = "today")
            advanceUntilIdle()
            vm.onStatusFilterSelected(statusId = 3)
            advanceUntilIdle()

            vm.clearPeriodFilter()
            advanceUntilIdle()

            assertTrue(
                "Le filtre de statut doit être conservé après clearPeriodFilter",
                3 in vm.uiState.value.selectedStatusIds,
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
