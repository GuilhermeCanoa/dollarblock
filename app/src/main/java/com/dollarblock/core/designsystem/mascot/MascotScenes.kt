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
 * O escudo que aparece de vez em quando na Home, por trás do número da conta — primeiro
 * espiando do canto, depois cenas sorteadas (atravessar, investigar, tchau, trombada no
 * número, café, varrer; ver [MascotChoreography]), com pausas sorteadas entre elas. Fica atrás do conteúdo — o chamador o põe
 * primeiro num `Box`, do tamanho do bloco. Os pés pisam em [ground] (medido do topo); [unit]
 * é o tamanho de uma unidade da arte. [numberSpan] dá, em px deste bloco, onde a tinta do
 * número começa e termina (para a trombada acertar a borda). Sem animações do sistema, não
 * aparece. Decorativo: sem semântica e sem toque.
 */
@Composable
fun HomeMascot(
    modifier: Modifier = Modifier,
    ground: Dp = 140.dp,
    unit: Dp = 0.8.dp,
    firstDelayMs: Long = 4_000L,
    numberSpan: () -> Pair<Float, Float>? = { null },
) {
    if (!rememberMotionEnabled()) return
    var act by remember { mutableStateOf<HomeAct?>(null) }
    var stage by remember { mutableStateOf<HomeStage?>(null) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    var sceneWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val unitPx = with(density) { unit.toPx() }
    val groundPx = with(density) { ground.toPx() }

    LaunchedEffect(unitPx, groundPx) {
        val random = Random(System.nanoTime())
        delay(firstDelayMs + random.nextLong(3_000L))
        val recent = ArrayDeque<HomeActKind>()
        while (true) {
            if (sceneWidthPx <= 0f) {
                delay(500L)
                continue
            }
            // O palco é congelado no início do ato: se o número mudar no meio, nada pula.
            val width = sceneWidthPx / unitPx
            val span = numberSpan()
            val now = if (span != null) {
                HomeStage(width, groundPx / unitPx, span.first / unitPx, span.second / unitPx)
            } else {
                HomeStage(width, groundPx / unitPx)
            }
            val next = MascotChoreography.nextHomeAct(recent, random)
            val duration = MascotChoreography.homeDuration(next, now)
            val start = withFrameNanos { it }
            elapsed = 0f
            stage = now
            act = next
            while (elapsed < duration) {
                withFrameNanos { elapsed = (it - start) / 1e9f }
            }
            act = null
            recent.addLast(next.kind)
            if (recent.size > MascotChoreography.RECENT_MEMORY) recent.removeFirst()
            delay((MascotChoreography.homeGapSeconds(random) * 1000).toLong())
        }
    }

    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged { sceneWidthPx = it.width.toFloat() },
    ) {
        val current = act ?: return@Canvas
        val scene = stage ?: return@Canvas
        drawShieldMascot(MascotChoreography.homePose(current, elapsed, scene), unitPx)
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
