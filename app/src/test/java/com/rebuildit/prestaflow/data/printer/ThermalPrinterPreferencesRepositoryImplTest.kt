package com.rebuildit.prestaflow.data.printer

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rebuildit.prestaflow.domain.printer.model.SavedPrinterDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests unitaires JVM de [ThermalPrinterPreferencesRepositoryImpl].
 *
 * Le DataStore est instancié dans [backgroundScope] (requis par la lib pour éviter
 * [kotlinx.coroutines.test.UncompletedCoroutinesError]) et écrit dans un fichier temporaire JUnit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThermalPrinterPreferencesRepositoryImplTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    /**
     * Construit le repository en utilisant [TestScope.backgroundScope] pour le DataStore.
     * C'est la pratique recommandée par kotlinx-coroutines-test pour éviter que le DataStore
     * (qui garde une coroutine interne active) ne bloque la fin du runTest.
     */
    private fun buildRepository(): ThermalPrinterPreferencesRepositoryImpl {
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = testScope.backgroundScope,
                produceFile = { tmpFolder.newFile("thermal_prefs_test.preferences_pb") },
            )
        return ThermalPrinterPreferencesRepositoryImpl(
            dataStore = dataStore,
            ioDispatcher = testDispatcher,
        )
    }

    @Test
    fun `savedDevice est null initialement`() =
        testScope.runTest {
            val repository = buildRepository()
            advanceUntilIdle()
            val device = repository.savedDevice.first()
            assertNull("Aucune imprimante ne doit être configurée initialement", device)
        }

    @Test
    fun `saveDevice persiste l adresse MAC et le nom`() =
        testScope.runTest {
            val repository = buildRepository()
            val expected = SavedPrinterDevice(address = "AA:BB:CC:DD:EE:FF", name = "ITPP941B")
            repository.saveDevice(expected)
            advanceUntilIdle()

            val actual = repository.savedDevice.first()
            assertEquals(expected, actual)
        }

    @Test
    fun `saveDevice écrase la sélection précédente`() =
        testScope.runTest {
            val repository = buildRepository()
            val first = SavedPrinterDevice(address = "11:22:33:44:55:66", name = "Printer 1")
            val second = SavedPrinterDevice(address = "AA:BB:CC:DD:EE:FF", name = "MUNBYN")

            repository.saveDevice(first)
            advanceUntilIdle()
            repository.saveDevice(second)
            advanceUntilIdle()

            val actual = repository.savedDevice.first()
            assertEquals(second, actual)
        }

    @Test
    fun `clearDevice remet savedDevice à null`() =
        testScope.runTest {
            val repository = buildRepository()
            repository.saveDevice(SavedPrinterDevice(address = "AA:BB:CC:DD:EE:FF", name = "ITPP941B"))
            advanceUntilIdle()
            repository.clearDevice()
            advanceUntilIdle()

            val actual = repository.savedDevice.first()
            assertNull("La sélection doit être null après clearDevice", actual)
        }

    @Test
    fun `savedDevice retourne null si adresse est blank`() =
        testScope.runTest {
            val repository = buildRepository()
            // Injection directe via les clés DataStore pour simuler données corrompues
            // On vérifie que le Flow retourne null pour une adresse blanche
            repository.saveDevice(SavedPrinterDevice(address = "  ", name = "Test"))
            advanceUntilIdle()

            val actual = repository.savedDevice.first()
            assertNull("Un device avec adresse blanche ne doit pas être retourné", actual)
        }
}
