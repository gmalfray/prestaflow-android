package com.rebuildit.prestaflow.data.carts.mapper

import com.rebuildit.prestaflow.data.remote.dto.CartCustomerDto
import com.rebuildit.prestaflow.data.remote.dto.CartDetailDto
import com.rebuildit.prestaflow.data.remote.dto.CartProductDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du mapper [CartDetailDto.toDomain].
 *
 * Point principal : documenter que [CartDetailDto.itemsCount] peut valoir 0
 * alors que la liste de produits est remplie (défaut F). La correction est dans
 * l'écran de détail qui calcule le vrai compte depuis [CartDetail.products].
 *
 * Ces tests vérifient que le mapper transmet fidèlement les données du DTO
 * et que la somme des quantités produits est bien la source de vérité.
 */
class CartMappersTest {
    private fun makeProductDto(quantity: Int) =
        CartProductDto(
            productId = 1,
            quantity = quantity,
            totalTaxIncl = 10.0,
        )

    @Test
    fun `toDomain transmet les produits avec les bonnes quantites`() {
        val dto = CartDetailDto(
            id = 42,
            customer = CartCustomerDto(firstname = "Alice", lastname = "Dupont"),
            itemsCount = 0, // ← valeur backend potentiellement erronée
            products = listOf(
                makeProductDto(quantity = 2),
                makeProductDto(quantity = 3),
            ),
        )

        val domain = dto.toDomain()

        assertEquals("2 produits dans le domaine", 2, domain.products.size)
        assertEquals(
            "La somme des quantités doit valoir 5",
            5,
            domain.products.sumOf { it.quantity },
        )
    }

    @Test
    fun `itemsCount dans le domaine reflète la valeur backend meme si incorrect`() {
        val dto = CartDetailDto(
            id = 1,
            itemsCount = 0, // backend ne remplit pas ce champ dans le détail
            products = listOf(makeProductDto(quantity = 3)),
        )

        val domain = dto.toDomain()

        // La logique de correction est dans l'UI, pas le mapper
        assertEquals(
            "Le mapper transmet itemsCount du DTO tel quel (0), la correction est côté UI",
            0,
            domain.itemsCount,
        )
        assertEquals(
            "La somme des quantités depuis products est la source de vérité",
            3,
            domain.products.sumOf { it.quantity },
        )
    }

    @Test
    fun `toDomain sans produits retourne liste vide`() {
        val dto = CartDetailDto(id = 1, itemsCount = 0, products = emptyList())
        val domain = dto.toDomain()
        assertTrue(domain.products.isEmpty())
    }

    @Test
    fun `toDomain construit le nom client depuis firstname et lastname`() {
        val dto = CartDetailDto(
            id = 1,
            customer = CartCustomerDto(firstname = "Jean", lastname = "Martin"),
        )
        val domain = dto.toDomain()
        assertEquals("Jean Martin", domain.customerName)
    }

    @Test
    fun `toDomain utilise email si nom est vide`() {
        val dto = CartDetailDto(
            id = 1,
            customer = CartCustomerDto(firstname = "", lastname = "", email = "guest@test.fr"),
        )
        val domain = dto.toDomain()
        assertEquals("guest@test.fr", domain.customerName)
    }
}
