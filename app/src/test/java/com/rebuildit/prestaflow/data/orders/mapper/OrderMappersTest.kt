package com.rebuildit.prestaflow.data.orders.mapper

import com.rebuildit.prestaflow.data.local.entity.OrderEntity
import com.rebuildit.prestaflow.data.remote.dto.OrderCustomerDto
import com.rebuildit.prestaflow.data.remote.dto.OrderDatesDto
import com.rebuildit.prestaflow.data.remote.dto.OrderDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListCustomerDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListItemDto
import com.rebuildit.prestaflow.data.remote.dto.OrderStatusDto
import com.rebuildit.prestaflow.data.remote.dto.OrderTotalsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests des mappers DTO↔domaine des commandes.
 *
 * Cible principale : vérifier que les champs de date sont correctement mappés
 * depuis les deux formes (liste = date_add/date_upd, détail = created_at/updated_at).
 * Ces bugs ont déjà causé des régressions.
 */
class OrderMappersTest {
    // ─── OrderListItemDto.toEntity() ────────────────────────────────────────

    @Test
    fun `OrderListItemDto toEntity utilise date_add pour createdAtIso`() {
        val dto = buildListItemDto(dateAdded = "2024-03-15T10:00:00+00:00", dateUpdated = "2024-03-16T12:00:00+00:00")

        val entity = dto.toEntity()

        assertEquals("2024-03-15T10:00:00+00:00", entity.createdAtIso)
    }

    @Test
    fun `OrderListItemDto toEntity utilise date_upd pour updatedAtIso`() {
        val dto = buildListItemDto(dateAdded = "2024-03-15T10:00:00+00:00", dateUpdated = "2024-03-16T12:00:00+00:00")

        val entity = dto.toEntity()

        assertEquals("2024-03-16T12:00:00+00:00", entity.updatedAtIso)
    }

    @Test
    fun `OrderListItemDto toEntity avec dates nulles donne chaines vides`() {
        val dto = buildListItemDto(dateAdded = null, dateUpdated = null)

        val entity = dto.toEntity()

        assertEquals("", entity.createdAtIso)
        assertEquals("", entity.updatedAtIso)
    }

    @Test
    fun `OrderListItemDto toEntity concatène prénom et nom du client`() {
        val dto = buildListItemDto(firstName = "Jean", lastName = "Dupont")

        val entity = dto.toEntity()

        assertEquals("Jean Dupont", entity.customerName)
    }

    @Test
    fun `OrderListItemDto toEntity avec prénom vide ne laisse pas d'espace en tête`() {
        val dto = buildListItemDto(firstName = "", lastName = "Dupont")

        val entity = dto.toEntity()

        assertEquals("Dupont", entity.customerName)
    }

    // ─── OrderDto.toEntity() (endpoint détail) ──────────────────────────────

    @Test
    fun `OrderDto toEntity utilise created_at pour createdAtIso`() {
        val dto = buildDetailDto(createdAt = "2024-03-15T10:00:00+00:00", updatedAt = "2024-03-16T12:00:00+00:00")

        val entity = dto.toEntity()

        assertEquals("2024-03-15T10:00:00+00:00", entity.createdAtIso)
    }

    @Test
    fun `OrderDto toEntity utilise updated_at pour updatedAtIso`() {
        val dto = buildDetailDto(createdAt = "2024-03-15T10:00:00+00:00", updatedAt = "2024-03-16T12:00:00+00:00")

        val entity = dto.toEntity()

        assertEquals("2024-03-16T12:00:00+00:00", entity.updatedAtIso)
    }

    @Test
    fun `OrderDto toEntity avec dates nulles donne chaines vides`() {
        val dto = buildDetailDto(createdAt = null, updatedAt = null)

        val entity = dto.toEntity()

        assertEquals("", entity.createdAtIso)
        assertEquals("", entity.updatedAtIso)
    }

    @Test
    fun `OrderDto toEntity utilise paid_tax_incl pour totalPaid`() {
        val dto = buildDetailDto(paidTaxIncl = 99.90)

        val entity = dto.toEntity()

        assertEquals(99.90, entity.totalPaid, 0.001)
    }

    @Test
    fun `OrderDto toEntity avec totals null donne totalPaid a zero`() {
        val dto = buildDetailDto(paidTaxIncl = null)

        val entity = dto.toEntity()

        assertEquals(0.0, entity.totalPaid, 0.001)
    }

    @Test
    fun `OrderDto toEntity mappe le nom du statut`() {
        val dto = buildDetailDto(statusName = "En attente de paiement")

        val entity = dto.toEntity()

        assertEquals("En attente de paiement", entity.status)
    }

