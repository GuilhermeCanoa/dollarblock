package com.dollarblock.feature.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.components.PreviewBanner
import com.dollarblock.core.designsystem.components.ScreenHeader

/**
 * Apps — lista de apps monitorados com uso vs limite e toggle de monitoramento.
 * No E0.5 usa dados de exemplo; o E3 substitui por apps instalados reais (PackageManager).
 */
@Composable
fun AppsScreen(modifier: Modifier = Modifier) {
    val monitored = remember {
        mutableStateMapOf<String, Boolean>().apply {
            previewApps.forEach { put(it.name, it.defaultMonitored) }
        }
    }
    val activeCount = monitored.count { it.value }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.apps_title),
                subtitle = stringResource(R.string.apps_subtitle),
            )
        }
        item { PreviewBanner() }
        item {
            Text(
                text = stringResource(R.string.apps_monitored_count, activeCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(previewApps, key = { it.name }) { app ->
            AppListItem(
                app = app,
                monitored = monitored[app.name] == true,
                onToggle = { monitored[app.name] = it },
            )
        }
    }
}

@Composable
private fun AppListItem(
    app: PreviewApp,
    monitored: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ratio = (app.usedMinutes.toFloat() / app.limitMinutes).coerceIn(0f, 1f)
    val overLimit = app.usedMinutes >= app.limitMinutes
    val progressColor = if (overLimit) {
        DollarBlockTheme.colors.penalty
    } else {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppAvatar(letter = app.name.first(), color = app.accent)
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.apps_used_of_limit,
                            formatMinutes(app.usedMinutes),
                            formatMinutes(app.limitMinutes),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (overLimit) {
                            DollarBlockTheme.colors.penalty
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Switch(
                    checked = monitored,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { if (monitored) ratio else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun AppAvatar(letter: Char, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

private fun formatMinutes(total: Int): String {
    val hours = total / 60
    val minutes = total % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private data class PreviewApp(
    val name: String,
    val accent: Color,
    val usedMinutes: Int,
    val limitMinutes: Int,
    val defaultMonitored: Boolean,
)

private val previewApps = listOf(
    PreviewApp("Instagram", Color(0xFFE1306C), usedMinutes = 78, limitMinutes = 60, defaultMonitored = true),
    PreviewApp("TikTok", Color(0xFF222222), usedMinutes = 52, limitMinutes = 45, defaultMonitored = true),
    PreviewApp("YouTube", Color(0xFFFF0000), usedMinutes = 35, limitMinutes = 90, defaultMonitored = true),
    PreviewApp("Reddit", Color(0xFFFF4500), usedMinutes = 20, limitMinutes = 30, defaultMonitored = true),
    PreviewApp("X", Color(0xFF1DA1F2), usedMinutes = 41, limitMinutes = 40, defaultMonitored = true),
    PreviewApp("Facebook", Color(0xFF1877F2), usedMinutes = 12, limitMinutes = 30, defaultMonitored = false),
    PreviewApp("Games", Color(0xFF7C3AED), usedMinutes = 65, limitMinutes = 60, defaultMonitored = true),
)

@Preview(showBackground = true)
@Composable
private fun AppsScreenPreview() {
    DollarBlockTheme {
        AppsScreen()
    }
}
