package com.dollarblock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.navigation.DollarBlockBottomBar
import com.dollarblock.core.navigation.DollarBlockNavHost
import com.dollarblock.feature.permission.UsageAccessGateScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity única que hospeda o NavHost do DollarBlock com a Bottom Navigation
 * (Home / Apps / Statistics / Profile). Antes de exibir as abas, exige a
 * permissão de Usage Access (gate mínimo; onboarding completo fica para o E2).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DollarBlockTheme {
                var accessGranted by remember { mutableStateOf(false) }

                if (accessGranted) {
                    val navController = rememberNavController()
                    Scaffold(
                        bottomBar = { DollarBlockBottomBar(navController) },
                    ) { innerPadding ->
                        DollarBlockNavHost(
                            navController = navController,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                } else {
                    UsageAccessGateScreen(onAccessGranted = { accessGranted = true })
                }
            }
        }
    }
}
