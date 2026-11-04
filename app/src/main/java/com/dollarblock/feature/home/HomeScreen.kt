package com.dollarblock.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dollarblock.R
import com.dollarblock.core.designsystem.AlertAmber
import com.dollarblock.core.designsystem.BlockingRed
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.EmeraldPremium
import com.dollarblock.core.designsystem.InterFamily
import com.dollarblock.core.designsystem.JakartaFamily
import com.dollarblock.core.designsystem.MintGlow
import com.dollarblock.core.designsystem.components.DollarBlockDialog
import com.dollarblock.domain.model.AppCurrency
import com.dollarblock.domain.model.MoneyFormat
import com.dollarblock.domain.model.MoneySettings
import com.dollarblock.domain.model.MonthWallet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Home — "a conta de hoje" (redesenho de out/2026, a partir do mockup aprovado): o valor do
 * dia com o orçamento por app, dois atalhos (tentativas e próximo a fechar), os apps na conta
 * e a carteira do mês, que abre para mostrar o preço do minuto e o salário.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToApps: () -> Unit = {},
    onOpenStatement: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val visuals by viewModel.visuals.collectAsStateWithLifecycle()
    var infoCard by remember { mutableStateOf<HomeCardInfo?>(null) }
    var showSalaryDialog by remember { mutableStateOf(false) }
    val palette = homePalette()
    val hasApps = uiState.moneyLostToday != null
    val scrollState = rememberScrollState()
    val statusBarPx = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            // Brilho do topo fora do recorte da rolagem, para poder subir por baixo da barra de
            // status (sem corte); acompanha a rolagem pelo deslocamento do scroll.
            .drawBehind {
                with(palette) {
                    drawHeroGlow(
                        centerY = 90.dp.toPx() - scrollState.value,
                        radius = 420.dp.toPx(),
                        extendUp = statusBarPx,
                    )
                }
            }
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp),
    ) {
        TopRow(onOpenProfile = onOpenProfile, palette = palette)

        Hero(
            moneyLost = uiState.moneyLostToday,
            currency = uiState.moneySettings.currency,
            budget = uiState.budget,
            visuals = visuals,
            palette = palette,
            onClick = { infoCard = HomeCardInfo.DAMAGE },
            onPickApps = onNavigateToApps,
        )

        // Primeiro acesso: os atalhos já aparecem zerados, para a tela ter a cara final.
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile(
                title = stringResource(R.string.home_tile_attempts),
                value = uiState.attemptsToday.toString(),
                unit = null,
                icon = uiState.attemptsTopPackage?.let { visuals[it]?.icon },
                colors = palette.triesTile,
                onClick = { infoCard = HomeCardInfo.ADDICTION },
                modifier = Modifier.weight(1f),
            )
            StatTile(
                title = stringResource(R.string.home_tile_next),
                value = uiState.nextToClose?.minutesLeft?.toString(),
                // Sem número: ou não há limite nenhum ainda, ou todos os apps com limite já trancaram.
                emptyText = stringResource(
                    if (uiState.budget == null) R.string.home_tile_next_none else R.string.home_tile_next_all_locked,
                ),
                unit = uiState.nextToClose?.let { stringResource(R.string.home_tile_min) },
                icon = uiState.nextToClose?.let { visuals[it.packageName]?.icon },
                colors = palette.nextTile,
                onClick = onNavigateToApps,
                modifier = Modifier.weight(1f),
            )
        }

        AppsSection(
            apps = uiState.apps,
            visuals = visuals,
            palette = palette,
            onManage = onNavigateToApps,
        )

        Wallet(
            wallet = uiState.wallet,
            settings = uiState.moneySettings,
            highlightSalary = uiState.showSalaryTip,
            palette = palette,
            onRecalculate = {
                if (uiState.showSalaryTip) viewModel.dismissSalaryTip()
                showSalaryDialog = true
            },
            onOpenStatement = onOpenStatement,
        )
    }

    infoCard?.let { card ->
        HomeCardInfoDialog(
            card = card,
            referenceSalary = MoneyFormat.format(
                uiState.moneySettings.referenceMonthlySalary,
                uiState.moneySettings.currency,
                cents = false,
            ),
            onDismiss = { infoCard = null },
            // Sem apps ainda, a explicação do número termina num convite para escolhê-los.
            onPickApps = if (card == HomeCardInfo.DAMAGE && !hasApps) {
                {
                    infoCard = null
                    onNavigateToApps()
                }
            } else null,
        )
    }

    if (showSalaryDialog) {
        SalaryDialog(
            settings = uiState.moneySettings,
            onDismiss = { showSalaryDialog = false },
            onConfirm = { salary ->
                viewModel.setMonthlySalary(salary)
                showSalaryDialog = false
            },
        )
    }
}

