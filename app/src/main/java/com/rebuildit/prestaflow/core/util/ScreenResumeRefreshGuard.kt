package com.rebuildit.prestaflow.core.util

import javax.inject.Inject

/**
 * Garde-fou du rafraîchissement automatique déclenché au retour au premier plan d'un écran liste
 * (typiquement `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)` posé sur une Route Compose, cf.
 * `OrdersRoute`/`OrdersTwoPaneRoute`). **Unique implémentation** du couple throttle + anti-
 * concurrence dans le projet — factorisée pour que les ViewModels d'écran liste (Commandes,
 * Clients, Produits, SAV, Avis, Paniers) partagent exactement la même règle plutôt que de laisser
 * diverger cinq copies du même calcul.
 *
 * Règles imposées par [shouldRefresh] :
 * - jamais si un chargement de CET écran est déjà en cours (paramètre [shouldRefresh], l'appelant
 *   est seul à connaître la forme de son propre état de chargement) ;
 * - jamais avant [MIN_INTERVAL_MS] depuis le dernier rafraîchissement **réussi** — le repère
 *   n'avance QUE via [markRefreshSucceeded], jamais sur une simple tentative (cf. sa Javadoc) : un
 *   aller-retour rapide entre deux onglets ne doit pas déclencher un appel réseau à chaque fois,
 *   mais un échec réseau au retour ne doit jamais geler indéfiniment le rattrapage automatique.
 *
 * Le geste manuel de l'utilisateur (tirer-pour-rafraîchir, bouton actualiser…) ne doit **jamais**
 * passer par ce garde-fou : c'est un geste explicite, toujours immédiat — l'appelant déclenche son
 * rafraîchissement normal directement, sans consulter [shouldRefresh].
 *
 * Une instance par écran (pas de partage) : classe injectable Hilt via un constructeur `@Inject`
 * sans `@Singleton` ni module dédié — Dagger fournit une instance neuve à chaque point d'injection
 * dès que [TimeProvider] est lié (cf. `AppModule.provideTimeProvider`), donc chaque ViewModel
 * obtient son propre repère de dernier rafraîchissement réussi, sans interférence entre écrans.
 */
class ScreenResumeRefreshGuard
    @Inject
    constructor(
        private val timeProvider: TimeProvider,
    ) {
        /**
         * Horodatage ([TimeProvider.nowMillis]) du dernier rafraîchissement réussi, `null` tant
         * qu'aucun n'a encore abouti depuis la création de cette instance (jamais rafraîchi, ou
         * seulement des tentatives en échec jusqu'ici).
         */
        private var lastSuccessfulRefreshAtMs: Long? = null

        /**
         * Vrai si un rafraîchissement automatique de reprise doit être déclenché maintenant.
         *
         * @param isBusy vrai si un chargement de cet écran est déjà en cours — jamais deux
         * rafraîchissements simultanés.
         */
        fun shouldRefresh(isBusy: Boolean): Boolean {
            if (isBusy) return false
            val last = lastSuccessfulRefreshAtMs
            return last == null || timeProvider.nowMillis() - last >= MIN_INTERVAL_MS
        }

        /**
         * À appeler UNIQUEMENT après un rafraîchissement RÉUSSI (jamais après un échec) : avance
         * le repère utilisé par [shouldRefresh]. Un échec ne doit jamais repousser ce repère — cf.
         * KDoc de la classe.
         */
        fun markRefreshSucceeded() {
            lastSuccessfulRefreshAtMs = timeProvider.nowMillis()
        }

        companion object {
            /**
             * Délai minimal (ms) entre deux rafraîchissements automatiques de reprise. 1 minute :
             * assez court pour que les changements survenus pendant qu'on était sur un autre onglet
             * apparaissent vite en revenant, assez long pour qu'un aller-retour rapide entre deux
             * onglets (quelques secondes, geste courant en navigation par onglets) ne déclenche pas
             * un appel réseau à chaque fois.
             */
            const val MIN_INTERVAL_MS = 60_000L
        }
    }
