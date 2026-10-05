package com.dollarblock.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.components.BrandShield
import com.dollarblock.data.permissions.AppPermission
import com.dollarblock.data.permissions.PermissionsState
import com.dollarblock.domain.model.AppCurrency
import com.dollarblock.domain.model.MoneyFormat
import com.dollarblock.feature.home.HomeMetrics
import com.dollarblock.feature.home.text
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Quantas páginas de "papelada" (permissões) o onboarding tem — para o "Papelada N de M". */
private const val PAPERWORK_PAGES = 3

// ---------------------------------------------------------------------------------------
// 1. Entrada
// ---------------------------------------------------------------------------------------

/**
 * A marca entra, o título sobe, e a conta já começa a correr: o tempo desde que o app
 * abriu, convertido em dinheiro na referência de R$ 2.000/mês (a mesma régua da Home).
 */
@Composable
fun EntryPage(active: Boolean, modifier: Modifier = Modifier) {
    val animate = rememberAnimationsEnabled()
    val shieldScale = remember { Animatable(if (animate) 0.6f else 1f) }
    val shieldAlpha = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        shieldAlpha.animateTo(1f, tween(350))
    }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        shieldScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 300f))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandShield(
            size = 112.dp,
            cornerRadius = 26.dp,
            modifier = Modifier.graphicsLayer {
                scaleX = shieldScale.value
                scaleY = shieldScale.value
                alpha = shieldAlpha.value
            },
        )
        Reveal(active, order = 3) {
            Text(
                text = stringResource(R.string.onb_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 28.dp),
            )
        }
        Reveal(active, order = 5) {
            Text(
                text = stringResource(R.string.onb_entry_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        Reveal(active, order = 8) {
            RunningTabMeter(modifier = Modifier.padding(top = 28.dp))
        }
    }
}

/** "Desde que você abriu o DollarBlock: 0:42 · R$ 0,03" — atualiza a cada segundo. */
@Composable
private fun RunningTabMeter(modifier: Modifier = Modifier) {
    val openedAt = rememberSaveable { System.currentTimeMillis() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }
    val elapsed = (now - openedAt).coerceAtLeast(0L)
    val seconds = elapsed / 1_000L
    val clock = "%d:%02d".format(seconds / 60, seconds % 60)
    val money = MoneyFormat.format(screenTimeCost(elapsed), AppCurrency.BRL).unbreakable()
    val perMinute = MoneyFormat.format(screenTimeCost(60_000L), AppCurrency.BRL).unbreakable()
    val penalty = DollarBlockTheme.colors.penalty

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(penalty.copy(alpha = 0.08f))
            .border(1.dp, penalty.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = stringResource(R.string.onb_entry_meter_label).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Text(
                text = clock,
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "  ·  ",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = money,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = penalty,
            )
        }
        Text(
            text = stringResource(R.string.onb_entry_meter_note, perMinute),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------
// 2. O contrato
// ---------------------------------------------------------------------------------------

private val clauses = listOf(
    R.string.onb_clause_1,
    R.string.onb_clause_2,
    R.string.onb_clause_3,
    R.string.onb_clause_4,
)

/**
 * "O contrato" — papel/mono do recibo de bloqueio (ver `InvoiceReceipt` em BlockActivity.kt).
 * As cláusulas saem uma a uma, como recibo da maquininha; a 4ª é a saída livre. A
 * assinatura é o botão do rodapé ([SignButton]).
 */
@Composable
fun ContractPage(active: Boolean, modifier: Modifier = Modifier) {
    val paper = Color(0xFFF7F4EC)
    val ink = Color(0xFF1C2B26)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(paper)
                .border(1.dp, ink.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.onb_contract_header),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                color = ink.copy(alpha = 0.55f),
            )
            Text(
                text = stringResource(R.string.onb_penalty_title),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, bottom = 14.dp),
            )
            ContractDivider(ink.copy(alpha = 0.35f))
            clauses.forEachIndexed { index, clauseRes ->
                Reveal(active, order = index + 1, stepMs = 380L, fromTop = true) {
                    Column(modifier = Modifier.padding(top = 14.dp)) {
                        Text(
                            text = stringResource(R.string.onb_clause_label, index + 1).uppercase(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            letterSpacing = 1.5.sp,
                            color = ink.copy(alpha = 0.5f),
                        )
                        Text(
                            text = stringResource(clauseRes),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = ink.copy(alpha = 0.9f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            Reveal(active, order = clauses.size + 1, stepMs = 380L) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(16.dp))
                    ContractDivider(ink.copy(alpha = 0.35f))
                    Text(
                        text = stringResource(R.string.onb_contract_footer),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = ink.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContractDivider(color: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(1.dp),
    ) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
}

// ---------------------------------------------------------------------------------------
// Cabeçalho comum das páginas de papelada
// ---------------------------------------------------------------------------------------

@Composable
private fun PaperworkHeader(step: Int, required: Boolean?, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.onb_perm_step, step, PAPERWORK_PAGES).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (required != null) RequirementTag(required)
    }
}

@Composable
private fun RequirementTag(required: Boolean, modifier: Modifier = Modifier) {
    val color = if (required) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = stringResource(if (required) R.string.onb_perm_required else R.string.onb_perm_optional).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun PageBody(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------------------------------
// 3. A medição (Acesso de uso → resumo da semana)
// ---------------------------------------------------------------------------------------

/**
 * Antes de conceder: o porquê em duas frases e o passo a passo animado. Depois: a mesma
 * página vira a recompensa — quanto a última semana custou, com o donut se desenhando.
 */
@Composable
fun MeasurementPage(
    active: Boolean,
    granted: Boolean,
    summary: QuickSummaryState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PaperworkHeader(step = 1, required = true)
        if (!granted) {
            Reveal(active, order = 1) {
                PageTitle(stringResource(R.string.onb_perm_usage_title), Modifier.padding(top = 24.dp))
            }
            Reveal(active, order = 2) {
                PageBody(stringResource(R.string.onb_measure_body), Modifier.padding(top = 12.dp))
            }
            Reveal(active, order = 3) {
                TrustCard(
                    titleRes = R.string.onb_trust_measure_title,
                    pointsRes = listOf(
                        R.string.onb_trust_measure_1,
                        R.string.onb_trust_measure_2,
                        R.string.onb_trust_measure_3,
                    ),
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
            Reveal(active, order = 5) {
                PermissionHowTo(
                    screenTitle = stringResource(R.string.perm_usage),
                    steps = listOf(HowToStep.LIST, HowToStep.TOGGLE),
                    description = stringResource(R.string.onb_howto_usage_description),
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        } else {
            GrantedStamp(modifier = Modifier.padding(top = 20.dp))
            WeeklyBill(summary = summary, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

private val summaryPalette = listOf(
    Color(0xFF00E676), // Emerald Premium
    Color(0xFF64FFDA), // Mint Glow
    Color(0xFF00A86B), // Emerald médio
    Color(0xFF2DD4BF), // Teal
    Color(0xFFA7F432), // Lime
)

@Composable
private fun WeeklyBill(summary: QuickSummaryState, modifier: Modifier = Modifier) {
    when {
        summary.isLoading -> CircularProgressIndicator(modifier = modifier.padding(32.dp))
        summary.topApps.isEmpty() -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier,
        ) {
            PageTitle(stringResource(R.string.onb_summary_no_usage_title))
            PageBody(stringResource(R.string.onb_summary_no_usage_body), Modifier.padding(top = 12.dp))
        }
        else -> WeeklyBillContent(summary, modifier)
    }
}

@Composable
private fun WeeklyBillContent(summary: QuickSummaryState, modifier: Modifier = Modifier) {
    val animate = rememberAnimationsEnabled()
    // O valor sobe do zero, como a conta da Home; o donut se desenha no mesmo ritmo.
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) progress.animateTo(1f, tween(1_400, easing = FastOutSlowInEasing))
    }
    val cost = screenTimeCost(summary.topMillis)
    val shownCost = MoneyFormat.format(cost * progress.value, AppCurrency.BRL).unbreakable()
    val equivalence = remember(cost) { HomeMetrics.equivalence(cost, AppCurrency.BRL, seed = 0) }
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.surfaceVariant

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.fillMaxWidth()) {
        PageTitle(stringResource(R.string.onb_measure_result_title, shownCost))
        if (equivalence != null) {
            val itemText = equivalence.item.text
            Text(
                text = if (equivalence.count != null) {
                    pluralStringResource(itemText.countRes, equivalence.count, equivalence.count)
                } else {
                    stringResource(itemText.fractionRes, equivalence.percent ?: 0)
                },
                style = MaterialTheme.typography.titleMedium,
                color = DollarBlockTheme.colors.penalty,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .graphicsLayer { alpha = progress.value },
            )
        }
        PageBody(
            stringResource(R.string.onb_measure_result_body, summary.totalTime),
            Modifier.padding(top = 10.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            Canvas(modifier = Modifier.size(112.dp)) {
                val sw = 18.dp.toPx()
                val radius = (size.minDimension - sw) / 2f
                val topLeft = Offset((size.width - radius * 2) / 2f, (size.height - radius * 2) / 2f)
                val arcSize = Size(radius * 2, radius * 2)
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
                var start = -90f
                summary.topApps.forEachIndexed { idx, entry ->
                    val sweep = 360f * entry.percentage * progress.value
                    drawArc(
                        color = summaryPalette[idx % summaryPalette.size],
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = sw, cap = StrokeCap.Butt),
                    )
                    start += sweep
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f),
            ) {
                summary.topApps.forEachIndexed { idx, entry ->
                    SummaryAppRow(entry, summaryPalette[idx % summaryPalette.size], progress.value)
                }
            }
        }
        Text(
            text = stringResource(R.string.onb_measure_result_note),
            style = MaterialTheme.typography.labelSmall,
            color = onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun SummaryAppRow(entry: QuickSummaryEntry, color: Color, progress: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.icon != null) {
                Image(
                    bitmap = entry.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = entry.appName.first().uppercaseChar().toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
        ) {
            Row {
                Text(
                    text = entry.appName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(entry.percentage * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { entry.percentage * progress },
                modifier = Modifier
                    .padding(top = 2.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// 4. A tranca (Acessibilidade)
// ---------------------------------------------------------------------------------------

@Composable
fun LockPage(active: Boolean, granted: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PaperworkHeader(step = 2, required = true)
        Reveal(active, order = 1) {
            PageTitle(stringResource(R.string.onb_perm_accessibility_title), Modifier.padding(top = 24.dp))
        }
        Reveal(active, order = 2) {
            PageBody(stringResource(R.string.onb_lock_body), Modifier.padding(top = 12.dp))
        }
        Reveal(active, order = 3) {
            TrustCard(
                titleRes = R.string.onb_lock_privacy,
                pointsRes = listOf(R.string.onb_trust_lock_1, R.string.onb_trust_lock_2, R.string.onb_trust_lock_3),
                modifier = Modifier.padding(top = 20.dp),
            )
        }
        if (granted) {
            GrantedStamp(modifier = Modifier.padding(top = 36.dp))
        } else {
            Reveal(active, order = 5) {
                PermissionHowTo(
                    screenTitle = stringResource(R.string.perm_accessibility),
                    steps = listOf(HowToStep.LIST, HowToStep.TOGGLE, HowToStep.CONFIRM),
                    description = stringResource(R.string.onb_howto_accessibility_description),
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}

/**
 * Cartão de confiança das páginas de papelada: a garantia de privacidade em destaque
 * (título com cadeado) e três garantias curtas com ✓. Mesmo vocabulário do "Sigilo
 * bancário" do Perfil (E19) — tudo o que ele afirma precisa continuar verdade no app.
 */
@Composable
private fun TrustCard(
    titleRes: Int,
    pointsRes: List<Int>,
    modifier: Modifier = Modifier,
) {
    val success = DollarBlockTheme.colors.success
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(success.copy(alpha = 0.10f))
            .border(1.dp, success.copy(alpha = 0.35f), shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(success.copy(alpha = 0.18f)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = success,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        pointsRes.forEach { pointRes ->
            Row(modifier = Modifier.padding(top = 10.dp)) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = success,
                    modifier = Modifier
                        .padding(start = 8.dp, top = 2.dp)
                        .size(18.dp),
                )
                Text(
                    text = stringResource(pointRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 18.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// 5. Últimos ajustes (Sobreposição + Notificações)
// ---------------------------------------------------------------------------------------

@Composable
fun FinalSettingsPage(
    active: Boolean,
    page: OnboardingPage.FinalSettings,
    permissions: PermissionsState,
    onRequest: (AppPermission) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PaperworkHeader(step = 3, required = null)
        Reveal(active, order = 1) {
            PageTitle(stringResource(R.string.onb_final_title), Modifier.padding(top = 24.dp))
        }
        Reveal(active, order = 2) {
            PageBody(stringResource(R.string.onb_final_body), Modifier.padding(top = 12.dp))
        }
        permissionsOn(page).forEachIndexed { index, permission ->
            Reveal(active, order = 3 + index) {
                SettingCard(
                    permission = permission,
                    granted = permissions.isGranted(permission),
                    onRequest = { onRequest(permission) },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

private data class SettingCopy(val icon: ImageVector, val nameRes: Int, val bodyRes: Int)

private fun settingCopy(permission: AppPermission): SettingCopy = when (permission) {
    AppPermission.OVERLAY -> SettingCopy(Icons.Filled.Layers, R.string.perm_overlay, R.string.onb_overlay_card_body)
    AppPermission.NOTIFICATIONS ->
        SettingCopy(Icons.Filled.Notifications, R.string.perm_notifications, R.string.onb_notifications_card_body)
    // Uso e Acessibilidade têm página própria; aqui só por completude do when.
    AppPermission.USAGE_ACCESS -> SettingCopy(Icons.Filled.Layers, R.string.perm_usage, R.string.onb_measure_body)
    AppPermission.ACCESSIBILITY -> SettingCopy(Icons.Filled.Lock, R.string.perm_accessibility, R.string.onb_lock_body)
}

@Composable
private fun SettingCard(
    permission: AppPermission,
    granted: Boolean,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = settingCopy(permission)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    imageVector = copy.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            ) {
                Text(
                    text = stringResource(copy.nameRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                RequirementTag(permission.required, modifier = Modifier.padding(top = 4.dp))
            }
            if (granted) {
                GrantedStamp(fontSize = 12.sp, borderWidth = 1.5.dp)
            } else {
                TextButton(onClick = onRequest) {
                    Text(stringResource(R.string.onb_turn_on), fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            text = stringResource(copy.bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (!granted && permission == AppPermission.OVERLAY) {
            PermissionHowTo(
                screenTitle = stringResource(R.string.perm_overlay),
                steps = listOf(HowToStep.TOGGLE),
                description = stringResource(R.string.onb_howto_overlay_description),
                height = 150.dp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}


/** "R$ 20,59" sem quebra entre o símbolo e o número. */
private fun String.unbreakable(): String = replace(' ', ' ')
