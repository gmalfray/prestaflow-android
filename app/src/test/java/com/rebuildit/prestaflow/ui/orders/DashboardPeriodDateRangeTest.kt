package com.rebuildit.prestaflow.ui.orders

import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Vérifie que [DashboardPeriod.toDateRange] produit des bornes de dates correctes.
 *
 * Règle critique : `date_to` doit inclure le suffixe `23:59:59` pour aligner avec le filtre
 * `BETWEEN ... 23:59:59` du Dashboard côté serveur. Sans ce suffixe, MySQL interprète une date
 * nue comme `00:00:00`, excluant toutes les commandes de la journée après minuit.
 */
class DashboardPeriodDateRangeTest {
    // Date de référence fixe : lundi 15 janvier 2024.
    private val referenceDate = LocalDate.of(2024, 1, 15)

    @Test
    fun `TODAY date_from = aujourd_hui et date_to = aujourd_hui a 23h59`() {
        val (from, to) = DashboardPeriod.TODAY.toDateRange(referenceDate)
        assertEquals("2024-01-15", from)
        assertEquals("2024-01-15 23:59:59", to)
    }

    @Test
    fun `WEEK date_from = aujourd_hui moins 6 jours et date_to = aujourd_hui a 23h59`() {
        val (from, to) = DashboardPeriod.WEEK.toDateRange(referenceDate)
        assertEquals("2024-01-09", from)
        assertEquals("2024-01-15 23:59:59", to)
    }

    @Test
    fun `MONTH date_from = aujourd_hui moins 29 jours et date_to = aujourd_hui a 23h59`() {
        val (from, to) = DashboardPeriod.MONTH.toDateRange(referenceDate)
        assertEquals("2023-12-17", from)
        assertEquals("2024-01-15 23:59:59", to)
    }

    @Test
    fun `QUARTER date_from = aujourd_hui moins 3 mois et date_to = aujourd_hui a 23h59`() {
        val (from, to) = DashboardPeriod.QUARTER.toDateRange(referenceDate)
        assertEquals("2023-10-15", from)
        assertEquals("2024-01-15 23:59:59", to)
    }

    @Test
    fun `YEAR date_from = premier janvier de l_annee en cours et date_to = aujourd_hui a 23h59`() {
        val (from, to) = DashboardPeriod.YEAR.toDateRange(referenceDate)
        assertEquals("2024-01-01", from)
        assertEquals("2024-01-15 23:59:59", to)
    }

    @Test
    fun `date_to ne se termine jamais par une date nue sans heure`() {
        DashboardPeriod.entries.forEach { period ->
            val (_, to) = period.toDateRange(referenceDate)
            assertEquals(
                "Le date_to de $period doit se terminer par ' 23:59:59'",
                "2024-01-15 23:59:59",
                to,
            )
        }
    }
}
