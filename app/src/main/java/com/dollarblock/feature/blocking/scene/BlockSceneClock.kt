package com.dollarblock.feature.blocking.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.dollarblock.core.designsystem.mascot.MascotPose
import com.dollarblock.feature.onboarding.rememberAnimationsEnabled
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * O tempo de uma cena: [t] é o instante da entrada (s) e, no repouso, [gesture]/[gestureT] o
 * gesto em curso. Tudo o que a cena desenha é função disto ([SceneChoreography]); lido só em
 * lambdas de desenho/graphicsLayer, cada frame redesenha sem recompor.
 */
@Stable
internal class BlockSceneClock(val choreography: SceneChoreography, startAt: Float, val animate: Boolean) {
    var t by mutableFloatStateOf(if (animate) startAt.coerceIn(0f, choreography.entranceSeconds) else choreography.entranceSeconds)
        internal set
    var gesture by mutableStateOf<IdleGesture?>(null)
        internal set
    var gestureT by mutableFloatStateOf(0f)
        internal set

    val entranceDone: Boolean get() = t >= choreography.entranceSeconds

    fun pose(): MascotPose = gesture?.let { choreography.idle(it, gestureT) } ?: choreography.entrance(t)

    fun controlsAlpha(): Float = choreography.controlsAlpha(t)
}

/**
 * Relógio da cena, já rodando. A entrada roda a 60 fps (≤ 2 s); no repouso, gestos curtos
 * separados por pausas de 6–12 s com `delay` — sem frames entre eles. O laço só existe com a
 * activity em RESUMED: a BlockActivity é re-afirmada por cima do app alvo e não pode deixar
 * animação girando em segundo plano. Se pausar no meio da entrada, volta já na pose final.
 * Com "Remover animações" ligado, nada roda: a cena fica parada na pose final.
 *
 * [startAtSeconds] > 0 continua uma entrada que uma instância anterior da activity começou
 * (re-afirmação do bloqueio, ver [BLOCK_SCENE_REASSERT_WINDOW_MS]).
 */
@Composable
internal fun rememberBlockSceneClock(choreography: SceneChoreography, startAtSeconds: Float): BlockSceneClock {
    val animate = rememberAnimationsEnabled()
    val clock = remember(choreography) { BlockSceneClock(choreography, startAtSeconds, animate) }
    if (!animate) return clock
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(clock) {
        val random = Random(System.nanoTime())
        var resumedBefore = false
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!clock.entranceDone) {
                if (resumedBefore) {
                    clock.t = choreography.entranceSeconds
                } else {
                    val origin = withFrameNanos { it } - (clock.t * 1e9f).toLong()
                    while (!clock.entranceDone) {
                        withFrameNanos { now ->
                            val prev = clock.t
                            val next = ((now - origin) / 1e9f).coerceAtMost(choreography.entranceSeconds)
                            if (choreography.hapticAt.any { it > prev && it <= next }) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            clock.t = next
                        }
                    }
                }
            }
            resumedBefore = true
            while (true) {
                delay((BlockSceneChoreography.gapSeconds(random) * 1000).toLong())
                val gesture = choreography.gestures[random.nextInt(choreography.gestures.size)]
                val duration = choreography.gestureSeconds(gesture)
                try {
                    val start = withFrameNanos { it }
                    clock.gestureT = 0f
                    clock.gesture = gesture
                    while (clock.gestureT < duration) {
                        withFrameNanos { clock.gestureT = ((it - start) / 1e9f).coerceAtMost(duration) }
                    }
                } finally {
                    clock.gesture = null
                    clock.gestureT = 0f
                }
            }
        }
    }
    return clock
}