// ── Paleta ────────────────────────────────────────────────────────────────────────────────

private data class TileColors(val background: Color, val border: Color, val title: Color, val value: Color)

/** Cores do mockup (escuro) com o equivalente no tema claro. */
private data class HomePalette(
    val dark: Boolean,
    val background: Color,
    val text: Color,
    val muted: Color,
    val heroLabel: Color,
    val heroEquiv: Color,
    val track: Color,
    val divider: Color,
    val chip: Color,
    val triesTile: TileColors,
    val nextTile: TileColors,
    val walletBg: Color,
    val walletBorder: Color,
    val paidTile: TileColors,
    val savedTile: TileColors,
    val fieldBg: Color,
    val emerald: Color,
) {
    /**
     * Brilho verde (marca) no topo, atrás da conta. [extendUp] estende o desenho para cima,
     * por baixo da barra de status, para a tela fundir com o fundo sem corte no topo.
     */
    fun DrawScope.drawHeroGlow(centerY: Float, radius: Float, extendUp: Float) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(EmeraldPremium.copy(alpha = if (dark) 0.22f else 0.14f), Color.Transparent),
                center = Offset(size.width / 2, centerY),
                radius = radius,
            ),
            topLeft = Offset(0f, -extendUp),
            size = Size(size.width, size.height + extendUp),
        )
    }
}

@Composable
private fun homePalette(): HomePalette {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    return if (dark) {
        HomePalette(
            dark = true,
            background = scheme.background,
            text = Color.White,
            muted = scheme.onSurfaceVariant,
            heroLabel = MintGlow,
            heroEquiv = Color(0xFFD3E6DF),
            track = Color.White.copy(alpha = 0.08f),
            divider = Color.White.copy(alpha = 0.06f),
            chip = Color.White.copy(alpha = 0.06f),
            triesTile = TileColors(Color(0xFF3A2E10), AlertAmber.copy(alpha = 0.25f), Color(0xFFFFD98A), Color.White),
            nextTile = TileColors(Color(0xFF0F3A3A), MintGlow.copy(alpha = 0.22f), Color(0xFF8FF3E2), Color.White),
            walletBg = Color.White.copy(alpha = 0.04f),
            walletBorder = MintGlow.copy(alpha = 0.10f),
            paidTile = TileColors(BlockingRed.copy(alpha = 0.10f), Color.Transparent, Color(0xFFFFB4AB), Color.White),
            savedTile = TileColors(EmeraldPremium.copy(alpha = 0.10f), Color.Transparent, Color(0xFF8CF5BB), Color.White),
            fieldBg = scheme.background.copy(alpha = 0.6f),
            emerald = EmeraldPremium,
        )
    } else {
        val ink = scheme.onSurface
        HomePalette(
            dark = false,
            background = scheme.background,
            text = ink,
            muted = scheme.onSurfaceVariant,
            heroLabel = scheme.primary,
            heroEquiv = Color(0xFF3E534B),
            track = ink.copy(alpha = 0.08f),
            divider = ink.copy(alpha = 0.08f),
            chip = ink.copy(alpha = 0.06f),
            triesTile = TileColors(Color(0xFFFFF1CC), AlertAmber.copy(alpha = 0.6f), Color(0xFF7A5300), ink),
            nextTile = TileColors(Color(0xFFD9F7F0), MintGlow.copy(alpha = 0.8f), Color(0xFF0B5C50), ink),
            walletBg = scheme.surface,
            walletBorder = scheme.outlineVariant,
            paidTile = TileColors(BlockingRed.copy(alpha = 0.10f), Color.Transparent, Color(0xFFB3261E), ink),
            savedTile = TileColors(EmeraldPremium.copy(alpha = 0.14f), Color.Transparent, Color(0xFF00693A), ink),
            fieldBg = scheme.surfaceVariant.copy(alpha = 0.5f),
            emerald = scheme.primary,
        )
    }
}

