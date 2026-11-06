package com.dollarblock.core.designsystem.mascot

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// O escudo, nas cores da cabeça do segurança do onboarding (OnboardingBouncerScene) e do
// personagem da entrada (onb_hero): moldura petróleo, faixa esmeralda, rosto lima.
private val Rim = Color(0xFF0B5E3C)
private val BandLight = Color(0xFF2BF08C)
private val BandDark = Color(0xFF00A86B)
private val FaceLight = Color(0xFF6CF7B0)
private val FaceDark = Color(0xFF00C878)
private val Ink = Color(0xFF0A3A24)
private val Shades = Color(0xFF0A0F0D)
private val Limb = Color(0xFF1B2622)
private val LimbLine = Color(0xFF0A100E)
private val Sole = Color(0xFFEFF5F0)
private val Lime = Color(0xFF00E676)
private val Brass = Color(0xFFD9A94E)
private val BrassDark = Color(0xFF8F6A22)
private val Lens = Color(0xFFB9FBE0)
private val QuestionInk = Color(0xFF64FFDA)

// Esqueleto, em unidades da arte, com os pés na origem e o y crescendo para baixo.
private const val SHIELD_TOP = -86f
private val ShieldCenter = Offset(0f, SHIELD_TOP + 26f)
private const val SHOULDER_X = 20f
private const val SHOULDER_Y = -68f
private const val HIP_X = 5f
private const val HIP_Y = -36f
private const val LEG = 36f
private const val UPPER_ARM = 14f
private const val FOREARM = 13f

private fun rad(degrees: Float) = degrees * PI.toFloat() / 180f

/** Contorno do escudo (o mesmo do onboarding): ponta no alto, ombros, laterais e ponta embaixo. */
private fun shieldPath(top: Float): Path = Path().apply {
    moveTo(0f, top - 4f)
    lineTo(23f, top + 5f)
    lineTo(23f, top + 27f)
    quadraticTo(22f, top + 45f, 0f, top + 56f)
    quadraticTo(-22f, top + 45f, -23f, top + 27f)
    lineTo(-23f, top + 5f)
    close()
}

private class ArmPoints(val shoulder: Offset, val elbow: Offset, val hand: Offset, val forearmDir: Offset)

/** [side] 1 = braço da frente, -1 = o de trás (no referencial do personagem). */
private fun armPoints(pose: ArmPose, side: Float, swing: Float): ArmPoints {
    val shoulder = Offset(side * SHOULDER_X, SHOULDER_Y)
    val a = rad(pose.shoulder + swing)
    val elbow = shoulder + Offset(side * sin(a), cos(a)) * UPPER_ARM
    val b = rad(pose.shoulder + swing + pose.elbow)
    val dir = Offset(side * sin(b), cos(b))
    return ArmPoints(shoulder, elbow, elbow + dir * FOREARM, dir)
}

/**
 * O escudo do DollarBlock de corpo inteiro (guia visual §4): o escudo em camadas é cabeça e
 * tronco, com óculos wayfarer, sobrancelhas grossas e o `$` no peito; braços e pernas finos e
 * escuros, tênis de sola clara com friso lima. [unit] é quantos px vale uma unidade da arte.
 * Desenha a pose [pose] como está — quem anima é a [MascotChoreography].
 */
fun DrawScope.drawShieldMascot(pose: MascotPose, unit: Float) {
    withTransform({
        translate(pose.x * unit, pose.y * unit)
        scale(unit * pose.facing, unit, pivot = Offset.Zero)
    }) {
        // Pés plantados: a perna de apoio abre e o corpo desce o que ela "encurta".
        val maxSwing = 22f * pose.walk
        val drop = LEG * (1f - cos(rad(maxSwing * abs(sin(pose.stride)))))
        drawOval(Color.Black.copy(alpha = 0.22f), Offset(-20f, -3f), Size(40f, 6f))
        rotate(pose.lean, pivot = Offset.Zero) {
            withTransform({ translate(0f, drop) }) {
                drawFigure(pose, maxSwing)
            }
        }
    }
}

