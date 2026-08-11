package com.rebuildit.prestaflow.data.capabilities

import com.rebuildit.prestaflow.core.security.ShopConnectionStore
import com.rebuildit.prestaflow.data.remote.dto.ShopCapabilitiesDto
import com.rebuildit.prestaflow.domain.auth.model.AuthToken
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
import com.rebuildit.prestaflow.fakes.FakePrestaFlowApi
import com.rebuildit.prestaflow.fakes.FakeSharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Tests unitaires de [CapabilitiesRepositoryImpl].
 *
 * Points vérifiés :
 * - Valeur initiale = dernière valeur persistée pour la boutique active (ou défaut si aucune).
 * - [CapabilitiesRepositoryImpl.refresh] réussi : met à jour le flux ET la persistance par boutique.
 * - Échec réseau : la dernière valeur connue est conservée (pas de reset silencieux qui ferait
 *   disparaître Avis à cause d'un simple accroc réseau).
 * - Bascule immédiate sur la valeur persistée de la boutique active avant même l'appel réseau.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CapabilitiesRepositoryImplTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var api: FakePrestaFlowApi
    private lateinit var connectionStore: ShopConnectionStore

    @Before
    fun setUp() {
        api = FakePrestaFlowApi()
        connectionStore = ShopConnectionStore(FakeSharedPreferences())
    }

    private fun buildRepository() = CapabilitiesRepositoryImpl(api, connectionStore, testDispatcher)

    private fun activateShop(
        id: String = "https://shop.test",
        capabilities: ShopCapabilities = ShopCapabilities(),
    ) {
        connectionStore.write(
            listOf(
                ShopConnection(
                    id = id,
                    shopUrl = id,
                    label = "Boutique",
                    token = AuthToken(value = "tok", expiresAtEpochMillis = null),
                    capabilities = capabilities,
                ),
            ),
        )
        connectionStore.setActiveId(id)
    }

    @Test
    fun `valeur initiale = defaut si aucune boutique active`() =
        runTest(testDispatcher) {
            val repository = buildRepository()

            assertEquals(ShopCapabilities(), repository.capabilities.value)
        }

    @Test
    fun `valeur initiale = derniere capacite persistee pour la boutique active`() =
        runTest(testDispatcher) {
            activateShop(capabilities = ShopCapabilities(sav = true, reviews = true))

            val repository = buildRepository()

            assertTrue(repository.capabilities.value.reviews)
        }

    @Test
    fun `refresh reussi met a jour le flux et la persistance de la boutique active`() =
        runTest(testDispatcher) {
            activateShop(capabilities = ShopCapabilities(reviews = false))
            api.capabilitiesResponse = ShopCapabilitiesDto(sav = true, reviews = true, shippingLabels = false)
            val repository = buildRepository()

            val result = repository.refresh()

            assertTrue(result.reviews)
            assertTrue(repository.capabilities.value.reviews)
            assertTrue(
                "La persistance par boutique doit être mise à jour",
                connectionStore.read().first().capabilities.reviews,
            )
        }

    @Test
    fun `un echec reseau conserve la derniere valeur connue`() =
        runTest(testDispatcher) {
            activateShop(capabilities = ShopCapabilities(reviews = true))
            api.capabilitiesException = IOException("Pas de réseau")
            val repository = buildRepository()

            val result = repository.refresh()

            assertTrue(
                "Un accroc réseau ne doit jamais faire disparaître Avis",
                result.reviews,
            )
            assertTrue(repository.capabilities.value.reviews)
        }

    @Test
    fun `refresh bascule immediatement sur la valeur persistee de la boutique active`() =
        runTest(testDispatcher) {
            // Deux boutiques aux capacités différentes : shop1 a Avis, shop2 non.
            connectionStore.write(
                listOf(
                    ShopConnection(
                        id = "https://shop1.test",
                        shopUrl = "https://shop1.test",
                        label = "Boutique 1",
                        token = AuthToken(value = "tok1", expiresAtEpochMillis = null),
                        capabilities = ShopCapabilities(reviews = true),
                    ),
                    ShopConnection(
                        id = "https://shop2.test",
                        shopUrl = "https://shop2.test",
                        label = "Boutique 2",
                        token = AuthToken(value = "tok2", expiresAtEpochMillis = null),
                        capabilities = ShopCapabilities(reviews = false),
                    ),
                ),
            )
            connectionStore.setActiveId("https://shop1.test")
            // Réseau qui ne répond jamais avant qu'on relise `capabilities` : simule ici un
            // échec immédiat pour isoler la bascule synchrone (avant tout appel réseau).
            api.capabilitiesException = IOException("timeout")
            val repository = buildRepository()
            assertTrue(repository.capabilities.value.reviews)

            // Changement de boutique active (switchActiveConnection côté AuthRepositoryImpl).
            connectionStore.setActiveId("https://shop2.test")
            repository.refresh()

            assertFalse(
                "refresh doit d'abord refléter la boutique désormais active, avant même le réseau",
                repository.capabilities.value.reviews,
            )
        }
}
