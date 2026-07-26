package com.dollarblock.data.usage

import com.dollarblock.data.usage.UsageAggregator.OngoingPolicy
import com.dollarblock.data.usage.UsageAggregator.SessionEvent
import com.dollarblock.data.usage.UsageAggregator.Type
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes puros da agregação de tempo de uso (o coração da medição do DollarBlock).
 * Timestamps em millis; para leitura, uso `min(n) = n * 60_000`.
 */
class UsageAggregatorTest {

    private val app = "com.example.app"
    private val other = "com.example.other"

    private fun min(n: Long) = n * 60_000L
    private fun resumed(pkg: String, t: Long) = SessionEvent(pkg, Type.RESUMED, t)
    private fun paused(pkg: String, t: Long) = SessionEvent(pkg, Type.PAUSED, t)

    // --- Sessões fechadas -------------------------------------------------

    @Test
    fun `soma uma unica sessao fechada`() {
        val events = listOf(resumed(app, min(10)), paused(app, min(25)))
        assertEquals(min(15), UsageAggregator.totalForPackage(app, events))
    }

    @Test
    fun `soma multiplas sessoes fechadas`() {
        val events = listOf(
            resumed(app, min(1)), paused(app, min(3)),   // 2 min
            resumed(app, min(10)), paused(app, min(15)),  // 5 min
        )
        assertEquals(min(7), UsageAggregator.totalForPackage(app, events))
    }

    @Test
    fun `ignora eventos de outros apps`() {
        val events = listOf(
            resumed(other, min(0)), paused(other, min(30)),
            resumed(app, min(10)), paused(app, min(12)),
        )
        assertEquals(min(2), UsageAggregator.totalForPackage(app, events))
    }

    @Test
    fun `sem eventos retorna zero`() {
        assertEquals(0L, UsageAggregator.totalForPackage(app, emptyList()))
    }

    // --- App nunca fechado (sessão em andamento) --------------------------

    @Test
    fun `app aberto e nunca fechado conta ate now via leftover resume`() {
        // RESUMED sem PAUSED; sem override → usa o último RESUMED como início.
        val events = listOf(resumed(app, min(10)))
        val total = UsageAggregator.totalForPackage(
            app, events, OngoingPolicy.CloseAt(now = min(40), lowerBound = min(0)),
        )
        assertEquals(min(30), total)
    }

    @Test
    fun `sessao em andamento ignorada quando policy e Ignore`() {
        val events = listOf(resumed(app, min(10)))
        assertEquals(0L, UsageAggregator.totalForPackage(app, events, OngoingPolicy.Ignore))
    }

    @Test
    fun `override start tem preferencia sobre o leftover resume`() {
        // O serviço de acessibilidade sabe o instante exato do foreground (min 20),
        // mais preciso que o RESUMED lido (min 10).
        val events = listOf(resumed(app, min(10)))
        val total = UsageAggregator.totalForPackage(
            app, events,
            OngoingPolicy.CloseAt(now = min(40), lowerBound = min(0), overrideStart = min(20)),
        )
        assertEquals(min(20), total)
    }

    @Test
    fun `override fora da janela cai de volta para o leftover resume`() {
        // overrideStart no futuro (depois de now) é inválido → usa o RESUMED lido.
        val events = listOf(resumed(app, min(10)))
        val total = UsageAggregator.totalForPackage(
            app, events,
            OngoingPolicy.CloseAt(now = min(40), lowerBound = min(0), overrideStart = min(999)),
        )
        assertEquals(min(30), total)
    }

    @Test
    fun `sessao fechada mais sessao em andamento somam`() {
        val events = listOf(
            resumed(app, min(1)), paused(app, min(6)),  // 5 min fechados
            resumed(app, min(20)),                       // aberta
        )
        val total = UsageAggregator.totalForPackage(
            app, events, OngoingPolicy.CloseAt(now = min(35), lowerBound = min(0)),
        )
        assertEquals(min(5) + min(15), total)
    }

