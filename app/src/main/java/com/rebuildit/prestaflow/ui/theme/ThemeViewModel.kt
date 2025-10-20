package com.rebuildit.prestaflow.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.domain.theme.DarkThemeConfig
import com.rebuildit.prestaflow.domain.theme.ThemeRepository
import com.rebuildit.prestaflow.domain.theme.ThemeSettings
import com.rebuildit.prestaflow.domain.theme.PrestaFlowSkin
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeRepository: ThemeRepository
) : ViewModel() {

    val uiState: StateFlow<ThemeUiState> = themeRepository.settings
        .map { ThemeUiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeUiState()
        )

    fun selectSkin(skin: PrestaFlowSkin) {
        viewModelScope.launch {
            themeRepository.selectSkin(skin)
        }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            themeRepository.setUseDynamicColor(enabled)
        }
    }

    fun setDarkThemeConfig(config: DarkThemeConfig) {
        viewModelScope.launch {
            themeRepository.setDarkThemeConfig(config)
        }
    }
}

data class ThemeUiState(val settings: ThemeSettings = ThemeSettings())
