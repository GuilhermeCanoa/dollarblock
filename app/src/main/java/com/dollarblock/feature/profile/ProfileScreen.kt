package com.dollarblock.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dollarblock.BuildConfig
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.components.ScreenHeader
import com.dollarblock.core.designsystem.components.SectionHeader
import com.dollarblock.data.permissions.AppPermission
import com.dollarblock.data.permissions.PermissionsState

/**
 * Profile — identidade do usuário, estatísticas, permissões e preferências.
 *
 * E8: **Permissões** refletem o estado real (via [ProfileViewModel] + `PermissionsProvider`),
 * re-checado em `ON_RESUME` — tocar numa permissão pendente abre a tela do sistema. O
 * **cabeçalho de estatísticas** (limites ativos, tempo economizado, bloqueios de hoje) usa
 * dados reais. O item **Histórico** abre a tela de eventos reais. Tema/Sobre ainda são mock.
 */
@Composable
fun ProfileScreen(
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-checa as permissões sempre que a tela volta ao foreground (igual ao onboarding).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh() }

    fun requestPermission(permission: AppPermission) {
        if (permission == AppPermission.NOTIFICATIONS &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            notificationsLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.intentFor(permission)?.let { context.startActivity(it) }
        }
    }

    ProfileScreenContent(
        permissions = permissions,
        stats = stats,
        onRequestPermission = ::requestPermission,
        onOpenHistory = onOpenHistory,
        onResetAllData = viewModel::resetAllData,
        modifier = modifier,
    )
}

@Composable
private fun ProfileScreenContent(
    permissions: PermissionsState,
    stats: ProfileStats,
    onRequestPermission: (AppPermission) -> Unit,
    onOpenHistory: () -> Unit,
    onResetAllData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("[DEBUG] Resetar todos os dados?") },
            text = { Text("Apaga o banco de dados, onboarding e preferências. O app voltará à tela inicial na próxima abertura.") },
            confirmButton = {
                Button(
                    onClick = { onResetAllData(); showResetDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Resetar") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancelar") }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.profile_title))

        UserHeaderCard()

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                icon = Icons.Filled.Block,
                value = stats.blocksToday.toString(),
                label = stringResource(R.string.profile_blocks_today),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                icon = Icons.Filled.Savings,
                value = stats.moneyLostToday?.let { formatReais(it) } ?: "—",
                label = stringResource(R.string.home_money_lost_today),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                icon = Icons.Filled.Shield,
                value = stats.activeLimitsCount.toString(),
                label = stringResource(R.string.profile_active_limits),
                modifier = Modifier.weight(1f),
            )
        }

        SectionHeader(text = stringResource(R.string.profile_permissions))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column {
                PermissionRow(
                    icon = Icons.Filled.QueryStats,
                    title = stringResource(R.string.perm_usage),
                    description = stringResource(R.string.perm_usage_desc),
                    granted = permissions.usageAccess,
                    onClick = { onRequestPermission(AppPermission.USAGE_ACCESS) },
                )
                PermissionRow(
                    icon = Icons.Filled.Accessibility,
                    title = stringResource(R.string.perm_accessibility),
                    description = stringResource(R.string.perm_accessibility_desc),
                    granted = permissions.accessibility,
                    onClick = { onRequestPermission(AppPermission.ACCESSIBILITY) },
                )
                PermissionRow(
                    icon = Icons.Filled.Layers,
                    title = stringResource(R.string.perm_overlay),
                    description = stringResource(R.string.perm_overlay_desc),
                    granted = permissions.overlay,
                    onClick = { onRequestPermission(AppPermission.OVERLAY) },
                )
                PermissionRow(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.perm_notifications),
                    description = stringResource(R.string.perm_notifications_desc),
                    granted = permissions.notifications,
                    onClick = { onRequestPermission(AppPermission.NOTIFICATIONS) },
                )
            }
        }

        SectionHeader(text = stringResource(R.string.profile_preferences))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column {
                SettingRow(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.pref_theme),
                    value = stringResource(R.string.pref_theme_value),
                )
                SettingRow(
                    icon = Icons.Filled.History,
                    title = stringResource(R.string.pref_history),
                    value = stringResource(R.string.pref_history_value),
                    onClick = onOpenHistory,
                )
                SettingRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.pref_about),
                    value = null,
                )
            }
        }

        if (BuildConfig.DEBUG) {
            SectionHeader(text = "Debug")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showResetDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Resetar todos os dados",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "Apaga DB, onboarding e preferências",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserHeaderCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.profile_user_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.profile_user_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        StatusChip(granted = granted)
    }
}

@Composable
private fun StatusChip(granted: Boolean, modifier: Modifier = Modifier) {
    val color = if (granted) DollarBlockTheme.colors.success else DollarBlockTheme.colors.alert
    val icon = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Schedule
    val label = stringResource(
        if (granted) R.string.perm_status_granted else R.string.perm_status_pending,
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    value: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun formatReais(value: Double): String =
    "R$ %,.2f".format(value).replace(',', 'X').replace('.', ',').replace('X', '.')

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    DollarBlockTheme {
        ProfileScreenContent(
            permissions = PermissionsState(
                usageAccess = true,
                accessibility = false,
                overlay = false,
                notifications = true,
            ),
            stats = ProfileStats(activeLimitsCount = 3, moneyLostToday = 8.33, blocksToday = 4),
            onRequestPermission = {},
            onOpenHistory = {},
            onResetAllData = {},
        )
    }
}