private fun DrawScope.drawFigure(pose: MascotPose, maxSwing: Float) {
    // Pernas e tênis (a de trás primeiro).
    listOf(-1f, 1f).forEach { side ->
        val phase = pose.stride + if (side < 0) PI.toFloat() else 0f
        val a = rad(maxSwing * sin(phase))
        val hip = Offset(side * HIP_X, HIP_Y)
        val lift = maxOf(0f, cos(phase)) * 4f * pose.walk
        val foot = hip + Offset(sin(a) * LEG, cos(a) * LEG - lift)
        drawLine(LimbLine, hip, foot, strokeWidth = 7.4f, cap = StrokeCap.Round)
        drawLine(Limb, hip, foot, strokeWidth = 5.6f, cap = StrokeCap.Round)
        drawSneaker(foot)
    }

    // Braços balançam ao andar, contra as pernas.
    val armSwing = 14f * pose.walk * sin(pose.stride)
    val back = armPoints(pose.backArm, -1f, -armSwing)
    val front = armPoints(pose.frontArm, 1f, armSwing)

    rotate(pose.tilt, pivot = ShieldCenter) {
        // Braços saem de trás do escudo; as mãos vêm por cima dele.
        listOf(back, front).forEach { arm ->
            drawLimb(arm.shoulder, arm.elbow)
            drawLimb(arm.elbow, arm.hand)
        }
        drawShield(pose)
        drawHand(back.hand)
        drawHand(front.hand)
        when (pose.prop) {
            MascotProp.MAGNIFIER -> drawMagnifier(front.hand, front.forearmDir, pose.glint)
            MascotProp.NONE -> Unit
        }
        if (pose.question > 0f) {
            val bob = sin(pose.question * PI.toFloat()) * 1.5f
            drawQuestion(Offset(-17f, SHIELD_TOP - 18f - bob), pose.question, pose.facing)
        }
    }
}

private fun DrawScope.drawLimb(from: Offset, to: Offset) {
    drawLine(LimbLine, from, to, strokeWidth = 6.4f, cap = StrokeCap.Round)
    drawLine(Limb, from, to, strokeWidth = 4.8f, cap = StrokeCap.Round)
}

private fun DrawScope.drawHand(c: Offset) {
    drawCircle(Limb, 4.2f, c)
    drawCircle(LimbLine, 4.2f, c, style = Stroke(0.9f))
}

/** Tênis streetwear escuro, bico para a frente, sola clara e um friso lima. */
private fun DrawScope.drawSneaker(foot: Offset) {
    drawRoundRect(Sole, Offset(foot.x - 5.5f, foot.y - 1.2f), Size(15f, 2.8f), CornerRadius(1.4f))
    drawRoundRect(Limb, Offset(foot.x - 5f, foot.y - 6.2f), Size(13.5f, 5.6f), CornerRadius(3f, 3f))
    drawRoundRect(LimbLine, Offset(foot.x - 5f, foot.y - 6.2f), Size(13.5f, 5.6f), CornerRadius(3f, 3f), style = Stroke(0.8f))
    drawLine(Lime, Offset(foot.x - 1.5f, foot.y - 4.6f), Offset(foot.x + 3.5f, foot.y - 2.6f), strokeWidth = 1.1f, cap = StrokeCap.Round)
}