private val Mono = FontFamily.Monospace

// ── Topo ──────────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopRow(onOpenProfile: () -> Unit, palette: HomePalette) {
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(locale) {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        LocalDate.now().format(DateTimeFormatter.ofPattern(pattern, locale))
            .replaceFirstChar { it.titlecase(locale) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(date, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = palette.muted)
        // Escudo do app no lugar de um avatar: o app não guarda nome; tocar abre o Perfil.
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (palette.dark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onOpenProfile),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.db_logo),
                contentDescription = stringResource(R.string.home_open_profile),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ── Herói: a conta de hoje ────────────────────────────────────────────────────────────────

@Composable
private fun Hero(
    moneyLost: Double?,
    currency: AppCurrency,
    budget: DailyBudget?,
    visuals: Map<String, AppVisual>,
    palette: HomePalette,
    onClick: () -> Unit,
    onPickApps: () -> Unit,
) {
    // Count-up de odômetro ao entrar na tela, com haptic a cada café inteiro cruzado.
    val animatedValue = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val coffeePrice = ComparisonItem.COFFEE.priceIn(currency) ?: HomeMetrics.COFFEE_PRICE
    LaunchedEffect(moneyLost) {
        var lastReported = 0.0
        animatedValue.animateTo(
            targetValue = (moneyLost ?: 0.0).toFloat(),
            animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        ) {
            if (HomeMetrics.crossedCoffeeMultiple(lastReported, value.toDouble(), coffeePrice)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            lastReported = value.toDouble()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 24.dp, top = 34.dp, bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // "ⓘ" ao lado do rótulo: avisa que o bloco é tocável e explica o número.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.home_today_label),
                fontFamily = Mono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 2.5.sp,
                color = palette.heroLabel,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Outlined.Info,
                contentDescription = stringResource(R.string.home_hero_info),
                tint = palette.heroLabel,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        // Sem apps ainda, a conta mostra R$ 0,00 (não um traço): é o mesmo número, só zerado.
        BigMoney(value = animatedValue.value.toDouble(), currency = currency, color = palette.text)
        Spacer(Modifier.height(10.dp))

        if (moneyLost == null) {
            Text(
                text = stringResource(R.string.home_hero_empty),
                fontFamily = InterFamily,
                fontSize = 15.sp,
                color = palette.heroEquiv,
                textAlign = TextAlign.Center,
            )
            EmptyBudgetBar(palette)
            Spacer(Modifier.height(18.dp))
            PickAppsChip(palette, onClick = onPickApps)
            return@Column
        }

        // Sorteia a comparação uma vez por visita: "2 cafés" hoje, "9% de um Big Mac" amanhã.
        val equivalenceSeed = rememberSaveable { Random.nextInt() }
        HomeMetrics.equivalence(moneyLost, currency, equivalenceSeed)?.let { eq ->
            val itemText = (
                if (eq.count != null) pluralStringResource(eq.item.text.countRes, eq.count, eq.count)
                else stringResource(eq.item.text.fractionRes, eq.percent ?: 0)
                ).removePrefix("=").trim()
            Text(
                text = highlighted(stringResource(R.string.home_hero_equiv, itemText), itemText, palette.text),
                fontFamily = InterFamily,
                fontSize = 15.sp,
                color = palette.heroEquiv,
            )
        }

        budget?.let { BudgetBar(it, visuals, palette) }
    }
}

/**
 * "R$ 6,74" com o número centralizado na tela e o símbolo menor, erguido e pendurado à
 * esquerda dele — o olho cai no valor, não no conjunto "R$ + valor".
 */
@Composable
private fun BigMoney(value: Double, currency: AppCurrency, color: Color) {
    val formatted = MoneyFormat.format(value, currency)
    val firstDigit = formatted.indexOfFirst { it.isDigit() }.coerceAtLeast(0)
    val symbol = formatted.substring(0, firstDigit).trim()
    val number = formatted.substring(firstDigit)
    val gap = 6.dp
    // Topo do símbolo alinhado ao topo dos dígitos: a linha de base dele sobe ~0,4 × 76sp.
    val symbolRise = 30.sp
    Layout(
        content = {
            Text(
                text = number,
                fontFamily = JakartaFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 76.sp,
                lineHeight = 76.sp,
                letterSpacing = (-2.5).sp,
                style = TextStyle(fontFeatureSettings = "tnum"),
                color = color,
                maxLines = 1,
            )
            Text(
                text = symbol,
                fontFamily = JakartaFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 34.sp,
                letterSpacing = (-1).sp,
                color = color,
                maxLines = 1,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0)
        val numberPlaceable = measurables[0].measure(loose)
        val symbolPlaceable = measurables[1].measure(loose)
        val width = constraints.maxWidth
        val gapPx = if (symbol.isEmpty()) 0 else gap.roundToPx()
        // Número no centro; se o valor for largo demais, desliza para caber o símbolo na tela.
        val numberX = maxOf((width - numberPlaceable.width) / 2, symbolPlaceable.width + gapPx)
        val numberBaseline = numberPlaceable[FirstBaseline]
        val symbolBaseline = symbolPlaceable[FirstBaseline]
        val symbolY = numberBaseline - symbolRise.roundToPx() - symbolBaseline
        layout(width, numberPlaceable.height) {
            numberPlaceable.placeRelative(numberX, 0)
            symbolPlaceable.placeRelative(numberX - gapPx - symbolPlaceable.width, symbolY)
        }
    }
}

/** Texto com o trecho [bold] em negrito e na cor [boldColor] (ex.: "≈ **2 cafés**, doados…"). */
private fun highlighted(text: String, bold: String, boldColor: Color): AnnotatedString = buildAnnotatedString {
    val start = text.indexOf(bold)
    if (start < 0) {
        append(text)
        return@buildAnnotatedString
    }
    append(text.substring(0, start))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) { append(bold) }
    append(text.substring(start + bold.length))
}

@Composable
private fun BudgetBar(budget: DailyBudget, visuals: Map<String, AppVisual>, palette: HomePalette) {
    val fallback = listOf(MintGlow, AlertAmber, BlockingRed, EmeraldPremium)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 22.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.track),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            budget.segments.forEachIndexed { i, (pkg, fraction) ->
                if (fraction <= 0f) return@forEachIndexed
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(fraction)
                        .background(visuals[pkg]?.color ?: fallback[i % fallback.size]),
                )
            }
            val rest = 1f - budget.segments.sumOf { it.second.toDouble() }.toFloat()
            if (rest > 0.001f) Spacer(Modifier.weight(rest))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.home_budget_used, duration(budget.usedMinutes)),
                fontSize = 12.sp,
                color = palette.muted,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            Text(
                stringResource(R.string.home_budget_limit, duration(budget.limitMinutes)),
                fontSize = 12.sp,
                color = palette.muted,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }
    }
}

