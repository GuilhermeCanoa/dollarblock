package com.dollarblock.feature.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dollarblock.R
import com.dollarblock.core.designsystem.components.PrimaryActionButton

/**
 * Gate de tela cheia exibido na primeira execução (ou enquanto a permissão de
 * Usage Access não for concedida). Bloqueia o acesso às abas principais porque
 * o tracking de tempo de uso depende inteiramente dessa permissão.
 *
 * Onboarding completo (demais permissões e textos explicativos) é tratado em
 * épico futuro (E2); este gate cobre apenas Usage Access.
 */
@Composable
fun UsageAccessGateScreen(
    onAccessGranted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UsageAccessGateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val hasAccess by viewModel.hasAccess.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.recheckAccess()
    }

    LaunchedEffect(hasAccess) {
        if (hasAccess) onAccessGranted()
    }

    if (hasAccess) return

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = Icons.Filled.QueryStats,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(20.dp),
            )
        }
        Text(
            text = stringResource(R.string.usage_gate_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = stringResource(R.string.usage_gate_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        PrimaryActionButton(
            text = stringResource(R.string.usage_gate_button),
            onClick = { context.startActivity(viewModel.settingsIntent()) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
        Text(
            text = stringResource(R.string.usage_gate_hint),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
