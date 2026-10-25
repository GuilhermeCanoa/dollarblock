package com.dollarblock.core.locale

import com.dollarblock.data.local.prefs.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleManagerTest {

    @Test
    fun `SYSTEM na India aplica hindi`() {
        assertEquals("hi", LocaleManager.resolveLanguageTag(AppLanguage.SYSTEM, "IN"))
        assertEquals("hi", LocaleManager.resolveLanguageTag(AppLanguage.SYSTEM, "in"))
    }

    @Test
    fun `SYSTEM fora da India segue o sistema`() {
        assertEquals("", LocaleManager.resolveLanguageTag(AppLanguage.SYSTEM, "BR"))
        assertEquals("", LocaleManager.resolveLanguageTag(AppLanguage.SYSTEM, "US"))
        assertEquals("", LocaleManager.resolveLanguageTag(AppLanguage.SYSTEM, ""))
    }

    @Test
    fun `escolha explicita vence a regiao`() {
        assertEquals("en", LocaleManager.resolveLanguageTag(AppLanguage.ENGLISH, "IN"))
        assertEquals("pt", LocaleManager.resolveLanguageTag(AppLanguage.PORTUGUESE, "IN"))
        assertEquals("hi", LocaleManager.resolveLanguageTag(AppLanguage.HINDI, "BR"))
    }
}
