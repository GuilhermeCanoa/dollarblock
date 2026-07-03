package com.dollarblock.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Totais monetários exibidos na Home. A UI só conhece esta interface — a fonte hoje é o Room
 * local, mas pode virar uma API sem tocar em feature/home (basta trocar o binding no Hilt).
 */
interface MoneySummaryRepository {

    /** Total já pago pelo usuário em liberações (passes do dia), todas as datas, em BRL. */
    fun observeTotalSpent(): Flow<Double>

    /**
     * Total "economizado": para cada (app, dia) em que a tela de bloqueio apareceu e nenhum
     * pagamento foi feito naquele dia, conta um passe do dia ao preço atual, em BRL.
     */
    fun observeTotalSaved(): Flow<Double>
}
