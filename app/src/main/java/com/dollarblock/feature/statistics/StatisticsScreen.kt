package com.dollarblock.feature.statistics

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dollarblock.R
import com.dollarblock.core.designsystem.AlertAmber
import com.dollarblock.core.designsystem.BlockingRed
import com.dollarblock.core.designsystem.InterFamily
import com.dollarblock.core.designsystem.JakartaFamily
import com.dollarblock.core.designsystem.MintGlow
import com.dollarblock.core.designsystem.components.AppIcon
import com.dollarblock.core.designsystem.components.DollarBlockDialog
import com.dollarblock.core.designsystem.components.HomePalette
import com.dollarblock.core.designsystem.components.HomeTopRow
import com.dollarblock.core.designsystem.components.Mono
import com.dollarblock.core.designsystem.components.TileColors
import com.dollarblock.core.designsystem.components.homePalette
import com.dollarblock.domain.model.AppCurrency
import com.dollarblock.domain.model.MoneyFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Extrato (E24) — o calendário da disciplina, na linguagem visual da Home. Um dia vira uma
 * bolinha colorida pelo desfecho; o herói conta os dias dentro do limite. Nada aqui repete a
 * Home (uso de hoje, tentativas, carteira do mês): é o histórico, não o dia.
 */
@Composable
fun StatisticsScreen(
    modifier: Modifier = Modifier,
    onOpenProfile: () -> Unit = {},
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = homePalette()
    val scrollState = rememberScrollState()
    val statusBarPx = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    var showInfo by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val currency = state.moneySettings.currency

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .drawBehind {
                with(palette) {
                    drawHeroGlow(
                        centerY = 120.dp.toPx() - scrollState.value,
                        radius = 380.dp.toPx(),
                        extendUp = statusBarPx,
                    )
                }
            }
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp),
    ) {
        HomeTopRow(palette = palette, onOpenProfile = onOpenProfile)
        Header(period = state.period, palette = palette, onPeriod = { viewModel.period.value = it })
        Hero(state, palette, onInfo = { showInfo = true })

        if (state.hasLimits) {
            CalendarPanel(state, palette, locale, onDay = viewModel::selectDay)
            Legend(state.days.any { it.beforeDollarBlock }, palette)
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InfoTile(
                    label = stringResource(R.string.stat_tile_average),
                    value = state.averageMillis?.let { duration(it) } ?: "—",
                    caption = state.averageCost?.let { stringResource(R.string.stat_tile_average_caption, MoneyFormat.format(it, currency)) }
                        ?: stringResource(R.string.stat_day_no_usage),
                    colors = palette.nextTile,
                    modifier = Modifier.weight(1f),
                )
                InfoTile(
                    label = stringResource(R.string.stat_tile_priciest),
                    value = state.priciestCost?.let { MoneyFormat.format(it, currency) } ?: "—",
                    caption = state.priciestDay?.let { dayLabel(it.epochDay, locale) } ?: stringResource(R.string.stat_day_no_usage),
                    colors = palette.paidTile,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = state.priciestDay != null) { state.priciestDay?.let { viewModel.selectDay(it.epochDay) } },
                )
            }
            AppsSection(state, palette)
        }
    }

    if (showInfo) {
        DollarBlockDialog(
            onDismissRequest = { showInfo = false },
            title = stringResource(R.string.stat_info_calendar_title),
            body = stringResource(R.string.stat_info_calendar_body),
            confirmText = stringResource(R.string.stat_info_ok),
            onConfirm = { showInfo = false },
        )
    }

    state.selected?.let { detail ->
        DaySheet(detail, currency, palette, locale, onDismiss = { viewModel.selectDay(null) })
    }
}

// ── Topo ──────────────────────────────────────────────────────────────────────────────────

