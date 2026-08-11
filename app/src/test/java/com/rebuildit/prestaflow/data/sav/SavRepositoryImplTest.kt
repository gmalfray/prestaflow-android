package com.rebuildit.prestaflow.data.sav

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lot socle : [SavRepositoryImpl.unreadThreadCount] émet volontairement 0 — jamais un chiffre
 * inventé tant qu'aucun endpoint réel n'est branché (cf. la Javadoc de la classe). Ce test fige
 * ce comportement pour qu'un futur câblage réel ne l'oublie pas silencieusement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavRepositoryImplTest {
    @Test
    fun `unreadThreadCount emet 0 tant qu aucun endpoint n est branche`() =
        runTest {
            val repository = SavRepositoryImpl()

            repository.unreadThreadCount.test {
                assertEquals(0, awaitItem())
                awaitComplete()
            }
        }
}
