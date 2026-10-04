package com.dollarblock.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.components.BrandShield
import com.dollarblock.core.designsystem.components.DollarBlockDialog
import com.dollarblock.core.designsystem.components.PrimaryActionButton
import com.dollarblock.data.permissions.AppPermission
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class ConceptPage(
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int,
    val isBrand: Boolean = false,
    val isContract: Boolean = false,
)

private val conceptPages = listOf(
    // A primeira página é a apresentação da marca — usa o emblema do escudo.
    ConceptPage(Icons.Filled.Savings, R.string.onb_welcome_title, R.string.onb_welcome_body, isBrand = true),
    // O confronto: você já tentou controlar o tempo e não deu — por isso a carteira.
    ConceptPage(Icons.Filled.History, R.string.onb_reality_title, R.string.onb_reality_body),
    // A página do contrato — recebe estética de recibo/papel.
    ConceptPage(Icons.Filled.Bolt, R.string.onb_penalty_title, R.string.onb_penalty_body, isContract = true),
)

/**
 * Fluxo de onboarding da primeira execução (E2, reorganizado no E18): apresenta o conceito
 * do DollarBlock e pede as permissões **uma por página**, cada uma no momento em que faz
 * sentido (ordem em [onboardingPages]), com o porquê, o selo obrigatória/opcional e o que o
 * app perde sem ela. Nada trava o usuário: recusar uma obrigatória só mostra um aviso.
 * Concluído uma vez, não volta a aparecer.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val permissionsState by viewModel.permissionsState.collectAsStateWithLifecycle()
    val quickSummaryState by viewModel.quickSummaryState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.recheckPermissions()
        viewModel.reloadQuickSummary()
    }

    val pages = remember {
        onboardingPages(
            conceptCount = conceptPages.size,
            askNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        )
    }
    val permissionPages = remember(pages) { pages.filterIsInstance<OnboardingPage.Permission>() }
    val firstPermissionPageIndex = remember(pages) { pages.indexOfFirst { it is OnboardingPage.Permission } }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val currentPage = pages[pagerState.currentPage]

    fun goTo(index: Int) {
        scope.launch { pagerState.animateScrollToPage(index) }
    }
    fun next() = goTo(pagerState.currentPage + 1)

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.recheckPermissions() }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    var skipWarningFor by remember { mutableStateOf<AppPermission?>(null) }
    var showAccessibilityTutorial by remember { mutableStateOf(false) }

    fun request(permission: AppPermission) {
        if (permission == AppPermission.NOTIFICATIONS &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (permission == AppPermission.ACCESSIBILITY) {
            // Declaração em destaque (política do Play) antes de abrir as Configurações.
            showAccessibilityDisclosure = true
        } else {
            viewModel.intentFor(permission)?.let { context.startActivity(it) }
        }
    }

    // Com o swipe desligado nas páginas de permissão, o Back do sistema é o caminho de volta.
    BackHandler(enabled = pagerState.currentPage > 0) { goTo(pagerState.currentPage - 1) }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAllow = {
                showAccessibilityDisclosure = false
                viewModel.intentFor(AppPermission.ACCESSIBILITY)?.let { context.startActivity(it) }
            },
            onDeny = { showAccessibilityDisclosure = false },
        )
    }

    if (showAccessibilityTutorial) {
        AccessibilityTutorialDialog(onDismiss = { showAccessibilityTutorial = false })
    }

    skipWarningFor?.let { permission ->
        val copy = permissionCopy(permission)
        DollarBlockDialog(
            onDismissRequest = { skipWarningFor = null },
            title = stringResource(R.string.onb_skip_required_title, stringResource(copy.nameRes)),
            body = stringResource(R.string.onb_skip_required_body, stringResource(copy.withoutRes)),
            confirmText = stringResource(R.string.onb_skip_required_grant),
            onConfirm = {
                skipWarningFor = null
                request(permission)
            },
            dismissText = stringResource(R.string.onb_skip_required_proceed),
            onDismiss = {
                skipWarningFor = null
                next()
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            // Sair de uma página de permissão só pelos botões — o swipe pularia o aviso de
            // obrigatória. Nas demais páginas o swipe segue livre.
            userScrollEnabled = currentPage !is OnboardingPage.Permission,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { index ->
            when (val page = pages[index]) {
                is OnboardingPage.Concept -> ConceptPageContent(conceptPages[page.index])
                OnboardingPage.QuickSummary -> QuickSummaryPageContent(
                    state = quickSummaryState,
                    hasUsageAccess = permissionsState.usageAccess,
                    onGrantUsageAccess = { request(AppPermission.USAGE_ACCESS) },
                )
                is OnboardingPage.Permission -> PermissionPageContent(
                    permission = page.permission,
                    step = permissionPages.indexOf(page) + 1,
                    stepCount = permissionPages.size,
                    granted = permissionsState.isGranted(page.permission),
                    onShowTutorial = { showAccessibilityTutorial = true },
                )
                OnboardingPage.Control -> ControlPageContent()
            }
        }

        PageIndicator(
            count = pages.size,
            current = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 16.dp),
        )

        when (currentPage) {
            is OnboardingPage.Permission -> {
                val permission = currentPage.permission
                if (permissionsState.isGranted(permission)) {
                    PrimaryActionButton(
                        text = stringResource(R.string.onb_continue),
                        onClick = ::next,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PrimaryActionButton(
                        text = stringResource(R.string.onb_perm_grant),
                        onClick = { request(permission) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = { if (permission.required) skipWarningFor = permission else next() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.onb_perm_not_now))
                    }
                }
            }
            OnboardingPage.Control -> {
                SignButton(
                    text = stringResource(R.string.onb_finish),
                    onClick = {
                        viewModel.completeOnboarding()
                        onFinished()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Não trava ninguém: só avisa o que fica faltando e onde regularizar.
                val missingRequired = missingRequiredPermissions(permissionsState)
                AnimatedVisibility(missingRequired.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.onb_finish_missing_hint,
                            missingRequired.map { stringResource(permissionCopy(it).nameRes) }.joinToString(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
            else -> {
                PrimaryActionButton(
                    text = stringResource(R.string.onb_continue),
                    onClick = ::next,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (currentPage is OnboardingPage.Concept) {
                    TextButton(
                        onClick = { goTo(firstPermissionPageIndex) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.onb_skip))
                    }
                }
            }
        }
    }
}

private val quickSummaryPalette = listOf(
    Color(0xFF00E676), // Emerald Premium
    Color(0xFF64FFDA), // Mint Glow
    Color(0xFF00A86B), // Emerald médio
    Color(0xFF2DD4BF), // Teal
    Color(0xFFA7F432), // Lime
)

@Composable
private fun QuickSummaryPageContent(
    state: QuickSummaryState,
    hasUsageAccess: Boolean,
    onGrantUsageAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onb_summary_title),
            style = MaterialTheme.typography.headlineMedium,
            color = onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.onb_summary_body),
            style = MaterialTheme.typography.bodyMedium,
            color = onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(32.dp))
        } else if (!hasUsageAccess) {
            Spacer(Modifier.height(32.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(80.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.QueryStats,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.onb_summary_need_usage_title),
                style = MaterialTheme.typography.titleMedium,
                color = onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.onb_summary_need_usage_body),
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            PrimaryActionButton(
                text = stringResource(R.string.onb_summary_grant),
                onClick = onGrantUsageAccess,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (state.topApps.isEmpty()) {
            Spacer(Modifier.height(32.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(80.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.onb_summary_no_usage_title),
                style = MaterialTheme.typography.titleMedium,
                color = onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.onb_summary_no_usage_body),
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        } else {
            // Donut chart
            Box(contentAlignment = Alignment.Center) {
                val strokeWidth = 30.dp
                Canvas(modifier = Modifier.size(200.dp)) {
                    val sw = strokeWidth.toPx()
                    val radius = (size.minDimension - sw) / 2f
                    val topLeft = Offset((size.width - radius * 2) / 2f, (size.height - radius * 2) / 2f)
                    val arcSize = Size(radius * 2, radius * 2)
                    var startAngle = -90f

                    drawArc(
                        color = surfaceVariant,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = sw, cap = StrokeCap.Butt),
                    )
                    state.topApps.forEachIndexed { idx, entry ->
                        val sweep = 360f * entry.percentage
                        drawArc(
                            color = quickSummaryPalette[idx % quickSummaryPalette.size],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = sw, cap = StrokeCap.Butt),
                        )
                        startAngle += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.onb_summary_total),
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant,
                    )
                    Text(
                        text = state.totalTime,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // App list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                state.topApps.forEachIndexed { idx, entry ->
                    val color = quickSummaryPalette[idx % quickSummaryPalette.size]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (entry.icon != null) {
                                Image(
                                    bitmap = entry.icon,
                                    contentDescription = entry.appName,
                                    modifier = Modifier.size(38.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    text = entry.appName.first().uppercaseChar().toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = color,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = entry.appName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${(entry.percentage * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { entry.percentage },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                color = color,
                                trackColor = surfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Última página, depois da burocracia: "Você no controle" — deixa explícito que o
 * app não faz o usuário de refém; sair é grátis, mas custa tempo de vida.
 */
