package com.rebuildit.prestaflow.data.dashboard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rebuildit.prestaflow.domain.dashboard.DashboardPreferencesRepository
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardPreferencesRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val ioDispatcher: CoroutineDispatcher,
    ) : DashboardPreferencesRepository {
        override val defaultPeriod: Flow<DashboardPeriod> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        Timber.w(error, "Erreur lecture préférence période dashboard par défaut")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }
                .map { prefs ->
                    prefs[KEY_DEFAULT_PERIOD]
                        ?.let { runCatching { DashboardPeriod.valueOf(it) }.getOrNull() }
                        ?: DEFAULT_PERIOD
                }
                .distinctUntilChanged()

        override suspend fun setDefaultPeriod(period: DashboardPeriod) {
            withContext(ioDispatcher) {
                dataStore.edit { prefs -> prefs[KEY_DEFAULT_PERIOD] = period.name }
            }
        }

        companion object {
            private val KEY_DEFAULT_PERIOD = stringPreferencesKey("dashboard_default_period")
            val DEFAULT_PERIOD: DashboardPeriod = DashboardPeriod.WEEK
        }
    }
