package com.rebuildit.prestaflow.data.printer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rebuildit.prestaflow.domain.printer.ThermalPrinterPreferencesRepository
import com.rebuildit.prestaflow.domain.printer.model.SavedPrinterDevice
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

/**
 * Persistance DataStore de l'imprimante thermique Bluetooth sélectionnée par l'utilisateur.
 *
 * Les deux clés sont [KEY_ADDRESS] et [KEY_NAME] : elles sont toutes les deux nécessaires
 * pour reconstituer un [SavedPrinterDevice]. Si l'une est absente, [savedDevice] émet null.
 */
@Singleton
class ThermalPrinterPreferencesRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val ioDispatcher: CoroutineDispatcher,
    ) : ThermalPrinterPreferencesRepository {
        override val savedDevice: Flow<SavedPrinterDevice?> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        Timber.w(error, "Erreur lecture préférence imprimante thermique")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }
                .map { prefs ->
                    val address = prefs[KEY_ADDRESS]
                    val name = prefs[KEY_NAME]
                    if (!address.isNullOrBlank() && name != null) {
                        SavedPrinterDevice(address = address, name = name)
                    } else {
                        null
                    }
                }
                .distinctUntilChanged()

        override suspend fun saveDevice(device: SavedPrinterDevice) {
            withContext(ioDispatcher) {
                dataStore.edit { prefs ->
                    prefs[KEY_ADDRESS] = device.address
                    prefs[KEY_NAME] = device.name
                }
            }
        }

        override suspend fun clearDevice() {
            withContext(ioDispatcher) {
                dataStore.edit { prefs ->
                    prefs.remove(KEY_ADDRESS)
                    prefs.remove(KEY_NAME)
                }
            }
        }

        companion object {
            internal val KEY_ADDRESS = stringPreferencesKey("thermal_printer_address")
            internal val KEY_NAME = stringPreferencesKey("thermal_printer_name")
        }
    }
