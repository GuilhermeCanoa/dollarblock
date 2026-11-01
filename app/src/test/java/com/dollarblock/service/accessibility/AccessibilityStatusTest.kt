package com.dollarblock.service.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityStatusTest {

    private val pkg = "com.dollarblock"
    private val cls = "com.dollarblock.service.accessibility.DollarBlockAccessibilityService"

    private fun enabled(setting: String?) = isServiceInEnabledList(setting, pkg, cls)

    @Test
    fun `reconhece o formato completo (AOSP, Samsung)`() {
        assertTrue(enabled("$pkg/$cls"))
    }

    @Test
    fun `reconhece o formato abreviado (Motorola e outros)`() {
        // O bug: o Motorola grava assim e o onboarding pedia a permissão para sempre.
        assertTrue(enabled("$pkg/.service.accessibility.DollarBlockAccessibilityService"))
    }

    @Test
    fun `reconhece o servico no meio de outros`() {
        assertTrue(
            enabled(
                "com.google.android.marvin.talkback/.TalkBackService:" +
                    "$pkg/.service.accessibility.DollarBlockAccessibilityService:" +
                    "com.other.app/com.other.app.Svc",
            ),
        )
    }

    @Test
    fun `ignora caixa e espacos`() {
        assertTrue(enabled(" COM.DOLLARBLOCK/${cls.uppercase()} "))
    }

    @Test
    fun `lista vazia ou nula nao conta como habilitado`() {
        assertFalse(enabled(null))
        assertFalse(enabled(""))
        assertFalse(enabled("null"))
    }

    @Test
    fun `nao confunde com outro servico do mesmo pacote nem com outro pacote`() {
        assertFalse(enabled("$pkg/.service.OutroServico"))
        assertFalse(enabled("com.dollarblock.fake/.service.accessibility.DollarBlockAccessibilityService"))
        assertFalse(enabled("com.other.app/$cls"))
    }
}
