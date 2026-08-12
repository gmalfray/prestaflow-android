package com.rebuildit.prestaflow.ui.clients

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
import com.rebuildit.prestaflow.navigation.formatBadgeCount
import com.rebuildit.prestaflow.ui.reviews.ReviewsRoute
import com.rebuildit.prestaflow.ui.sav.SavRoute
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme

/**
 * Hôte de la destination [com.rebuildit.prestaflow.navigation.AppDestination.Clients] :
 * sous-navigation Clients/SAV/Avis calculée à partir des capacités de la boutique active — cf.
 * étude `rebuild-it/docs/app-avis-sav.md`. `AppDestination` reste un enum à 6 entrées ; c'est
 * uniquement CETTE sous-navigation qui varie.
 *
 * [ClientsRoute] (liste clients existante) est monté tel quel dans la section [ClientsSection.CLIENTS]
 * : il continue de lire ses arguments de navigation (`filter`, `created_from`) via le
 * [androidx.lifecycle.SavedStateHandle] de la même entrée de back-stack, inchangé par cet hôte.
 */
@Composable
fun ClientsTabsRoute(
    onClientClick: (Long) -> Unit = {},
    onAddShop: () -> Unit = {},
    onSavThreadClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ClientsTabsViewModel = hiltViewModel(),
) {
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val unreadSavCount by viewModel.unreadSavCount.collectAsStateWithLifecycle(initialValue = 0)
    val pendingReviewCount by viewModel.pendingReviewCount.collectAsStateWithLifecycle(initialValue = 0)

    ClientsTabsScreen(
        capabilities = capabilities,
        unreadSavCount = unreadSavCount,
        pendingReviewCount = pendingReviewCount,
        modifier = modifier,
        clientsContent = { contentModifier ->
            ClientsRoute(
                onClientClick = onClientClick,
                onAddShop = onAddShop,
                modifier = contentModifier,
            )
        },
        savContent = { contentModifier -> SavRoute(onThreadClick = onSavThreadClick, modifier = contentModifier) },
        reviewsContent = { contentModifier -> ReviewsRoute(modifier = contentModifier) },
    )
}

@Suppress("LongParameterList")
@Composable
fun ClientsTabsScreen(
    capabilities: ShopCapabilities,
    modifier: Modifier = Modifier,
    // Répartition du chiffre agrégé de la pastille du shell (cf.
    // com.rebuildit.prestaflow.ui.root.RootViewModel.clientsBadgeCount) sur les sous-onglets
    // concernés : dès l'entrée dans l'onglet, on doit voir SANS naviguer si la pastille du shell
    // parlait du SAV, des avis, ou des deux — c'est le défaut remonté (cf. commentaire sur la
    // TabRow ci-dessous).
    unreadSavCount: Int = 0,
    pendingReviewCount: Int = 0,
    clientsContent: @Composable (Modifier) -> Unit = {},
    savContent: @Composable (Modifier) -> Unit = {},
    reviewsContent: @Composable (Modifier) -> Unit = {},
) {
    val sections = ClientsSection.visibleSections(capabilities)
    var selected by rememberSaveable { mutableStateOf(ClientsSection.CLIENTS) }

    // Filet de sécurité : si la section affichée disparaît (capacité repassée à false pendant que
    // l'utilisateur y était, ex. désinstallation du module en cours de session), on retombe sur
    // Clients plutôt que de garder un onglet fantôme sélectionné.
    LaunchedEffect(sections) {
        if (selected !in sections) selected = ClientsSection.CLIENTS
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Toujours au moins 2 sections (Clients + SAV, natif) : la barre d'onglets est donc
        // toujours pertinente — seule la 3ᵉ section (Avis) apparaît ou non selon la capacité.
        val selectedIndex = sections.indexOf(selected).coerceAtLeast(0)
        TabRow(selectedTabIndex = selectedIndex) {
            sections.forEach { section ->
                // Compteur porté par CE sous-onglet — Clients n'en a pas (pas de file d'attente
                // naturelle). C'est la répartition qui manquait : la pastille du shell annonçait un
                // total sans dire lequel de SAV/Avis il concernait (retour Greg).
                val badgeLabel =
                    when (section) {
                        ClientsSection.CLIENTS -> null
                        ClientsSection.SAV -> formatBadgeCount(unreadSavCount)
                        ClientsSection.REVIEWS -> formatBadgeCount(pendingReviewCount)
                    }
                val badgeDescription =
                    when (section) {
                        ClientsSection.SAV ->
                            stringResource(R.string.clients_badge_unread_sav_content_description, unreadSavCount)
                        ClientsSection.REVIEWS ->
                            stringResource(R.string.clients_badge_pending_reviews_content_description, pendingReviewCount)
                        ClientsSection.CLIENTS -> null
                    }
                Tab(
                    selected = section == selected,
                    onClick = { selected = section },
                    text = {
                        ClientsTabLabel(
                            text = stringResource(section.labelRes),
                            badgeLabel = badgeLabel,
                            badgeDescription = badgeDescription,
                        )
                    },
                )
            }
        }

        val contentModifier = Modifier.weight(1f)
        when (selected) {
            ClientsSection.CLIENTS -> clientsContent(contentModifier)
            ClientsSection.SAV -> savContent(contentModifier)
            ClientsSection.REVIEWS -> reviewsContent(contentModifier)
        }
    }
}

