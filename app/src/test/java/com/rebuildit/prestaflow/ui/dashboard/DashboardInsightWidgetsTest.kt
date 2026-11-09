package com.rebuildit.prestaflow.ui.dashboard

import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.dashboard.model.FreeConversionCohort
import com.rebuildit.prestaflow.domain.dashboard.model.MonthlyTurnoverPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.util.Locale

class DashboardInsightWidgetsTest {
    @Test
    fun `libellés de canal - traduits, noms propres et clé inconnue capitalisée`() {
        assertEquals(R.string.dashboard_channel_direct, channelLabelRes("direct"))
        assertEquals(R.string.dashboard_channel_search, channelLabelRes("Recherche"))
        assertEquals(R.string.dashboard_channel_email, channelLabelRes("email"))
        assertEquals(R.string.dashboard_channel_other, channelLabelRes("autre"))
        assertNull(channelLabelRes("instagram"))
        assertNull(channelLabelRes("newsletter"))
        assertEquals("Instagram", channelFallbackLabel("instagram"))
        assertEquals("Facebook", channelFallbackLabel("facebook"))
        assertEquals("Pinterest", channelFallbackLabel("pinterest"))
        assertEquals("Newsletter", channelFallbackLabel("newsletter"))
    }

    @Test
    fun `mois de cohorte - formaté en français, texte brut si illisible`() {
        assertEquals("Mai 2026", formatCohortMonth("2026-05", Locale.FRANCE))
        assertEquals("Mai 2026", formatCohortMonth("2026-05", Locale.FRENCH))
        assertEquals("not-a-month", formatCohortMonth("not-a-month", Locale.FRANCE))
    }

    @Test
    fun `cohorte immature - le mois courant seulement`() {
        val now = YearMonth.of(2026, 11)
        assertTrue(isImmatureCohort("2026-11", now))
        assertFalse(isImmatureCohort("2026-10", now))
        assertFalse(isImmatureCohort("garbage", now))
    }

    @Test
    fun `échelle des barres - plus grand taux à 90 jours`() {
        val cohorts = listOf(FreeConversionCohort("2026-05", 40, 1, 2, 3), FreeConversionCohort("2026-04", 50, 3, 5, 10))
        assertEquals(20.0, maxRate90(cohorts), 0.0001)
        assertEquals(0.0, maxRate90(emptyList()), 0.0)
    }

    @Test
    fun `mois abrégé - sans point final, texte brut si illisible`() {
        assertEquals("janv", formatMonthShort("2026-01", Locale.FRANCE))
        assertEquals("déc", formatMonthShort("2025-12", Locale.FRANCE))
        assertEquals("Mar", formatMonthShort("2026-03", Locale.ENGLISH))
        assertEquals("nope", formatMonthShort("nope", Locale.FRANCE))
    }

    @Test
    fun `hauteurs des barres - rapportées au pic, zéro et négatif à 0`() {
        val months =
            listOf(
                MonthlyTurnoverPoint("2026-08", 50.0, 1),
                MonthlyTurnoverPoint("2026-09", 100.0, 2),
                MonthlyTurnoverPoint("2026-10", 0.0, 0),
                MonthlyTurnoverPoint("2026-11", -5.0, 0),
            )
        assertEquals(listOf(0.5f, 1f, 0f, 0f), monthlyBarFractions(months))
        assertEquals(emptyList<Float>(), monthlyBarFractions(emptyList()))
        assertEquals(listOf(0f), monthlyBarFractions(listOf(MonthlyTurnoverPoint("2026-08", 0.0, 0))))
    }

    @Test
    fun `étiquettes de mois - une sur deux ou trois, le dernier mois toujours étiqueté`() {
        assertEquals(2, monthLabelStep(1f))
        assertEquals(3, monthLabelStep(2f))
        val shown2 = (0..11).filter { isMonthLabelShown(it, 11, 2) }
        assertEquals(listOf(1, 3, 5, 7, 9, 11), shown2)
        val shown3 = (0..11).filter { isMonthLabelShown(it, 11, 3) }
        assertEquals(listOf(2, 5, 8, 11), shown3)
    }
}
