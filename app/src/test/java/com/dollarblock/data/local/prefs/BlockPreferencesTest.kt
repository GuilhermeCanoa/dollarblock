package com.dollarblock.data.local.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BlockPreferencesTest {

    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test
    fun `endOfDayMillis retorna meia-noite local do dia seguinte`() {
        val now = LocalDate.of(2026, 7, 2).atTime(15, 30).atZone(zone).toInstant().toEpochMilli()

        val until = BlockPreferences.endOfDayMillis(now, zone)

        val expected = LocalDate.of(2026, 7, 3).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, until)
    }

    @Test
    fun `endOfDayMillis perto da meia-noite ainda pertence ao dia corrente`() {
        val now = LocalDate.of(2026, 7, 2).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()

        val until = BlockPreferences.endOfDayMillis(now, zone)

        assertEquals(
            LocalDate.of(2026, 7, 3),
            Instant.ofEpochMilli(until).atZone(zone).toLocalDate(),
        )
        assertTrue(until > now)
    }

    @Test
    fun `serializa e parseia grants no formato v2`() {
        val grants = mapOf(
            "com.instagram.android" to BlockPreferences.UnlockGrant(1_000L),
            "com.tiktok" to BlockPreferences.UnlockGrant(2_000L),
        )

        val roundTrip = BlockPreferences.parseGrants(BlockPreferences.serializeGrants(grants))

        assertEquals(grants, roundTrip)
    }

    @Test
    fun `parse ignora formato antigo de 3 partes e entradas corrompidas`() {
        val raw = setOf(
            "com.instagram.android|123|456", // formato antigo (janela de 5 min) — descartado
            "com.tiktok|9999",               // formato v2 válido
            "lixo",                          // corrompida
            "com.x|nao-numero",              // corrompida
        )

        val parsed = BlockPreferences.parseGrants(raw)

        assertEquals(mapOf("com.tiktok" to BlockPreferences.UnlockGrant(9999L)), parsed)
    }
}
