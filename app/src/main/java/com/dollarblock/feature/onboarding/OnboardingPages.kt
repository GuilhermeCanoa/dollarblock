package com.dollarblock.feature.onboarding

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.spring
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.data.permissions.AppPermission
import com.dollarblock.data.permissions.PermissionsState
import com.dollarblock.domain.model.AppCurrency
import com.dollarblock.domain.model.MoneyFormat
import com.dollarblock.feature.home.HomeMetrics
import com.dollarblock.feature.home.text
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sin

/** Quantas páginas de "papelada" (permissões) o onboarding tem — para o "Papelada N de M". */
private const val PAPERWORK_PAGES = 3

/** Respiro acima da arte da 1ª página. Pouco: o teto esticado da arte já ocupa o espaço de cima. */
private val HERO_TOP_GAP = 6.dp

/** Distância do pé do mascote até o título (~0,5 cm). */
private val HERO_TEXT_GAP = 31.dp

// ---------------------------------------------------------------------------------------
// 1. Entrada
// ---------------------------------------------------------------------------------------

/**
 * A marca entra, o título sobe, e a conta já começa a correr: o tempo desde que o app
 * abriu, convertido em dinheiro na referência de R$ 3.000/mês (a mesma régua da Home).
 */
@Composable
fun EntryPage(active: Boolean, modifier: Modifier = Modifier) {
    val animate = rememberAnimationsEnabled()
    // Fundo entra como cena de cinema: aparece e "assenta" de um leve zoom.
    val heroScale = remember { Animatable(if (animate) 1.08f else 1f) }
    val heroAlpha = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        heroAlpha.animateTo(1f, tween(500))
    }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        heroScale.animateTo(1f, tween(1_200, easing = FastOutSlowInEasing))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Escudo no sofá com ventilador de teto girando: fundo de ponta a ponta que se
        // dissolve na página (o texto sobe por cima da parte esmaecida).
        OnboardingHero(
            modifier = Modifier
                .padding(top = HERO_TOP_GAP)
                .graphicsLayer {
                    scaleX = heroScale.value
                    scaleY = heroScale.value
                    alpha = heroAlpha.value
                },
        )
        // O texto começa HERO_TEXT_GAP abaixo do pé do mascote (a arte ocupa a largura toda,
        // então a altura dela — e a do pé — sai da largura da tela).
        // Positivo vira espaço de verdade (entra na rolagem); negativo sobe o texto sobre a arte.
        val heroHeight = LocalConfiguration.current.screenWidthDp.dp / HERO_ASPECT
        val textShift = HERO_TEXT_GAP - heroHeight * (1f - HERO_FOOT_Y)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(top = textShift.coerceAtLeast(0.dp))
                .offset(y = textShift.coerceAtMost(0.dp)),
        ) {
            Reveal(active, order = 3) {
                Text(
                    text = stringResource(R.string.onb_welcome_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
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

/** Tempo para a caneta escrever as quatro cláusulas inteiras. */
private const val CONTRACT_WRITE_MS = 14_000

/**
 * "O contrato" — papel/mono do recibo de bloqueio (ver `InvoiceReceipt` em BlockActivity.kt).
 * Uma caneta escreve as cláusulas em sequência, letra a letra; o rodapé aparece quando ela
 * termina. A assinatura é o botão do rodapé ([SignButton]).
 */
@Composable
fun ContractPage(active: Boolean, modifier: Modifier = Modifier) {
    val paper = Color(0xFFF7F4EC)
    val ink = Color(0xFF1C2B26)

    // Quantos caracteres já foram escritos, somando as cláusulas na ordem.
    val clauseTexts = clauses.map { stringResource(it) }
    val total = clauseTexts.sumOf { it.length }.toFloat()
    val animate = rememberAnimationsEnabled()
    val written = remember { Animatable(if (animate) 0f else total) }
    LaunchedEffect(active) {
        if (active && written.value < total) {
            delay(350)
            written.animateTo(total, tween(CONTRACT_WRITE_MS, easing = LinearEasing))
        }
    }
    val footerAlpha by animateFloatAsState(
        targetValue = if (written.value >= total) 1f else 0f,
        animationSpec = tween(420),
        label = "contractFooter",
    )

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
                fontSize = 28.sp,
                color = ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, bottom = 14.dp),
            )
            ContractDivider(ink.copy(alpha = 0.35f))
            var start = 0
            clauseTexts.forEachIndexed { index, text ->
                val visible = (written.value - start).toInt().coerceIn(0, text.length)
                start += text.length
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    Text(
                        text = stringResource(R.string.onb_clause_label, index + 1).uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        color = ink.copy(alpha = 0.5f),
                        modifier = Modifier.graphicsLayer { alpha = (visible / 6f).coerceIn(0f, 1f) },
                    )
                    HandwrittenText(
                        text = text,
                        visible = visible,
                        ink = Color(0xFF111C18), // tinta um tom mais escura que o resto do papel
                        penWobble = written.value,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Box(modifier = Modifier.graphicsLayer { alpha = footerAlpha }) {
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

/**
 * Texto sendo escrito: os primeiros [visible] caracteres em tinta, o resto já diagramado mas
 * transparente (nada pula de lugar enquanto escreve). Enquanto não termina, a pena fica
 * com a ponta no fim do que já foi escrito, balançando com [penWobble].
 */
@Composable
private fun HandwrittenText(
    text: String,
    visible: Int,
    ink: Color,
    penWobble: Float,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val writing = visible in 1 until text.length
    Box(modifier = modifier) {
        Text(
            text = buildAnnotatedString {
                append(text.substring(0, visible))
                withStyle(SpanStyle(color = Color.Transparent)) { append(text.substring(visible)) }
            },
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium, // tinta de pena: um pouco mais encorpada
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = ink,
            onTextLayout = { layout = it },
        )
        val cursor = layout?.takeIf { writing }?.getCursorRect(visible)
        if (cursor != null) {
            QuillPen(
                modifier = Modifier
                    .offset { IntOffset(cursor.left.roundToInt(), cursor.bottom.roundToInt()) }
                    .graphicsLayer {
                        // Ponta da pena no canto inferior esquerdo; sobe/desce e gira de leve.
                        translationY = -size.height + sin(penWobble * 1.7f) * 2.dp.toPx()
                        rotationZ = sin(penWobble * 0.9f) * 6f
                        transformOrigin = TransformOrigin(0f, 1f)
                    },
            )
        }
    }
}

/**
 * Pena de escrever inclinada, com a ponta no canto inferior esquerdo do desenho: haste fina,
 * ponta molhada de tinta e as duas metades da pena (uma mais larga), com nervuras e entalhes.
 */
@Composable
private fun QuillPen(modifier: Modifier = Modifier) {
    val vaneDark = Color(0xFF1F4D3C)
    val vaneLight = Color(0xFF2F7358)
    val barb = Color(0xFF9CC9A8)
    val shaft = Color(0xFFE9E2C9)
    val inkTip = Color(0xFF101A16)
    Canvas(modifier = modifier.size(40.dp)) {
        val s = size.width
        val tip = Offset(0f, s)
        val len = s * 1.3f                       // comprimento total da pena (em pé)
        fun at(along: Float, across: Float) = Offset(tip.x + across, tip.y - along)
        // Desenhada em pé (ponta embaixo) e girada 40° em torno da ponta.
        rotate(degrees = 40f, pivot = tip) {
            val vaneStart = len * 0.28f
            val vaneEnd = len
            // Metade larga (esquerda) e metade estreita (direita), em curvas de folha.
            val wide = Path().apply {
                moveTo(at(vaneStart, 0f).x, at(vaneStart, 0f).y)
                val c1 = at(len * 0.45f, -s * 0.24f)
                val p1 = at(len * 0.8f, -s * 0.2f)
                quadraticTo(c1.x, c1.y, p1.x, p1.y)
                val c2 = at(len * 0.97f, -s * 0.12f)
                val p2 = at(vaneEnd, 0f)
                quadraticTo(c2.x, c2.y, p2.x, p2.y)
                close()
            }
            val narrow = Path().apply {
                moveTo(at(vaneStart + len * 0.06f, 0f).x, at(vaneStart + len * 0.06f, 0f).y)
                val c1 = at(len * 0.55f, s * 0.13f)
                val p1 = at(len * 0.86f, s * 0.1f)
                quadraticTo(c1.x, c1.y, p1.x, p1.y)
                val c2 = at(len * 0.97f, s * 0.05f)
                val p2 = at(vaneEnd, 0f)
                quadraticTo(c2.x, c2.y, p2.x, p2.y)
                close()
            }
            drawPath(wide, vaneDark)
            drawPath(narrow, vaneLight)
            // Nervuras: riscos finos saindo da haste para cima e para fora.
            for (k in 1..7) {
                val a = vaneStart + (vaneEnd - vaneStart) * k / 8.5f
                val reach = 0.75f - 0.35f * ((k - 4).let { it * it } / 16f)
                drawLine(barb.copy(alpha = 0.55f), at(a, 0f), at(a + len * 0.07f, -s * 0.2f * reach), strokeWidth = s * 0.012f)
                drawLine(barb.copy(alpha = 0.4f), at(a, 0f), at(a + len * 0.06f, s * 0.1f * reach), strokeWidth = s * 0.01f)
            }
            // Entalhes nas bordas da pena (a "pluma" separando).
            drawLine(Color(0xFFF7F4EC), at(len * 0.52f, -s * 0.23f), at(len * 0.58f, -s * 0.15f), strokeWidth = s * 0.02f)
            drawLine(Color(0xFFF7F4EC), at(len * 0.7f, s * 0.12f), at(len * 0.74f, s * 0.07f), strokeWidth = s * 0.018f)
            // Haste: do fim da pena até a ponta, afinando.
            drawLine(shaft, at(len * 1.02f, 0f), at(len * 0.08f, 0f), strokeWidth = s * 0.035f, cap = StrokeCap.Round)
            // Ponta cortada, molhada de tinta.
            val nib = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(at(len * 0.12f, -s * 0.03f).x, at(len * 0.12f, -s * 0.03f).y)
                lineTo(at(len * 0.12f, s * 0.03f).x, at(len * 0.12f, s * 0.03f).y)
                close()
            }
            drawPath(nib, inkTip)
        }
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
private fun PageTitle(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = TextUnit.Unspecified) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontSize = fontSize,
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
    bottomBleed: Dp = 0.dp, // espaço por baixo do botão (a praia continua até o fim da tela)
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
                PageTitle(stringResource(R.string.onb_perm_usage_title), Modifier.padding(top = 4.dp))
            }
            // O texto à esquerda, encostado no celular; à direita, o feed rolando e o cronômetro
            // contando o tempo de tela.
            // Coluna estreita (~3 palavras por linha): o texto acompanha, o celular é o foco.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    // Desloca o bloco para a esquerda: o cronômetro à direita pesava o conjunto.
                    .padding(top = 4.dp, end = 62.dp),
            ) {
                Reveal(active, order = 3) {
                    Text(
                        text = stringResource(R.string.onb_measure_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .width(124.dp)
                            .padding(end = 12.dp),
                    )
                }
                Reveal(active, order = 2) {
                    ScreenTimeScene(
                        Modifier
                            .height(210.dp)
                            .aspectRatio(SCREEN_TIME_ASPECT),
                    )
                }
            }
            // O mesmo cofre de segurança da Tranca, com as mesmas garantias.
            Reveal(active, order = 4) {
                SecurityVault(
                    active = active,
                    titleRes = R.string.onb_lock_privacy,
                    points = listOf(
                        SecurityPoint(Icons.Filled.VisibilityOff, R.string.onb_trust_lock_1),
                        SecurityPoint(Icons.Filled.PhonelinkLock, R.string.onb_trust_lock_2),
                        SecurityPoint(Icons.Filled.PowerSettingsNew, R.string.onb_trust_lock_3),
                    ),
                    firstOrder = 4,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
        } else {
            RubberStampGranted(active, modifier = Modifier.padding(top = 52.dp, bottom = 4.dp)) // espaço para o cabo do carimbo caber na batida
            // A rosca do gráfico é de onde sai a chuva de ícones da cena de baixo.
            var rainSource by remember { mutableStateOf<RainSource?>(null) }
            WeeklyBill(
                summary = summary,
                onDonutPositioned = { rainSource = it },
                modifier = Modifier.padding(top = 20.dp),
            )
            // Fecho da página: sob o guarda-sol do DollarBlock, as redes sociais quicam para longe.
            LoungeScene(
                rainSource = rainSource,
                bottomExtension = bottomBleed,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .height(160.dp + bottomBleed),
            )
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
private fun WeeklyBill(
    summary: QuickSummaryState,
    onDonutPositioned: (RainSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        summary.isLoading -> CircularProgressIndicator(modifier = modifier.padding(32.dp))
        summary.topApps.isEmpty() -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier,
        ) {
            PageTitle(stringResource(R.string.onb_summary_no_usage_title))
            PageBody(stringResource(R.string.onb_summary_no_usage_body), Modifier.padding(top = 12.dp))
        }
        else -> WeeklyBillContent(summary, onDonutPositioned, modifier)
    }
}

@Composable
private fun WeeklyBillContent(
    summary: QuickSummaryState,
    onDonutPositioned: (RainSource) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Canvas(
                modifier = Modifier
                    .size(100.dp)
                    .onGloballyPositioned { coords ->
                        val bounds = coords.boundsInRoot()
                        onDonutPositioned(RainSource(bounds.center, bounds.width / 2f))
                    },
            ) {
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
            PageTitle(
                stringResource(R.string.onb_perm_accessibility_title),
                Modifier.padding(top = 14.dp),
                fontSize = 28.sp,
            )
        }
        // O segurança da porta: deixa a fila entrar até o limite e aí fecha a corda.
        Reveal(active, order = 2) {
            BouncerScene(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .aspectRatio(BOUNCER_ASPECT),
            )
        }
        Reveal(active, order = 3) {
            // Um pouco menor que o PageBody, para caber em 2 linhas e o tutorial subir.
            Text(
                text = stringResource(R.string.onb_lock_body),
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
        // Uma linha só de privacidade (o cofre completo fica na Medição).
        var privacySize by remember { mutableStateOf(12.sp) }
        var privacyFits by remember { mutableStateOf(false) }
        Reveal(active, order = 4) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    tint = DollarBlockTheme.colors.success,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.onb_trust_lock_1),
                    style = MaterialTheme.typography.bodyMedium,
                    // Numa linha só: encolhe até caber (a fonte do sistema pode estar maior)
                    // e só aparece depois de caber, para não piscar cortada.
                    fontSize = privacySize,
                    maxLines = 1,
                    softWrap = false,
                    onTextLayout = { layout ->
                        if (layout.hasVisualOverflow && privacySize.value > 8f) {
                            privacySize *= 0.94f
                        } else {
                            privacyFits = true
                        }
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .drawWithContent { if (privacyFits) drawContent() },
                )
            }
        }
        if (granted) {
            // Mesmo carimbo de madeira da Medição, no mesmo tamanho (espaço para o cabo na batida).
            RubberStampGranted(active, modifier = Modifier.padding(top = 52.dp, bottom = 8.dp))
        } else {
            Reveal(active, order = 7) {
                AccessibilityTutorialThumbnail(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// 5. Últimos ajustes (Sobreposição + Notificações)
// ---------------------------------------------------------------------------------------

// Espaço entre as notificações e a altura reservada ao segurança, embaixo.
private val NOTE_GAP = 12.dp
private val PULLER_ROOM = 168.dp
// Cada unidade da arte do segurança, em dp, e onde ficam os pés dele (da direita/de baixo).
private const val PULLER_UNIT_DP = 1.25f
private val PULLER_END = 64.dp
private val PULLER_BOTTOM = 6.dp
private val CordColor = Color(0xFFE3D3AE)
private val RingColor = Color(0xFFD9A94E)
// O cesto das notificações barradas, no canto de baixo à esquerda.
private val BIN_WIDTH = 78.dp
private val BIN_CENTER_X = 45.dp

/**
 * A página inteira chega como notificações, uma embaixo da outra: o título, a frase e um
 * cartão por permissão. Embaixo, o segurança da Tranca segura uma cordinha presa na última
 * notificação; a cada puxão, desce a próxima (saindo de trás da anterior, com mola). Sem
 * animações do sistema, tudo já aparece no lugar e ele fica parado.
 */
@Composable
fun FinalSettingsPage(
    active: Boolean,
    page: OnboardingPage.FinalSettings,
    permissions: PermissionsState,
    onRequest: (AppPermission) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagePermissions = permissionsOn(page)
    val count = 2 + pagePermissions.size
    val animate = rememberAnimationsEnabled()
    val drops = remember { List(count) { Animatable(if (animate) 0f else 1f) } }
    val pull = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        delay(350)
        drops.forEach { drop ->
            if (drop.value >= 1f) return@forEach
            // A notificação desce junto com o puxão; o braço volta enquanto ela ainda quica.
            launch { drop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f)) }
            pull.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
            pull.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
            delay(90)
        }
    }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active, animate) {
        if (!active || !animate) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1e9f }
    }

    // Onde cada notificação está (sem o deslocamento da descida), relativo à página.
    val density = LocalDensity.current
    var pageTop by remember { mutableFloatStateOf(0f) }
    val noteTops = remember { mutableStateListOf(*Array(count) { 0f }) }
    val noteHeights = remember { mutableStateListOf(*Array(count) { 0f }) }
    val gapPx = with(density) { NOTE_GAP.toPx() }

    // O segurança no canto de baixo à direita; o cesto das penetras no canto da esquerda.
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    val unit = PULLER_UNIT_DP * density.density
    val foot = with(density) {
        Offset(pageSize.width - PULLER_END.toPx(), pageSize.height - PULLER_BOTTOM.toPx())
    }
    val binWidth = with(density) { BIN_WIDTH.toPx() }
    val binBottom = with(density) { Offset(BIN_CENTER_X.toPx(), pageSize.height - PULLER_BOTTOM.toPx()) }
    val binMouth = binBottom - Offset(0f, binWidth * 0.7f)
    // As penetras só começam depois que a última notificação da página desceu.
    val allDown = drops.last().value >= 1f
    val spam = rememberSpamState(running = active && animate && allDown)

    @Composable
    fun Note(index: Int, content: @Composable () -> Unit) {
        Box(
            Modifier
                .padding(top = NOTE_GAP)
                .zIndex((count - index).toFloat())
                .onGloballyPositioned {
                    noteTops[index] = it.positionInRoot().y
                    noteHeights[index] = it.size.height.toFloat()
                }
                .graphicsLayer {
                    val p = drops[index].value
                    // Sai de trás da notificação de cima.
                    translationY = -(1f - p) * (size.height + gapPx)
                    alpha = (p * 4f).coerceAtMost(1f)
                },
        ) { content() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                pageTop = it.positionInRoot().y
                pageSize = it.size
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = PULLER_ROOM),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PaperworkHeader(step = 3, required = null, modifier = Modifier.padding(bottom = 4.dp))
            Note(0) {
                NoteCard {
                    // O nome da página: centralizado, sem ponto final.
                    Text(
                        text = stringResource(R.string.onb_final_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Note(1) {
                NoteCard {
                    Text(
                        text = stringResource(R.string.onb_final_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            pagePermissions.forEachIndexed { index, permission ->
                Note(2 + index) {
                    SettingCard(
                        permission = permission,
                        granted = permissions.isGranted(permission),
                        onRequest = { onRequest(permission) },
                    )
                }
            }
        }

        // O cesto, o segurança e a cordinha, por cima (não pegam toque).
        Canvas(Modifier.matchParentSize()) {
            drawSpamBin(binBottom, binWidth, spam.wobble.value)
            val hand = drawCordPuller(foot, unit, pull.value, spam.swat.value, time)
            // A cordinha sai da base da última notificação que desceu (ou da primeira, no começo).
            val last = drops.indexOfLast { it.value > 0f }
            val anchorY = if (last < 0) {
                noteTops[0] - pageTop
            } else {
                noteTops[last] - pageTop + noteHeights[last] - (1f - drops[last].value) * (noteHeights[last] + gapPx)
            }
            val anchor = Offset(hand.x, anchorY)
            val slack = (1f - pull.value) * 6.dp.toPx()
            val cord = Path().apply {
                moveTo(anchor.x, anchor.y)
                quadraticTo((anchor.x + hand.x) / 2f + slack, (anchor.y + hand.y) / 2f, hand.x, hand.y)
            }
            drawPath(cord, CordColor, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(RingColor, 4.dp.toPx(), anchor)
            drawCircle(RingColor, 5.dp.toPx(), hand, style = Stroke(2.dp.toPx()))
        }

        // A notificação penetra que leva o peteleco (só com animações).
        if (animate && pageSize != IntSize.Zero) {
            SpamIntruder(spam, swatPoint = cordPullerSwatPoint(foot, unit), binMouth = binMouth)
        }
    }
}

/** A cara de notificação: cartão opaco (passa por trás do de cima), escudo, "DollarBlock · agora". */
@Composable
private fun NoteCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Image(
                painter = painterResource(R.drawable.db_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(5.dp)),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
            Text(
                text = " · " + stringResource(R.string.onb_final_note_when),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
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
    NoteCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    imageVector = copy.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
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
                TurnOnButton(onClick = onRequest)
            }
        }
        Text(
            text = stringResource(copy.bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}


/** "Ativar" com cara de botão: pílula no mesmo gradiente do botão principal, com seta. */
@Composable
private fun TurnOnButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(
                Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, DollarBlockTheme.colors.glow)),
            )
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
    ) {
        Text(
            text = stringResource(R.string.onb_turn_on),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(16.dp),
        )
    }
}

/** "R$ 20,59" sem quebra entre o símbolo e o número. */
private fun String.unbreakable(): String = replace(' ', ' ')
