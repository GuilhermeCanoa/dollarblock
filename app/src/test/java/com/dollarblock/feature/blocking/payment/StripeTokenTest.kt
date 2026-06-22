package com.dollarblock.feature.blocking.payment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StripeTokenTest {

    @Test
    fun `extracts id from stripe google pay token json`() {
        // Formato real devolvido pelo Google Pay com gateway stripe.
        val raw = """
            {"id":"tok_1ABCdefGHIjklMNO","object":"token","card":{"id":"card_1XYZ",
            "brand":"Visa","last4":"4242"},"client_ip":"1.2.3.4","created":1700000000,
            "livemode":false,"type":"card","used":false}
        """.trimIndent()

        assertEquals("tok_1ABCdefGHIjklMNO", StripeToken.extractId(raw))
    }

    @Test
    fun `passes through a bare token`() {
        assertEquals("tok_visa", StripeToken.extractId("tok_visa"))
        assertEquals("pm_card_visa", StripeToken.extractId("  pm_card_visa  "))
    }

    @Test
    fun `returns null for empty or unusable input`() {
        assertNull(StripeToken.extractId(null))
        assertNull(StripeToken.extractId(""))
        assertNull(StripeToken.extractId("   "))
        // JSON sem campo id.
        assertNull(StripeToken.extractId("""{"object":"token"}"""))
    }
}
