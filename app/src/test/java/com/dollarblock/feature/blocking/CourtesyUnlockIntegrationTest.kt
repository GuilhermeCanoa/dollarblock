package com.dollarblock.feature.blocking

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dollarblock.data.local.db.DollarBlockDatabase
import com.dollarblock.data.repository.EventsRepositoryImpl
import com.dollarblock.domain.model.AppDay
import com.dollarblock.domain.model.BlockReason
import com.dollarblock.domain.model.MoneyReport
import com.dollarblock.domain.model.PaymentMethod
import com.dollarblock.domain.model.RecentEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Fluxo do desbloqueio de cortesia (E17) ponta a ponta na camada de dados: repositório
 * real + Room in-memory, sem emulador.
 *
 * Garante que a cortesia é **auditável** — aparece no extrato marcada como tal e com valor
 * zero — e que ela não contamina a contabilidade de dinheiro da Home: um passe que saiu de
 * graça não pode ser somado como gasto nem contado como "resistido".
 */
@RunWith(RobolectricTestRunner::class)
class CourtesyUnlockIntegrationTest {

    private lateinit var db: DollarBlockDatabase
    private lateinit var repository: EventsRepositoryImpl

    private val pkg = "com.instagram.android"
    private val label = "Instagram"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DollarBlockDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = EventsRepositoryImpl(db.eventDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Reproduz o que a BlockActivity grava em onCourtesyUnlock. */
    private suspend fun recordCourtesy() = repository.recordUnlock(
        packageName = pkg,
        appLabel = label,
        amount = "0.00",
        currency = "BRL",
        method = PaymentMethod.COURTESY,
    )

    @Test
    fun `cortesia entra no extrato marcada como cortesia e com valor zero`() = runTest {
        recordCourtesy()

        val event = repository.recentEvents(limit = 10).first()
            .filterIsInstance<RecentEvent.Unlocked>()
            .single()

        assertEquals(PaymentMethod.COURTESY, event.method)
        assertEquals("0.00", event.amount)
        assertEquals(label, event.appLabel)
    }

    @Test
    fun `cortesia nao soma nada no total gasto`() = runTest {
        repository.recordUnlock(pkg, label, "5.00", "BRL", PaymentMethod.PLAY_BILLING)
        recordCourtesy()

        val amounts = db.eventDao().observeUnlockAmounts().first()

        assertEquals(5.0, MoneyReport.totalSpent(amounts), 0.001)
    }

    @Test
    fun `dia liberado por cortesia nao conta como passe resistido`() = runTest {
        // O usuário foi bloqueado e acabou abrindo o app (de graça, por erro nosso):
        // não resistiu à tentação, então não pode entrar no "economizado".
        repository.recordBlock(pkg, label, BlockReason.DAILY_LIMIT)
        recordCourtesy()

        val day = 20_000L
        val resisted = MoneyReport.resistedAppDays(
            blocks = listOf(AppDay(pkg, day)),
            unlocks = listOf(AppDay(pkg, day)),
        )

        assertEquals(0, resisted)
    }

    @Test
    fun `cortesia conta como desbloqueio do dia para o tom da tela de bloqueio`() = runTest {
        recordCourtesy()

        // unlocksPaidToday alimenta a mensagem escalonada do recibo; a cortesia é um
        // desbloqueio como outro qualquer para efeito de "quantas vezes hoje".
        assertEquals(1, repository.unlocksPaidToday())
    }

    @Test
    fun `pagamento normal e cortesia coexistem no historico`() = runTest {
        repository.recordUnlock(pkg, label, "5.00", "BRL", PaymentMethod.PLAY_BILLING)
        recordCourtesy()

        val methods = repository.recentEvents(limit = 10).first()
            .filterIsInstance<RecentEvent.Unlocked>()
            .map { it.method }

        assertEquals(2, methods.size)
        assertTrue(methods.contains(PaymentMethod.COURTESY))
        assertTrue(methods.contains(PaymentMethod.PLAY_BILLING))
    }
}
