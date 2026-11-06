package com.dollarblock.feature.blocking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dollarblock.R
import com.dollarblock.core.designsystem.EmeraldPremium
import com.dollarblock.core.designsystem.NeutralWhite
import com.dollarblock.core.designsystem.OnEmeraldContainerLight
import com.dollarblock.feature.blocking.payment.PaymentProvider

/*
 * Os controles da tela de bloqueio, iguais nas três cenas (E25): pagar, processando, falha
 * (cortesia do E17, com tentar de novo), Billing indisponível, simular (debug) e "Ficar com
 * meu dinheiro". Extraídos da BlockActivity sem mudar o comportamento — só o visual (botão
 * esmeralda, preço no botão nas cenas B/C).
 */

/**
 * [payLabel] é o rótulo do botão de pagar já resolvido pela cena (com ou sem o preço). Os
 * callbacks são os mesmos de antes: quem decide o que acontece é a BlockActivity.
 */
@Composable
internal fun ColumnScope.BlockControls(
    appLabel: String,
    payLabel: String,
    provider: PaymentProvider,
    paymentReady: Boolean,
    paymentInProgress: Boolean,
    paymentFailed: Boolean,
    showDebugSimulate: Boolean,
    onPay: () -> Unit,
    onSimulatePayment: () -> Unit,
    onCourtesyUnlock: () -> Unit,
    onGoHome: () -> Unit,
) {
    if (paymentInProgress) {
        CircularProgressIndicator(color = NeutralWhite)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.pay_processing),
            style = MaterialTheme.typography.bodyMedium,
            color = NeutralWhite,
        )
    } else if (paymentFailed) {
        // Falha na cobrança: a culpa é nossa, então o passe do dia sai de graça.
        // O botão de tentar de novo continua disponível pra quem quiser pagar.
        PaymentFailedNotice(
            appLabel = appLabel,
            onCourtesyUnlock = onCourtesyUnlock,
            onRetry = onPay.takeIf { paymentReady },
        )
    } else {
        if (paymentReady) {
            PayButton(label = payLabel, onClick = onPay)
        } else {
            Text(
                text = stringResource(
                    when (provider) {
                        PaymentProvider.PLAY_BILLING -> R.string.pay_billing_unavailable
                        PaymentProvider.STRIPE_GOOGLE_PAY -> R.string.pay_unavailable
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = NeutralWhite.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
        if (showDebugSimulate) {
            Spacer(Modifier.height(12.dp))
            SecondaryButton(label = stringResource(R.string.pay_simulate), onClick = onSimulatePayment)
        }
    }

    if (!paymentInProgress) {
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onGoHome) {
            Text(
                text = stringResource(R.string.block_screen_home),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = NeutralWhite.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * Rodapé de transparência: "Entenda o bloqueio · cobrança pelo Google Play". O link abre a
 * explicação de que pagar é opcional (o bloqueio é o contrato, não um sequestro); a nota do
 * Google Play é a do E19, que saiu de baixo do botão e veio para cá — continua visível.
 */
@Composable
internal fun BlockFooter(provider: PaymentProvider, onUnderstand: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.block_footer_understand),
            fontSize = 11.sp,
            color = NeutralWhite.copy(alpha = 0.55f),
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onUnderstand)
                .padding(vertical = 8.dp, horizontal = 2.dp),
        )
        if (provider == PaymentProvider.PLAY_BILLING) {
            Text(
                text = " · " + stringResource(R.string.block_footer_billing),
                fontSize = 11.sp,
                color = NeutralWhite.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun ColumnScope.PaymentFailedNotice(
    appLabel: String,
    onCourtesyUnlock: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    Text(
        text = stringResource(R.string.pay_failed_title),
        style = MaterialTheme.typography.titleMedium,
        color = NeutralWhite,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.pay_failed_body),
        style = MaterialTheme.typography.bodyMedium,
        color = NeutralWhite.copy(alpha = 0.85f),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    PayButton(
        label = stringResource(R.string.pay_failed_action, appLabel),
        onClick = onCourtesyUnlock,
    )
    if (onRetry != null) {
        Spacer(Modifier.height(12.dp))
        SecondaryButton(label = stringResource(R.string.pay_failed_retry), onClick = onRetry)
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeutralWhite),
        border = BorderStroke(1.dp, NeutralWhite.copy(alpha = 0.5f)),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PayButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = EmeraldPremium,
            contentColor = OnEmeraldContainerLight,
        ),
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
