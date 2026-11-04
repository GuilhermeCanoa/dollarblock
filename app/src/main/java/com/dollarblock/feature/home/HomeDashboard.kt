package com.dollarblock.feature.home

import com.dollarblock.domain.model.MonitoredAppUsage

/** Faixa de cor da barra de um app na Home: verde, âmbar (passou da metade), vermelho (trancado). */
enum class UsageLevel { NO_LIMIT, OK, WARN, LOCKED }

/** Uma linha de "Apps na conta". [progress] em 0..1 (null sem limite). */
data class HomeAppRow(
    val packageName: String,
    val appName: String,
    val usedMinutes: Int,
    val limitMinutes: Int?,
    val level: UsageLevel,
    val progress: Float?,
)

/** Orçamento do dia: soma dos limites dos apps com limite e quanto já foi usado deles. */
data class DailyBudget(
    val usedMinutes: Int,
    val limitMinutes: Int,
    /** Fatia de cada app na barra (fração do total; a soma nunca passa de 1). */
    val segments: List<Pair<String, Float>>,
)

/** App com limite que vai trancar primeiro, e em quantos minutos. */
data class NextToClose(val packageName: String, val minutesLeft: Int)

/**
 * Regras puras do painel da Home (redesenho de out/2026): linhas por app, barra do orçamento,
 * "próximo a fechar" e o app mais insistido. Testáveis na JVM, longe do Compose.
 */
object HomeDashboard {

    /** Máximo de apps em "Apps na conta" — os mais urgentes; o resto fica em Gerenciar. */
    const val MAX_APP_ROWS = 3

    /** A partir daqui a barra do app fica âmbar: metade do limite já foi. */
    const val WARN_RATIO = 0.5f

    fun level(usedMinutes: Int, limitMinutes: Int?): UsageLevel = when {
        limitMinutes == null || limitMinutes <= 0 -> UsageLevel.NO_LIMIT
        usedMinutes >= limitMinutes -> UsageLevel.LOCKED
        usedMinutes.toFloat() / limitMinutes >= WARN_RATIO -> UsageLevel.WARN
        else -> UsageLevel.OK
    }

    /**
     * Até [MAX_APP_ROWS] linhas, ordenadas por urgência: trancados, depois mais perto do
     * limite; sem limite por último.
     */
    fun appRows(usage: List<MonitoredAppUsage>): List<HomeAppRow> =
        usage.filter { it.isMonitored }
            .map { app ->
                val limit = app.dailyLimitMinutes?.takeIf { it > 0 }
                HomeAppRow(
                    packageName = app.packageName,
                    appName = app.appName,
                    usedMinutes = app.usedMinutesToday,
                    limitMinutes = limit,
                    level = level(app.usedMinutesToday, limit),
                    progress = limit?.let { (app.usedMinutesToday.toFloat() / it).coerceIn(0f, 1f) },
                )
            }
            .sortedWith(
                compareBy<HomeAppRow> { if (it.limitMinutes == null) 1 else 0 }
                    .thenByDescending { it.progress ?: 0f }
                    .thenByDescending { it.usedMinutes },
            )
            .take(MAX_APP_ROWS)

    /** Null quando nenhum app tem limite — sem orçamento, não há barra. */
    fun budget(usage: List<MonitoredAppUsage>): DailyBudget? {
        val limited = usage.filter { it.isMonitored && (it.dailyLimitMinutes ?: 0) > 0 }
        if (limited.isEmpty()) return null
        val limitTotal = limited.sumOf { it.dailyLimitMinutes ?: 0 }
        val usedTotal = limited.sumOf { it.usedMinutesToday }
        // Estourou o orçamento: as fatias encolhem na proporção para caber na barra inteira.
        val scale = maxOf(usedTotal, limitTotal).toFloat()
        val segments = limited
            .filter { it.usedMinutesToday > 0 }
            .sortedByDescending { it.usedMinutesToday }
            .map { it.packageName to it.usedMinutesToday / scale }
        return DailyBudget(usedMinutes = usedTotal, limitMinutes = limitTotal, segments = segments)
    }

    /** App com limite ainda aberto que tranca primeiro; null se não houver nenhum. */
    fun nextToClose(usage: List<MonitoredAppUsage>): NextToClose? =
        usage.filter { it.isMonitored && (it.dailyLimitMinutes ?: 0) > 0 }
            .map { it.packageName to (it.dailyLimitMinutes!! - it.usedMinutesToday) }
            .filter { (_, left) -> left > 0 }
            .minByOrNull { (_, left) -> left }
            ?.let { (pkg, left) -> NextToClose(pkg, left) }

    /** App com mais tentativas hoje (empate: o primeiro em ordem alfabética, para não piscar). */
    fun topAttempts(attemptsByPackage: Map<String, Int>): String? =
        attemptsByPackage.filterValues { it > 0 }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key
}
