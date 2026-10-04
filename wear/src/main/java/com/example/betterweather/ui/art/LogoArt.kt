package com.example.betterweather.ui.art

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.example.betterweather.core.ThemeId
import com.example.betterweather.ui.theme.MascotKind
import com.example.betterweather.ui.theme.StickerType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Launcher icon art: a pup sitting on a bench at a lake, watching the sunset behind the mountains.
 * Drawn as two layers for an adaptive icon (background = landscape, foreground = bench + pup).
 * Important content stays inside the central 66% so any launcher mask can crop it safely.
 */
private class Dusk(
    val skyTop: Color, val skyMid: Color, val horizon: Color, val sun: Color,
    val farMtn: Color, val nearMtn: Color, val lakeTop: Color, val lakeBottom: Color,
    val bank: Color, val bench: Color, val cloud: Color,
)

private fun dusk(theme: ThemeId) = when (theme) {
    ThemeId.CINNAMOROLL -> Dusk(Color(0xFF8E7CC3), Color(0xFFF5A6C8), Color(0xFFFFD8A8), Color(0xFFFFEBB0),
        Color(0xFFB79AD8), Color(0xFF8A74B8), Color(0xFFF7B5CF), Color(0xFF9CA8E8), Color(0xFF5A4A82), Color(0xFF8A6552), Color(0xFFFFD9EA))
    ThemeId.MOCHA -> Dusk(Color(0xFF7A4E6A), Color(0xFFE58F7A), Color(0xFFFFC48A), Color(0xFFFFE0A0),
        Color(0xFFA9708A), Color(0xFF7D4F6A), Color(0xFFF0A07A), Color(0xFF8A6A9A), Color(0xFF4E3040), Color(0xFF6A4632), Color(0xFFFFC9B0))
    ThemeId.ESPRESSO -> Dusk(Color(0xFF2A1B3A), Color(0xFF8A4B5A), Color(0xFFE8884A), Color(0xFFFFC070),
        Color(0xFF5A3A55), Color(0xFF35203F), Color(0xFFC06A5A), Color(0xFF3A2A4A), Color(0xFF1E1220), Color(0xFF4A2E20), Color(0xFFD9948A))
    ThemeId.CLASSIC -> Dusk(Color(0xFF3A6BE0), Color(0xFFFF9E6B), Color(0xFFFFD27A), Color(0xFFFFF1B0),
        Color(0xFF6D7FB8), Color(0xFF46588F), Color(0xFFFFB488), Color(0xFF4F78C8), Color(0xFF2F4A3A), Color(0xFF7A5A3C), Color(0xFFFFD2B8))
}

private val farPeaks = listOf(-0.05f to 0.6f, 0.1f to 0.43f, 0.2f to 0.5f, 0.3f to 0.4f, 0.42f to 0.55f, 0.5f to 0.585f,
    0.6f to 0.52f, 0.72f to 0.38f, 0.84f to 0.5f, 0.93f to 0.44f, 1.05f to 0.6f)
private val nearPeaks = listOf(-0.05f to 0.6f, 0.12f to 0.5f, 0.3f to 0.6f, 0.7f to 0.6f, 0.88f to 0.49f, 1.05f to 0.6f)

private fun DrawScope.ridge(peaks: List<Pair<Float, Float>>, s: Float, color: Color, alpha: Float = 1f) {
    val p = Path().apply {
        moveTo(peaks.first().first * s, 0.6f * s)
        peaks.forEach { (x, y) -> lineTo(x * s, y * s) }
        lineTo(peaks.last().first * s, 0.6f * s)
        close()
    }
    drawPath(p, color.copy(alpha = alpha))
    drawPath(p, color.copy(alpha = alpha), style = Stroke(s * 0.012f, join = StrokeJoin.Round))
}

fun DrawScope.drawLogoBackground(theme: ThemeId) {
    val s = size.minDimension
    val d = dusk(theme)
    val horizon = 0.6f * s

    // sky
    drawRect(Brush.verticalGradient(0f to d.skyTop, 0.55f to d.skyMid, 1f to d.horizon, startY = 0f, endY = horizon), size = Size(s, horizon))
    for (i in 0 until 14) {
        drawCircle(Color.White.copy(alpha = 0.35f + 0.4f * rnd(i, 3)), s * (0.003f + 0.003f * rnd(i, 4)),
            Offset(s * rnd(i, 1), s * 0.28f * rnd(i, 2)))
    }
    // sun with glow, half sunk behind the mountains
    val sc = Offset(0.5f * s, horizon)
    drawCircle(Brush.radialGradient(listOf(d.sun.copy(alpha = 0.75f), d.sun.copy(alpha = 0f)), sc, s * 0.5f), s * 0.5f, sc)
    drawCircle(d.sun, s * 0.12f, sc)
    // soft clouds
    listOf(Triple(0.08f, 0.2f, 0.34f), Triple(0.58f, 0.3f, 0.36f), Triple(0.4f, 0.12f, 0.24f)).forEach { (x, y, w) ->
        withTransform({ translate(x * s, y * s); scale(w * s, w * s, Offset.Zero) }) { drawPath(cloudUnit, d.cloud.copy(alpha = 0.8f)) }
    }
    // mountains
    ridge(farPeaks, s, d.farMtn)
    ridge(nearPeaks, s, d.nearMtn)

    // lake
    drawRect(Brush.verticalGradient(listOf(d.lakeTop, d.lakeBottom), startY = horizon, endY = s), Offset(0f, horizon), Size(s, s - horizon))
    clipRect(0f, horizon, s, s) {
        // mountain reflections
        withTransform({ scale(1f, -1f, Offset(0f, horizon)) }) {
            ridge(farPeaks, s, d.farMtn, 0.28f); ridge(nearPeaks, s, d.nearMtn, 0.3f)
        }
        // sun glitter
        for (i in 0 until 9) {
            val y = horizon + s * (0.02f + i * 0.032f)
            val w = s * (0.22f - i * 0.016f) * (0.8f + 0.4f * rnd(i, 7))
            drawLine(d.sun.copy(alpha = 0.75f - i * 0.06f), Offset(sc.x - w / 2 + s * 0.02f * (i % 2), y),
                Offset(sc.x + w / 2 + s * 0.02f * (i % 2), y), s * 0.008f, StrokeCap.Round)
        }
    }
    // grassy bank in the foreground
    val bank = Path().apply {
        moveTo(0f, s); lineTo(0f, 0.88f * s)
        cubicTo(0.2f * s, 0.84f * s, 0.35f * s, 0.9f * s, 0.5f * s, 0.9f * s)
        cubicTo(0.65f * s, 0.9f * s, 0.8f * s, 0.84f * s, s, 0.88f * s)
        lineTo(s, s); close()
    }
    drawPath(bank, d.bank)
}

