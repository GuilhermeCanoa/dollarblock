package com.dollarblock.feature.apps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstDayPassOfferTest {

    private fun offer(
        isInstallDay: Boolean = true,
        used: Int = 180,
        limit: Int? = 120,
        unlocked: Boolean = false,
        answered: Boolean = false,
    ) = shouldOfferFirstDayPass(isInstallDay, used, limit, unlocked, answered)

    @Test
    fun `primeiro dia com uso acima do novo limite oferece o passe`() = assertTrue(offer())

    @Test
    fun `uso exatamente no limite tambem oferece - ja trancaria`() = assertTrue(offer(used = 120, limit = 120))

    @Test
    fun `uso abaixo do limite nao oferece`() = assertFalse(offer(used = 30))

    @Test
    fun `fora do dia da instalacao nao oferece`() = assertFalse(offer(isInstallDay = false))

    @Test
    fun `remover o limite nao oferece`() = assertFalse(offer(limit = null))

    @Test
    fun `app ja liberado hoje nao oferece`() = assertFalse(offer(unlocked = true))

    @Test
    fun `oferta ja respondida nao repete`() = assertFalse(offer(answered = true))
}
