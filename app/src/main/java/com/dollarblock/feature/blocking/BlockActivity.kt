package com.dollarblock.feature.blocking

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dollarblock.MainActivity
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.DollarGreenDark
import com.dollarblock.core.designsystem.NeutralWhite

/**
 * Tela exibida quando o usuário abre um app bloqueado. No MVP apenas informa e
 * devolve o usuário ao início; o desbloqueio com penalidade chega em épicos futuros.
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appLabel = intent.getStringExtra(EXTRA_LABEL) ?: getString(R.string.app_name)

        // Voltar não deve retornar ao app bloqueado.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = goHome()
            },
        )

        setContent {
            DollarBlockTheme {
                BlockScreen(
                    appLabel = appLabel,
                    onGoHome = ::goHome,
                    onOpenDollarBlock = ::openDollarBlock,
                )
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    private fun openDollarBlock() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }

    companion object {
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_PACKAGE = "extra_package"
    }
}

@Composable
private fun BlockScreen(
    appLabel: String,
    onGoHome: () -> Unit,
    onOpenDollarBlock: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DollarGreenDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(NeutralWhite.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = NeutralWhite,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.block_screen_title),
                style = MaterialTheme.typography.headlineLarge,
                color = NeutralWhite,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = appLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = NeutralWhite,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.block_screen_message),
                style = MaterialTheme.typography.bodyLarge,
                color = NeutralWhite.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onGoHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeutralWhite,
                    contentColor = DollarGreenDark,
                ),
            ) {
                Text(
                    text = stringResource(R.string.block_screen_home),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onOpenDollarBlock,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeutralWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeutralWhite.copy(alpha = 0.5f)),
            ) {
                Text(
                    text = stringResource(R.string.block_screen_open_app),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
