package com.dollarblock.data.usage

/**
 * Lógica **pura** (sem Android) de somar o tempo em foreground a partir de uma
 * sequência de eventos de uso — o coração da medição do DollarBlock, extraído de
 * [UsageStatsProvider] para poder ser testado na JVM com sequências sintéticas.
 *
 * O provider lê os eventos reais do `UsageStatsManager`, traduz cada um para um
 * [SessionEvent] e delega a soma para cá. Assim toda a matemática de sessão
 * (RESUMED/PAUSED, sessão em andamento, virada da meia-noite, eventos fora de
 * ordem, app nunca fechado) fica coberta por teste, sem depender de emulador.
 */
object UsageAggregator {

    /** Tipo de transição de sessão, independente das constantes de `UsageEvents`. */
    enum class Type { RESUMED, PAUSED }

    /**
     * Um evento de uso já traduzido: o app [packageName] entrou (RESUMED) ou saiu
     * (PAUSED/STOPPED → [Type.PAUSED]) do foreground no instante [timeStamp] (epoch millis).
     */
    data class SessionEvent(
        val packageName: String,
        val type: Type,
        val timeStamp: Long,
    )

    /**
     * Como tratar uma sessão que ficou aberta (RESUMED sem PAUSED correspondente) ao
     * fim da sequência — o app ainda estava em foreground quando lemos os eventos.
     */
    sealed interface OngoingPolicy {
        /** Ignora a sessão em andamento (conta só sessões já fechadas). */
        data object Ignore : OngoingPolicy

        /**
         * Fecha a sessão em andamento em [now], contando `now - inícioDaSessão`.
         *
         * O início usado é, em ordem de preferência:
         *  1. [overrideStart], se informado e dentro de `[lowerBound, now]` — normalmente
         *     o instante exato em que o app entrou em foreground, conhecido pelo
         *     serviço de acessibilidade (mais preciso que o último RESUMED lido);
         *  2. senão, o último RESUMED sem PAUSED encontrado na sequência.
         *
         * A sessão só é somada se o início resolvido for `>= lowerBound`
         * (tipicamente a meia-noite local, para não contar tempo de ontem).
         * Se [lowerBound] for 0, qualquer início não-negativo é aceito — usado por
         * `getUsageMillisSince`, onde a janela já começa em `sinceMs`.
         */
        data class CloseAt(
            val now: Long,
            val lowerBound: Long = 0L,
            val overrideStart: Long? = null,
        ) : OngoingPolicy
    }

    /**
     * Soma o tempo total em foreground (millis) de [target] numa sequência de eventos.
     * A sequência é percorrida uma única vez; eventos de outros apps são ignorados.
     */
    fun totalForPackage(
        target: String,
        events: Iterable<SessionEvent>,
        ongoing: OngoingPolicy = OngoingPolicy.Ignore,
    ): Long = totalForPackages(setOf(target), events, ongoingFor(target, ongoing))[target] ?: 0L

    /**
     * Versão multi-app: soma o tempo de cada pacote em [targets] numa única passagem.
     * [ongoingByPackage] descreve, por pacote, se há sessão em andamento a fechar.
     * Pacotes sem entrada usam [OngoingPolicy.Ignore].
     */
    fun totalForPackages(
        targets: Set<String>,
        events: Iterable<SessionEvent>,
        ongoingByPackage: Map<String, OngoingPolicy> = emptyMap(),
    ): Map<String, Long> {
        if (targets.isEmpty()) return emptyMap()

        val totals = mutableMapOf<String, Long>()
        val openResume = mutableMapOf<String, Long>()

        for (event in events) {
            val pkg = event.packageName
            if (pkg !in targets) continue
            when (event.type) {
                Type.RESUMED -> openResume[pkg] = event.timeStamp
                Type.PAUSED -> {
                    val start = openResume.remove(pkg) ?: continue
                    // Ignora pares invertidos (PAUSED com timestamp antes do RESUMED):
                    // eventos fora de ordem não devem gerar duração negativa.
                    val delta = event.timeStamp - start
                    if (delta > 0) totals[pkg] = (totals[pkg] ?: 0L) + delta
                }
            }
        }

        // Sessões que ficaram abertas ao fim da sequência.
        for (pkg in targets) {
            val policy = ongoingByPackage[pkg] ?: OngoingPolicy.Ignore
            if (policy !is OngoingPolicy.CloseAt) continue

            val leftoverResume = openResume[pkg]
            val start = resolveOngoingStart(policy, leftoverResume) ?: continue
            if (start < policy.lowerBound) continue
            val delta = policy.now - start
            if (delta > 0) totals[pkg] = (totals[pkg] ?: 0L) + delta
        }

        return totals
    }

    /**
     * Início da sessão em andamento: [OngoingPolicy.CloseAt.overrideStart] quando estiver
     * dentro de `[lowerBound, now]`, senão o último RESUMED em aberto. Null se não houver
     * nenhum dos dois.
     */
    private fun resolveOngoingStart(policy: OngoingPolicy.CloseAt, leftoverResume: Long?): Long? {
        val override = policy.overrideStart
        if (override != null && override in policy.lowerBound..policy.now) return override
        return leftoverResume
    }

    private fun ongoingFor(pkg: String, policy: OngoingPolicy): Map<String, OngoingPolicy> =
        if (policy is OngoingPolicy.CloseAt) mapOf(pkg to policy) else emptyMap()
}
