package com.rebuildit.prestaflow.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.BuildConfig
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.notifications.SaleNotifications
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.theme.DarkThemeConfig
import com.rebuildit.prestaflow.domain.theme.PrestaFlowSkin
import com.rebuildit.prestaflow.domain.theme.ThemeSettings
import com.rebuildit.prestaflow.ui.dashboard.labelRes
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import com.rebuildit.prestaflow.ui.theme.ThemeViewModel
import com.rebuildit.prestaflow.ui.theme.displayNameRes

@Composable
fun SettingsRoute(
    onLogoutClick: () -> Unit,
    onNotifCategoriesClick: () -> Unit = {},
    themeViewModel: ThemeViewModel = hiltViewModel(),
    shopsViewModel: ShopsViewModel = hiltViewModel(),
    dashboardPrefsViewModel: DashboardPrefsViewModel = hiltViewModel(),
) {
    val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()
    val connections by shopsViewModel.connections.collectAsStateWithLifecycle()
    val addState by shopsViewModel.addState.collectAsStateWithLifecycle()
    val defaultPeriod by dashboardPrefsViewModel.defaultPeriod.collectAsStateWithLifecycle()
    SettingsScreen(
        settings = themeState.settings,
        onSkinSelected = themeViewModel::selectSkin,
        onDarkThemeSelected = themeViewModel::setDarkThemeConfig,
        onLogoutClick = onLogoutClick,
        onNotifCategoriesClick = onNotifCategoriesClick,
        connections = connections,
        addState = addState,
        onSwitchShop = shopsViewModel::switchShop,
        onRemoveShop = shopsViewModel::removeShop,
        onShowAddShop = shopsViewModel::showAddDialog,
        onDismissAddShop = shopsViewModel::dismissAddDialog,
        onAddShopUrlChange = shopsViewModel::onUrlChange,
        onAddShopKeyChange = shopsViewModel::onKeyChange,
        onAddShopLabelChange = shopsViewModel::onLabelChange,
        onSubmitAddShop = shopsViewModel::submitAdd,
        dashboardDefaultPeriod = defaultPeriod,
        onDashboardDefaultPeriodSelected = dashboardPrefsViewModel::setDefaultPeriod,
    )
}

@Suppress("LongMethod", "LongParameterList") // Écran Réglages : thème + boutiques + notifs + logout
@Composable
fun SettingsScreen(
    settings: ThemeSettings,
    onSkinSelected: (PrestaFlowSkin) -> Unit,
    onDarkThemeSelected: (DarkThemeConfig) -> Unit,
    onLogoutClick: () -> Unit,
    onNotifCategoriesClick: () -> Unit = {},
    connections: List<ShopConnection> = emptyList(),
    addState: AddShopUiState = AddShopUiState(),
    onSwitchShop: (String) -> Unit = {},
    onRemoveShop: (String) -> Unit = {},
    onShowAddShop: () -> Unit = {},
    onDismissAddShop: () -> Unit = {},
    onAddShopUrlChange: (String) -> Unit = {},
    onAddShopKeyChange: (String) -> Unit = {},
    onAddShopLabelChange: (String) -> Unit = {},
    onSubmitAddShop: () -> Unit = {},
    dashboardDefaultPeriod: DashboardPeriod = DashboardPeriod.WEEK,
    onDashboardDefaultPeriodSelected: (DashboardPeriod) -> Unit = {},
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (addState.visible) {
        AddShopDialog(
            state = addState,
            onUrlChange = onAddShopUrlChange,
            onKeyChange = onAddShopKeyChange,
            onLabelChange = onAddShopLabelChange,
            onSubmit = onSubmitAddShop,
            onDismiss = onDismissAddShop,
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            shape = RoundedCornerShape(Dimensions.cardCornerRadius),
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
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingL),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
    ) {
        // Section BOUTIQUES — liste des connexions + bascule + suppression + ajout
        SettingsSection(label = stringResource(R.string.settings_shops_label)) {
            connections.forEach { connection ->
                ShopRow(
                    connection = connection,
                    onSwitch = { onSwitchShop(connection.id) },
                    onRemove = { onRemoveShop(connection.id) },
                )
            }
            OutlinedButton(
                onClick = onShowAddShop,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Dimensions.spacingS),
                )
                Text(
                    text = stringResource(R.string.settings_shops_add),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        // Section THÈME — sélecteur skin cercles colorés
        SettingsSection(label = stringResource(R.string.settings_theme_label)) {
            SkinColorRow(
                currentSkin = settings.skin,
                useDynamicColor = settings.useDynamicColor,
                onSkinSelected = onSkinSelected,
            )
        }

        // Section MODE SOMBRE
        SettingsSection(label = stringResource(R.string.settings_dark_mode_title)) {
            DarkModeSelector(
                current = settings.darkThemeConfig,
                onSelected = onDarkThemeSelected,
            )
        }

        // Section DASHBOARD — période par défaut
        SettingsSection(label = stringResource(R.string.settings_dashboard_label)) {
            DashboardDefaultPeriodSelector(
                current = dashboardDefaultPeriod,
                onSelected = onDashboardDefaultPeriodSelected,
            )
        }

        // Section À PROPOS
        SettingsSection(label = stringResource(R.string.settings_about_label)) {
            AboutRow(
                label = stringResource(R.string.settings_about_app),
                value = stringResource(R.string.app_name),
            )
            AboutRow(
                label = stringResource(R.string.settings_about_version),
                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
            AboutRow(
                label = stringResource(R.string.settings_about_environment),
                value = BuildConfig.ENVIRONMENT_NAME,
            )
        }

        // Section NOTIFICATIONS — son du canal + catégories
        SettingsSection(label = stringResource(R.string.settings_notifications_label)) {
            val context = LocalContext.current
            OutlinedButton(
                onClick = { openSalesNotificationSettings(context) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Dimensions.spacingS),
                )
                Text(
                    text = stringResource(R.string.settings_notification_sound),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            OutlinedButton(
                onClick = onNotifCategoriesClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Dimensions.spacingS),
                )
                Text(
                    text = stringResource(R.string.settings_notif_categories),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.spacingM))

        // Bouton déconnexion — pill, fond errorContainer léger (maquette Stitch)
        Button(
            onClick = { showLogoutDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
        ) {
            Icon(
                imageVector = Icons.Outlined.ExitToApp,
                contentDescription = null,
                modifier = Modifier.padding(end = Dimensions.spacingS),
            )
            Text(
                text = stringResource(R.string.settings_logout),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

// ─── Section card ──────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

// ─── Boutiques (multi-boutiques) ─────────────────────────────────────────────────

@Composable
private fun ShopRow(
    connection: ShopConnection,
    onSwitch: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSwitch)
                .padding(vertical = Dimensions.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
    ) {
        RadioButton(selected = connection.isActive, onClick = onSwitch)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = connection.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = connection.shopUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.settings_shops_remove),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AddShopDialog(
    state: AddShopUiState,
    onUrlChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        title = { Text(stringResource(R.string.settings_shops_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
                OutlinedTextField(
                    value = state.shopUrl,
                    onValueChange = onUrlChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.auth_field_shop_url)) },
                    placeholder = { Text(stringResource(R.string.auth_field_shop_url_placeholder)) },
                )
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = onKeyChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.auth_field_api_key)) },
                )
                OutlinedTextField(
                    value = state.label,
                    onValueChange = onLabelChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.shops_add_label_optional)) },
                )
                state.error?.let {
                    Text(
                        text = it.asString(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !state.loading) {
                Text(stringResource(R.string.auth_action_connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_logout_cancel))
            }
        },
    )
}

