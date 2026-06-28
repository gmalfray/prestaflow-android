package com.rebuildit.prestaflow.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests unitaires de [formatXAxisLabel].
 *
 * Vérifie que :
 * - les horodatages "yyyy-MM-dd HH:mm:ss" (période Aujourd'hui) sont convertis en "HH'h'"
 * - les dates ISO yyyy-MM-dd sont converties en dd/MM
 * - les labels non-ISO (presets, semaines) sont retournés tels quels
 */
class DashboardXLabelFormatTest {

    // ─── Labels horaires (période Aujourd'hui) ───────────────────────────────

    @Test
    fun `horodatage datetime formate en heure avec suffixe h`() {
        assertEquals("15h", formatXAxisLabel("2026-06-28 15:00:00"))
    }

    @Test
    fun `horodatage datetime minuit formate en 00h`() {
        assertEquals("00h", formatXAxisLabel("2026-06-28 00:00:00"))
    }

    @Test
    fun `horodatage datetime 09h avec zero de tete`() {
        assertEquals("09h", formatXAxisLabel("2026-06-28 09:00:00"))
    }

    @Test
    fun `horodatage datetime 23h`() {
        assertEquals("23h", formatXAxisLabel("2026-06-28 23:00:00"))
    }

    // ─── Labels journaliers ──────────────────────────────────────────────────

    @Test
    fun `date ISO standard convertie en dd-MM`() {
        assertEquals("26/06", formatXAxisLabel("2026-06-26"))
    }

    @Test
    fun `date ISO debut de mois avec zero`() {
        assertEquals("01/01", formatXAxisLabel("2026-01-01"))
    }

    // ─── Presets non-parseable ───────────────────────────────────────────────

    @Test
    fun `label preset Lun retourne tel quel`() {
        assertEquals("Lun", formatXAxisLabel("Lun"))
    }

    @Test
    fun `label preset S1 retourne tel quel`() {
        assertEquals("S1", formatXAxisLabel("S1"))
    }

    @Test
    fun `label Semaine 1 retourne tel quel`() {
        assertEquals("Semaine 1", formatXAxisLabel("Semaine 1"))
    }

    @Test
    fun `string vide retourne tel quel`() {
        assertEquals("", formatXAxisLabel(""))
    }

    @Test
    fun `string partielle non parseable retourne tel quel`() {
        assertEquals("2026-06", formatXAxisLabel("2026-06"))
    }
}
