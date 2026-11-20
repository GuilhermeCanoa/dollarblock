package com.dollarblock.feature.celebration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dollarblock.R
import com.dollarblock.core.FeatureFlags
import com.dollarblock.core.designsystem.components.DollarBlockDialog
import com.dollarblock.domain.model.MoneyFormat
import kotlinx.coroutines.delay

/**
 * O parabéns por recusar a fatura (E27), na Home, ao chegar da tela de bloqueio. Frase
 * sorteada entre as variações de `celebration_titles`/`celebration_bodies` (pares na mesma
 * posição), o guardado no mês contando até o valor e a saída para o Extrato.
 */
@Composable
fun CelebrationHost(
    appLabel: String,
    onDone: () -> Unit,
    onOpenStatement: () -> Unit,
    viewModel: CelebrationViewModel = hiltViewModel(),
) {
    val titles = stringArrayResource(R.array.celebration_titles)
    val bodies = stringArrayResource(R.array.celebration_bodies)
    LaunchedEffect(appLabel) {
        // Deixa a splash sair antes de o diálogo subir.
        if (FeatureFlags.SPLASH_ENABLED) delay(FeatureFlags.SPLASH_DURATION_MS)
        viewModel.prepare(appLabel, minOf(titles.size, bodies.size))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    val close = {
        viewModel.dismiss()
        onDone()
    }
    DollarBlockDialog(
        onDismissRequest = close,
        overline = stringResource(R.string.celebration_overline),
        title = titles[current.variant],
        body = bodies[current.variant].format(current.appLabel),
        confirmText = stringResource(R.string.celebration_ok),
        onConfirm = close,
        dismissText = stringResource(R.string.celebration_open_statement),
        onDismiss = {
            close()
            onOpenStatement()
        },
    ) {
        current.saved?.takeIf { it > 0.0 }?.let { saved -> SavedCounter(saved, current) }
    }
}

@Composable
private fun SavedCounter(saved: Double, state: CelebrationUiState) {
    val counter = remember { Animatable(0f) }
    LaunchedEffect(saved) {
        counter.animateTo(saved.toFloat(), tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.celebration_saved_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = MoneyFormat.format(counter.value.toDouble(), state.currency),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        if (state.refusedThisMonth > 0) {
            Text(
                text = pluralStringResource(R.plurals.celebration_refused_count, state.refusedThisMonth, state.refusedThisMonth),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
