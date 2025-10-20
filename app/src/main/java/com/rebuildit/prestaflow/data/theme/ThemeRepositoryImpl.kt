package com.rebuildit.prestaflow.data.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rebuildit.prestaflow.domain.theme.DarkThemeConfig
import com.rebuildit.prestaflow.domain.theme.ThemeRepository
import com.rebuildit.prestaflow.domain.theme.ThemeSettings
import com.rebuildit.prestaflow.domain.theme.PrestaFlowSkin
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class ThemeRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val ioDispatcher: CoroutineDispatcher
) : ThemeRepository {

    override val settings: Flow<ThemeSettings> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    Timber.w(error, "Failed to read theme preferences")
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { prefs -> prefs.toDomain() }
            .distinctUntilChanged()

    override suspend fun selectSkin(skin: PrestaFlowSkin) {
        updatePreference(KEY_SKIN, skin.name)
    }

    override suspend fun setUseDynamicColor(enabled: Boolean) {
        updatePreference(KEY_DYNAMIC_COLOR, enabled)
    }

    override suspend fun setDarkThemeConfig(config: DarkThemeConfig) {
        updatePreference(KEY_DARK_THEME_CONFIG, config.name)
    }

    private suspend fun updatePreference(key: Preferences.Key<String>, value: String) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }

    private suspend fun updatePreference(key: Preferences.Key<Boolean>, value: Boolean) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }

    private fun Preferences.toDomain(): ThemeSettings {
        val skin = this[KEY_SKIN]?.let { runCatching { PrestaFlowSkin.valueOf(it) }.getOrNull() }
            ?: DEFAULT_SETTINGS.skin
        val useDynamic = this[KEY_DYNAMIC_COLOR] ?: DEFAULT_SETTINGS.useDynamicColor
        val darkConfig = this[KEY_DARK_THEME_CONFIG]?.let { runCatching { DarkThemeConfig.valueOf(it) }.getOrNull() }
            ?: DEFAULT_SETTINGS.darkThemeConfig
        return ThemeSettings(
            skin = skin,
            useDynamicColor = useDynamic,
            darkThemeConfig = darkConfig
        )
    }

    companion object {
        private val KEY_SKIN = stringPreferencesKey("theme_skin")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("theme_dynamic_color")
        private val KEY_DARK_THEME_CONFIG = stringPreferencesKey("theme_dark_theme_config")
        private val DEFAULT_SETTINGS = ThemeSettings()
    }
}
