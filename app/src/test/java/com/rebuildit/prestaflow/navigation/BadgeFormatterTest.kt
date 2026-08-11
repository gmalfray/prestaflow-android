package com.rebuildit.prestaflow.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BadgeFormatterTest {
    @Test
    fun `un compte nul ne produit aucune pastille`() {
        assertNull(formatBadgeCount(0))
    }

    @Test
    fun `un compte negatif ne produit aucune pastille`() {
        assertNull(formatBadgeCount(-1))
    }

    @Test
    fun `un compte positif est affiche tel quel`() {
        assertEquals("97", formatBadgeCount(97))
    }

    @Test
    fun `un seul fil non lu est affiche`() {
        assertEquals("1", formatBadgeCount(1))
    }

    @Test
    fun `un compte de 99 est affiche tel quel (borne du plafond)`() {
        assertEquals("99", formatBadgeCount(99))
    }

    @Test
    fun `un compte au-dela de 99 est plafonne a 99 plus`() {
        assertEquals("99+", formatBadgeCount(100))
        assertEquals("99+", formatBadgeCount(481))
    }
}