private fun DrawScope.drawShield(pose: MascotPose) {
    val top = SHIELD_TOP
    val outline = shieldPath(top)
    val c = ShieldCenter
    drawPath(outline, Rim)
    scale(0.9f, pivot = c) {
        drawPath(outline, Brush.linearGradient(listOf(BandLight, BandDark), start = Offset(-20f, top), end = Offset(20f, top + 56f)))
    }
    scale(0.8f, pivot = c) { drawPath(outline, Rim) }
    scale(0.74f, pivot = c) {
        drawPath(outline, Brush.radialGradient(listOf(FaceLight, FaceDark), center = Offset(-3f, top + 18f), radius = 34f))
    }
    drawLine(Color.White.copy(alpha = 0.35f), Offset(-3f, top - 1f), Offset(-19f, top + 6f), strokeWidth = 1.6f, cap = StrokeCap.Round)

    val dx = pose.look * 2.6f

    // Sobrancelhas: [brow] ergue (ou franze) as duas; [quirk] só a da frente, em arco.
    val browY = top + 12f
    val frown = maxOf(0f, -pose.brow)
    listOf(-1f, 1f).forEach { side ->
        val lift = maxOf(0f, pose.brow) * 2.5f + if (side > 0) pose.quirk * 4f else 0f
        val arch = if (side > 0) pose.quirk * 1.8f else 0f
        val outer = Offset(dx + side * 14f, browY + 1f - lift * 0.8f)
        val inner = Offset(dx + side * 4f, browY + 0.5f + 3f * frown - lift)
        val control = Offset(dx + side * 9f, browY - 3f + 2.5f * frown - lift * 1.2f - arch)
        drawPath(
            Path().apply {
                moveTo(outer.x, outer.y)
                quadraticTo(control.x, control.y, inner.x, inner.y)
            },
            Ink,
            style = Stroke(2.6f, cap = StrokeCap.Round),
        )
    }

    // Óculos wayfarer com reflexos fixos e o brilho que atravessa a lente da frente.
    val gy = top + 17f
    listOf(-1f, 1f).forEach { side ->
        val lens = Path().apply {
            moveTo(dx + side * 1.5f, gy)
            lineTo(dx + side * 19f, gy)
            lineTo(dx + side * 17.5f, gy + 9f)
            quadraticTo(dx + side * 10f, gy + 11.5f, dx + side * 3f, gy + 8.5f)
            close()
        }
        drawPath(lens, Shades)
        drawPath(lens, Ink, style = Stroke(1.4f))
        val lx = dx + side * 10.5f
        drawLine(Color.White.copy(alpha = 0.32f), Offset(lx - 3.5f, gy + 7.5f), Offset(lx - 0.5f, gy + 1.5f), strokeWidth = 1.3f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.22f), Offset(lx + 0.5f, gy + 7.5f), Offset(lx + 3f, gy + 2.5f), strokeWidth = 0.9f, cap = StrokeCap.Round)
    }
    drawLine(Ink, Offset(dx - 20f, gy + 0.4f), Offset(dx + 20f, gy + 0.4f), strokeWidth = 2.4f, cap = StrokeCap.Round)
    if (pose.glint in 0f..1f) {
        val gx = dx + 4f + pose.glint * 13f
        drawLine(Color.White.copy(alpha = 0.75f * (1f - pose.glint)), Offset(gx, gy + 8f), Offset(gx + 4f, gy + 1.5f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    }

    // O cifrão no peito — desespelhado, para não sair ao contrário quando ele olha à esquerda.
    val dollar = Offset(dx * 0.6f, top + 40f)
    scale(pose.facing, 1f, pivot = dollar) {
        withTransform({ translate(dollar.x, dollar.y); scale(0.85f, 0.85f, pivot = Offset.Zero) }) {
            val s = Path().apply {
                moveTo(5.5f, -5.5f)
                cubicTo(3f, -8.5f, -6f, -8.5f, -6f, -4f)
                cubicTo(-6f, 0f, 6f, -0.5f, 6f, 4f)
                cubicTo(6f, 8.5f, -3f, 8.5f, -5.5f, 5.5f)
            }
            drawLine(Ink, Offset(0f, -10f), Offset(0f, 10f), strokeWidth = 2.4f, cap = StrokeCap.Round)
            drawPath(s, Ink, style = Stroke(3f, cap = StrokeCap.Round))
            drawPath(s, Color.White.copy(alpha = 0.18f), style = Stroke(0.8f, cap = StrokeCap.Round))
        }
    }
}

/** Lupa de latão na mão, seguindo o antebraço; o brilho da lente acompanha o dos óculos. */
private fun DrawScope.drawMagnifier(hand: Offset, dir: Offset, glint: Float) {
    val neck = hand + dir * 6f
    val center = hand + dir * 13f
    drawLine(LimbLine, hand, neck, strokeWidth = 3.4f, cap = StrokeCap.Round)
    drawLine(BrassDark, hand, neck, strokeWidth = 2.2f, cap = StrokeCap.Round)
    drawCircle(Lens.copy(alpha = 0.28f), 6.6f, center)
    drawCircle(Brass, 6.6f, center, style = Stroke(2.2f))
    drawCircle(BrassDark, 7.7f, center, style = Stroke(0.6f))
    val g = if (glint in 0f..1f) 0.35f + 0.5f * (1f - glint) else 0.35f
    drawLine(Color.White.copy(alpha = g), center + Offset(-3f, 1.5f), center + Offset(-0.5f, -3.2f), strokeWidth = 1.2f, cap = StrokeCap.Round)
    drawHand(hand)
}

/** "?" no ar, em menta, desespelhado como o cifrão. */
private fun DrawScope.drawQuestion(at: Offset, alpha: Float, facing: Float) {
    scale(facing, 1f, pivot = at) {
        val q = Path().apply {
            moveTo(at.x - 4.5f, at.y - 4.5f)
            cubicTo(at.x - 4.5f, at.y - 10f, at.x + 4.5f, at.y - 10f, at.x + 4.5f, at.y - 4.5f)
            cubicTo(at.x + 4.5f, at.y - 1f, at.x, at.y - 1f, at.x, at.y + 3f)
        }
        drawPath(q, Ink.copy(alpha = alpha * 0.5f), style = Stroke(4.6f, cap = StrokeCap.Round))
        drawPath(q, QuestionInk.copy(alpha = alpha), style = Stroke(2.6f, cap = StrokeCap.Round))
        drawCircle(QuestionInk.copy(alpha = alpha), 1.6f, Offset(at.x, at.y + 7.5f))
    }
}
