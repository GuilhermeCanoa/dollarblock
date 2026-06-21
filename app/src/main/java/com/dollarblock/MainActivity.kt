package com.dollarblock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.navigation.DollarBlockBottomBar
import com.dollarblock.core.navigation.DollarBlockNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity única que hospeda o NavHost do DollarBlock com a Bottom Navigation
 * (Home / Apps / Statistics / Profile).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DollarBlockTheme {
                val navController = rememberNavController()
                Scaffold(
                    bottomBar = { DollarBlockBottomBar(navController) },
                ) { innerPadding ->
                    DollarBlockNavHost(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
