package com.rebuildit.prestaflow.data.sav

import app.cash.turbine.test
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.remote.dto.SavCustomerDto
import com.rebuildit.prestaflow.data.remote.dto.SavMessageDto
import com.rebuildit.prestaflow.data.remote.dto.SavOrderDto
import com.rebuildit.prestaflow.data.remote.dto.SavReplyResponseDto
import com.rebuildit.prestaflow.data.remote.dto.SavThreadDetailResponseDto
import com.rebuildit.prestaflow.data.remote.dto.SavThreadDto
import com.rebuildit.prestaflow.data.remote.dto.SavThreadListResponseDto
import com.rebuildit.prestaflow.domain.sav.model.SavThreadStatus
import com.rebuildit.prestaflow.fakes.FakePrestaFlowApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavRepositoryImplTest {
    private lateinit var fakeApi: FakePrestaFlowApi
    private lateinit var repository: SavRepositoryImpl

    @Before
    fun setUp() {
        fakeApi = FakePrestaFlowApi()
        repository =
            SavRepositoryImpl(
                api = fakeApi,
                networkErrorMapper = NetworkErrorMapper(),
                ioDispatcher = UnconfinedTestDispatcher(),
            )
    }

    // ─── unreadThreadCount / refreshUnreadCount ──────────────────────────────

    @Test
    fun `unreadThreadCount emet 0 avant tout refresh`() =
        runTest {
            repository.unreadThreadCount.test {
                assertEquals(0, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `refreshUnreadCount compte les fils marques unread dans la premiere page`() =
        runTest {
            fakeApi.savThreadsResponse =
                SavThreadListResponseDto(
                    threads =
                        listOf(
                            buildThreadDto(id = 1L, unread = true),
                            buildThreadDto(id = 2L, unread = false),
                            buildThreadDto(id = 3L, unread = true),
                        ),
                )

            repository.refreshUnreadCount()

            repository.unreadThreadCount.test {
                assertEquals(2, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `refreshUnreadCount conserve la derniere valeur connue en cas d echec reseau`() =
        runTest {
            fakeApi.savThreadsResponse = SavThreadListResponseDto(threads = listOf(buildThreadDto(id = 1L, unread = true)))
            repository.refreshUnreadCount()

            fakeApi.savThreadsException = RuntimeException("Erreur réseau simulée")
            repository.refreshUnreadCount()

            repository.unreadThreadCount.test {
                assertEquals(1, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─── fetchThreads ─────────────────────────────────────────────────────────

    @Test
    fun `fetchThreads transmet le statut en filtre quand fourni`() =
        runTest {
            fakeApi.savThreadsResponse = SavThreadListResponseDto(threads = emptyList())

            repository.fetchThreads(status = SavThreadStatus.CLOSED, limit = 20, offset = 0)

            assertEquals("closed", fakeApi.lastSavThreadsFilters?.get("status"))
        }

    @Test
    fun `fetchThreads n envoie pas de filtre status quand null`() =
        runTest {
            fakeApi.savThreadsResponse = SavThreadListResponseDto(threads = emptyList())

            repository.fetchThreads(status = null, limit = 20, offset = 0)

            assertFalse("Aucun filtre 'status' ne doit être transmis", fakeApi.lastSavThreadsFilters?.containsKey("status") == true)
        }

    @Test
    fun `fetchThreads mappe la pagination en page domaine`() =
        runTest {
            fakeApi.savThreadsResponse =
                SavThreadListResponseDto(
                    threads = listOf(buildThreadDto(id = 1L)),
                    pagination = com.rebuildit.prestaflow.data.remote.dto.PaginationDto(hasNext = true, nextOffset = 20),
                )

            val page = repository.fetchThreads()

            assertEquals(1, page.threads.size)
            assertTrue(page.hasNext)
            assertEquals(20, page.nextOffset)
        }

    // ─── fetchThread ──────────────────────────────────────────────────────────

    @Test
    fun `fetchThread mappe le fil et les messages chronologiquement`() =
        runTest {
            fakeApi.savThreadDetailResponse =
                SavThreadDetailResponseDto(
                    thread = buildThreadDto(id = 154L),
                    messages =
                        listOf(
                            SavMessageDto(id = 512L, author = "customer", message = "Bonjour"),
                            SavMessageDto(id = 513L, author = "employee", employeeName = "Marina", message = "Bonjour à vous"),
                        ),
                )

            val detail = repository.fetchThread(154L)

            assertEquals(154L, detail.thread.id)
            assertEquals(2, detail.messages.size)
            assertEquals("Marina", detail.messages[1].employeeName)
        }

    // ─── updateThreadStatus ───────────────────────────────────────────────────

    @Test
    fun `updateThreadStatus transmet la valeur API du statut`() =
        runTest {
            repository.updateThreadStatus(154L, SavThreadStatus.CLOSED)

            assertEquals(154L to "closed", fakeApi.updateSavThreadStatusCalls.last())
        }

    @Test(expected = RuntimeException::class)
    fun `updateThreadStatus propage l erreur reseau`() =
        runTest {
            fakeApi.updateSavThreadStatusException = RuntimeException("Erreur réseau simulée")

            repository.updateThreadStatus(154L, SavThreadStatus.CLOSED)
        }

    // ─── replyToThread ────────────────────────────────────────────────────────

    @Test
    fun `replyToThread transmet le message et mappe le resultat`() =
        runTest {
            fakeApi.replySavThreadResponse =
                SavReplyResponseDto(
                    thread = buildThreadDto(id = 154L, status = "pending1"),
                    message = SavMessageDto(id = 514L, author = "employee", message = "Votre colis arrive"),
                    emailSent = true,
                )

            val result = repository.replyToThread(154L, "Votre colis arrive")

            assertEquals(154L to "Votre colis arrive", fakeApi.replySavThreadCalls.last())
            assertTrue(result.emailSent)
            assertEquals(SavThreadStatus.AWAITING_CUSTOMER_REPLY, result.thread.status)
        }

    // ─── Builders ─────────────────────────────────────────────────────────────

    private fun buildThreadDto(
        id: Long,
        status: String = "open",
        unread: Boolean = false,
    ) = SavThreadDto(
        id = id,
        status = status,
        unread = unread,
        customer = SavCustomerDto(id = 88L, name = "Camille Martin", email = "camille@example.com"),
        order = SavOrderDto(id = 4021L, reference = "ABCDEF123"),
        lastMessageAt = "2026-08-09 16:42:00",
        dateAdd = "2026-08-01 10:03:00",
        dateUpd = "2026-08-09 16:42:00",
    )
}