@Composable
private fun Header(period: StatPeriod, palette: HomePalette, onPeriod: (StatPeriod) -> Unit) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatPeriod.entries.forEach { entry ->
                val selected = entry == period
                Text(
                    stringResource(entry.labelRes),
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    // Escuro: tinta escura sobre o verde vivo; claro: o verde é escuro, texto branco.
                    color = when {
                        !selected -> palette.muted
                        palette.dark -> Color(0xFF04210E)
                        else -> Color.White
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) palette.emerald else palette.chip)
                        .clickable { onPeriod(entry) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun Hero(state: StatisticsUiState, palette: HomePalette, onInfo: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onInfo)
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.stat_hero_label),
                fontFamily = Mono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 2.5.sp,
                color = palette.heroLabel,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Outlined.Info,
                contentDescription = stringResource(R.string.stat_info_calendar_title),
                tint = palette.heroLabel,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (!state.hasLimits || state.counted == 0) {
            Text(
                "—",
                fontFamily = JakartaFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 64.sp,
                color = palette.text,
            )
            Text(
                stringResource(if (state.hasLimits) R.string.stat_hero_waiting else R.string.stat_hero_empty),
                fontFamily = InterFamily,
                fontSize = 15.sp,
                color = palette.heroEquiv,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            return@Column
        }
        Text(
            text = digitsBig(stringResource(R.string.stat_hero_count, state.within, state.counted)),
            fontFamily = JakartaFamily,
            fontWeight = FontWeight.ExtraBold,
            color = palette.text,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
        Text(
            text = if (state.streak > 0) {
                pluralStringResource(R.plurals.stat_streak, state.streak, state.streak)
            } else {
                stringResource(R.string.stat_streak_zero)
            },
            fontFamily = InterFamily,
            fontSize = 15.sp,
            color = palette.heroEquiv,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** "5 de 7" com os números no tamanho do herói e as palavras menores (vale para qualquer idioma). */
private fun digitsBig(text: String): AnnotatedString = buildAnnotatedString {
    text.forEach { c ->
        if (c.isDigit()) {
            withStyle(SpanStyle(fontSize = 72.sp, letterSpacing = (-2).sp)) { append(c) }
        } else {
            withStyle(SpanStyle(fontSize = 30.sp, letterSpacing = 0.sp)) { append(c) }
        }
    }
}

// ── Calendário ────────────────────────────────────────────────────────────────────────────

@Composable
private fun CalendarPanel(
    state: StatisticsUiState,
    palette: HomePalette,
    locale: Locale,
    onDay: (Long) -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(palette.walletBg)
            .border(1.dp, palette.walletBorder, shape)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (state.period) {
            StatPeriod.WEEKLY -> {
                WeekdayHeader(palette, locale)
                Row(modifier = Modifier.fillMaxWidth()) {
                    state.days.forEach { day ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            DayCell(day, state.today, palette, size = 38, onClick = onDay)
                        }
                    }
                }
            }
            StatPeriod.MONTHLY -> MonthGrid(state.days, state.today, palette, locale, title = null, onDay = onDay)
            StatPeriod.TOTAL -> {
                // Um bloco por mês, do mais recente para o mais antigo.
                state.days.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }
                    .toSortedMap(compareByDescending { it })
                    .forEach { (month, days) ->
                        MonthGrid(days, state.today, palette, locale, title = monthTitle(month, locale), onDay = onDay)
                    }
            }
        }
    }
}

@Composable
private fun WeekdayHeader(palette: HomePalette, locale: Locale) {
    Row(modifier = Modifier.fillMaxWidth()) {
        java.time.DayOfWeek.entries.forEach { dow ->
            Text(
                dow.getDisplayName(JavaTextStyle.NARROW, locale).uppercase(locale),
                fontFamily = Mono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = palette.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Grade de um mês, semanas começando na segunda; [days] pode cobrir só parte do mês. */
@Composable
private fun MonthGrid(
    days: List<CalendarDay>,
    today: Long,
    palette: HomePalette,
    locale: Locale,
    title: String?,
    onDay: (Long) -> Unit,
) {
    if (days.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                title,
                fontFamily = Mono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                color = palette.heroLabel,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        WeekdayHeader(palette, locale)
        val leading = LocalDate.ofEpochDay(days.first().epochDay).dayOfWeek.value - 1
        val cells: List<CalendarDay?> = List(leading) { null } + days
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        week.getOrNull(i)?.let { DayCell(it, today, palette, size = 34, onClick = onDay) }
                    }
                }
            }
        }
    }
}

private data class ToneStyle(val fill: Color, val ring: Color?, val text: Color)

@Composable
private fun toneStyle(tone: DayTone, palette: HomePalette): ToneStyle {
    val ink = Color(0xFF04210E)
    return when (tone) {
        DayTone.WITHIN -> ToneStyle(palette.emerald, null, ink)
        DayTone.HELD -> ToneStyle(AlertAmber, null, Color(0xFF2A1C00))
        DayTone.FREE -> ToneStyle(Color.Transparent, MintGlow, palette.text)
        DayTone.OVER, DayTone.PAID -> ToneStyle(BlockingRed, null, Color.White)
        DayTone.EMPTY -> ToneStyle(Color.Transparent, palette.track, palette.muted)
    }
}

@Composable
private fun DayCell(day: CalendarDay, today: Long, palette: HomePalette, size: Int, onClick: (Long) -> Unit) {
    val future = day.epochDay > today
    val style = toneStyle(day.tone, palette)
    val isToday = day.epochDay == today
    val todayRing = palette.text
    // Antes do DollarBlock: cor mais fraca e contorno tracejado — é o histórico do Android.
    val fill = if (day.beforeDollarBlock) style.fill.copy(alpha = style.fill.alpha * 0.45f) else style.fill
    val dashedColor = style.ring ?: style.fill
    Box(
        modifier = Modifier
            .size((size + 6).dp)
            .clip(CircleShape)
            .clickable(enabled = !future) { onClick(day.epochDay) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size.dp)) {
            val r = this.size.minDimension / 2
            drawCircle(fill, radius = r)
            when {
                day.beforeDollarBlock -> drawCircle(
                    dashedColor,
                    radius = r - 1.dp.toPx(),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                )
                style.ring != null && !future -> drawCircle(style.ring, radius = r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
            if (isToday) {
                drawCircle(todayRing, radius = r + 2.5.dp.toPx(), center = Offset(this.size.width / 2, this.size.height / 2), style = Stroke(1.5.dp.toPx()))
            }
        }
        Text(
            LocalDate.ofEpochDay(day.epochDay).dayOfMonth.toString(),
            fontFamily = InterFamily,
            fontWeight = if (day.tone == DayTone.EMPTY) FontWeight.Medium else FontWeight.Bold,
            fontSize = 12.sp,
            color = when {
                future -> palette.muted.copy(alpha = 0.4f)
                day.beforeDollarBlock -> palette.text
                else -> style.text
            },
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(showBefore: Boolean, palette: HomePalette) {
    FlowRow(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendItem(DayTone.WITHIN, R.string.stat_legend_within, palette)
        LegendItem(DayTone.HELD, R.string.stat_legend_held, palette)
        LegendItem(DayTone.FREE, R.string.stat_legend_free, palette)
        LegendItem(DayTone.PAID, R.string.stat_legend_paid, palette)
        if (showBefore) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(10.dp)) {
                    drawCircle(
                        palette.muted,
                        radius = size.minDimension / 2 - 0.5.dp.toPx(),
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))),
                    )
                }
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.stat_legend_before), fontFamily = InterFamily, fontSize = 11.5.sp, color = palette.muted)
            }
        }
    }
}

