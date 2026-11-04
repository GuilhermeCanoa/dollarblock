package com.dollarblock.service.nudge

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Catálogo das "cobranças do gerente" (E22): notificações que põem preço no tempo de tela
 * fora do aviso de limite. Os ids seguem a lista aprovada (A1…E24; C12 foi cortada).
 * Textos em `strings.xml` (`nudge_<id>`), mapeados em [NudgeNotifier].
 */
enum class Nudge(val group: Group) {
    // A — sessão longa (app monitorado aberto há [NudgePolicy.SESSION_THRESHOLD_MIN] seguidos)
    A1_WALK(Group.SESSION),
    A2_JUST_CHECKING(Group.SESSION),
    A3_NO_PAUSE(Group.SESSION),
    A4_MOVIE(Group.SESSION),
    A5_ROBBED(Group.SESSION),

    // B — projeção anual (sorteadas no mesmo gatilho da sessão, se houver histórico)
    B6_DAYS_AWAKE(Group.PROJECTION),
    B7_BOOKS(Group.PROJECTION),
    B8_MARATHONS(Group.PROJECTION),
    B9_VACATION(Group.PROJECTION),
    B10_ANNUAL_BILL(Group.PROJECTION),
    B11_SPANISH(Group.PROJECTION),

    // C — roubo e cobrança (C13–C16 no gatilho da sessão; C17 no hábito de horário)
    C13_THEFT(Group.SESSION),
    C14_CRUMBS(Group.SESSION),
    C15_BILLIONS(Group.SESSION),
    C16_SALARY(Group.SESSION),
    C17_AUTO_DEBIT(Group.HABIT),

    // D — fechamento do dia (21h)
    D18_CLOSING(Group.DAILY_CLOSE),
    D19_WONT_RETURN(Group.DAILY_CLOSE),
    D20_CLEAN_DAY(Group.DAILY_CLOSE),

    // E — hábitos e passe do dia
    E21_EARLY_OPEN(Group.HABIT),
    E22_STRAIGHT_FROM_UNLOCK(Group.HABIT),
    E23_PASS_FIRST(Group.PASS),
    E24_PASS_SECOND(Group.PASS),
    ;

    enum class Group { SESSION, PROJECTION, HABIT, DAILY_CLOSE, PASS }
}

/**
 * Contas das projeções anuais (B6–B11), a partir da média diária de uso de um app.
 * Referências: livro médio ≈ 7 h de leitura; plano de maratona ≈ 80 h de treino
 * (16–20 semanas × 4–5 h); curso completo de espanhol ≈ 700 h (FSI, 600–750 h).
 */
object NudgeMath {
    const val BOOK_HOURS = 7.0
    const val MARATHON_HOURS = 80.0
    const val SPANISH_COURSE_HOURS = 700.0

    /** Abaixo disso a projeção anual dá números pequenos demais para impressionar. */
    const val MIN_AVG_DAILY_MINUTES_FOR_PROJECTION = 15.0

    fun annualHours(avgDailyMinutes: Double): Int = (avgDailyMinutes * 365 / 60).roundToInt()

    /** Dias inteiros de 24 h. */
    fun annualDays(avgDailyMinutes: Double): Int = (avgDailyMinutes * 365 / 60 / 24).toInt()

    fun books(avgDailyMinutes: Double): Int = (annualHours(avgDailyMinutes) / BOOK_HOURS).toInt()

    fun marathons(avgDailyMinutes: Double): Int = (annualHours(avgDailyMinutes) / MARATHON_HOURS).toInt()

    fun spanishPercent(avgDailyMinutes: Double): Int =
        (annualHours(avgDailyMinutes) / SPANISH_COURSE_HOURS * 100).roundToInt()

    fun annualValue(avgDailyMinutes: Double, perMinuteRate: Double): Double = avgDailyMinutes * 365 * perMinuteRate

