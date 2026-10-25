package com.dollarblock.core.locale

import android.content.Context
import android.content.res.Resources
import android.telephony.TelephonyManager

/**
 * País onde o aparelho está, para escolher a moeda (ver
 * [com.dollarblock.domain.model.AppCurrency.forRegion]).
 *
 * Ordem: país do chip → país da rede celular → região do idioma do celular. O idioma vem
 * por último porque muito brasileiro usa o celular em inglês (en-US) — pelo idioma, ele
 * viraria americano. Nenhuma das leituras pede permissão.
 */
object DeviceRegion {

    fun current(context: Context): String {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        return pick(
            telephony?.simCountryIso,
            telephony?.networkCountryIso,
            // `Resources.getSystem()` ignora o override de idioma por app (ver LocaleManager).
            Resources.getSystem().configuration.locales[0]?.country,
        )
    }

    /** Primeiro código não vazio, em maiúsculas; vazio se nenhum. Puro, para teste em JVM. */
    fun pick(vararg candidates: String?): String =
        candidates.firstOrNull { !it.isNullOrBlank() }?.trim()?.uppercase().orEmpty()
}