    @Test
    fun `OrderDto toEntity avec nom de statut null donne chaine vide`() {
        val dto = buildDetailDto(statusName = null)

        val entity = dto.toEntity()

        assertEquals("", entity.status)
    }

    // ─── OrderEntity.toDomain() ─────────────────────────────────────────────

    @Test
    fun `OrderEntity toDomain preserve createdAtIso`() {
        val entity = buildEntity(createdAtIso = "2024-03-15T10:00:00+00:00")

        val domain = entity.toDomain()

        assertEquals("2024-03-15T10:00:00+00:00", domain.createdAtIso)
    }

    @Test
    fun `OrderEntity toDomain preserve updatedAtIso`() {
        val entity = buildEntity(updatedAtIso = "2024-03-16T12:00:00+00:00")

        val domain = entity.toDomain()

        assertEquals("2024-03-16T12:00:00+00:00", domain.updatedAtIso)
    }

    @Test
    fun `OrderEntity toDomain avec itemsJson invalide donne liste vide`() {
        val entity = buildEntity(itemsJson = "{not_an_array}")

        val domain = entity.toDomain()

        assertTrue("Les items doivent être vides en cas de JSON invalide", domain.items.isEmpty())
    }

    @Test
    fun `OrderEntity toDomain avec itemsJson null donne liste vide`() {
        val entity = buildEntity(itemsJson = null)

        val domain = entity.toDomain()

        assertTrue(domain.items.isEmpty())
    }

    @Test
    fun `OrderEntity toDomain avec shippingJson null donne shipping null`() {
        val entity = buildEntity(shippingJson = null)

        val domain = entity.toDomain()

        assertEquals(null, domain.shipping)
    }

    // ─── Builders ───────────────────────────────────────────────────────────

    private fun buildListItemDto(
        id: Long = 1L,
        reference: String = "REF001",
        status: String = "En préparation",
        totalPaid: Double = 49.99,
        currency: String = "EUR",
        dateAdded: String? = "2024-01-01T00:00:00+00:00",
        dateUpdated: String? = "2024-01-02T00:00:00+00:00",
        firstName: String = "Alice",
        lastName: String = "Martin",
        hasInvoice: Boolean = false,
    ) = OrderListItemDto(
        id = id,
        reference = reference,
        status = status,
        totalPaid = totalPaid,
        currency = currency,
        dateAdded = dateAdded,
        dateUpdated = dateUpdated,
        customer = OrderListCustomerDto(id = 10L, firstName = firstName, lastName = lastName),
        hasInvoice = hasInvoice,
    )

    private fun buildDetailDto(
        id: Long = 1L,
        reference: String = "REF001",
        statusName: String? = "En préparation",
        paidTaxIncl: Double? = 49.99,
        currency: String = "EUR",
        createdAt: String? = "2024-01-01T00:00:00+00:00",
        updatedAt: String? = "2024-01-02T00:00:00+00:00",
        hasInvoice: Boolean = false,
    ) = OrderDto(
        id = id,
        reference = reference,
        status = OrderStatusDto(id = 2, name = statusName),
        totals =
            if (paidTaxIncl != null) {
                OrderTotalsDto(paidTaxIncl = paidTaxIncl, currency = currency)
            } else {
                null
            },
        customer = OrderCustomerDto(id = 10L, firstName = "Alice", lastName = "Martin"),
        dates =
            if (createdAt != null || updatedAt != null) {
                OrderDatesDto(createdAt = createdAt, updatedAt = updatedAt)
            } else {
                null
            },
        hasInvoice = hasInvoice,
    )

    private fun buildEntity(
        id: Long = 1L,
        reference: String = "REF001",
        status: String = "En préparation",
        totalPaid: Double = 49.99,
        currency: String = "EUR",
        customerName: String = "Alice Martin",
        createdAtIso: String = "2024-01-01T00:00:00+00:00",
        updatedAtIso: String = "2024-01-02T00:00:00+00:00",
        hasInvoice: Boolean = false,
        itemsJson: String? = null,
        shippingJson: String? = null,
    ) = OrderEntity(
        id = id,
        reference = reference,
        status = status,
        totalPaid = totalPaid,
        currency = currency,
        customerName = customerName,
        createdAtIso = createdAtIso,
        updatedAtIso = updatedAtIso,
        hasInvoice = hasInvoice,
        itemsJson = itemsJson,
        shippingJson = shippingJson,
    )
}
