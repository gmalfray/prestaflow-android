package com.rebuildit.prestaflow.ui.sav

import androidx.lifecycle.SavedStateHandle
import com.rebuildit.prestaflow.domain.sav.model.SavMessage
import com.rebuildit.prestaflow.domain.sav.model.SavMessageAuthor
import com.rebuildit.prestaflow.domain.sav.model.SavReplyResult
import com.rebuildit.prestaflow.domain.sav.model.SavThread
import com.rebuildit.prestaflow.domain.sav.model.SavThreadDetail
import com.rebuildit.prestaflow.domain.sav.model.SavThreadStatus
import com.rebuildit.prestaflow.fakes.FakeSavRepository
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
 * Tests du [SavThreadDetailViewModel] — en particulier [SavThreadDetailViewModel.sendReply], qui
 * ⚠️ déclenche un vrai envoi d'e-mail côté connecteur. La confirmation elle-même est de la
 * responsabilité de l'écran (cf. `SavThreadDetailScreen`) : ce ViewModel n'implémente PAS de
 * garde-fou d'appel — c'est voulu, et donc vérifié explicitement ici pour ne pas le perdre de vue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavThreadDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeSavRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeSavRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(threadId: Long = 154L) =
        SavThreadDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("threadId" to threadId)),
            savRepository = fakeRepo,
        )

    @Test
    fun `charge le fil au demarrage`() =
        runTest {
            fakeRepo.fetchThreadResult = buildDetail()

            val vm = buildViewModel()
            advanceUntilIdle()

            val state = vm.uiState.value as SavThreadDetailUiState.Success
            assertEquals(154L, state.detail.thread.id)
            assertEquals(2, state.detail.messages.size)
        }

    @Test
    fun `etat Error si le chargement echoue`() =
        runTest {
            fakeRepo.shouldThrowOnFetchThread = true

            val vm = buildViewModel()
            advanceUntilIdle()

            assertTrue(vm.uiState.value is SavThreadDetailUiState.Error)
        }

    @Test
    fun `updateStatus appelle le repository avec le bon statut`() =
        runTest {
            fakeRepo.fetchThreadResult = buildDetail()
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.updateStatus(SavThreadStatus.CLOSED)
            advanceUntilIdle()

            assertEquals(154L to SavThreadStatus.CLOSED, fakeRepo.updateStatusCalls.last())
        }

    @Test
    fun `updateStatus expose un message de succes`() =
        runTest {
            fakeRepo.fetchThreadResult = buildDetail()
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.updateStatus(SavThreadStatus.CLOSED)
            advanceUntilIdle()

            assertTrue(vm.actionState.value.message != null)
            assertNull(vm.actionState.value.error)
        }

    // ─── sendReply — ⚠️ envoie un vrai e-mail ───────────────────────────────

    @Test
    fun `sendReply transmet le message trimme au repository`() =
        runTest {
            fakeRepo.fetchThreadResult = buildDetail()
            fakeRepo.replyResult = buildReplyResult(emailSent = true)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.sendReply("  Votre colis arrive.  ")
            advanceUntilIdle()

            assertEquals(154L to "Votre colis arrive.", fakeRepo.replyCalls.last())
        }

    @Test
    fun `sendReply ignore un message vide sans appeler le repository`() =
        runTest {
            fakeRepo.fetchThreadResult = buildDetail()
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.sendReply("   ")
            advanceUntilIdle()

            assertTrue("Aucun appel ne doit être fait pour un message vide", fakeRepo.replyCalls.isEmpty())
        }

    @Test
    fun `sendReply avec email_sent=true confirme l envoi`() =
        runTest {
            fakeRepo.fetchThreadResult = buildDetail()
            fakeRepo.replyResult = buildReplyResult(emailSent = true)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.sendReply("Votre colis arrive.")
            advanceUntilIdle()

            assertTrue(vm.actionState.value.message != null)
        }

    @Test
    fun `sendReply avec email_sent=false expose quand meme un message sans erreur`() =
        runTest {
            fakeRepo.fetchThreadResult = buildDetail()
            fakeRepo.replyResult = buildReplyResult(emailSent = false)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.sendReply("Votre colis arrive.")
            advanceUntilIdle()

            // Message enregistré côté connecteur mais e-mail non envoyé : ce n'est pas un échec.
            assertTrue(vm.actionState.value.message != null)
            assertNull(vm.actionState.value.error)
        }

    @Test
    fun `sendReply expose une erreur en cas d echec reseau`() =
        runTest {
            fakeRepo.fetchThreadResult = buildDetail()
            fakeRepo.shouldThrowOnReply = true
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.sendReply("Votre colis arrive.")
            advanceUntilIdle()

            assertTrue(vm.actionState.value.error != null)
        }

    @Test
    fun `consumeActionFeedback reinitialise message et erreur`() =
        runTest {
            fakeRepo.fetchThreadResult = buildDetail()
            fakeRepo.replyResult = buildReplyResult(emailSent = true)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.sendReply("Votre colis arrive.")
            advanceUntilIdle()
            vm.consumeActionFeedback()

            assertNull(vm.actionState.value.message)
            assertNull(vm.actionState.value.error)
        }

    private fun buildDetail() =
        SavThreadDetail(
            thread =
                SavThread(
                    id = 154L,
                    status = SavThreadStatus.AWAITING_MERCHANT_REPLY,
                    unread = true,
                    toProcess = true,
                    customerId = 88L,
                    customerName = "Camille Martin",
                    customerEmail = "camille@example.com",
                    orderId = 4021L,
                    orderReference = "ABCDEF123",
                    lastMessageAtIso = "2026-08-09 16:42:00",
                    dateAddedIso = "2026-08-01 10:03:00",
                    dateUpdatedIso = "2026-08-09 16:42:00",
                ),
            messages =
                listOf(
                    SavMessage(
                        id = 512L,
                        author = SavMessageAuthor.CUSTOMER,
                        employeeName = null,
                        message = "Bonjour, ma commande n'est toujours pas arrivée.",
                        private = false,
                        read = true,
                        dateAddedIso = "2026-08-01 10:03:00",
                    ),
                    SavMessage(
                        id = 513L,
                        author = SavMessageAuthor.EMPLOYEE,
                        employeeName = "Marina",
                        message = "Bonjour, votre colis a été retardé.",
                        private = false,
                        read = true,
                        dateAddedIso = "2026-08-01 14:10:00",
                    ),
                ),
        )

    private fun buildReplyResult(emailSent: Boolean) =
        SavReplyResult(
            thread = buildDetail().thread.copy(status = SavThreadStatus.AWAITING_CUSTOMER_REPLY),
            message =
                SavMessage(
                    id = 514L,
                    author = SavMessageAuthor.EMPLOYEE,
                    employeeName = null,
                    message = "Votre colis arrive.",
                    private = false,
                    read = true,
                    dateAddedIso = "2026-08-11 09:00:00",
                ),
            emailSent = emailSent,
        )
}