@Composable
private fun ControlPageContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.onb_control_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            text = stringResource(R.string.onb_control_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun ConceptPageContent(page: ConceptPage, modifier: Modifier = Modifier) {
    if (page.isContract) {
        ContractPageContent(page, modifier)
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (page.isBrand) {
            BrandShield(size = 128.dp, cornerRadius = 28.dp)
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/**
 * "O contrato" — mesma estética de papel/mono do recibo de bloqueio
 * (ver `InvoiceReceipt` em BlockActivity.kt), reforçando que as cláusulas
 * de penalidade são um documento a ser assinado, não uma tela de feature.
 */
@Composable
private fun ContractPageContent(page: ConceptPage, modifier: Modifier = Modifier) {
    val paper = Color(0xFFF7F4EC)
    val ink = Color(0xFF1C2B26)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(paper)
                .border(1.dp, ink.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.onb_contract_header),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                color = ink.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(page.titleRes),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            ContractDivider(ink.copy(alpha = 0.35f))
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(page.bodyRes),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = ink.copy(alpha = 0.85f),
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(16.dp))
            ContractDivider(ink.copy(alpha = 0.35f))
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.onb_contract_footer),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = ink.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
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
 * O botão "Assinar" traça uma assinatura (path animado) antes de disparar
 * onClick — o momento-assinatura do contrato, com haptic no fim do traço.
 */
@Composable
private fun SignButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var signing by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(signing) {
        if (signing) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(420, easing = LinearEasing))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
            signing = false
        }
    }

    Box(modifier = modifier) {
        PrimaryActionButton(
            text = text,
            onClick = { if (!signing) signing = true },
            enabled = enabled && !signing,
            modifier = Modifier.fillMaxWidth(),
        )
        if (signing) {
            // Traço de assinatura desenhado incrementalmente: mede o path com
            // android.graphics.PathMeasure e recorta o segmento [0, progress] a cada frame.
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 28.dp, vertical = 18.dp),
            ) {
                val w = size.width
                val h = size.height
                val fullPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.08f, h * 0.6f)
                    cubicTo(w * 0.16f, h * 0.1f, w * 0.24f, h * 0.1f, w * 0.30f, h * 0.55f)
                    cubicTo(w * 0.36f, h * 1.0f, w * 0.42f, h * 1.0f, w * 0.48f, h * 0.45f)
                    cubicTo(w * 0.54f, h * 0.0f, w * 0.62f, h * 0.0f, w * 0.68f, h * 0.5f)
                    lineTo(w * 0.92f, h * 0.45f)
                }
                val measure = android.graphics.PathMeasure(fullPath.asAndroidPath(), false)
                val trimmedAndroidPath = android.graphics.Path()
                measure.getSegment(0f, measure.length * progress.value, trimmedAndroidPath, true)
                drawPath(
                    path = trimmedAndroidPath.asComposePath(),
                    color = Color.White,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

/** Textos e ícone de cada permissão no onboarding. */
private data class PermissionCopy(
    val icon: ImageVector,
    val nameRes: Int,
    val titleRes: Int,
    val bodyRes: Int,
    val withoutRes: Int,
)

private fun permissionCopy(permission: AppPermission): PermissionCopy = when (permission) {
    AppPermission.USAGE_ACCESS -> PermissionCopy(
        Icons.Filled.QueryStats,
        R.string.perm_usage,
        R.string.onb_perm_usage_title,
        R.string.onb_perm_usage_body,
        R.string.onb_perm_usage_without,
    )
    AppPermission.ACCESSIBILITY -> PermissionCopy(
        Icons.Filled.Accessibility,
        R.string.perm_accessibility,
        R.string.onb_perm_accessibility_title,
        R.string.onb_perm_accessibility_body,
        R.string.onb_perm_accessibility_without,
    )
    AppPermission.NOTIFICATIONS -> PermissionCopy(
        Icons.Filled.Notifications,
        R.string.perm_notifications,
        R.string.onb_perm_notifications_title,
        R.string.onb_perm_notifications_body,
        R.string.onb_perm_notifications_without,
    )
    AppPermission.OVERLAY -> PermissionCopy(
        Icons.Filled.Layers,
        R.string.perm_overlay,
        R.string.onb_perm_overlay_title,
        R.string.onb_perm_overlay_body,
        R.string.onb_perm_overlay_without,
    )
}

@Composable
private fun AccessibilityDisclosureDialog(onAllow: () -> Unit, onDeny: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* consentimento exige ação afirmativa — ignora toque fora/back */ },
        title = { Text(stringResource(R.string.onb_a11y_disclosure_title)) },
        text = { Text(stringResource(R.string.onb_a11y_disclosure_body)) },
        confirmButton = {
            Button(onClick = onAllow) {
                Text(stringResource(R.string.onb_a11y_disclosure_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text(stringResource(R.string.onb_a11y_disclosure_deny))
            }
        },
    )
}

/**
 * Uma permissão, uma página: "Papelada N de M" + selo obrigatória/opcional, o porquê e,
 * enquanto não concedida, o bloco "Sem ela" com o que o app perde. Os botões (Conceder /
 * Agora não / Continuar) ficam no rodapé do [OnboardingScreen].
 */
@Composable
private fun PermissionPageContent(
    permission: AppPermission,
    step: Int,
    stepCount: Int,
    granted: Boolean,
    onShowTutorial: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = permissionCopy(permission)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.onb_perm_step, step, stepCount).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RequirementTag(required = permission.required)
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .padding(top = 32.dp)
                .size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = copy.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Text(
            text = stringResource(copy.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            text = stringResource(copy.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (granted) {
            GrantedChip(modifier = Modifier.padding(top = 24.dp))
        } else {
            // O caminho nas Configurações é o mais chato dos quatro: mostra o passo a passo.
            if (permission == AppPermission.ACCESSIBILITY) {
                TextButton(onClick = onShowTutorial, modifier = Modifier.padding(top = 12.dp)) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.onb_a11y_tutorial_open),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.onb_perm_without).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(copy.withoutRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
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
private fun GrantedChip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(DollarBlockTheme.colors.success.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = DollarBlockTheme.colors.success,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.perm_status_granted),
            style = MaterialTheme.typography.labelLarge,
            color = DollarBlockTheme.colors.success,
        )
    }
}

@Composable
private fun PageIndicator(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { index ->
            val active = index == current
            Box(
                modifier = Modifier
                    .size(if (active) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    ),
            )
        }
    }
}
