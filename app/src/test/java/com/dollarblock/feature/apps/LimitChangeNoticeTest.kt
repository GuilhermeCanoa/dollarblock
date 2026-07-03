package com.dollarblock.feature.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LimitChangeNoticeTest {

    @Test
    fun `aumento de limite gera aviso ironico`() {
        val notice = classifyLimitChange(previousMinutes = 30, newMinutes = 60)
        assertEquals(LimitChangeNotice(increased = true, previousMinutes = 30, newMinutes = 60), notice)
    }

    @Test
    fun `reducao de limite gera aviso de reconhecimento`() {
        val notice = classifyLimitChange(previousMinutes = 60, newMinutes = 30)
        assertEquals(LimitChangeNotice(increased = false, previousMinutes = 60, newMinutes = 30), notice)
    }

    @Test
    fun `primeiro limite nao gera aviso`() {
        assertNull(classifyLimitChange(previousMinutes = null, newMinutes = 30))
    }

    @Test
    fun `remover limite nao gera aviso`() {
        assertNull(classifyLimitChange(previousMinutes = 30, newMinutes = null))
    }

    @Test
    fun `limite igual nao gera aviso`() {
        assertNull(classifyLimitChange(previousMinutes = 30, newMinutes = 30))
    }
}
