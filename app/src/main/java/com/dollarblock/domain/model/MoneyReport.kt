package com.dollarblock.domain.model

/** Um evento reduzido ao que importa para o relatório: o app e o dia local em que ocorreu. */
data class AppDay(val packageName: String, val epochDay: Long)

/**
 * Contabilidade pura dos cards de dinheiro da Home.
 *
 * "Gasto" soma os valores efetivamente pagos em desbloqueios. "Economizado" conta os pares
 * (app, dia) em que houve bloqueio (a fatura chegou) mas nenhum pagamento naquele dia — o
 * usuário viu o preço e resistiu. Agrega por app/dia, e não por evento, porque o serviço de
 * acessibilidade registra vários block_events por sessão e o passe do dia permite no máximo
 * um pagamento por app por dia.
 */
object MoneyReport {

    /** Soma valores monetários armazenados como texto ("1.00"); entradas ilegíveis valem 0. */
    fun totalSpent(amounts: List<String>): Double =
        amounts.sumOf { it.trim().replace(',', '.').toDoubleOrNull() ?: 0.0 }

    /**
     * Número de pares (app, dia) com bloqueio e sem desbloqueio — cada um é um passe do dia
     * que o usuário decidiu não comprar.
     */
    fun resistedAppDays(blocks: List<AppDay>, unlocks: List<AppDay>): Int {
        val paid = unlocks.toSet()
        return blocks.toSet().count { it !in paid }
    }

    /** Valor economizado: passes resistidos × preço do passe do dia. */
    fun totalSaved(blocks: List<AppDay>, unlocks: List<AppDay>, dayPassPrice: Double): Double =
        resistedAppDays(blocks, unlocks) * dayPassPrice
}
