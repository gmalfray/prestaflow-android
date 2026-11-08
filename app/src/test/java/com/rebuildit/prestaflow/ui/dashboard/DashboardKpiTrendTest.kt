package com.rebuildit.prestaflow.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests unitaires des fonctions pures de calcul de tendance des cartes KPI :
 * [kpiTrendPercent] (CA vs période précédente) et [kpiTrendPercentFromSeries]
 * (tendance dérivée d'une série de buckets : commandes, nouveaux clients, panier moyen).
 *
 * Vérifie que le badge de tendance est masqué (retour `null`) dans tous les cas où la
 * donnée ne serait pas fiable : série vide/trop courte, dénominateur nul, mode plage libre.
 */
class DashboardKpiTrendTest {
    // ─── kpiTrendPercent (CA vs previousTurnover) ─────────────────────────────

    @Test
    fun `kpiTrendPercent hausse calculee correctement`() {
        val result = kpiTrendPercent(current = 1200.0, previous = 1000.0, isCustomRange = false)
        assertEquals(20.0, (result as KpiTrend.Percent).value, 0.0001)
    }

    @Test
    fun `kpiTrendPercent baisse calculee correctement`() {
        val result = kpiTrendPercent(current = 800.0, previous = 1000.0, isCustomRange = false)
        assertEquals(-20.0, (result as KpiTrend.Percent).value, 0.0001)
    }

    @Test
    fun `kpiTrendPercent plat quand valeurs identiques`() {
        val result = kpiTrendPercent(current = 1000.0, previous = 1000.0, isCustomRange = false)
        assertEquals(0.0, (result as KpiTrend.Percent).value, 0.0001)
    }

    @Test
    fun `kpiTrendPercent masque si previous absent`() {
        assertNull(kpiTrendPercent(current = 1000.0, previous = null, isCustomRange = false))
    }

    @Test
    fun `kpiTrendPercent nouv si previous nul et current positif`() {
        assertEquals(KpiTrend.New, kpiTrendPercent(current = 1000.0, previous = 0.0, isCustomRange = false))
    }

    @Test
    fun `kpiTrendPercent masque si previous et current nuls`() {
        assertNull(kpiTrendPercent(current = 0.0, previous = 0.0, isCustomRange = false))
    }

    @Test
    fun `kpiTrendPercent hausse enorme (CA 5 euros N-1) donne Huge et pas un pourcentage`() {
        assertEquals(KpiTrend.Huge, kpiTrendPercent(current = 900.0, previous = 5.0, isCustomRange = false))
    }

    @Test
    fun `kpiTrendPercent seuil 1000 pct inclus pour Huge, juste en dessous reste un pourcentage`() {
        assertEquals(KpiTrend.Huge, kpiTrendPercent(current = 1100.0, previous = 100.0, isCustomRange = false))
        val below = kpiTrendPercent(current = 1090.0, previous = 100.0, isCustomRange = false)
        assertEquals(990.0, (below as KpiTrend.Percent).value, 0.0001)
    }

    @Test
    fun `kpiTrendPercent baisse totale reste un pourcentage`() {
        val result = kpiTrendPercent(current = 0.0, previous = 50.0, isCustomRange = false)
        assertEquals(-100.0, (result as KpiTrend.Percent).value, 0.0001)
    }

    @Test
    fun `kpiTrendPercent masque en mode plage libre meme avec previous valide`() {
        assertNull(kpiTrendPercent(current = 1200.0, previous = 1000.0, isCustomRange = true))
    }

    // ─── kpiTrendPercentFromSeries (commandes, nouveaux clients, panier moyen) ─

    @Test
    fun `kpiTrendPercentFromSeries hausse — 2e moitie superieure a la 1ere`() {
        // 1re moitie = [10, 10] = 20 ; 2e moitie = [15, 15] = 30 → +50%
        val result = kpiTrendPercentFromSeries(listOf(10.0, 10.0, 15.0, 15.0), isCustomRange = false)
        assertEquals(50.0, (result as KpiTrend.Percent).value, 0.0001)
    }

    @Test
    fun `kpiTrendPercentFromSeries baisse — 2e moitie inferieure a la 1ere`() {
        // 1re moitie = [20, 20] = 40 ; 2e moitie = [10, 10] = 20 → -50%
        val result = kpiTrendPercentFromSeries(listOf(20.0, 20.0, 10.0, 10.0), isCustomRange = false)
        assertEquals(-50.0, (result as KpiTrend.Percent).value, 0.0001)
    }

    @Test
    fun `kpiTrendPercentFromSeries plat — moities identiques`() {
        val result = kpiTrendPercentFromSeries(listOf(10.0, 10.0, 10.0, 10.0), isCustomRange = false)
        assertEquals(0.0, (result as KpiTrend.Percent).value, 0.0001)
    }

    @Test
    fun `kpiTrendPercentFromSeries gere une taille impaire (mediane exclue de la 1ere moitie)`() {
        // size=5, mid=2 → 1re moitie=[10,10]=20, 2e moitie=[10,20,20]=50 → +150%
        val result = kpiTrendPercentFromSeries(listOf(10.0, 10.0, 10.0, 20.0, 20.0), isCustomRange = false)
        assertEquals(150.0, (result as KpiTrend.Percent).value, 0.0001)
    }

    @Test
    fun `kpiTrendPercentFromSeries masque si serie vide`() {
        assertNull(kpiTrendPercentFromSeries(emptyList(), isCustomRange = false))
    }

    @Test
    fun `kpiTrendPercentFromSeries masque si un seul point (pas de comparaison possible)`() {
        assertNull(kpiTrendPercentFromSeries(listOf(42.0), isCustomRange = false))
    }

    @Test
    fun `kpiTrendPercentFromSeries nouv si 1re moitie a somme nulle`() {
        assertEquals(KpiTrend.New, kpiTrendPercentFromSeries(listOf(0.0, 0.0, 5.0, 5.0), isCustomRange = false))
    }

    @Test
    fun `kpiTrendPercentFromSeries masque si les deux moities sont nulles`() {
        assertNull(kpiTrendPercentFromSeries(listOf(0.0, 0.0, 0.0, 0.0), isCustomRange = false))
    }

    @Test
    fun `kpiTrendPercentFromSeries Huge si plus de dix fois`() {
        assertEquals(KpiTrend.Huge, kpiTrendPercentFromSeries(listOf(1.0, 1.0, 20.0, 20.0), isCustomRange = false))
    }

    @Test
    fun `kpiTrendPercentFromSeries masque en mode plage libre meme avec serie valide`() {
        assertNull(kpiTrendPercentFromSeries(listOf(10.0, 10.0, 15.0, 15.0), isCustomRange = true))
    }
}
