package com.dollarblock.feature.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dollarblock.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Quadros recortados das Configurações do Android (emulador API 36, pt-BR), com o alvo de
 * cada toque contornado: lista de Acessibilidade → chave "Usar DollarBlock" → "Permitir" →
 * chave ligada. Feitos pelo Guilherme no E20 (commit bff9a0e; script de geração em
 * scripts/a11y-tutorial no master).
 */
private val tutorialFrames = listOf(
    R.drawable.a11y_tutorial_1,
    R.drawable.a11y_tutorial_2,
    R.drawable.a11y_tutorial_3,
    R.drawable.a11y_tutorial_4,
)

private const val FRAME_MS = 2_200L
private const val SLIDE_MS = 500

/** Páginas "virtuais" do pager: o suficiente para o loop parecer infinito nos dois sentidos. */
private const val VIRTUAL_PAGES = 10_000

/**
 * Miniatura do tutorial na página da Tranca: os quadros em loop, pequenos, com selo de
 * "ampliar". Um toque abre [AccessibilityTutorialDialog] com a versão interativa.
 */
@Composable
fun AccessibilityTutorialThumbnail(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(FRAME_MS)
            frame = (frame + 1) % tutorialFrames.size
        }
    }
    val openLabel = stringResource(R.string.onb_a11y_tutorial_open)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = openLabel) { expanded = true }
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(132.dp)
                .aspectRatio(540f / 500f)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
        ) {
            Crossfade(
                targetState = frame,
                animationSpec = tween(durationMillis = 300),
                label = "a11yTutorialThumb",
            ) { index ->
                Image(
                    painter = painterResource(tutorialFrames[index]),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInFull,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = openLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (expanded) {
        AccessibilityTutorialDialog(onDismiss = { expanded = false })
    }
}

/** Pop-up com o tutorial grande e interativo; fecha ao tocar fora ou no ✕. */
@Composable
private fun AccessibilityTutorialDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(12.dp)) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.onb_a11y_tutorial_close))
                }
                AccessibilityTutorialAnimation(modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/**
 * Os passos para ligar a Acessibilidade, em loop, como um GIF.
 * Interativo: um toque pausa/retoma no quadro atual; arrastar mostra o quadro anterior ou o
 * seguinte (o loop não avança enquanto o dedo está na tela); as bolinhas pulam para um quadro.
 */
@Composable
private fun AccessibilityTutorialAnimation(modifier: Modifier = Modifier) {
    val frameCount = tutorialFrames.size
    val startPage = VIRTUAL_PAGES / 2 - (VIRTUAL_PAGES / 2) % frameCount
    val pagerState = rememberPagerState(initialPage = startPage) { VIRTUAL_PAGES }
    var paused by remember { mutableStateOf(false) }
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()

    // Reinicia a contagem a cada quadro novo, pausa ou fim de arraste.
    LaunchedEffect(paused, dragging, pagerState.settledPage) {
        if (paused || dragging) return@LaunchedEffect
        delay(FRAME_MS)
        pagerState.animateScrollToPage(pagerState.settledPage + 1, animationSpec = tween(SLIDE_MS))
    }

    val frame = pagerState.currentPage % frameCount
    val description = stringResource(R.string.onb_a11y_tutorial_description)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(540f / 500f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = description }
                    .pointerInput(Unit) { detectTapGestures { paused = !paused } },
            ) { page ->
                Image(
                    painter = painterResource(tutorialFrames[page % frameCount]),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Qualificado: dentro do Column, o AnimatedVisibility sem receptor ficaria ambíguo.
            androidx.compose.animation.AnimatedVisibility(
                visible = paused,
                enter = fadeIn() + scaleIn(initialScale = 0.7f),
                exit = fadeOut() + scaleOut(targetScale = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            tutorialFrames.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == frame) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == frame) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        )
                        .clickable {
                            val target = pagerState.currentPage - frame + index
                            scope.launch { pagerState.animateScrollToPage(target) }
                        },
                )
            }
        }
    }
}
