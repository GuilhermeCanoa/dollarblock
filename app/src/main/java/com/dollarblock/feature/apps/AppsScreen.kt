package com.dollarblock.feature.apps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.components.ScreenHeader

/**
 * Apps — busca para adicionar apps ao monitoramento; lista apenas os apps já monitorados.
 */
@Composable
fun AppsScreen(
    modifier: Modifier = Modifier,
    viewModel: AppsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val limitChangeNotice by viewModel.limitChangeNotice.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<AppUsageRow?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.apps_title),
                subtitle = stringResource(R.string.apps_subtitle),
                subtitleStyle = MaterialTheme.typography.titleLarge,
                subtitleColor = MaterialTheme.colorScheme.primary,
                subtitleTextAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        item {
            AppsSearchField(
                query = uiState.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
            )
        }

        // Sugestões de busca (apps não monitorados que batem com a query)
        if (uiState.searchQuery.isNotBlank()) {
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
            } else if (uiState.searchSuggestions.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.apps_search_empty, uiState.searchQuery),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            } else {
                items(uiState.searchSuggestions, key = { "suggestion_${it.packageName}" }) { row ->
                    AppSuggestionItem(
                        row = row,
                        onAdd = { viewModel.addMonitoredFromSearch(row.packageName, row.label) },
                    )
                }
            }
        }

        // Separador / título dos monitorados (só quando não está pesquisando)
        if (uiState.searchQuery.isBlank()) {
            if (uiState.monitoredRows.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.apps_empty_state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.apps_monitored_count, uiState.monitoredRows.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(uiState.monitoredRows, key = { it.packageName }) { row ->
                    AppListItem(
                        row = row,
                        onToggle = { checked -> viewModel.setMonitored(row.packageName, row.label, checked) },
                        onClickLimit = { viewModel.openLimitEditor(row.packageName) },
                    )
                }
            }

            // Sugeridos (ralos de tempo clássicos instalados e fora do taxímetro) —
            // sempre acima dos desativados quando ambas as seções existem.
            if (uiState.suggestedRows.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.apps_suggested_header),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(uiState.suggestedRows, key = { "suggested_${it.packageName}" }) { row ->
                    AppSuggestionItem(
                        row = row,
                        onAdd = { viewModel.addMonitoredFromSearch(row.packageName, row.label) },
                    )
                }
            }

            if (uiState.deactivatedRows.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.apps_deactivated_count, uiState.deactivatedRows.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(uiState.deactivatedRows, key = { "deactivated_${it.packageName}" }) { row ->
                    DeactivatedAppListItem(
                        row = row,
                        onReactivate = { viewModel.setMonitored(row.packageName, row.label, true) },
                        onRequestDelete = { pendingDelete = row },
                    )
                }
            }
        }
    }

    val editingRow = uiState.editingLimitFor
    if (editingRow != null) {
        DailyLimitDialog(
            row = editingRow,
            onDismiss = viewModel::dismissLimitEditor,
            onConfirm = { minutes -> viewModel.confirmDailyLimit(editingRow.packageName, minutes) },
        )
    }

    limitChangeNotice?.let { notice ->
        LimitChangeNoticeDialog(
            notice = notice,
            onDismiss = viewModel::dismissLimitChangeNotice,
        )
    }

    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.apps_deactivated_delete_confirm_title, row.label)) },
            text = { Text(stringResource(R.string.apps_deactivated_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDeactivatedApp(row.packageName)
                    pendingDelete = null
                }) {
                    Text(
                        text = stringResource(R.string.apps_deactivated_delete_confirm_action),
                        color = DollarBlockTheme.colors.penalty,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.apps_deactivated_delete_confirm_cancel))
                }
            },
        )
    }
}

