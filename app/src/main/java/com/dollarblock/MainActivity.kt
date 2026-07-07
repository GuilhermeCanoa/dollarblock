package com.dollarblock

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.dollarblock.core.FeatureFlags
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.components.BrandShield
import com.dollarblock.core.designsystem.components.DollarBlockDialog
import kotlinx.coroutines.delay
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

                SplashOverlay()
            }
        }
    }
}

/**
 * Splash rápida (logo + nome) sobreposta ao conteúdo na abertura, com fade-out.
 * Desativável por [FeatureFlags.SPLASH_ENABLED]. `rememberSaveable` evita que ela
 * reapareça em rotação/recriação da Activity.
 */
@Composable
private fun SplashOverlay() {
    if (!FeatureFlags.SPLASH_ENABLED) return
    var visible by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(FeatureFlags.SPLASH_DURATION_MS)
        visible = false
    }
    AnimatedVisibility(visible = visible, exit = fadeOut(animationSpec = tween(350))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BrandShield(size = 104.dp, cornerRadius = 28.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
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