/** Barra do orçamento antes de haver limites: o trilho vazio, com os rótulos zerados. */
@Composable
private fun EmptyBudgetBar(palette: HomePalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 22.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.track),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.home_budget_used, duration(0)), fontSize = 12.sp, color = palette.muted)
            Text(stringResource(R.string.home_budget_no_limits), fontSize = 12.sp, color = palette.muted)
        }
    }
}

@Composable
private fun duration(minutes: Int): String =
    if (minutes >= 60) stringResource(R.string.home_duration_hm, minutes / 60, minutes % 60)
    else stringResource(R.string.home_duration_m, minutes)

/** Estado vazio do herói: deixa explícito que tocar leva à escolha dos apps. */
@Composable
private fun PickAppsChip(palette: HomePalette, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(EmeraldPremium, MintGlow)))
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val ink = Color(0xFF04210E)
        Text(
            text = stringResource(R.string.home_money_lost_cta),
            fontFamily = InterFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = ink,
        )
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
    }
}

// ── Atalhos ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatTile(
    title: String,
    value: String?,
    /** Mostrado no lugar do número quando [value] é null (ex.: nenhum limite ainda). */
    emptyText: String? = null,
    unit: String?,
    icon: ImageBitmap?,
    colors: TileColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .height(104.dp)
            .clip(shape)
            .background(colors.background)
            .border(1.dp, colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.title)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (value == null) {
                Text(
                    emptyText.orEmpty(),
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colors.value.copy(alpha = 0.8f),
                )
                return@Row
            }
            Text(
                text = buildAnnotatedString {
                    append(value)
                    if (unit != null) {
                        withStyle(SpanStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp)) {
                            append(" $unit")
                        }
                    }
                },
                fontFamily = JakartaFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                letterSpacing = (-0.5).sp,
                color = colors.value,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            if (icon != null) AppIcon(icon, size = 26, corner = 8)
        }
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap?, size: Int, corner: Int) {
    val shape = RoundedCornerShape(corner.dp)
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size.dp).clip(shape))
    } else {
        Box(Modifier.size(size.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

// ── Apps na conta ─────────────────────────────────────────────────────────────────────────

@Composable
private fun AppsSection(
    apps: List<HomeAppRow>,
    visuals: Map<String, AppVisual>,
    palette: HomePalette,
    onManage: () -> Unit,
) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.home_apps_title),
                fontFamily = JakartaFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = palette.text,
            )
            Text(
                stringResource(R.string.home_apps_manage),
                fontFamily = InterFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = palette.emerald,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onManage)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        if (apps.isEmpty()) {
            EmptyAppsPlaceholder(palette, onClick = onManage)
        }
        apps.forEachIndexed { i, app ->
            AppUsageRow(app, visuals[app.packageName]?.icon, palette, showDivider = i < apps.lastIndex)
        }
    }
}

