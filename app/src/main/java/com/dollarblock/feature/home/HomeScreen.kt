package com.dollarblock.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.components.MetricCard
import com.dollarblock.core.designsystem.components.PenaltyButton
import com.dollarblock.core.designsystem.components.PrimaryActionButton
import com.dollarblock.core.designsystem.components.ScreenHeader
import com.dollarblock.core.designsystem.components.SectionHeader
import com.dollarblock.data.apps.InstalledApp
import com.dollarblock.service.accessibility.accessibilitySettingsIntent
import com.dollarblock.service.accessibility.isAccessibilityServiceEnabled

/**
 * Home — painel diário + controle de bloqueio de apps (E5).
 * O card de bloqueio é funcional; os demais cartões ainda usam placeholders.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        accessibilityEnabled = isAccessibilityServiceEnabled(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.home_title),
            subtitle = stringResource(R.string.msg_on_track),
        )

        BlockControlCard(
            state = uiState,
            accessibilityEnabled = accessibilityEnabled,
            onSelect = viewModel::selectApp,
            onToggle = viewModel::setBlocked,
            onOpenAccessibility = { context.startActivity(accessibilitySettingsIntent()) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = stringResource(R.string.home_daily_score),
                value = "—",
                icon = Icons.Filled.Bolt,
                subtitle = stringResource(R.string.coming_soon),
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = stringResource(R.string.home_time_saved),
                value = "0m",
                icon = Icons.Filled.Savings,
                modifier = Modifier.weight(1f),
            )
        }

        MetricCard(
            title = stringResource(R.string.home_active_limits),
            value = uiState.blockedPackages.size.toString(),
            icon = Icons.Filled.Timer,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader(text = stringResource(R.string.home_recent_events))
        EmptyEventsCard()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BlockControlCard(
    state: HomeUiState,
    accessibilityEnabled: Boolean,
    onSelect: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.block_section_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.block_section_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (accessibilityEnabled) {
                AccessibilityActiveRow()
            } else {
                AccessibilityWarning(onOpenAccessibility = onOpenAccessibility)
            }

            AppSelector(
                apps = state.installedApps,
                selectedPackage = state.selectedPackage,
                loading = state.loadingApps,
                onSelect = onSelect,
            )

            val selected = state.selectedPackage
            if (selected != null) {
                val isBlocked = state.blockedPackages.contains(selected)
                if (isBlocked) {
                    PenaltyButton(
                        text = stringResource(R.string.block_disable_button),
                        onClick = { onToggle(selected, false) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PrimaryActionButton(
                        text = stringResource(R.string.block_enable_button),
                        onClick = { onToggle(selected, true) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (state.blockedPackages.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.block_blocked_list_title,
                        state.blockedPackages.size,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.blockedPackages.forEach { pkg ->
                        val label = state.installedApps.find { it.packageName == pkg }?.label ?: pkg
                        InputChip(
                            selected = true,
                            onClick = { onToggle(pkg, false) },
                            label = { Text(label) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.block_unblock_action),
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSelector(
    apps: List<InstalledApp>,
    selectedPackage: String?,
    loading: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedApp = apps.find { it.packageName == selectedPackage }
    val selectedIcon = selectedApp?.icon

    ExposedDropdownMenuBox(
        expanded = expanded && !loading,
        onExpandedChange = { if (!loading) expanded = it },
    ) {
        OutlinedTextField(
            value = if (loading) stringResource(R.string.block_loading_apps) else selectedApp?.label.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = !loading,
            label = { Text(stringResource(R.string.block_select_label)) },
            placeholder = { Text(stringResource(R.string.block_select_placeholder)) },
            leadingIcon = if (selectedIcon != null) {
                {
                    Image(
                        bitmap = selectedIcon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                null
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            apps.forEach { app ->
                val icon = app.icon
                DropdownMenuItem(
                    text = { Text(app.label) },
                    onClick = {
                        onSelect(app.packageName)
                        expanded = false
                    },
                    leadingIcon = if (icon != null) {
                        {
                            Image(
                                bitmap = icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun AccessibilityWarning(
    onOpenAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = DollarBlockTheme.colors.alert.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = DollarBlockTheme.colors.alert,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.block_a11y_needed_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(R.string.block_a11y_needed_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpenAccessibility) {
            Text(stringResource(R.string.block_a11y_button))
        }
    }
}

@Composable
private fun AccessibilityActiveRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = DollarBlockTheme.colors.success,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.block_a11y_active),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyEventsCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = DollarBlockTheme.colors.success,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.home_no_events),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    DollarBlockTheme {
        HomeScreen()
    }
}
