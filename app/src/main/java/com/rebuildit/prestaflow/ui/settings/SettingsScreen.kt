package com.rebuildit.prestaflow.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.theme.DarkThemeConfig
import com.rebuildit.prestaflow.domain.theme.PrestaFlowSkin
import com.rebuildit.prestaflow.domain.theme.ThemeSettings
import com.rebuildit.prestaflow.ui.theme.ThemeViewModel
import com.rebuildit.prestaflow.ui.theme.displayNameRes

@Composable
fun SettingsRoute(
    onLogoutClick: () -> Unit,
    themeViewModel: ThemeViewModel = hiltViewModel(),
) {
    val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        settings = themeState.settings,
        onSkinSelected = themeViewModel::selectSkin,
        onDarkThemeSelected = themeViewModel::setDarkThemeConfig,
        onLogoutClick = onLogoutClick,
    )
}

@Suppress("LongMethod") // Composable paramètres avec plusieurs sections (thème, skin, déconnexion)
@Composable
fun SettingsScreen(
    settings: ThemeSettings,
    onSkinSelected: (PrestaFlowSkin) -> Unit,
    onDarkThemeSelected: (DarkThemeConfig) -> Unit,
    onLogoutClick: () -> Unit,
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.settings_logout_confirm_title)) },
            text = { Text(stringResource(R.string.settings_logout_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogoutClick()
                }) {
                    Text(
                        stringResource(R.string.settings_logout_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.settings_logout_cancel))
                }
            },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Apparence — sélecteur de skin
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.settings_appearance_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.skin_selector_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SkinSelectorGrid(
                    currentSkin = settings.skin,
                    useDynamicColor = settings.useDynamicColor,
                    onSkinSelected = onSkinSelected,
                )

                HorizontalDivider()

                // Mode sombre
                Text(
                    text = stringResource(R.string.settings_dark_mode_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                DarkModeSelector(
                    current = settings.darkThemeConfig,
                    onSelected = onDarkThemeSelected,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bouton déconnexion
        Button(
            onClick = { showLogoutDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
        ) {
            Text(stringResource(R.string.settings_logout))
        }
    }
}

@Composable
private fun SkinSelectorGrid(
    currentSkin: PrestaFlowSkin,
    useDynamicColor: Boolean,
    onSkinSelected: (PrestaFlowSkin) -> Unit,
) {
    val skins = PrestaFlowSkin.values().toList()
    val rows = skins.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowSkins ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowSkins.forEach { skin ->
                    FilterChip(
                        selected = !useDynamicColor && currentSkin == skin,
                        onClick = { onSkinSelected(skin) },
                        label = {
                            Text(
                                text = stringResource(skin.displayNameRes()),
                                maxLines = 1,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowSkins.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DarkModeSelector(
    current: DarkThemeConfig,
    onSelected: (DarkThemeConfig) -> Unit,
) {
    val options =
        listOf(
            DarkThemeConfig.FOLLOW_SYSTEM to stringResource(R.string.skin_dark_follow_system),
            DarkThemeConfig.LIGHT to stringResource(R.string.skin_dark_light),
            DarkThemeConfig.DARK to stringResource(R.string.skin_dark_dark),
        )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (config, label) ->
            FilterChip(
                selected = current == config,
                onClick = { onSelected(config) },
                label = { Text(label, maxLines = 1) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