/**
 * Primeiro acesso: um quadro só com o convite — os apps escolhidos aparecem aqui; tocar no
 * quadro leva à escolha dos apps.
 */
@Composable
private fun EmptyAppsPlaceholder(palette: HomePalette, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(palette.walletBg)
            .border(1.dp, palette.walletBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.home_apps_empty_title),
            fontFamily = JakartaFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = palette.text,
        )
        Text(
            stringResource(R.string.home_apps_empty_body),
            fontFamily = InterFamily,
            fontSize = 12.5.sp,
            color = palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp),
        )
    }
}

@Composable
private fun AppUsageRow(app: HomeAppRow, icon: ImageBitmap?, palette: HomePalette, showDivider: Boolean) {
    val barColor = when (app.level) {
        UsageLevel.LOCKED -> BlockingRed
        UsageLevel.WARN -> AlertAmber
        UsageLevel.OK -> palette.emerald
        UsageLevel.NO_LIMIT -> palette.muted.copy(alpha = 0.5f)
    }
    val progress by animateFloatAsState(app.progress ?: 0f, tween(900), label = "usage")
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(icon, size = 36, corner = 10)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.appName,
                            fontFamily = JakartaFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = palette.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (app.level == UsageLevel.LOCKED) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.home_app_locked),
                                fontFamily = Mono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                color = BlockingRed,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BlockingRed.copy(alpha = 0.14f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Text(
                        text = app.limitMinutes?.let { stringResource(R.string.home_app_usage, app.usedMinutes, it) }
                            ?: stringResource(R.string.home_app_usage_no_limit, app.usedMinutes),
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = palette.muted,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.track),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(if (app.limitMinutes == null) 1f else progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (app.limitMinutes == null) Color.Transparent else barColor),
                    )
                }
            }
        }
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
    }
}

// ── Carteira do mês ───────────────────────────────────────────────────────────────────────

