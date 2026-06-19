package com.rebuildit.prestaflow.domain.theme

data class ThemeSettings(
    val skin: PrestaFlowSkin = PrestaFlowSkin.TERRACOTTA,
    val useDynamicColor: Boolean = false,
    val darkThemeConfig: DarkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
)
