package com.rebuildit.prestaflow.ui.dashboard

import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.dashboard.model.FreeConversionCohort
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
}
