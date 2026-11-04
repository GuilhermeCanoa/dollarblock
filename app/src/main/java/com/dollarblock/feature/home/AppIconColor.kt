package com.dollarblock.feature.home

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor

/**
 * Cor de marca de um app, tirada do próprio ícone — pinta a fatia dele na barra do orçamento
 * da Home (Instagram rosa, TikTok ciano, YouTube vermelho), sem tabela de marcas para manter.
 *
 * Pega a cor saturada mais frequente: ignora transparência, quase-branco, quase-preto e
 * cinzas (fundo e contorno da maioria dos ícones). Null quando o ícone não tem cor viva.
 */
object AppIconColor {

    fun dominant(bitmap: Bitmap): Int? {
        val step = maxOf(1, bitmap.width / 48)
        val buckets = HashMap<Int, IntArray>() // bucket → [contagem, somaR, somaG, somaB]
        val hsv = FloatArray(3)
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val px = bitmap.getPixel(x, y)
                if (AndroidColor.alpha(px) < 200) continue
                AndroidColor.colorToHSV(px, hsv)
                if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue
                val r = AndroidColor.red(px)
                val g = AndroidColor.green(px)
                val b = AndroidColor.blue(px)
                val key = (r shr 5 shl 6) or (g shr 5 shl 3) or (b shr 5)
                val acc = buckets.getOrPut(key) { IntArray(4) }
                acc[0]++
                acc[1] += r
                acc[2] += g
                acc[3] += b
            }
        }
        val best = buckets.values.maxByOrNull { it[0] } ?: return null
        return AndroidColor.rgb(best[1] / best[0], best[2] / best[0], best[3] / best[0])
    }
}
