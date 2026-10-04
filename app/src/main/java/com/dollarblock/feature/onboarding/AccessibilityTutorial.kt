package com.dollarblock.feature.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dollarblock.R
import kotlinx.coroutines.delay

/**
 * Quadros recortados das Configurações do Android (emulador API 36, pt-BR), com o alvo de
 * cada toque contornado: lista de Acessibilidade → chave "Usar DollarBlock" → "Permitir" →
 * chave ligada. Gerados por captura via adb; ver docs/specs/E20-tutorial-acessibilidade.md.
 */
private val tutorialFrames = listOf(
    R.drawable.a11y_tutorial_1,
    R.drawable.a11y_tutorial_2,
    R.drawable.a11y_tutorial_3,
    R.drawable.a11y_tutorial_4,
)

private const val FRAME_MS = 1_400L

/** Pop-up sem texto: os passos para ligar a Acessibilidade, em loop, como um GIF. */
@Composable
fun AccessibilityTutorialDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            AccessibilityTutorialAnimation(modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun AccessibilityTutorialAnimation(modifier: Modifier = Modifier) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(FRAME_MS)
            frame = (frame + 1) % tutorialFrames.size
        }
    }
    val description = stringResource(R.string.onb_a11y_tutorial_description)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Crossfade(
            targetState = frame,
            animationSpec = tween(durationMillis = 250),
            label = "a11yTutorialFrame",
        ) { index ->
            Image(
                painter = painterResource(tutorialFrames[index]),
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(540f / 500f)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            tutorialFrames.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == frame) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }
    }
}
