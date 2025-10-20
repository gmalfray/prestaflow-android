package com.rebuildit.prestaflow.domain.theme

import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    val settings: Flow<ThemeSettings>

    suspend fun selectSkin(skin: PrestaFlowSkin)

    suspend fun setUseDynamicColor(enabled: Boolean)

    suspend fun setDarkThemeConfig(config: DarkThemeConfig)
}