fun DrawScope.drawLogoForeground(theme: ThemeId) {
    val s = size.minDimension
    val d = dusk(theme)
    val mascot = when (theme) {
        ThemeId.CINNAMOROLL -> MascotKind.CINNAMOROLL
        ThemeId.MOCHA -> MascotKind.MOCHA
        ThemeId.ESPRESSO -> MascotKind.ESPRESSO
        ThemeId.CLASSIC -> null
    }
    val benchDark = lerp(d.bench, Color.Black, 0.25f)

    // bench legs and seat
    for (x in floatArrayOf(0.32f, 0.68f)) {
        drawRoundRect(benchDark, Offset((x - 0.018f) * s, 0.8f * s), Size(0.036f * s, 0.11f * s), CornerRadius(s * 0.01f))
    }
    drawRoundRect(d.bench, Offset(0.26f * s, 0.785f * s), Size(0.48f * s, 0.03f * s), CornerRadius(s * 0.012f))

    if (mascot != null) {
        val l = look(mascot)
        val warm = d.sun
        val fur = lerp(l.fur, warm, 0.14f)
        val shade = lerp(fur, Color.Black, 0.2f)
        val line = Stroke(s * 0.006f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val earFill = lerp(l.earColor, warm, 0.14f)

        withTransform({ translate(-0.11f * s, 0f); scale(0.8f, 0.8f, Offset(0.5f * s, 0.8f * s)) }) {
        // body, seen from behind
        val body = Offset(0.385f * s, 0.61f * s) to Size(0.23f * s, 0.2f * s)
        drawOval(fur, body.first, body.second)
        drawOval(shade, body.first, body.second, style = line)
        // tail curled beside the body
        val tc = Offset(0.64f * s, 0.755f * s)
        drawCircle(fur, s * 0.045f, tc)
        drawCircle(shade, s * 0.045f, tc, style = line)
        val sp = Path()
        for (i in 0..36) {
            val f = i / 36f; val a = f * 2.3f * 2f * PI.toFloat(); val r = s * 0.04f * f
            if (i == 0) sp.moveTo(tc.x, tc.y) else sp.lineTo(tc.x + cos(a) * r, tc.y + sin(a) * r)
        }
        drawPath(sp, shade, style = Stroke(s * 0.007f, cap = StrokeCap.Round))
        // head
        val head = Offset(0.37f * s, 0.43f * s) to Size(0.26f * s, 0.23f * s)
        drawOval(fur, head.first, head.second)
        drawOval(shade, head.first, head.second, style = line)
        // ears hang down the back of the head
        for (side in intArrayOf(-1, 1)) {
            val ear = earOutline(side, (0.5f + side * 0.115f) * s, 0.415f * s, 0.27f * s, 0.085f * s, 0.055f * s, 0.035f * s)
            drawPath(ear, earFill)
            drawPath(ear, shade, style = line)
        }
        if (mascot == MascotKind.MOCHA) {
            val fc = Offset(0.42f * s, 0.45f * s); val pr = s * 0.026f
            for (i in 0 until 5) {
                val a = (-PI / 2 + i * 2 * PI / 5).toFloat()
                val pc = fc + Offset(cos(a) * pr * 1.05f, sin(a) * pr * 1.05f)
                drawCircle(Color.White, pr * 1.12f, pc); drawCircle(Color(0xFFFF8FB8), pr, pc)
            }
            drawCircle(Color(0xFFFFE27A), pr * 0.75f, fc)
        }
        }
    }

    // bench back (nearest to us), drawn over the pup's lower back
    for (x in floatArrayOf(0.285f, 0.715f)) {
        drawRoundRect(benchDark, Offset((x - 0.014f) * s, 0.7f * s), Size(0.028f * s, 0.1f * s), CornerRadius(s * 0.008f))
    }
    drawRoundRect(d.bench, Offset(0.27f * s, 0.735f * s), Size(0.46f * s, 0.022f * s), CornerRadius(s * 0.01f))
    drawRoundRect(lerp(d.bench, Color.White, 0.08f), Offset(0.27f * s, 0.705f * s), Size(0.46f * s, 0.022f * s), CornerRadius(s * 0.01f))

    // sparkles
    if (mascot != null) {
        drawSticker(StickerType.SPARKLE, Offset(0.3f * s, 0.3f * s), s * 0.03f, 10f, Color(0xFFFFF3C4))
        drawSticker(StickerType.HEART, Offset(0.72f * s, 0.34f * s), s * 0.028f, -10f, Color(0xFFFF8FB8))
    } else {
        drawSticker(StickerType.SPARKLE, Offset(0.3f * s, 0.3f * s), s * 0.03f, 10f, Color(0xFFFFF3C4))
    }
}
