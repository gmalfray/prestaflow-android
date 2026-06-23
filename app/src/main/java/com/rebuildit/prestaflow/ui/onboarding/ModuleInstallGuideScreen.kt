package com.rebuildit.prestaflow.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.config.AppLinks

/**
 * Écran « Guide d'installation » — affiché quand l'utilisateur n'a pas encore déployé
 * le module Rebuild Connector sur sa boutique.
 *
 * L'installation se fait depuis un **ordinateur** (Back-Office PrestaShop), pas depuis
 * le téléphone. Cet écran guide l'utilisateur en 3 étapes et propose de partager l'URL
 * de téléchargement pour l'ouvrir plus tard sur PC.
 *
 * @param onBack navigation retour vers l'écran d'onboarding.
 * @param onGoToConnect navigation vers le flux de connexion (scanner QR / saisir URL+clé).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleInstallGuideRoute(
    onBack: () -> Unit,
    onGoToConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModuleInstallGuideScreen(
        onBack = onBack,
        onGoToConnect = onGoToConnect,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod") // Écran guide linéaire : étapes 1-2-3 + boutons d'action ; découpage en sous-composables non justifié ici
@Composable
fun ModuleInstallGuideScreen(
    onBack: () -> Unit,
    onGoToConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backLabel = stringResource(id = R.string.content_description_back)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.module_guide_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backLabel,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 520.dp)
                        .align(Alignment.TopCenter)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.module_guide_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider()

                // Étape 1
                InstallStep(
                    number = 1,
                    title = stringResource(id = R.string.module_guide_step1_title),
                    description = stringResource(id = R.string.module_guide_step1_desc),
                )

                // Étape 2
                InstallStep(
                    number = 2,
                    title = stringResource(id = R.string.module_guide_step2_title),
                    description = stringResource(id = R.string.module_guide_step2_desc),
                )

                // Étape 3
                InstallStep(
                    number = 3,
                    title = stringResource(id = R.string.module_guide_step3_title),
                    description = stringResource(id = R.string.module_guide_step3_desc),
                )

                HorizontalDivider()

                // Bouton « Partager le lien » : envoie l'URL par mail/messagerie pour l'ouvrir sur PC
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val intent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, AppLinks.MODULE_INSTALL_URL)
                            }
                        context.startActivity(
                            Intent.createChooser(
                                intent,
                                context.getString(R.string.module_guide_share_chooser),
                            ),
                        )
                    },
                ) {
                    Text(text = stringResource(id = R.string.module_guide_action_share))
                }

                // Bouton « Ouvrir » : ouvre directement la page dans le navigateur
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.MODULE_INSTALL_URL))
                        context.startActivity(intent)
                    },
                ) {
                    Text(text = stringResource(id = R.string.module_guide_action_open))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bouton secondaire : retour vers le flux de connexion une fois le module installé
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onGoToConnect,
                ) {
                    Text(text = stringResource(id = R.string.module_guide_action_go_connect))
                }
            }
        }
    }
}

@Composable
private fun InstallStep(
    number: Int,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
