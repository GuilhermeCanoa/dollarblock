package com.dollarblock.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Paleta DollarBlock — identidade fintech premium do styleguide
 * (docs/STYLEGUIDE_ANDROID.md): Proteção (escudo), Tempo (ampulheta) e Valor (cifrão).
 *
 * Construída sobre três pilares de cor: Emerald Premium (energia/CTA),
 * Deep Green Velvet (sofisticação/segurança, fundo Dark Mode nativo) e
 * Mint Glow (brilho neon sutil em bordas, progresso e conquistas).
 */

// ── Brand Colors (styleguide §2) ────────────────────────────────────────────
/** Emerald Premium — primária: CTA, estados ativos, sucesso. */
val EmeraldPremium = Color(0xFF00E676)

/** Deep Green Velvet — fundo principal (Dark Mode nativo). */
val DeepGreenVelvet = Color(0xFF0A241D)

/** Mint Glow — accent/neon: bordas iluminadas, gradientes de progresso, badges. */
val MintGlow = Color(0xFF64FFDA)

/** Emerald médio — passo intermediário do gradiente de marca. */
val EmeraldMid = Color(0xFF00A86B)

// ── Superfícies derivadas do Velvet (profundidade no dark) ──────────────────
/** Superfície de cards no dark — um degrau acima do fundo Velvet. */
val VelvetSurface = Color(0xFF103129)
/** Superfície elevada / containers translúcidos. */
val VelvetSurfaceHigh = Color(0xFF15453A)
/** Variante de superfície (tracks, divisores) no dark. */
val VelvetSurfaceVariant = Color(0xFF1C4A3D)

// ── Texto (styleguide §2) ───────────────────────────────────────────────────
val TextPrimary = Color(0xFFFFFFFF)
/** Text Secondary (Muted) — apoio, legendas, descrições. */
val TextMuted = Color(0xFFA0B2AE)

// ── Semânticas ──────────────────────────────────────────────────────────────
/** Alert/Blocking — estritamente tempo esgotado / app bloqueado. */
val BlockingRed = Color(0xFFFF5252)
/** Alerta intermediário (pendências, janelas de desbloqueio). */
val AlertAmber = Color(0xFFFFC24B)

// ── Light scheme (coerente com a marca, derivado do verde dólar) ────────────
val LightBackground = Color(0xFFF1F7F4)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE3EFE9)
val EmeraldContainerLight = Color(0xFFB9F0CE)
val OnEmeraldContainerLight = Color(0xFF04210E)
val EmeraldOnLight = Color(0xFF00894B) // emerald legível sobre fundo claro
val TextMutedLight = Color(0xFF566460)

// ── Aliases legados (mantidos para não quebrar usos diretos existentes) ──────
// BlockActivity referencia DollarGreenDark/NeutralWhite; o tema usa estes nomes.
val DollarGreen = EmeraldPremium
val DollarGreenDark = DeepGreenVelvet
val SuccessGreen = EmeraldPremium
val PenaltyRed = BlockingRed
val NeutralWhite = TextPrimary
val NeutralDarkest = DeepGreenVelvet
val SurfaceDark = VelvetSurface
val OnSurfaceDark = Color(0xFFE6F2EC)
