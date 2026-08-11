package com.rebuildit.prestaflow.data.remote.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de désérialisation de [ShopCapabilitiesDto] contre le [Json] réellement configuré par
 * l'app (`ignoreUnknownKeys=true`, `explicitNulls=false` — cf. `AppModule.provideJson`).
 *
 * Le connecteur peut n'exposer que les capacités pertinentes (`sav` toujours vrai n'a par
 * exemple pas besoin d'être renvoyé) : chaque champ absent doit retomber sur sa valeur par défaut
 * plutôt que faire échouer la désérialisation.
 */
class CapabilitiesDtoTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = false
        }

    @Test
    fun `payload complet est decode fidelement`() {
        val dto =
            json.decodeFromString<ShopCapabilitiesDto>(
                """{"sav":true,"reviews":true,"shipping_labels":true}""",
            )

        assertTrue(dto.sav)
        assertTrue(dto.reviews)
        assertTrue(dto.shippingLabels)
    }

    @Test
    fun `reviews absent du payload retombe sur false par defaut`() {
        val dto = json.decodeFromString<ShopCapabilitiesDto>("""{"sav":true}""")

        assertFalse(dto.reviews)
    }

    @Test
    fun `payload vide retombe sur les valeurs par defaut (sav vrai, reste faux)`() {
        val dto = json.decodeFromString<ShopCapabilitiesDto>("{}")

        assertTrue("sav doit rester vrai par defaut : natif, jamais absent", dto.sav)
        assertFalse(dto.reviews)
        assertFalse(dto.shippingLabels)
    }

    @Test
    fun `les champs inconnus du payload sont ignores`() {
        val dto =
            json.decodeFromString<ShopCapabilitiesDto>(
                """{"sav":true,"reviews":false,"champ_futur":"valeur_inconnue"}""",
            )

        assertTrue(dto.sav)
        assertFalse(dto.reviews)
    }

    @Test
    fun `toDomain propage fidelement les 3 capacites`() {
        val dto = ShopCapabilitiesDto(sav = true, reviews = true, shippingLabels = false)

        val domain = dto.toDomain()

        assertTrue(domain.sav)
        assertTrue(domain.reviews)
        assertFalse(domain.shippingLabels)
    }
}