    /**
     * Quais projeções fazem sentido para essa média: cada frase só entra quando o número
     * dela é crível (ex.: "0 maratonas" ou "100% de um curso" ficam de fora).
     * [annualItemAvailable] = o valor anual tem uma comparação em unidades inteiras.
     */
    fun eligibleProjections(avgDailyMinutes: Double?, annualItemAvailable: Boolean): List<Nudge> {
        if (avgDailyMinutes == null || avgDailyMinutes < MIN_AVG_DAILY_MINUTES_FOR_PROJECTION) return emptyList()
        return buildList {
            if (annualDays(avgDailyMinutes) >= 1) {
                add(Nudge.B6_DAYS_AWAKE)
                add(Nudge.B9_VACATION)
            }
            if (books(avgDailyMinutes) >= 2) add(Nudge.B7_BOOKS)
            if (marathons(avgDailyMinutes) >= 1) add(Nudge.B8_MARATHONS)
            if (annualItemAvailable) add(Nudge.B10_ANNUAL_BILL)
            if (spanishPercent(avgDailyMinutes) in 5..99) add(Nudge.B11_SPANISH)
        }
    }

    /**
     * Média diária de uso (minutos) nos dias com registro, ou null com menos de
     * [minDays] dias — sem histórico, a projeção seria chute.
     */
    fun averageDailyMinutes(dailyMillis: List<Long>, minDays: Int = 3): Double? {
        if (dailyMillis.size < minDays) return null
        return dailyMillis.average() / 60_000.0
    }

    /**
     * Horário habitual da primeira abertura (minuto do dia) quando ao menos [minDays] dos
     * dias observados caem a até [toleranceMin] min da mediana — o "débito automático"
     * do C17. Null quando não há padrão.
     */
    fun habitMinuteOfDay(firstOpenMinutes: List<Int>, minDays: Int = 5, toleranceMin: Int = 45): Int? {
        if (firstOpenMinutes.size < minDays) return null
        val median = firstOpenMinutes.sorted()[firstOpenMinutes.size / 2]
        val close = firstOpenMinutes.count { abs(it - median) <= toleranceMin }
        return if (close >= minDays) median else null
    }
}

/**
 * Regras de frequência: as cobranças não podem virar spam, senão o usuário desliga tudo —
 * inclusive o aviso de limite, que é o que importa.
 */
object NudgePolicy {
    /** Teto diário de cobranças (o fechamento do dia e o aviso de limite ficam de fora). */
    const val DAILY_CAP = 2

    const val SESSION_THRESHOLD_MIN = 30

    /** Primeira abertura "antes da padaria": até 9h. */
    const val EARLY_OPEN_BEFORE_MINUTE = 9 * 60

    /** Fechamento do dia a partir das 21h. */
    const val DAILY_CLOSE_FROM_MINUTE = 21 * 60

    /** "Desbloqueou e foi direto": app aberto até 10 s depois do desbloqueio. */
    const val STRAIGHT_FROM_UNLOCK_WINDOW_MS = 10_000L

    /** Intervalo mínimo (dias) entre repetições de cada cobrança de hábito. */
    const val EARLY_OPEN_COOLDOWN_DAYS = 3
    const val STRAIGHT_FROM_UNLOCK_COOLDOWN_DAYS = 2
    const val AUTO_DEBIT_COOLDOWN_DAYS = 7

    /** Abrir a até 45 min do horário habitual conta como "por volta de". */
    const val AUTO_DEBIT_TOLERANCE_MIN = 45

    fun capReached(sentToday: Int): Boolean = sentToday >= DAILY_CAP

    /** true se a última vez foi há [cooldownDays] dias ou mais (ou nunca). */
    fun cooledDown(lastEpochDay: Long?, today: Long, cooldownDays: Int): Boolean =
        lastEpochDay == null || today - lastEpochDay >= cooldownDays

    /** Sorteia do [pool] evitando repetir a [last] cobrança enviada (se houver alternativa). */
    fun pick(pool: List<Nudge>, last: Nudge?, random: kotlin.random.Random = kotlin.random.Random): Nudge? {
        if (pool.isEmpty()) return null
        val fresh = pool.filter { it != last }.ifEmpty { pool }
        return fresh.random(random)
    }

    /**
     * Qual fechamento do dia: estourou algum limite → D18 ou D19 (D19 precisa do app que
     * mais tomou tempo); nenhum estouro com limites ativos → D20; sem limites → D18.
     */
    fun dailyClosePool(anyLimitExceeded: Boolean, hasLimits: Boolean, hasTopApp: Boolean): List<Nudge> = when {
        anyLimitExceeded -> listOfNotNull(Nudge.D18_CLOSING, Nudge.D19_WONT_RETURN.takeIf { hasTopApp })
        hasLimits -> listOf(Nudge.D20_CLEAN_DAY)
        else -> listOf(Nudge.D18_CLOSING)
    }
}