/** Campo de busca por nome do app, com ícone de lupa e botão de limpar quando há texto. */
@Composable
private fun AppsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.apps_search_placeholder)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.apps_search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun AppSuggestionItem(
    row: AppUsageRow,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onAdd),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        border = BorderStroke(1.dp, DollarBlockTheme.colors.glow.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppAvatar(icon = row.icon, letter = row.label.firstOrNull() ?: '?')
            Spacer(Modifier.size(12.dp))
            Text(
                text = row.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AppListItem(
    row: AppUsageRow,
    onToggle: (Boolean) -> Unit,
    onClickLimit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val limit = row.dailyLimitMinutes
    val overtimeMinutes = if (limit != null) (row.usedMinutesToday - limit).coerceAtLeast(0) else 0
    val overLimit = limit != null && row.usedMinutesToday >= limit
    var showSabotageWarning by remember(row.packageName) { mutableStateOf(false) }
    val displayedUsed = if (limit != null) minOf(row.usedMinutesToday, limit) else row.usedMinutesToday

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        border = BorderStroke(1.dp, DollarBlockTheme.colors.glow.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppAvatar(icon = row.icon, letter = row.label.firstOrNull() ?: '?')
                Spacer(Modifier.size(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onClickLimit),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = row.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (row.unlockedToday) {
                            Spacer(Modifier.size(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DollarBlockTheme.colors.glow.copy(alpha = 0.18f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.apps_unlocked_today_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DollarBlockTheme.colors.glow,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    Text(
                        text = if (limit != null) {
                            stringResource(
                                R.string.apps_used_of_limit,
                                formatMinutes(displayedUsed),
                                formatMinutes(limit),
                            )
                        } else {
                            stringResource(R.string.apps_used_no_limit, formatMinutes(row.usedMinutesToday))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (overLimit) DollarBlockTheme.colors.penalty
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (overLimit && overtimeMinutes > 0) {
                        Text(
                            text = stringResource(R.string.apps_over_limit_text, formatMinutes(overtimeMinutes)),
                            style = MaterialTheme.typography.labelSmall,
                            color = DollarBlockTheme.colors.penalty,
                        )
                    }
                    if (limit == null) {
                        Text(
                            text = stringResource(R.string.apps_tap_to_set_limit),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Switch(
                    checked = row.isMonitored,
                    onCheckedChange = { checked ->
                        if (!checked && overLimit) {
                            showSabotageWarning = true
                        } else {
                            onToggle(checked)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            if (limit != null) {
                Spacer(Modifier.height(12.dp))

                // Barra 1: uso dentro do limite (trava em 100% quando excedido)
                val limitRatio = (row.usedMinutesToday.toFloat() / limit).coerceIn(0f, 1f)
                UsageBar(
                    label = stringResource(R.string.apps_limit_label),
                    progress = if (row.isMonitored) limitRatio else 0f,
                    color = if (overLimit) DollarBlockTheme.colors.penalty
                            else MaterialTheme.colorScheme.primary,
                )

                // Barra 2: overtime total acumulado (aparece quando passou do limite)
                if (overLimit && overtimeMinutes > 0) {
                    Spacer(Modifier.height(8.dp))
                    // Cresce de 0 a 100% conforme overtime vai de 0 ao limite; trava em 100%
                    val overtimeTotalRatio = (overtimeMinutes.toFloat() / limit).coerceIn(0f, 1f)
                    UsageBar(
                        label = stringResource(R.string.apps_overtime_bar_label, formatMinutes(overtimeMinutes)),
                        progress = if (row.isMonitored) overtimeTotalRatio else 0f,
                        color = DollarBlockTheme.colors.penalty,
                    )
                }
            }
        }
    }

    if (showSabotageWarning) {
        SabotageWarningDialog(
            appLabel = row.label,
            onConfirm = {
                showSabotageWarning = false
                onToggle(false)
            },
            onDismiss = { showSabotageWarning = false },
        )
    }
}

/**
 * Aviso exibido ao desativar o monitoramento de um app que já passou do limite hoje —
 * é a saída fácil de driblar a fatura sem pagar. Tom da casa (MANIFESTO.md · "Como falamos").
 */
@Composable
private fun SabotageWarningDialog(
    appLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.apps_sabotage_warning_title)) },
        text = {
            Text(
                text = stringResource(R.string.apps_sabotage_warning_body, appLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.apps_sabotage_warning_confirm),
                    color = DollarBlockTheme.colors.penalty,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.apps_sabotage_warning_cancel))
            }
        },
    )
}

/** Item da lista de "Desativados": permite reativar com um toque ou excluir de vez. */
@Composable
private fun DeactivatedAppListItem(
    row: AppUsageRow,
    onReactivate: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        ),
        border = BorderStroke(1.dp, DollarBlockTheme.colors.glow.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppAvatar(icon = row.icon, letter = row.label.firstOrNull() ?: '?')
            Spacer(Modifier.size(12.dp))
            Text(
                text = row.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = false,
                onCheckedChange = { onReactivate() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
            IconButton(onClick = onRequestDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.apps_deactivated_delete),
                    tint = DollarBlockTheme.colors.penalty,
                )
            }
        }
    }
}

@Composable
private fun UsageBar(
    label: String,
    progress: Float,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun AppAvatar(icon: ImageBitmap?, letter: Char, modifier: Modifier = Modifier) {
    if (icon != null) {
        androidx.compose.foundation.Image(
            bitmap = icon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(44.dp)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            DollarBlockTheme.colors.glow,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = letter.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** Teto do limite diário: o dia só tem 24 horas. */
private const val MAX_DAILY_LIMIT_MINUTES = 24 * 60

private fun formatMinutes(total: Int): String {
    val hours = total / 60
    val minutes = total % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * Aviso pós-troca de limite: irônico quando o usuário afrouxa, seco e aprovador quando aperta.
 * Copy no tom da casa (MANIFESTO.md · "Como falamos") — a fatura, nunca a bronca.
 */
@Composable
private fun LimitChangeNoticeDialog(
    notice: LimitChangeNotice,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                stringResource(
                    if (notice.increased) R.string.apps_limit_increased_title
                    else R.string.apps_limit_decreased_title,
                ),
            )
        },
        text = {
            Text(
                text = stringResource(
                    if (notice.increased) R.string.apps_limit_increased_body
                    else R.string.apps_limit_decreased_body,
                    formatMinutes(notice.previousMinutes),
                    formatMinutes(notice.newMinutes),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.apps_limit_notice_ok))
            }
        },
    )
}

/**
 * Diálogo para definir (ou remover) o limite diário de um app, em minutos.
 * `onConfirm(null)` remove o limite; `onConfirm(minutos)` salva um novo valor válido (> 0).
 */
@Composable
private fun DailyLimitDialog(
    row: AppUsageRow,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(row.packageName) {
        mutableStateOf(row.dailyLimitMinutes?.toString() ?: "")
    }
    val minutes = text.trim().toIntOrNull()
    val isInvalid = text.isNotBlank() &&
        (minutes == null || minutes <= 0 || minutes > MAX_DAILY_LIMIT_MINUTES)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(text = stringResource(R.string.apps_limit_dialog_title, row.label))
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.apps_limit_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.apps_limit_dialog_minutes_label)) },
                    placeholder = { Text(stringResource(R.string.apps_limit_dialog_no_limit)) },
                    singleLine = true,
                    isError = isInvalid,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isInvalid) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.apps_limit_dialog_invalid),
                        style = MaterialTheme.typography.labelSmall,
                        color = DollarBlockTheme.colors.penalty,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(minutes) },
                enabled = !isInvalid,
            ) {
                Text(stringResource(R.string.apps_limit_dialog_save))
            }
        },
        dismissButton = {
            Row {
                if (row.dailyLimitMinutes != null) {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text(
                            text = stringResource(R.string.apps_limit_dialog_remove),
                            color = DollarBlockTheme.colors.penalty,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.apps_limit_dialog_cancel))
                }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun AppsScreenPreview() {
    DollarBlockTheme {
        // Preview sem Hilt: a tela real depende de hiltViewModel(), então o preview
        // aqui só valida o tema/layout estático ao redor (sem dados injetados).
    }
}
