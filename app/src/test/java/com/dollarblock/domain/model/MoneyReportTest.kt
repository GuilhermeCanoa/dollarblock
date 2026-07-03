package com.dollarblock.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyReportTest {

    @Test
    fun `totalSpent soma valores em texto`() {
        assertEquals(3.5, MoneyReport.totalSpent(listOf("1.00", "1.50", "1.00")), 0.001)
    }

    @Test
    fun `totalSpent aceita virgula decimal e ignora lixo`() {
        assertEquals(2.0, MoneyReport.totalSpent(listOf("1,00", "abc", "1.00")), 0.001)
    }

    @Test
    fun `totalSpent de lista vazia eh zero`() {
        assertEquals(0.0, MoneyReport.totalSpent(emptyList()), 0.001)
    }

    @Test
    fun `dia bloqueado sem pagamento conta como resistido`() {
        val blocks = listOf(AppDay("com.instagram.android", 100))
        assertEquals(1, MoneyReport.resistedAppDays(blocks, emptyList()))
    }

    @Test
    fun `dia bloqueado com pagamento do mesmo app nao conta`() {
        val blocks = listOf(AppDay("com.instagram.android", 100))
        val unlocks = listOf(AppDay("com.instagram.android", 100))
        assertEquals(0, MoneyReport.resistedAppDays(blocks, unlocks))
    }

    @Test
    fun `varios block events do mesmo app no mesmo dia contam uma vez`() {
        val blocks = listOf(
            AppDay("com.instagram.android", 100),
            AppDay("com.instagram.android", 100),
            AppDay("com.instagram.android", 100),
        )
        assertEquals(1, MoneyReport.resistedAppDays(blocks, emptyList()))
    }

    @Test
    fun `pagamento em outro dia nao apaga a resistencia`() {
        val blocks = listOf(
            AppDay("com.instagram.android", 100),
            AppDay("com.instagram.android", 101),
        )
        val unlocks = listOf(AppDay("com.instagram.android", 101))
        assertEquals(1, MoneyReport.resistedAppDays(blocks, unlocks))
    }

    @Test
    fun `pagamento de outro app nao apaga a resistencia`() {
        val blocks = listOf(AppDay("com.instagram.android", 100))
        val unlocks = listOf(AppDay("com.google.android.youtube", 100))
        assertEquals(1, MoneyReport.resistedAppDays(blocks, unlocks))
    }

    @Test
    fun `totalSaved multiplica resistidos pelo preco do passe`() {
        val blocks = listOf(
            AppDay("com.instagram.android", 100),
            AppDay("com.google.android.youtube", 100),
        )
        assertEquals(2.0, MoneyReport.totalSaved(blocks, emptyList(), dayPassPrice = 1.0), 0.001)
    }
}