@Composable
private fun LegendItem(tone: DayTone, @StringRes label: Int, palette: HomePalette) {
    val style = toneStyle(tone, palette)
    Row(verticalAlignment = Alignment.CenterVertically) {
        ToneDot(style, size = 10)
        Spacer(Modifier.width(5.dp))
        Text(stringResource(label), fontFamily = InterFamily, fontSize = 11.5.sp, color = palette.muted)
    }
}

@Composable
private fun ToneDot(style: ToneStyle, size: Int) {
    Canvas(Modifier.size(size.dp)) {
        val r = this.size.minDimension / 2
        drawCircle(style.fill, radius = r)
        style.ring?.let { drawCircle(it, radius = r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx())) }
    }
}

// ── Tiles ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun InfoTile(label: String, value: String, caption: String, colors: TileColors, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.background)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.title)
        Text(
            value,
            fontFamily = JakartaFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
            letterSpacing = (-0.5).sp,
            color = colors.value,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            caption,
            fontFamily = InterFamily,
            fontSize = 12.sp,
            color = colors.title.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Apps ──────────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppsSection(state: StatisticsUiState, palette: HomePalette) {
    if (state.apps.isEmpty()) return
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
        Text(
            stringResource(R.string.stat_apps_title),
            fontFamily = JakartaFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = palette.text,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        state.apps.forEachIndexed { i, app ->
            AppStripRow(app, state.period, palette)
            if (i < state.apps.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
        }
    }
}

@Composable
private fun AppStripRow(app: StatementAppRow, period: StatPeriod, palette: HomePalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.icon, size = 36, corner = 10)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.appName,
                    fontFamily = JakartaFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.stat_app_within, app.within, app.counted),
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = palette.muted,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (period == StatPeriod.WEEKLY) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    app.tones.forEach { ToneDot(toneStyle(it, palette), size = 12) }
                }
            } else {
                // Mês/Tudo: muitos dias — uma barra fina de segmentos, um por dia.
                val gap = if (app.tones.size > 45) 0.dp else 2.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    app.tones.forEach { tone ->
                        val style = toneStyle(tone, palette)
                        val color = when (tone) {
                            DayTone.EMPTY -> palette.track
                            DayTone.FREE -> MintGlow
                            else -> style.fill
                        }
                        Box(Modifier.weight(1f).fillMaxHeight().background(color))
                    }
                }
            }
        }
    }
}

