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
/**
 * Carteira do mês na Home: o que foi pago em passes, o que foi poupado resistindo e quantas
 * faturas (pares app/dia com bloqueio) houve. A cortesia (valor 0) não conta como paga nem
 * como resistida — a cobrança falhou, ninguém escolheu nada.
 */
data class MonthWallet(
    val paid: Double,
    val passesPaid: Int,
    val saved: Double,
    val passesRefused: Int,
) {
    /** Faturas com decisão do usuário: pagas + recusadas. */
    val bills: Int get() = passesPaid + passesRefused
}

/** Um desbloqueio com valor e método, para separar pagos de cortesias. */
data class UnlockDay(val appDay: AppDay, val amount: String, val method: String)

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

    /** Carteira de um período (o mês, na Home) a partir dos bloqueios e desbloqueios dele. */
    fun wallet(blocks: List<AppDay>, unlocks: List<UnlockDay>, dayPassPrice: Double): MonthWallet {
        val paid = unlocks.filter { it.method != PaymentMethod.COURTESY }
        val refused = resistedAppDays(blocks, unlocks.map { it.appDay })
        return MonthWallet(
            paid = totalSpent(paid.map { it.amount }),
            passesPaid = paid.size,
            saved = refused * dayPassPrice,
            passesRefused = refused,
        )
    }
}