@Composable
private fun Wallet(
    wallet: MonthWallet?,
    settings: MoneySettings,
    highlightSalary: Boolean,
    palette: HomePalette,
    onRecalculate: () -> Unit,
    onOpenStatement: () -> Unit,
) {
    // Fechada por padrão; na primeira vez (salário nunca informado) abre para o campo aparecer.
    var expanded by rememberSaveable { mutableStateOf(highlightSalary) }
    val currency = settings.currency
    val shape = RoundedCornerShape(20.dp)
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, tween(250), label = "chevron")

    Column(
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 18.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(palette.walletBg)
            .border(1.dp, palette.walletBorder, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = BlockingRed)) { append("● ") }
                    append(stringResource(R.string.home_wallet_paid) + " ")
                    withStyle(SpanStyle(color = palette.text, fontWeight = FontWeight.SemiBold)) {
                        append(MoneyFormat.format(wallet?.paid ?: 0.0, currency))
                    }
                    append("  ·  ")
                    withStyle(SpanStyle(color = palette.emerald)) { append("● ") }
                    append(stringResource(R.string.home_wallet_saved) + " ")
                    withStyle(SpanStyle(color = palette.text, fontWeight = FontWeight.SemiBold)) {
                        append(MoneyFormat.format(wallet?.saved ?: 0.0, currency))
                    }
                },
                fontFamily = InterFamily,
                fontSize = 13.sp,
                color = palette.muted,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(palette.chip),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.home_wallet_toggle),
                    tint = palette.text,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevron),
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WalletTile(
                        label = stringResource(R.string.home_wallet_paid_month),
                        value = MoneyFormat.format(wallet?.paid ?: 0.0, currency),
                        caption = pluralStringResource(R.plurals.home_wallet_passes_paid, wallet?.passesPaid ?: 0, wallet?.passesPaid ?: 0),
                        colors = palette.paidTile,
                        modifier = Modifier.weight(1f),
                    )
                    WalletTile(
                        label = stringResource(R.string.home_wallet_saved_month),
                        value = MoneyFormat.format(wallet?.saved ?: 0.0, currency),
                        caption = pluralStringResource(R.plurals.home_wallet_passes_refused, wallet?.passesRefused ?: 0, wallet?.passesRefused ?: 0),
                        colors = palette.savedTile,
                        modifier = Modifier.weight(1f),
                    )
                }

                ResistedRatio(wallet, palette)
                DashedDivider(palette.muted.copy(alpha = 0.3f))
                MinuteRate(settings, highlightSalary, palette, onRecalculate)

                Row(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(palette.divider),
                ) {}
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenStatement)
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.home_wallet_statement),
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = palette.emerald,
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = palette.emerald, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun WalletTile(label: String, value: String, caption: String, colors: TileColors, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.background)
            .padding(12.dp),
    ) {
        Text(label, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.4.sp, color = colors.title)
        Text(
            value,
            fontFamily = JakartaFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            color = colors.value,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(caption, fontFamily = InterFamily, fontSize = 11.5.sp, color = colors.title.copy(alpha = 0.75f), modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ResistedRatio(wallet: MonthWallet?, palette: HomePalette) {
    val bills = wallet?.bills ?: 0
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.track),
        ) {
            if (bills > 0) {
                val refused = wallet!!.passesRefused.toFloat()
                val paid = wallet.passesPaid.toFloat()
                if (refused > 0f) Box(Modifier.fillMaxHeight().weight(refused).background(palette.emerald))
                if (paid > 0f) Box(Modifier.fillMaxHeight().weight(paid).background(BlockingRed))
            }
        }
        val count = if (bills > 0) {
            stringResource(R.string.home_wallet_resisted_count, wallet!!.passesRefused, bills)
        } else null
        Text(
            text = if (count != null) {
                highlighted(stringResource(R.string.home_wallet_resisted, count), count, palette.text)
            } else {
                AnnotatedString(stringResource(R.string.home_wallet_no_bills))
            },
            fontFamily = InterFamily,
            fontSize = 12.sp,
            color = palette.muted,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

@Composable
private fun DashedDivider(color: Color) {
    Canvas(
        modifier = Modifier
            .padding(top = 16.dp, bottom = 14.dp)
            .fillMaxWidth()
            .height(1.5.dp),
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
}

@Composable
private fun MinuteRate(
    settings: MoneySettings,
    highlightSalary: Boolean,
    palette: HomePalette,
    onRecalculate: () -> Unit,
) {
    val currency = settings.currency
    Text(
        stringResource(R.string.home_wallet_minute_title),
        fontFamily = Mono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        letterSpacing = 2.sp,
        color = palette.muted,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = buildAnnotatedString {
                append(MoneyFormat.format(HomeMetrics.perMinuteRate(settings.monthlySalary), currency))
                withStyle(SpanStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp, color = palette.muted)) {
                    append(" " + stringResource(R.string.home_wallet_per_min))
                }
            },
            fontFamily = JakartaFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 30.sp,
            letterSpacing = (-1).sp,
            color = palette.text,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.home_wallet_calc_on) + "\n")
                withStyle(SpanStyle(color = palette.text, fontWeight = FontWeight.SemiBold)) {
                    append(stringResource(R.string.home_wallet_salary_month, MoneyFormat.format(settings.monthlySalary, currency, cents = false)))
                }
                append(" ")
                append(
                    stringResource(
                        if (settings.salaryConfigured) R.string.home_wallet_your_salary else R.string.home_wallet_reference,
                    ),
                )
            },
            fontFamily = InterFamily,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = palette.muted,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp),
        )
    }

    val fieldShape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(fieldShape)
            .background(palette.fieldBg)
            .border(
                width = if (highlightSalary) 1.5.dp else 1.dp,
                color = MintGlow.copy(alpha = if (highlightSalary) 0.7f else 0.22f),
                shape = fieldShape,
            )
            .clickable(onClick = onRecalculate)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.home_wallet_salary_label), fontFamily = InterFamily, fontSize = 11.sp, color = palette.muted)
            Text(
                text = MoneyFormat.format(if (settings.salaryConfigured) settings.monthlySalary else 0.0, currency),
                fontFamily = JakartaFamily,
                fontWeight = if (settings.salaryConfigured) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 17.sp,
                color = if (settings.salaryConfigured) palette.text else palette.text.copy(alpha = 0.35f),
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }
        Text(
            stringResource(R.string.home_wallet_recalc),
            fontFamily = InterFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color(0xFF04210E),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(listOf(EmeraldPremium, MintGlow)))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
    Text(
        stringResource(R.string.home_wallet_provoke),
        fontFamily = InterFamily,
        fontStyle = FontStyle.Italic,
        fontSize = 11.5.sp,
        color = palette.muted.copy(alpha = 0.8f),
        modifier = Modifier.padding(top = 8.dp),
    )
}

