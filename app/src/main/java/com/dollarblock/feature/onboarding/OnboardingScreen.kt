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
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dollarblock.R
import com.dollarblock.core.designsystem.DollarBlockTheme
import com.dollarblock.core.designsystem.components.DollarBlockDialog
import com.dollarblock.core.designsystem.components.PrimaryActionButton
import com.dollarblock.data.permissions.AppPermission
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fluxo de onboarding da primeira execução (E2 → E18 → E21): 5 páginas animadas — entrada,
 * contrato (assinado no rodapé), medição (que vira o resumo da semana), tranca e últimos
 * ajustes. Cada permissão traz um passo a passo animado de como ligá-la. Nada trava o
 * usuário: recusar uma obrigatória só mostra um aviso. Concluído uma vez, não volta.
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
        onboardingPages(askNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    }
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
    var closing by remember { mutableStateOf(false) }

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

    /** Sai da página atual; na última, fecha o onboarding com o carimbo "CONTA ABERTA". */
    fun leavePage() {
        if (currentPage is OnboardingPage.FinalSettings) closing = true else next()
    }

    /** Sair sem uma obrigatória da página pede confirmação; senão, segue. */
    fun tryLeavePage() {
        val pending = pendingRequiredOn(currentPage, permissionsState)
        if (pending != null) skipWarningFor = pending else leavePage()
    }

    // O swipe só vale na entrada: o contrato pede assinatura e as permissões, os botões.
    // O Back do sistema é o caminho de volta.
    BackHandler(enabled = pagerState.currentPage > 0 && !closing) { goTo(pagerState.currentPage - 1) }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAllow = {
                showAccessibilityDisclosure = false
                viewModel.intentFor(AppPermission.ACCESSIBILITY)?.let { context.startActivity(it) }
            },
            onDeny = { showAccessibilityDisclosure = false },
        )
    }

    skipWarningFor?.let { permission ->
        DollarBlockDialog(
            onDismissRequest = { skipWarningFor = null },
            title = stringResource(R.string.onb_skip_required_title, stringResource(permissionNameRes(permission))),
            body = stringResource(R.string.onb_skip_required_body, stringResource(permissionWithoutRes(permission))),
            confirmText = stringResource(R.string.onb_skip_required_grant),
            onConfirm = {
                skipWarningFor = null
                request(permission)
            },
            dismissText = stringResource(R.string.onb_skip_required_proceed),
            onDismiss = {
                skipWarningFor = null
                leavePage()
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            OnboardingProgress(current = pagerState.currentPage, count = pages.size)

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = currentPage is OnboardingPage.Entry,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { index ->
                val active = pagerState.settledPage == index
                when (val page = pages[index]) {
                    OnboardingPage.Entry -> EntryPage(active)
                    OnboardingPage.Contract -> ContractPage(active)
                    OnboardingPage.Measurement -> MeasurementPage(
                        active = active,
                        granted = permissionsState.usageAccess,
                        summary = quickSummaryState,
                    )
                    OnboardingPage.Lock -> LockPage(active, granted = permissionsState.accessibility)
                    is OnboardingPage.FinalSettings -> FinalSettingsPage(
                        active = active,
                        page = page,
                        permissions = permissionsState,
                        onRequest = ::request,
                    )
                }
            }

            Column(modifier = Modifier.padding(top = 16.dp)) {
                when (currentPage) {
                    OnboardingPage.Entry -> PrimaryActionButton(
                        text = stringResource(R.string.onb_continue),
                        onClick = ::next,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OnboardingPage.Contract -> SignButton(
                        text = stringResource(R.string.onb_sign),
                        onClick = ::next,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OnboardingPage.Measurement, OnboardingPage.Lock -> {
                        val permission = permissionsOn(currentPage).first()
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
                                onClick = ::tryLeavePage,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            ) {
                                Text(stringResource(R.string.onb_perm_not_now))
                            }
                        }
                    }
                    is OnboardingPage.FinalSettings -> {
                        PrimaryActionButton(
                            text = stringResource(R.string.onb_open_tab),
                            onClick = { if (!closing) tryLeavePage() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Não trava ninguém: só avisa o que fica faltando e onde regularizar.
                        val missingRequired = missingRequiredPermissions(permissionsState)
                        AnimatedVisibility(missingRequired.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.onb_finish_missing_hint,
                                    missingRequired.map { stringResource(permissionNameRes(it)) }.joinToString(),
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
                }
            }
        }

        if (closing) {
            OpenTabOverlay(
                onDone = {
                    viewModel.completeOnboarding()
                    onFinished()
                },
            )
        }
    }
}

/** Fim do onboarding: a tela escurece e desce o carimbo "CONTA ABERTA"; depois, Home. */
@Composable
private fun OpenTabOverlay(onDone: () -> Unit) {
    val animate = rememberAnimationsEnabled()
    LaunchedEffect(Unit) {
        delay(if (animate) 1_300L else 300L)
        onDone()
    }
    AnimatedVisibility(visible = true, enter = fadeIn(tween(200))) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
                // Engole toques: nada de tocar duas vezes no botão por baixo.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Stamp(
                text = stringResource(R.string.onb_stamp_open),
                color = DollarBlockTheme.colors.success,
                fontSize = 30.sp,
                borderWidth = 3.dp,
            )
        }
    }
}

private fun permissionNameRes(permission: AppPermission): Int = when (permission) {
    AppPermission.USAGE_ACCESS -> R.string.perm_usage
    AppPermission.ACCESSIBILITY -> R.string.perm_accessibility
    AppPermission.OVERLAY -> R.string.perm_overlay
    AppPermission.NOTIFICATIONS -> R.string.perm_notifications
}

/** O que o app perde sem a permissão — o corpo do "Seguir sem X?". Só obrigatórias chegam aqui. */
private fun permissionWithoutRes(permission: AppPermission): Int = when (permission) {
    AppPermission.USAGE_ACCESS -> R.string.onb_perm_usage_without
    AppPermission.ACCESSIBILITY -> R.string.onb_perm_accessibility_without
    AppPermission.OVERLAY, AppPermission.NOTIFICATIONS -> R.string.onb_perm_overlay_without
}

/**
 * O botão "Assinar o contrato" traça uma assinatura (path animado) antes de disparar
 * onClick — o momento-assinatura do contrato, com haptic no fim do traço.
 */
@Composable
private fun SignButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var signing by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val animate = rememberAnimationsEnabled()

    LaunchedEffect(signing) {
        if (signing) {
            progress.snapTo(0f)
            if (animate) progress.animateTo(1f, tween(520, easing = LinearEasing))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
            signing = false
        }
    }

    Box(modifier = modifier) {
        PrimaryActionButton(
            text = text,
            onClick = { if (!signing) signing = true },
            enabled = !signing,
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
