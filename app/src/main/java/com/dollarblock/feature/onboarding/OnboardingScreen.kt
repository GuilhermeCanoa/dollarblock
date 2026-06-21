package com.dollarblock.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.components.PrimaryActionButton
import com.dollarblock.data.permissions.AppPermission
import com.dollarblock.data.permissions.PermissionsState
import kotlinx.coroutines.launch

private data class ConceptPage(
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int,
)

private val conceptPages = listOf(
    ConceptPage(Icons.Filled.Savings, R.string.onb_welcome_title, R.string.onb_welcome_body),
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

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.recheckPermissions()
    }

    val pageCount = conceptPages.size + 1 // páginas de conceito + página de permissões
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
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
            if (page < conceptPages.size) {
                ConceptPageContent(conceptPages[page])
            } else {
                PermissionsPageContent(
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

@Composable
private fun ConceptPageContent(page: ConceptPage, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
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
