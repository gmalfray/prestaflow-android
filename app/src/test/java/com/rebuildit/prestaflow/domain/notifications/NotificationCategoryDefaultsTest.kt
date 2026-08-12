package com.rebuildit.prestaflow.domain.notifications

import com.rebuildit.prestaflow.fakes.FakeNotificationCategoriesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * État par défaut des catégories de notifications et abonnement aux sujets qui en découle
 * ([NotificationCategoriesRepository.enabledTopics]) — en particulier [NotificationCategory.REVIEW_PENDING],
 * seule catégorie désactivée par défaut (cf. `review_pending_alerts_enabled` faux en prod) : tant
 * qu'elle reste désactivée, son sujet n'est jamais envoyé au hub, donc aucune notification "avis à
 * modérer" ne peut arriver.
 */
class NotificationCategoryDefaultsTest {
    @Test
    fun `sav-message est activee par defaut, review-pending desactivee par defaut`() {
        assertTrue(NotificationCategory.SAV_MESSAGE.defaultEnabled)
        assertFalse(NotificationCategory.REVIEW_PENDING.defaultEnabled)
    }

    @Test
    fun `les cles correspondent exactement aux events emis par le connecteur`() {
        assertEquals("sav.message", NotificationCategory.SAV_MESSAGE.key)
        assertEquals("review.pending", NotificationCategory.REVIEW_PENDING.key)
    }

    @Test
    fun `par defaut le sujet sav-message est abonne mais pas review-pending`() =
        runTest {
            val repository = FakeNotificationCategoriesRepository()

            val topics = repository.enabledTopics()

            assertTrue("sav.message doit être abonné par défaut", topics.contains("sav.message"))
            assertFalse("review.pending ne doit pas être abonné par défaut", topics.contains("review.pending"))
        }

    @Test
    fun `activer review-pending l ajoute a l abonnement`() =
        runTest {
            val repository = FakeNotificationCategoriesRepository()

            repository.setCategory(NotificationCategory.REVIEW_PENDING, true)
            val topics = repository.enabledTopics()

            assertTrue(topics.contains("review.pending"))
        }

    @Test
    fun `desactiver sav-message le retire de l abonnement`() =
        runTest {
            val repository = FakeNotificationCategoriesRepository()

            repository.setCategory(NotificationCategory.SAV_MESSAGE, false)
            val topics = repository.enabledTopics()

            assertFalse(topics.contains("sav.message"))
        }
}
