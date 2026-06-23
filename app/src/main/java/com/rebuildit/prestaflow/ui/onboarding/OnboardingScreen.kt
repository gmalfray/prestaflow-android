package com.rebuildit.prestaflow.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rebuildit.prestaflow.R

/**
 * Écran d'accueil proposé au nouvel utilisateur quand aucune connexion n'est configurée.
 *
 * Deux chemins :
 * - [onHasModule] : l'utilisateur a déjà installé le module → flux de connexion normal.
 * - [onNoModule] : l'utilisateur n'a pas encore le module → guide d'installation.
 */
@Composable
fun OnboardingRoute(
    onHasModule: () -> Unit,
    onNoModule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingScreen(
        onHasModule = onHasModule,
        onNoModule = onNoModule,
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    onHasModule: () -> Unit,
    onNoModule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { innerPadding ->
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
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(id = R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(id = R.string.onboarding_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onHasModule,
                ) {
                    Text(text = stringResource(id = R.string.onboarding_action_has_module))
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNoModule,
                ) {
                    Text(text = stringResource(id = R.string.onboarding_action_no_module))
                }
            }
        }
    }
}
