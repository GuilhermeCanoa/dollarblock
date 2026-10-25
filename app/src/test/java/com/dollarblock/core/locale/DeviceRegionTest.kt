package com.dollarblock.core.locale

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRegionTest {

    @Test
    fun `chip vence a rede e o idioma`() {
        // Brasileiro com celular em inglês (en-US): o chip brasileiro decide.
        assertEquals("BR", DeviceRegion.pick("br", "us", "US"))
    }

    @Test
    fun `sem chip cai para a rede e depois para o idioma`() {
        assertEquals("US", DeviceRegion.pick("", "us", "BR"))
        assertEquals("US", DeviceRegion.pick(null, " ", "US"))
        assertEquals("", DeviceRegion.pick(null, null, null))
    }
}
