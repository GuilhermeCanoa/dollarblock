package com.dollarblock.feature.home

import com.dollarblock.domain.model.AppCurrency

/**
 * Catálogo de coisas reais com que o prejuízo do dia é comparado na conta da Home.
 * Preço `null` = o item não faz sentido naquela moeda (ex.: pão de queijo em USD) e
 * fica fora do sorteio.
 *
 * Preços de referência de out/2026 — não precisam ser exatos, só críveis. Revisar uma
 * vez por ano (principalmente as ações, que oscilam). Fontes principais: Big Mac Index
 * (The Economist), ANP (gasolina), páginas de preço de Netflix/Spotify; o resto é preço
 * médio de balcão.
 *
 * Os textos ficam em `strings.xml` (`home_equiv_<item>` + `home_equiv_<item>_fraction`),
 * mapeados em `ComparisonItemText.kt`.
 */
enum class ComparisonItem(val priceBrl: Double?, val priceUsd: Double?) {
    BUS_FARE(5.00, 3.00),
    PAO_DE_QUEIJO(5.00, null),
    COFFEE(6.00, 3.50),
    GAS(6.53, 3.15), // BRL por litro (ANP); USD por galão
    COXINHA(8.00, null),
    EGGS_DOZEN(12.00, 4.00),
    DRAFT_BEER(12.00, 7.00),
    ACAI(22.00, null),
    BIG_MAC(23.90, 5.99),
    SPOTIFY_MONTH(23.90, 12.99),
    UBER_RIDE(25.00, 18.00),
    PETROBRAS_SHARE(32.00, null),
    MOVIE_TICKET(35.00, 12.00),
    PIZZA(45.00, 20.00),
    NETFLIX_MONTH(44.90, 19.99),
    BOOK(50.00, 18.00),
    COCA_COLA_SHARE(null, 70.00),
    PICANHA_KG(80.00, null),
    GYM_MONTH(120.00, 40.00),
    NVIDIA_SHARE(1020.00, 190.00),
    APPLE_SHARE(1340.00, 250.00),
    PS5(3800.00, 499.00),
    IPHONE(7999.00, 799.00),
    ;

    fun priceIn(currency: AppCurrency): Double? = when (currency) {
        AppCurrency.BRL -> priceBrl
        AppCurrency.USD -> priceUsd
    }
}
