package com.dollarblock

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.components.DollarBlockDialog
import com.dollarblock.core.navigation.DollarBlockBottomBar
import com.dollarblock.core.navigation.DollarBlockNavHost
import com.dollarblock.data.local.prefs.AppTheme
import com.dollarblock.data.permissions.AppPermission
import com.dollarblock.feature.onboarding.OnboardingScreen
import com.dollarblock.feature.onboarding.OnboardingViewModel
import com.dollarblock.feature.profile.PROFILE_ROUTE
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity única que hospeda o app. A primeira execução é roteada para o fluxo de
 * onboarding (E2 — conceito + permissões); concluído o onboarding, exibe as abas
 * (Home / Apps / Statistics / Profile). O roteamento é decidido pela flag persistida
 * em [com.dollarblock.data.local.prefs.OnboardingPreferences].
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val theme by mainViewModel.theme.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (theme) {
                AppTheme.DARK -> true
                AppTheme.LIGHT -> false
                AppTheme.SYSTEM -> systemDark
                null -> true // aguardando leitura do DataStore
            }

            DollarBlockTheme(darkTheme = darkTheme) {
                val viewModel: OnboardingViewModel = hiltViewModel()
                val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()

                when (onboardingCompleted) {
                    null -> Unit
                    false -> OnboardingScreen(
                        onFinished = { },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                    )
                    true -> MainTabs(mainViewModel)
                }
            }
        }
    }
}

@Composable
private fun MainTabs(mainViewModel: MainViewModel) {
    val navController = rememberNavController()
    val missingPermissions by mainViewModel.missingPermissionsNag.collectAsStateWithLifecycle()

    // Aviso diário de app degradado: sem as permissões o taxímetro não mede nem bloqueia.
    // Re-checa a cada retorno ao foreground; o ViewModel limita a 1 exibição por dia.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { mainViewModel.checkPermissionNag() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { DollarBlockBottomBar(navController) },
    ) { innerPadding ->
        DollarBlockNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }

    missingPermissions?.let { missing ->
        PermissionNagDialog(
            missing = missing,
            onFix = {
                mainViewModel.dismissPermissionNag()
                navController.navigate(PROFILE_ROUTE) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onDismiss = mainViewModel::dismissPermissionNag,
        )
    }
}

/**
 * Aviso de mau funcionamento sem permissões, no tom da casa: sem a papelada o
 * taxímetro roda às cegas — não mede, não bloqueia, não cobra.
 */
@Composable
private fun PermissionNagDialog(
    missing: List<AppPermission>,
    onFix: () -> Unit,
    onDismiss: () -> Unit,
) {
    DollarBlockDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.perm_nag_title),
        body = stringResource(R.string.perm_nag_body),
        confirmText = stringResource(R.string.perm_nag_fix),
        onConfirm = onFix,
        dismissText = stringResource(R.string.perm_nag_later),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            missing.forEach { permission ->
                Text(
                    text = "· " + stringResource(permissionNameRes(permission)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun permissionNameRes(permission: AppPermission): Int = when (permission) {
    AppPermission.USAGE_ACCESS -> R.string.perm_usage
    AppPermission.ACCESSIBILITY -> R.string.perm_accessibility
    AppPermission.OVERLAY -> R.string.perm_overlay
    AppPermission.NOTIFICATIONS -> R.string.perm_notifications
}
