package com.dollarblock.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.components.BrandShield
import com.dollarblock.core.designsystem.components.PrimaryActionButton
import com.dollarblock.data.permissions.AppPermission
import com.dollarblock.data.permissions.PermissionsState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class ConceptPage(
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int,
    val isBrand: Boolean = false,
)

private val conceptPages = listOf(
    // A primeira página é a apresentação da marca — usa o emblema do escudo.
    ConceptPage(Icons.Filled.Savings, R.string.onb_welcome_title, R.string.onb_welcome_body, isBrand = true),
    ConceptPage(Icons.Filled.Bolt, R.string.onb_penalty_title, R.string.onb_penalty_body),
)

/**
 * Fluxo de onboarding da primeira execução (E2): apresenta o conceito do DollarBlock,
 * explica como a penalidade funciona e solicita as permissões necessárias, cada uma com
 * o **porquê** e um atalho para a tela do sistema. Concluído uma vez, não volta a aparecer.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val permissionsState by viewModel.permissionsState.collectAsStateWithLifecycle()
    val quickSummaryState by viewModel.quickSummaryState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.recheckPermissions()
        viewModel.reloadQuickSummary()
    }

    // conceito + quick summary + permissões
    val pageCount = conceptPages.size + 2
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val quickSummaryPageIndex = conceptPages.size
    val permissionsPageIndex = pageCount - 1

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            when {
                page < conceptPages.size -> ConceptPageContent(conceptPages[page])
                page == quickSummaryPageIndex -> QuickSummaryPageContent(
                    state = quickSummaryState,
                    onGrantUsageAccess = {
                        viewModel.intentFor(AppPermission.USAGE_ACCESS)?.let { context.startActivity(it) }
                    },
                )
                else -> PermissionsPageContent(
                    state = permissionsState,
                    onRequest = viewModel::intentFor,
                    onRecheck = viewModel::recheckPermissions,
                )
            }
        }

        PageIndicator(
            count = pageCount,
            current = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 16.dp),
        )

        if (pagerState.currentPage < permissionsPageIndex) {
            PrimaryActionButton(
                text = stringResource(R.string.onb_continue),
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(permissionsPageIndex) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.onb_skip))
            }
        } else {
            PrimaryActionButton(
                text = stringResource(R.string.onb_finish),
                onClick = {
                    viewModel.completeOnboarding()
                    onFinished()
                },
                enabled = permissionsState.usageAccess,
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(!permissionsState.usageAccess) {
                Text(
                    text = stringResource(R.string.onb_finish_blocked_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

private val quickSummaryPalette = listOf(
    Color(0xFF00E676), // Emerald Premium
    Color(0xFF64FFDA), // Mint Glow
    Color(0xFF00A86B), // Emerald médio
    Color(0xFF2DD4BF), // Teal
    Color(0xFFA7F432), // Lime
)

@Composable
private fun QuickSummaryPageContent(
    state: QuickSummaryState,
    onGrantUsageAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Quick Summary",
            style = MaterialTheme.typography.headlineMedium,
            color = onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Here's how you used your phone\nin the last 7 days.",
            style = MaterialTheme.typography.bodyMedium,
            color = onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(32.dp))
        } else if (state.topApps.isEmpty()) {
            Spacer(Modifier.height(32.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(80.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.QueryStats,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Usage Access needed",
                style = MaterialTheme.typography.titleMedium,
                color = onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Allow DollarBlock to read your screen time\nso we can show your top apps here.",
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            PrimaryActionButton(
                text = "Grant Usage Access",
                onClick = onGrantUsageAccess,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // Donut chart
            Box(contentAlignment = Alignment.Center) {
                val strokeWidth = 30.dp
                Canvas(modifier = Modifier.size(200.dp)) {
                    val sw = strokeWidth.toPx()
                    val radius = (size.minDimension - sw) / 2f
                    val topLeft = Offset((size.width - radius * 2) / 2f, (size.height - radius * 2) / 2f)
                    val arcSize = Size(radius * 2, radius * 2)
                    var startAngle = -90f

                    drawArc(
                        color = surfaceVariant,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = sw, cap = StrokeCap.Butt),
                    )
                    state.topApps.forEachIndexed { idx, entry ->
                        val sweep = 360f * entry.percentage
                        drawArc(
                            color = quickSummaryPalette[idx % quickSummaryPalette.size],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = sw, cap = StrokeCap.Butt),
                        )
                        startAngle += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Total",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant,
                    )
                    Text(
                        text = state.totalTime,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // App list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                state.topApps.forEachIndexed { idx, entry ->
                    val color = quickSummaryPalette[idx % quickSummaryPalette.size]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (entry.icon != null) {
                                Image(
                                    bitmap = entry.icon,
                                    contentDescription = entry.appName,
                                    modifier = Modifier.size(38.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    text = entry.appName.first().uppercaseChar().toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = color,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = entry.appName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${(entry.percentage * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { entry.percentage },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                color = color,
                                trackColor = surfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConceptPageContent(page: ConceptPage, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (page.isBrand) {
            BrandShield(size = 128.dp, cornerRadius = 28.dp)
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun PermissionsPageContent(
    state: PermissionsState,
    onRequest: (AppPermission) -> android.content.Intent?,
    onRecheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onRecheck() }

    fun handle(permission: AppPermission) {
        if (permission == AppPermission.NOTIFICATIONS &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onRequest(permission)?.let { context.startActivity(it) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.onb_permissions_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.onb_permissions_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        PermissionItem(
            icon = Icons.Filled.QueryStats,
            title = stringResource(R.string.perm_usage),
            why = stringResource(R.string.onb_perm_usage_why),
            required = true,
            granted = state.usageAccess,
            onGrant = { handle(AppPermission.USAGE_ACCESS) },
        )
        PermissionItem(
            icon = Icons.Filled.Accessibility,
            title = stringResource(R.string.perm_accessibility),
            why = stringResource(R.string.onb_perm_accessibility_why),
            required = false,
            granted = state.accessibility,
            onGrant = { handle(AppPermission.ACCESSIBILITY) },
        )
        PermissionItem(
            icon = Icons.Filled.Layers,
            title = stringResource(R.string.perm_overlay),
            why = stringResource(R.string.onb_perm_overlay_why),
            required = false,
            granted = state.overlay,
            onGrant = { handle(AppPermission.OVERLAY) },
        )
        PermissionItem(
            icon = Icons.Filled.Notifications,
            title = stringResource(R.string.perm_notifications),
            why = stringResource(R.string.onb_perm_notifications_why),
            required = false,
            granted = state.notifications,
            onGrant = { handle(AppPermission.NOTIFICATIONS) },
        )
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    why: String,
    required: Boolean,
    granted: Boolean,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (required) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = stringResource(R.string.onb_perm_required),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = why,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        if (granted) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(DollarBlockTheme.colors.success.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = DollarBlockTheme.colors.success,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.perm_status_granted),
                    style = MaterialTheme.typography.labelMedium,
                    color = DollarBlockTheme.colors.success,
                )
            }
        } else {
            OutlinedButton(onClick = onGrant) {
                Text(stringResource(R.string.onb_perm_grant))
            }
        }
    }
}

@Composable
private fun PageIndicator(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { index ->
            val active = index == current
            Box(
                modifier = Modifier
                    .size(if (active) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    ),
            )
        }
    }
}
