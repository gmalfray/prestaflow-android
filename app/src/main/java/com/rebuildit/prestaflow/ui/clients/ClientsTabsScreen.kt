package com.rebuildit.prestaflow.ui.clients

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
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
    modifier: Modifier = Modifier,
    viewModel: ClientsTabsViewModel = hiltViewModel(),
) {
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()

    ClientsTabsScreen(
        capabilities = capabilities,
        modifier = modifier,
        clientsContent = { contentModifier ->
            ClientsRoute(
                onClientClick = onClientClick,
                onAddShop = onAddShop,
                modifier = contentModifier,
            )
        },
        savContent = { contentModifier -> SavRoute(modifier = contentModifier) },
        reviewsContent = { contentModifier -> ReviewsRoute(modifier = contentModifier) },
    )
}

@Composable
fun ClientsTabsScreen(
    capabilities: ShopCapabilities,
    modifier: Modifier = Modifier,
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
                Tab(
                    selected = section == selected,
                    onClick = { selected = section },
                    text = {
                        Text(
                            text = stringResource(section.labelRes),
                            style = MaterialTheme.typography.labelLarge,
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
