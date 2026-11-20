package com.dollarblock.feature.blocking.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dollarblock.R
import com.dollarblock.core.designsystem.NeutralWhite
import com.dollarblock.domain.model.MoneyFormat

/** A estatística pronta para a tela: o tipo escolhido e os dados que o preenchem. */
data class BlockStatShown(val kind: BlockStatKind, val data: BlockStatData, val appLabel: String)

/**
 * A linha da estatística (E27), acima dos botões, igual nas três cenas. A altura fica
 * reservada desde o primeiro frame: quando o dado chega, o texto aparece com fade sem empurrar
 * os botões. Tocar leva para o app ([BlockStatKind.target]).
 */
@Composable
internal fun BlockStatLine(stat: BlockStatShown?, onClick: (BlockStatTarget) -> Unit, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (stat == null) 0f else 1f, tween(350), label = "statAlpha")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (stat == null) return@Box
        val shape = RoundedCornerShape(14.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .graphicsLayer { this.alpha = alpha }
                .clip(shape)
                .border(1.dp, NeutralWhite.copy(alpha = 0.18f), shape)
                .clickable(role = Role.Button) { onClick(stat.kind.target) }
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(
                text = blockStatText(stat),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = NeutralWhite.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = NeutralWhite.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun blockStatText(stat: BlockStatShown): String {
    val input = stat.data.input
    val appLabel = stat.appLabel
    return when (stat.kind) {
        BlockStatKind.STREAK ->
            pluralStringResource(R.plurals.block_stat_streak, input.streakDays, input.streakDays, appLabel)
        BlockStatKind.ATTEMPTS -> stringResource(R.string.block_stat_attempts, input.attemptsToday, appLabel)
        BlockStatKind.OVERAGE -> stringResource(R.string.block_stat_overage, duration(BlockStatRules.overageMinutes(input)))
        BlockStatKind.FIRST_TODAY -> stringResource(R.string.block_stat_first_today)
        BlockStatKind.USAGE_VS_LIMIT -> stringResource(
            R.string.block_stat_usage,
            duration(input.usedMinutes),
            appLabel,
            duration(input.limitMinutes ?: 0),
        )
        BlockStatKind.COUNTDOWN -> stringResource(R.string.block_stat_countdown, duration(input.minutesUntilMidnight))
        BlockStatKind.MONTH_SAVED -> stringResource(
            R.string.block_stat_month_saved,
            MoneyFormat.format(input.monthSaved ?: 0.0, stat.data.currency),
        )
    }
}

@Composable
private fun duration(minutes: Int): String =
    if (minutes >= 60) {
        stringResource(R.string.home_duration_hm, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.home_duration_m, minutes)
    }

/** A portinha (E27): sai para o launcher sem passar pelo app. */
@Composable
internal fun BlockExitDoor(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.DoorFront,
            contentDescription = null,
            tint = NeutralWhite.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.block_exit_door),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = NeutralWhite.copy(alpha = 0.6f),
        )
    }
}
