package com.rebuildit.prestaflow.domain.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `fromTag retrouve la langue pour un tag connu`() {
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTag("fr"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTag("es"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromTag("de"))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.fromTag("it"))
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.fromTag("pt"))
        assertEquals(AppLanguage.DUTCH, AppLanguage.fromTag("nl"))
    }

    @Test
    fun `fromTag renvoie null pour un tag null (mode Systeme)`() {
        assertNull(AppLanguage.fromTag(null))
    }

    @Test
    fun `fromTag renvoie null pour un tag inconnu`() {
        assertNull(AppLanguage.fromTag("xx"))
    }
}
