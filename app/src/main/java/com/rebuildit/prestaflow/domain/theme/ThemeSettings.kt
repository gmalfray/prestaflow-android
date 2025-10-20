package com.rebuildit.prestaflow.domain.theme


data class ThemeSettings(
    val skin: PrestaFlowSkin = PrestaFlowSkin.ROYAL,
    val useDynamicColor: Boolean = true,
    val darkThemeConfig: DarkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM
)