// ── Extrato do dia ────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(
    detail: DayDetail,
    currency: AppCurrency,
    palette: HomePalette,
    locale: Locale,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.background,
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text(
                dayLabel(detail.day.epochDay, locale, long = true),
                fontFamily = JakartaFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = palette.text,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                ToneDot(toneStyle(detail.day.tone, palette), size = 10)
                Spacer(Modifier.width(6.dp))
                Text(
                    buildString {
                        append(stringResource(detail.day.tone.labelRes))
                        if (detail.day.beforeDollarBlock) append(" · ").append(stringResource(R.string.stat_legend_before))
                    },
                    fontFamily = InterFamily,
                    fontSize = 13.sp,
                    color = palette.muted,
                )
            }
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = palette.text, fontWeight = FontWeight.Bold)) {
                        append(MoneyFormat.format(detail.cost, currency))
                    }
                    append("  ·  ")
                    append(duration(detail.day.usedMillis))
                },
                fontFamily = InterFamily,
                fontSize = 14.sp,
                color = palette.muted,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
            )
            if (detail.rows.isEmpty()) {
                Text(
                    stringResource(R.string.stat_day_no_usage),
                    fontFamily = InterFamily,
                    fontSize = 13.sp,
                    color = palette.muted,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            detail.rows.forEachIndexed { i, row ->
                DayRow(row, currency, palette)
                if (i < detail.rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
            }
        }
    }
}

@Composable
private fun DayRow(row: DayDetailRow, currency: AppCurrency, palette: HomePalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(row.icon, size = 32, corner = 9)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.appName,
                fontFamily = JakartaFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.limitMinutes?.let { stringResource(R.string.stat_day_usage, duration(row.usedMillis), durationMin(it)) }
                    ?: duration(row.usedMillis),
                fontFamily = InterFamily,
                fontSize = 12.sp,
                color = palette.muted,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                MoneyFormat.format(row.cost, currency),
                fontFamily = JakartaFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = palette.text,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToneDot(toneStyle(row.tone, palette), size = 8)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(row.tone.shortLabelRes), fontFamily = InterFamily, fontSize = 11.5.sp, color = palette.muted)
            }
        }
    }
}

// ── Formatação ────────────────────────────────────────────────────────────────────────────

@Composable
private fun duration(millis: Long): String = durationMin((millis / 60_000L).toInt())

@Composable
private fun durationMin(minutes: Int): String =
    if (minutes >= 60) stringResource(R.string.home_duration_hm, minutes / 60, minutes % 60)
    else stringResource(R.string.home_duration_m, minutes)

private fun dayLabel(epochDay: Long, locale: Locale, long: Boolean = false): String {
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, if (long) "EEEEdMMMM" else "EEEdMMM")
    return LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern(pattern, locale))
        .replaceFirstChar { it.titlecase(locale) }
}

private fun monthTitle(month: YearMonth, locale: Locale): String {
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMMyyyy")
    return month.atDay(1).format(DateTimeFormatter.ofPattern(pattern, locale)).uppercase(locale)
}

private val StatPeriod.labelRes: Int
    @StringRes get() = when (this) {
        StatPeriod.WEEKLY -> R.string.stat_period_weekly
        StatPeriod.MONTHLY -> R.string.stat_period_monthly
        StatPeriod.TOTAL -> R.string.stat_period_total
    }

private val DayTone.labelRes: Int
    @StringRes get() = when (this) {
        DayTone.WITHIN -> R.string.stat_tone_within
        DayTone.HELD -> R.string.stat_tone_held
        DayTone.FREE -> R.string.stat_tone_free
        DayTone.OVER -> R.string.stat_tone_over
        DayTone.PAID -> R.string.stat_tone_paid
        DayTone.EMPTY -> R.string.stat_tone_empty
    }

private val DayTone.shortLabelRes: Int
    @StringRes get() = when (this) {
        DayTone.WITHIN -> R.string.stat_legend_within
        DayTone.HELD -> R.string.stat_legend_held
        DayTone.FREE -> R.string.stat_legend_free
        DayTone.OVER -> R.string.stat_legend_over
        DayTone.PAID -> R.string.stat_legend_paid
        DayTone.EMPTY -> R.string.stat_tone_empty
    }