    // --- Virada da meia-noite --------------------------------------------

    @Test
    fun `sessao em andamento iniciada antes da meia-noite e cortada no lower bound`() {
        // Um RESUMED de ontem (antes de startOfDay) não deve contar: fica abaixo do lower bound.
        val startOfDay = min(0)
        val events = listOf(resumed(app, -min(30))) // 30 min antes da meia-noite
        val total = UsageAggregator.totalForPackage(
            app, events, OngoingPolicy.CloseAt(now = min(10), lowerBound = startOfDay),
        )
        assertEquals(0L, total)
    }

    @Test
    fun `override antes da meia-noite e descartado`() {
        val startOfDay = min(0)
        val events = listOf(resumed(app, min(5)))
        // override aponta para antes da meia-noite → inválido; cai no leftover (min 5).
        val total = UsageAggregator.totalForPackage(
            app, events,
            OngoingPolicy.CloseAt(now = min(10), lowerBound = startOfDay, overrideStart = -min(1)),
        )
        assertEquals(min(5), total)
    }

    // --- Eventos fora de ordem / malformados ------------------------------

    @Test
    fun `par invertido (paused antes de resumed) nao gera duracao negativa`() {
        val events = listOf(paused(app, min(3)), resumed(app, min(10)), paused(app, min(2)))
        // PAUSED@3 sem RESUMED aberto → ignorado. RESUMED@10 seguido de PAUSED@2 → delta negativo, descartado.
        assertEquals(0L, UsageAggregator.totalForPackage(app, events))
    }

    @Test
    fun `resumed duplicado usa o ultimo antes do paused`() {
        // Dois RESUMED seguidos sem PAUSED: o segundo sobrescreve o início.
        val events = listOf(resumed(app, min(5)), resumed(app, min(8)), paused(app, min(12)))
        assertEquals(min(4), UsageAggregator.totalForPackage(app, events))
    }

    @Test
    fun `paused sem resumed e ignorado`() {
        val events = listOf(paused(app, min(5)), resumed(app, min(10)), paused(app, min(13)))
        assertEquals(min(3), UsageAggregator.totalForPackage(app, events))
    }

    // --- Multi-app --------------------------------------------------------

    @Test
    fun `multi-app soma cada pacote independentemente numa unica passagem`() {
        val events = listOf(
            resumed(app, min(0)), paused(app, min(10)),      // app: 10
            resumed(other, min(2)), paused(other, min(5)),   // other: 3
            resumed(app, min(20)), paused(app, min(25)),     // app: +5
        )
        val totals = UsageAggregator.totalForPackages(setOf(app, other), events)
        assertEquals(min(15), totals[app])
        assertEquals(min(3), totals[other])
    }

    @Test
    fun `multi-app aplica sessao em andamento so ao pacote em foreground`() {
        val events = listOf(
            resumed(app, min(0)), paused(app, min(5)),   // app fechado: 5
            resumed(other, min(30)),                      // other aberto
        )
        val totals = UsageAggregator.totalForPackages(
            setOf(app, other),
            events,
            ongoingByPackage = mapOf(
                other to OngoingPolicy.CloseAt(now = min(40), lowerBound = min(0)),
            ),
        )
        assertEquals(min(5), totals[app])
        assertEquals(min(10), totals[other])
    }

    @Test
    fun `targets vazio retorna mapa vazio`() {
        val events = listOf(resumed(app, min(0)), paused(app, min(5)))
        assertEquals(emptyMap<String, Long>(), UsageAggregator.totalForPackages(emptySet(), events))
    }

    @Test
    fun `pacote sem uso nao aparece no mapa`() {
        val events = listOf(resumed(app, min(0)), paused(app, min(5)))
        val totals = UsageAggregator.totalForPackages(setOf(app, other), events)
        assertEquals(min(5), totals[app])
        assertEquals(null, totals[other])
    }
}
