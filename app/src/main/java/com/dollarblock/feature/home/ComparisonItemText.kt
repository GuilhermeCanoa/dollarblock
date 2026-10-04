package com.dollarblock.feature.home

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.dollarblock.R

/** Textos de cada [ComparisonItem]: plural para unidades inteiras, string para a fração. */
internal data class ComparisonItemText(@PluralsRes val countRes: Int, @StringRes val fractionRes: Int)

internal val ComparisonItem.text: ComparisonItemText
    get() = when (this) {
        ComparisonItem.BUS_FARE -> ComparisonItemText(R.plurals.home_equiv_bus_fare, R.string.home_equiv_bus_fare_fraction)
        ComparisonItem.PAO_DE_QUEIJO -> ComparisonItemText(R.plurals.home_equiv_pao_de_queijo, R.string.home_equiv_pao_de_queijo_fraction)
        ComparisonItem.COFFEE -> ComparisonItemText(R.plurals.home_equiv_coffee, R.string.home_equiv_coffee_fraction)
        ComparisonItem.GAS -> ComparisonItemText(R.plurals.home_equiv_gas, R.string.home_equiv_gas_fraction)
        ComparisonItem.COXINHA -> ComparisonItemText(R.plurals.home_equiv_coxinha, R.string.home_equiv_coxinha_fraction)
        ComparisonItem.EGGS_DOZEN -> ComparisonItemText(R.plurals.home_equiv_eggs_dozen, R.string.home_equiv_eggs_dozen_fraction)
        ComparisonItem.DRAFT_BEER -> ComparisonItemText(R.plurals.home_equiv_draft_beer, R.string.home_equiv_draft_beer_fraction)
        ComparisonItem.ACAI -> ComparisonItemText(R.plurals.home_equiv_acai, R.string.home_equiv_acai_fraction)
        ComparisonItem.BIG_MAC -> ComparisonItemText(R.plurals.home_equiv_big_mac, R.string.home_equiv_big_mac_fraction)
        ComparisonItem.SPOTIFY_MONTH -> ComparisonItemText(R.plurals.home_equiv_spotify_month, R.string.home_equiv_spotify_month_fraction)
        ComparisonItem.UBER_RIDE -> ComparisonItemText(R.plurals.home_equiv_uber_ride, R.string.home_equiv_uber_ride_fraction)
        ComparisonItem.PETROBRAS_SHARE -> ComparisonItemText(R.plurals.home_equiv_petrobras_share, R.string.home_equiv_petrobras_share_fraction)
        ComparisonItem.MOVIE_TICKET -> ComparisonItemText(R.plurals.home_equiv_movie_ticket, R.string.home_equiv_movie_ticket_fraction)
        ComparisonItem.PIZZA -> ComparisonItemText(R.plurals.home_equiv_pizza, R.string.home_equiv_pizza_fraction)
        ComparisonItem.NETFLIX_MONTH -> ComparisonItemText(R.plurals.home_equiv_netflix_month, R.string.home_equiv_netflix_month_fraction)
        ComparisonItem.BOOK -> ComparisonItemText(R.plurals.home_equiv_book, R.string.home_equiv_book_fraction)
        ComparisonItem.COCA_COLA_SHARE -> ComparisonItemText(R.plurals.home_equiv_coca_cola_share, R.string.home_equiv_coca_cola_share_fraction)
        ComparisonItem.PICANHA_KG -> ComparisonItemText(R.plurals.home_equiv_picanha_kg, R.string.home_equiv_picanha_kg_fraction)
        ComparisonItem.GYM_MONTH -> ComparisonItemText(R.plurals.home_equiv_gym_month, R.string.home_equiv_gym_month_fraction)
        ComparisonItem.NVIDIA_SHARE -> ComparisonItemText(R.plurals.home_equiv_nvidia_share, R.string.home_equiv_nvidia_share_fraction)
        ComparisonItem.APPLE_SHARE -> ComparisonItemText(R.plurals.home_equiv_apple_share, R.string.home_equiv_apple_share_fraction)
        ComparisonItem.PS5 -> ComparisonItemText(R.plurals.home_equiv_ps5, R.string.home_equiv_ps5_fraction)
        ComparisonItem.IPHONE -> ComparisonItemText(R.plurals.home_equiv_iphone, R.string.home_equiv_iphone_fraction)
    }
