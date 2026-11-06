package com.dollarblock.core.designsystem.mascot

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

/** false com "Remover animações" ligado (escala de animação 0): nada se move sozinho. */
@Composable
private fun rememberMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/**
 * O escudo que aparece de vez em quando na Home, por trás do número da conta: espia do canto,
 * atravessa a parte de cima ou para para investigar com a lupa (ver [MascotChoreography]).
 * Fica atrás do conteúdo — o chamador o põe primeiro num `Box`, do tamanho do bloco. Os pés
 * pisam em [ground] (medido do topo); [unit] é o tamanho de uma unidade da arte. Sem
 * animações do sistema, não aparece. Decorativo: sem semântica e sem toque.
 */
@Composable
fun HomeMascot(
    modifier: Modifier = Modifier,
    ground: Dp = 140.dp,
    unit: Dp = 0.8.dp,
    firstDelayMs: Long = 4_000L,
) {
    if (!rememberMotionEnabled()) return
    var act by remember { mutableStateOf<HomeAct?>(null) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    var sceneWidth by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val random = Random(System.nanoTime())
        delay(firstDelayMs + random.nextLong(3_000L))
        var previous: HomeAct? = null
        while (true) {
            if (sceneWidth <= 0f) {
                delay(500L)
                continue
            }
            val next = MascotChoreography.nextHomeAct(previous, random)
            val duration = MascotChoreography.homeDuration(next, sceneWidth)
            val start = withFrameNanos { it }
            elapsed = 0f
            act = next
            while (elapsed < duration) {
                withFrameNanos { elapsed = (it - start) / 1e9f }
            }
            act = null
            previous = next
            delay((MascotChoreography.homeGapSeconds(random) * 1000).toLong())
        }
    }

    val density = LocalDensity.current
    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged { sceneWidth = it.width / with(density) { unit.toPx() } },
    ) {
        val unitPx = unit.toPx()
        val current = act ?: return@Canvas
        val pose = MascotChoreography.homePose(current, elapsed, size.width / unitPx, ground.toPx() / unitPx)
        drawShieldMascot(pose, unitPx)
    }
}

/**
 * O escudo reagindo à troca de limite, no topo do aviso: [increased] → inconformado e em
 * dúvida; senão, aprovando. Em loop lento; sem animações, uma pose parada.
 */
@Composable
fun LimitReactionMascot(increased: Boolean, modifier: Modifier = Modifier) {
    val animate = rememberMotionEnabled()
    var time by remember { mutableFloatStateOf(0f) }
    if (animate) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { time = (it - start) / 1e9f }
        }
    }
    Canvas(modifier) {
        // Figura de ~114 unidades dos pés ao "?", com folga em cima e embaixo (sombra).
        val unitPx = size.height / 128f
        val x = size.width / 2f / unitPx
        val ground = 122f
        val pose = if (animate) {
            MascotChoreography.reaction(increased, time, x, ground)
        } else {
            MascotChoreography.reactionStill(increased, x, ground)
        }
        drawShieldMascot(pose, unitPx)
    }
}
