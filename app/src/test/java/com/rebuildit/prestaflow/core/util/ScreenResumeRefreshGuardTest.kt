package com.rebuildit.prestaflow.core.util

import com.rebuildit.prestaflow.fakes.FakeTimeProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires JVM du [ScreenResumeRefreshGuard] — la SEULE implémentation du throttle et de la
 * garde anti-concurrence du rafraîchissement de reprise, partagée par tous les ViewModels d'écran
 * liste. Couvre exactement le contrat décrit dans sa KDoc.
 */
class ScreenResumeRefreshGuardTest {
    private lateinit var fakeTimeProvider: FakeTimeProvider
    private lateinit var guard: ScreenResumeRefreshGuard

    @Before
    fun setUp() {
        fakeTimeProvider = FakeTimeProvider()
        guard = ScreenResumeRefreshGuard(fakeTimeProvider)
    }

    @Test
    fun `shouldRefresh vrai au tout premier appel, aucun succes anterieur`() {
        assertTrue(
            "Sans repère de succès antérieur, un premier retour à l'écran doit déclencher un rafraîchissement",
            guard.shouldRefresh(isBusy = false),
        )
    }

    @Test
    fun `shouldRefresh declenche un refresh une fois le delai minimal depasse`() {
        guard.markRefreshSucceeded()
        fakeTimeProvider.advanceBy(ScreenResumeRefreshGuard.MIN_INTERVAL_MS)

        assertTrue(
            "Un retour à l'écran après le délai minimal doit déclencher un rafraîchissement",
            guard.shouldRefresh(isBusy = false),
        )
    }

    @Test
    fun `shouldRefresh est ignore avant expiration du delai minimal`() {
        guard.markRefreshSucceeded()
        fakeTimeProvider.advanceBy(ScreenResumeRefreshGuard.MIN_INTERVAL_MS - 1)

        assertFalse(
            "Un aller-retour rapide entre onglets ne doit pas déclencher un second appel réseau",
            guard.shouldRefresh(isBusy = false),
        )
    }

    @Test
    fun `shouldRefresh est ignore si un chargement est deja en cours, meme apres expiration du delai`() {
        guard.markRefreshSucceeded()
        fakeTimeProvider.advanceBy(ScreenResumeRefreshGuard.MIN_INTERVAL_MS)

        assertFalse(
            "Un chargement déjà en cours ne doit jamais être doublé par le rattrapage de reprise",
            guard.shouldRefresh(isBusy = true),
        )
    }

    @Test
    fun `un echec (markRefreshSucceeded non appele) n avance pas le repere`() {
        guard.markRefreshSucceeded()
        fakeTimeProvider.advanceBy(ScreenResumeRefreshGuard.MIN_INTERVAL_MS - 1)

        // Tentative de reprise : la logique appelante n'appelle PAS markRefreshSucceeded en cas
        // d'échec (simulé ici en ne l'appelant simplement pas).
        assertFalse(guard.shouldRefresh(isBusy = false))

        // Le repère reste celui du succès initial : encore 1 ms plus tard, le délai total depuis CE
        // succès est atteint, donc un nouveau rafraîchissement doit être autorisé — la tentative
        // précédente (non suivie d'un markRefreshSucceeded) n'a pas repoussé le repère.
        fakeTimeProvider.advanceBy(1)
        assertTrue(
            "Un échec ne doit jamais repousser le repère du dernier rafraîchissement réussi",
            guard.shouldRefresh(isBusy = false),
        )
    }

    @Test
    fun `markRefreshSucceeded avance le repere a l heure courante`() {
        fakeTimeProvider.advanceBy(500)
        guard.markRefreshSucceeded()

        // Juste après le succès, le délai n'est pas écoulé : ignoré.
        assertFalse(guard.shouldRefresh(isBusy = false))

        fakeTimeProvider.advanceBy(ScreenResumeRefreshGuard.MIN_INTERVAL_MS - 1)
        assertFalse(guard.shouldRefresh(isBusy = false))

        fakeTimeProvider.advanceBy(1)
        assertTrue(guard.shouldRefresh(isBusy = false))
    }
}
