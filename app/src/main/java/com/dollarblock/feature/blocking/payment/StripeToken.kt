package com.dollarblock.feature.blocking.payment

import org.json.JSONObject

/**
 * Extrai o identificador de token do Stripe a partir do campo
 * `paymentMethodData.tokenizationData.token` devolvido pelo Google Pay.
 *
 * Com o gateway `stripe`, o Google Pay devolve esse campo como um **JSON
 * stringificado** de um objeto de token do Stripe, por exemplo:
 *
 * ```json
 * {"id":"tok_1ABC...","object":"token","card":{...},"type":"card",...}
 * ```
 *
 * O backend (`/unlock-charge`) usa o valor recebido diretamente como
 * `card: { token }` na criação do PaymentIntent, então ele precisa receber
 * apenas o `id` (`tok_...`), e não o JSON inteiro. Encaminhar o JSON cru faz o
 * Stripe rejeitar a cobrança — era a causa do erro no pagamento.
 */
object StripeToken {

    /**
     * Retorna o `id` do token Stripe pronto para enviar ao backend.
     *
     * - Se [rawToken] for o JSON do objeto de token, devolve o campo `id`.
     * - Se já for um token cru (`tok_...` / `pm_...`), devolve-o como está
     *   (cobre o gateway de teste `example` e respostas inesperadas).
     * - Devolve `null` se não houver nada utilizável.
     */
    fun extractId(rawToken: String?): String? {
        val token = rawToken?.trim().orEmpty()
        if (token.isEmpty()) return null

        // Caso comum do gateway stripe: JSON com o objeto de token.
        if (token.startsWith("{")) {
            val id = runCatching { JSONObject(token).optString("id") }.getOrNull()
            return id?.takeIf { it.isNotBlank() }
        }

        // Token já cru (gateway de teste "example" ou formato alternativo).
        return token
    }
}