// ─── Ligne « À propos » (libellé / valeur) ──────────────────────────────────────

@Composable
private fun AboutRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Ouvre les réglages système du canal « Ventes » (où l'utilisateur change le son). */
private fun openSalesNotificationSettings(context: Context) {
    SaleNotifications.ensureChannel(context)
    val intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, SaleNotifications.CHANNEL_ID)
            }
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

// ─── Sélecteur skin — cercles colorés ─────────────────────────────────────────

/**
 * Palette de couleurs représentatives de chaque skin (couleur primary approximative).
 * Ces valeurs sont des constantes visuelles — elles ne changent pas avec le thème.
 */
private val skinSwatchColors: Map<PrestaFlowSkin, Color> =
    mapOf(
        PrestaFlowSkin.TERRACOTTA to Color(0xFF7F5448),
        PrestaFlowSkin.ROYAL to Color(0xFF4A4080),
        PrestaFlowSkin.LAGOON to Color(0xFF2A7D6F),
        PrestaFlowSkin.EMBER to Color(0xFFAD4000),
        PrestaFlowSkin.FOREST to Color(0xFF2D5A27),
        PrestaFlowSkin.SLATE to Color(0xFF4A5568),
        PrestaFlowSkin.SOFT to Color(0xFF8B5E8A),
    )

@Composable
private fun SkinColorRow(
    currentSkin: PrestaFlowSkin,
    useDynamicColor: Boolean,
    onSkinSelected: (PrestaFlowSkin) -> Unit,
) {
    val skins = PrestaFlowSkin.values().toList()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
    ) {
        skins.forEach { skin ->
            val isSelected = !useDynamicColor && currentSkin == skin
            val swatchColor = skinSwatchColors[skin] ?: MaterialTheme.colorScheme.primary
            val skinName = stringResource(skin.displayNameRes())
            SkinSwatch(
                color = swatchColor,
                isSelected = isSelected,
                contentDescription = skinName,
                onClick = { onSkinSelected(skin) },
            )
        }
    }
}

@Composable
private fun SkinSwatch(
    color: Color,
    isSelected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 2.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                )
                .clickable(
                    onClickLabel = contentDescription,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .semantics { role = Role.RadioButton },
    )
}

// ─── Mode sombre ──────────────────────────────────────────────────────────────

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
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
    ) {
        options.forEach { (config, label) ->
            FilterChip(
                selected = current == config,
                onClick = { onSelected(config) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                },
                shape = RoundedCornerShape(50),
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ─── Période par défaut du dashboard ─────────────────────────────────────────

@Composable
private fun DashboardDefaultPeriodSelector(
    current: DashboardPeriod,
    onSelected: (DashboardPeriod) -> Unit,
) {
    Text(
        text = stringResource(R.string.settings_dashboard_default_period),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)) {
        DashboardPeriod.values().forEach { period ->
            val label = stringResource(period.labelRes())
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClickLabel = label,
                            role = Role.RadioButton,
                        ) { onSelected(period) }
                        .padding(vertical = Dimensions.spacingXs)
                        .semantics { role = Role.RadioButton },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
            ) {
                RadioButton(
                    selected = period == current,
                    onClick = { onSelected(period) },
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Réglages")
@Composable
private fun PreviewSettingsScreen() {
    PrestaFlowTheme {
        SettingsScreen(
            settings = ThemeSettings(),
            onSkinSelected = {},
            onDarkThemeSelected = {},
            onLogoutClick = {},
        )
    }
}
