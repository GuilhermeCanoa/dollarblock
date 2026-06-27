package com.dollarblock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.navigation.DollarBlockBottomBar
import com.dollarblock.core.navigation.DollarBlockNavHost
import com.dollarblock.feature.onboarding.OnboardingScreen
import com.dollarblock.feature.onboarding.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity única que hospeda o app. A primeira execução é roteada para o fluxo de
 * onboarding (E2 — conceito + permissões); concluído o onboarding, exibe as abas
 * (Home / Apps / Statistics / Profile). O roteamento é decidido pela flag persistida
 * em [com.dollarblock.data.local.prefs.OnboardingPreferences].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DollarBlockTheme {
                val viewModel: OnboardingViewModel = hiltViewModel()
                val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()

                when (onboardingCompleted) {
                    // null = flag ainda sendo lida do DataStore; evita piscar o onboarding.
                    null -> Unit
                    false -> OnboardingScreen(onFinished = { /* flag persistida no ViewModel */ })
                    true -> MainTabs()
                }
            }
        }
    }
}

@Composable
private fun MainTabs() {
    val navController = rememberNavController()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { DollarBlockBottomBar(navController) },
    ) { innerPadding ->
        DollarBlockNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