/**
 * Libellé d'un sous-onglet, avec pastille compacte optionnelle. `badgeLabel` null/vide → pas de
 * pastille (jamais un badge "0", cf. [formatBadgeCount]).
 *
 * Présentation retenue : [BadgedBox] superpose la pastille en coin plutôt que de l'ajouter en
 * ligne à côté du texte — sur une `TabRow` à 3 entrées en largeur téléphone (ex. Galaxy A34,
 * ~360dp → 120dp/onglet), un `Text` + pastille EN LIGNE ("SAV" + espace + "88") tronque ou force un
 * retour à la ligne dès que le compteur dépasse un chiffre. La pastille en overlay ne consomme
 * aucune largeur supplémentaire dans le `Row` de la `TabRow` : elle tient donc systématiquement,
 * quel que soit le nombre de chiffres affichés (jusqu'à "99+", cf. [formatBadgeCount]).
 */
@Composable
private fun ClientsTabLabel(
    text: String,
    badgeLabel: String?,
    badgeDescription: String?,
) {
    if (badgeLabel == null) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
        return
    }
    BadgedBox(
        badge = {
            Badge(
                modifier =
                    Modifier.semantics {
                        this.contentDescription = badgeDescription ?: badgeLabel
                    },
            ) {
                Text(badgeLabel)
            }
        },
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Clients — SAV natif seul (pas de module avis)")
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewClientsTabsSavOnly() {
    PrestaFlowTheme {
        ClientsTabsScreen(capabilities = ShopCapabilities(sav = true, reviews = false))
    }
}

@Preview(showBackground = true, name = "Clients — SAV + Avis (module rbreviews installé)")
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewClientsTabsWithReviews() {
    PrestaFlowTheme {
        ClientsTabsScreen(capabilities = ShopCapabilities(sav = true, reviews = true))
    }
}

@Preview(showBackground = true, name = "Clients — pastilles SAV + Avis (cas remonté par Greg)")
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewClientsTabsWithBadges() {
    PrestaFlowTheme {
        ClientsTabsScreen(
            capabilities = ShopCapabilities(sav = true, reviews = true),
            unreadSavCount = 88,
            pendingReviewCount = 3,
        )
    }
}

@Preview(showBackground = true, name = "Clients — pastille SAV plafonnée (99+)")
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewClientsTabsWithCappedBadge() {
    PrestaFlowTheme {
        ClientsTabsScreen(
            capabilities = ShopCapabilities(sav = true, reviews = true),
            unreadSavCount = 137,
            pendingReviewCount = 0,
        )
    }
}