// ── Diálogos ──────────────────────────────────────────────────────────────────────────────

/** Modal de salário — copy no tom da casa: simulação melhor + provocação de vida adulta. */
@Composable
private fun SalaryDialog(
    settings: MoneySettings,
    onDismiss: () -> Unit,
    onConfirm: (Double?) -> Unit,
) {
    var text by remember {
        mutableStateOf(
            if (settings.salaryConfigured) {
                if (settings.monthlySalary % 1.0 == 0.0) settings.monthlySalary.toLong().toString()
                else settings.monthlySalary.toString()
            } else "",
        )
    }
    val parsed = text.trim().replace(',', '.').toDoubleOrNull()
    val isInvalid = text.isNotBlank() && (parsed == null || parsed <= 0.0)

    DollarBlockDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_salary_dialog_title),
        body = stringResource(R.string.home_salary_dialog_body),
        confirmText = stringResource(R.string.home_salary_dialog_save),
        confirmEnabled = !isInvalid && parsed != null,
        onConfirm = { onConfirm(parsed) },
        dismissText = if (settings.salaryConfigured) {
            stringResource(R.string.home_salary_dialog_remove)
        } else {
            stringResource(R.string.apps_limit_dialog_cancel)
        },
        onDismiss = if (settings.salaryConfigured) {
            { onConfirm(null) }
        } else onDismiss,
    ) {
        Text(
            text = stringResource(R.string.home_salary_dialog_provoke),
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
        Spacer(Modifier.size(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { input -> text = input.filter { it.isDigit() || it == '.' || it == ',' } },
            label = { Text(stringResource(R.string.home_salary_dialog_label)) },
            singleLine = true,
            isError = isInvalid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Blocos da Home que têm um balão informativo (o que significa + como a conta é feita). */
private enum class HomeCardInfo { DAMAGE, ADDICTION }

@Composable
private fun HomeCardInfoDialog(
    card: HomeCardInfo,
    /** Salário de referência já formatado — o corpo de [HomeCardInfo.DAMAGE] o cita. */
    referenceSalary: String,
    onDismiss: () -> Unit,
    /** Quando presente, o diálogo ganha "Ver apps" (primeiro acesso, sem apps na conta). */
    onPickApps: (() -> Unit)? = null,
) {
    val (titleRes, bodyRes) = when (card) {
        HomeCardInfo.DAMAGE -> R.string.home_info_damage_title to R.string.home_info_damage_body
        HomeCardInfo.ADDICTION -> R.string.home_info_addiction_title to R.string.home_info_addiction_body
    }
    DollarBlockDialog(
        onDismissRequest = onDismiss,
        title = stringResource(titleRes),
        body = if (card == HomeCardInfo.DAMAGE) stringResource(bodyRes, referenceSalary) else stringResource(bodyRes),
        confirmText = stringResource(R.string.home_card_info_ok),
        onConfirm = onDismiss,
        dismissText = onPickApps?.let { stringResource(R.string.home_card_info_open_apps) },
        onDismiss = onPickApps,
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    DollarBlockTheme {
        HomeScreen()
    }
}
