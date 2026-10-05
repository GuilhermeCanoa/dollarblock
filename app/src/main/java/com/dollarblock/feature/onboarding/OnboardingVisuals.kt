package com.dollarblock.feature.onboarding

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.components.BrandShield
import kotlinx.coroutines.delay

/**
 * false quando o usuário desligou as animações do sistema ("Remover animações" na
 * Acessibilidade, ou escala de animação 0). Aí o onboarding mostra direto o estado final
 * de cada página, sem loops.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/**
 * Entra uma vez, quando a página fica [active]: sobe e aparece, na ordem [order] (cada
 * degrau atrasa [stepMs]). [fromTop] faz o conteúdo descer, como papel saindo da maquininha.
 */
@Composable
fun Reveal(
    active: Boolean,
    order: Int,
    modifier: Modifier = Modifier,
    stepMs: Long = 110L,
    fromTop: Boolean = false,
    content: @Composable () -> Unit,
) {
    val animate = rememberAnimationsEnabled()
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(active) {
        if (active && progress.value < 1f) {
            delay(order * stepMs)
            progress.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        }
    }
    val distance = with(LocalDensity.current) { 20.dp.toPx() }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * distance * if (fromTop) -1f else 1f
        },
    ) { content() }
}

/** Barra de progresso do onboarding (substitui as bolinhas): enche página a página. */
@Composable
fun OnboardingProgress(current: Int, count: Int, modifier: Modifier = Modifier) {
    val target = (current + 1f) / count
    val progress by animateFloatAsState(target, tween(400), label = "onboardingProgress")
    val description = stringResource(R.string.onb_progress_description, current + 1, count)
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        drawStopIndicator = {},
    )
}

/**
 * Carimbo inclinado, na mesma estética do BLOQUEADO do recibo: entra grande e "bate" no
 * papel, com haptic. Usado no "CONCEDIDA" das permissões e no "CONTA ABERTA" do fim.
 */
@Composable
fun Stamp(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
    borderWidth: Dp = 2.dp,
) {
    val animate = rememberAnimationsEnabled()
    val haptic = LocalHapticFeedback.current
    val scale = remember { Animatable(if (animate) 2.2f else 1f) }
    val alpha = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (scale.value == 1f) return@LaunchedEffect
        alpha.snapTo(1f)
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 900f))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = fontSize,
        letterSpacing = 2.sp,
        color = color,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer {
                rotationZ = -8f
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .border(borderWidth, color, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** "CONCEDIDA" — carimbo verde que aparece quando a permissão da página foi dada. */
@Composable
fun GrantedStamp(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
    borderWidth: Dp = 2.dp,
) {
    Stamp(
        text = stringResource(R.string.perm_status_granted).uppercase(),
        color = DollarBlockTheme.colors.success,
        modifier = modifier,
        fontSize = fontSize,
        borderWidth = borderWidth,
    )
}

/** Telas das Configurações do Android que a ilustração de "como ativar" encena. */
enum class HowToStep {
    /** Lista de apps: o toque cai em DollarBlock. */
    LIST,

    /** A chave do DollarBlock, que liga. */
    TOGGLE,

    /** O diálogo de confirmação do sistema (Acessibilidade): toque em Permitir. */
    CONFIRM,
}

private data class HowToFrame(
    val step: HowToStep,
    val tapping: Boolean,
    val switchOn: Boolean,
    val durationMs: Long,
)

/** Roteiro em loop: cada tela aparece, recebe o toque e passa à seguinte; termina com a chave ligada. */
private fun howToFrames(steps: List<HowToStep>): List<HowToFrame> = buildList {
    steps.forEach { step ->
        add(HowToFrame(step, tapping = false, switchOn = false, durationMs = 650))
        add(HowToFrame(step, tapping = true, switchOn = false, durationMs = 550))
    }
    add(HowToFrame(HowToStep.TOGGLE, tapping = false, switchOn = true, durationMs = 1_500))
}

/**
 * Ilustração animada de como ligar uma permissão nas Configurações, desenhada em Compose:
 * não depende de print, segue o tema e vale para qualquer marca de celular (o desenho é
 * uma versão simplificada da tela, não uma cópia). Para o leitor de tela, é uma imagem só,
 * descrita por [description].
 */
@Composable
fun PermissionHowTo(
    screenTitle: String,
    steps: List<HowToStep>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 196.dp,
) {
    val frames = remember(steps) { howToFrames(steps) }
    val animate = rememberAnimationsEnabled()
    var index by remember { mutableIntStateOf(if (animate) 0 else frames.lastIndex) }
    if (animate) {
        LaunchedEffect(frames) {
            while (true) {
                delay(frames[index].durationMs)
                index = (index + 1) % frames.size
            }
        }
    }
    val frame = frames[index]
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = screenTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            AnimatedContent(
                targetState = frame.step,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "howToStep",
            ) { step ->
                when (step) {
                    HowToStep.LIST -> HowToList(tapping = frame.tapping && frame.step == HowToStep.LIST)
                    HowToStep.TOGGLE, HowToStep.CONFIRM -> HowToToggle(
                        switchOn = frame.switchOn,
                        tapping = frame.tapping && frame.step == HowToStep.TOGGLE,
                    )
                }
            }
            // Qualificado: dentro do Box da Column, o AnimatedVisibility sem receiver seria
            // resolvido para o ColumnScope de fora.
            androidx.compose.animation.AnimatedVisibility(
                visible = frame.step == HowToStep.CONFIRM,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(160)),
                modifier = Modifier.matchParentSize(),
            ) {
                // O diálogo do sistema escurece o que está atrás — sem isso parece parte da tela.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                ) {
                    HowToConfirm(tapping = frame.tapping)
                }
            }
        }
    }
}

@Composable
private fun HowToList(tapping: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PlaceholderRow()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (tapping) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            BrandShield(size = 26.dp, cornerRadius = 7.dp, glow = false)
            Text(
                text = stringResource(R.string.onb_howto_dollarblock),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
            )
            TapMark(visible = tapping)
        }
        PlaceholderRow()
    }
}

@Composable
private fun PlaceholderRow() {
    val tone = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(tone),
        )
        Box(
            Modifier
                .padding(start = 10.dp)
                .width(96.dp)
                .height(10.dp)
                .clip(CircleShape)
                .background(tone),
        )
    }
}

@Composable
private fun HowToToggle(switchOn: Boolean, tapping: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
            BrandShield(size = 34.dp, cornerRadius = 9.dp, glow = false)
            Text(
                text = stringResource(R.string.onb_howto_dollarblock),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(10.dp)
                    .padding(end = 48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)),
            )
            Box(contentAlignment = Alignment.Center) {
                Switch(checked = switchOn, onCheckedChange = null)
                TapMark(visible = tapping)
            }
        }
    }
}

@Composable
private fun HowToConfirm(tapping: Boolean) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        repeat(2) { line ->
            Box(
                Modifier
                    .padding(bottom = 6.dp)
                    .fillMaxWidth(if (line == 0) 1f else 0.7f)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
                    .align(Alignment.Start),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.onb_howto_allow),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            TapMark(visible = tapping)
        }
    }
}

/** O "dedo": um círculo que surge sobre o alvo do toque. */
@Composable
private fun TapMark(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(initialScale = 1.8f, animationSpec = tween(260)) + fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), CircleShape),
        )
    }
}
