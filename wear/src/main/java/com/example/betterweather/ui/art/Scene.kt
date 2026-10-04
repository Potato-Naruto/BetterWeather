package com.example.betterweather.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.betterweather.core.IconStyle
import com.example.betterweather.data.Condition
import com.example.betterweather.ui.theme.ThemeSpec
import com.example.betterweather.ui.theme.skyColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Everything needed to paint the live background for the current weather. */
data class SceneSpec(
    val condition: Condition,
    val isDay: Boolean,
    val theme: ThemeSpec,
    val dark: Boolean,
    val amoled: Boolean,
    val ambient: Boolean = false,
    val showMascot: Boolean = true,
)

private fun accessoryFor(c: Condition, isDay: Boolean) = when {
    c.isWet -> Accessory.UMBRELLA
    c == Condition.SNOW -> Accessory.SCARF
    c == Condition.CLEAR && isDay -> Accessory.SUNGLASSES
    else -> Accessory.NONE
}

private fun eyesFor(c: Condition, isDay: Boolean) = when {
    c == Condition.THUNDERSTORM -> Eyes.SURPRISED
    !isDay && !c.isWet -> Eyes.SLEEPY
    c == Condition.CLEAR || c == Condition.PARTLY_CLOUDY -> Eyes.HAPPY
    else -> Eyes.OPEN
}

/** Paints the whole animated scene. [t] loops 0..1 once every 12 seconds. */
fun DrawScope.drawWeatherScene(spec: SceneSpec, t: Float) {
    val w = size.width; val h = size.height
    val night = !spec.isDay
    val (top, bottom) = skyColors(spec.theme, spec.condition, spec.isDay, spec.dark)

    // 1. sky
    if (spec.amoled || spec.ambient) drawRect(Color.Black)
    else drawRect(Brush.verticalGradient(listOf(top, bottom)))
    if (spec.ambient) return

    val particle = if (spec.amoled) Color(0xFFDDDDDD) else Color.White
    val c = spec.condition

    // 2. sun / moon / stars
    if (!spec.amoled) {
        if (c == Condition.CLEAR || c == Condition.PARTLY_CLOUDY || c == Condition.WINDY) {
            if (spec.isDay) {
                val sc = Offset(w * 0.8f, h * 0.2f)
                drawCircle(Brush.radialGradient(listOf(spec.theme.icons.sun.copy(alpha = 0.55f), Color.Transparent), sc, w * 0.5f), w * 0.5f, sc)
                rotate(t * 360f, sc) {
                    for (i in 0 until 12) {
                        val a = i * (PI.toFloat() / 6f)
                        drawLine(spec.theme.icons.sun.copy(alpha = 0.35f), sc + Offset(cos(a), sin(a)) * (w * 0.14f),
                            sc + Offset(cos(a), sin(a)) * (w * (0.2f + 0.02f * wave(t, 3, i * 0.1f))), w * 0.012f, StrokeCap.Round)
                    }
                }
                drawCircle(spec.theme.icons.sun, w * 0.085f, sc)
            }
        }
        if (night && !c.isWet) {
            for (i in 0 until 26) {
                val a = 0.25f + 0.75f * (0.5f + 0.5f * wave(t, 1 + i % 3, rnd(i, 3)))
                drawCircle(Color.White.copy(alpha = a), w * (0.004f + 0.004f * rnd(i, 4)), Offset(w * rnd(i, 1), h * rnd(i, 2) * 0.8f))
            }
            if (c == Condition.CLEAR || c == Condition.PARTLY_CLOUDY) {
                val mc = Offset(w * 0.78f, h * 0.2f)
                drawCircle(Brush.radialGradient(listOf(spec.theme.icons.moon.copy(alpha = 0.35f), Color.Transparent), mc, w * 0.35f), w * 0.35f, mc)
                drawCircle(spec.theme.icons.moon, w * 0.075f, mc)
            }
        }
    }

    // 3. drifting clouds
    val cloudCount = when (c) {
        Condition.CLEAR -> 0
        Condition.PARTLY_CLOUDY -> 2
        Condition.WINDY -> 2
        else -> 4
    }
    val heavy = c == Condition.THUNDERSTORM || c == Condition.RAIN || c == Condition.SLEET
    for (i in 0 until cloudCount) {
        val cw = w * (0.5f + 0.25f * rnd(i, 5))
        val x = (frac(rnd(i, 6) + t * (1 + i % 2)) * (w + cw)) - cw
        val y = h * (0.04f + 0.2f * rnd(i, 7)) + h * 0.01f * wave(t, 1, rnd(i, 8))
        val tint = when {
            spec.amoled -> Color(0xFF9A9A9A)
            heavy -> spec.theme.icons.cloudDark
            else -> spec.theme.icons.cloud
        }
        withTransform({ translate(x, y); scale(cw, cw, Offset.Zero) }) {
            drawPath(cloudUnit, tint.copy(alpha = if (spec.amoled) 0.25f else if (heavy) 0.7f else 0.55f))
        }
    }

    // 4. precipitation
    when (c) {
        Condition.RAIN, Condition.THUNDERSTORM, Condition.DRIZZLE, Condition.SLEET -> {
            val n = when (c) { Condition.THUNDERSTORM -> 58; Condition.RAIN -> 46; Condition.SLEET -> 30; else -> 24 }
            val speed = if (c == Condition.DRIZZLE) 12 else 20
            val len = h * (if (c == Condition.DRIZZLE) 0.03f else 0.065f)
            val col = if (spec.amoled) particle else spec.theme.icons.rain.copy(alpha = 0.85f)
            for (i in 0 until n) {
                val p = frac(rnd(i, 1) + t * (speed + i % 3))
                val x = w * (rnd(i, 2) * 1.2f - 0.1f) - p * h * 0.12f
                val y = -len + p * (h + len * 2)
                drawLine(col.copy(alpha = col.alpha * (0.35f + 0.65f * rnd(i, 3))), Offset(x, y), Offset(x - len * 0.25f, y + len),
                    w * 0.007f, StrokeCap.Round)
            }
            // splashes at the bottom edge
            for (i in 0 until 8) {
                val p = frac(rnd(i, 9) + t * 10)
                val sx = w * (0.15f + 0.7f * rnd(i, 10))
                drawCircle(col.copy(alpha = (1f - p) * 0.5f), w * 0.004f + p * w * 0.02f, Offset(sx, h * 0.97f), style = Stroke(w * 0.003f))
            }
        }
        else -> Unit
    }
    if (c == Condition.SNOW || c == Condition.SLEET) {
        val n = if (c == Condition.SNOW) 44 else 16
        for (i in 0 until n) {
            val p = frac(rnd(i, 11) + t * (2 + i % 3))
            val x = w * rnd(i, 12) + w * 0.03f * wave(t, 2 + i % 3, rnd(i, 13))
            val y = -8f + p * (h + 16f)
            drawCircle(particle.copy(alpha = 0.5f + 0.5f * rnd(i, 14)), w * (0.006f + 0.008f * rnd(i, 15)), Offset(x, y))
        }
    }

    // 5. fog
    if (c == Condition.FOG) {
        for (i in 0 until 5) {
            val bandW = w * 1.2f
            val x = -bandW * 0.25f + w * 0.3f * wave(t, 1, i * 0.2f)
            val y = h * (0.18f + i * 0.16f)
            drawRoundRect(particle.copy(alpha = if (spec.amoled) 0.12f else 0.22f), Offset(x, y), androidx.compose.ui.geometry.Size(bandW, h * 0.1f),
                androidx.compose.ui.geometry.CornerRadius(h * 0.05f))
        }
    }

    // 6. wind streaks
    if (c == Condition.WINDY) {
        for (i in 0 until 7) {
            val p = frac(rnd(i, 16) + t * (3 + i % 3))
            val y = h * (0.15f + 0.7f * rnd(i, 17))
            val x = -w * 0.3f + p * w * 1.6f
            drawLine(particle.copy(alpha = 0.4f * sin(PI.toFloat() * p)), Offset(x, y), Offset(x + w * 0.28f, y), w * 0.008f, StrokeCap.Round)
        }
    }

    // 7. lightning
    if (c == Condition.THUNDERSTORM) {
        val f = lightning(t)
        if (f > 0f) {
            drawRect(Color.White.copy(alpha = 0.32f * f))
            val strike = (t * 2f).toInt()
            val bx = w * (0.25f + 0.5f * rnd(strike, 20))
            val bolt = Path().apply {
                moveTo(bx, 0f)
                var x = bx; var y = 0f
                for (k in 0 until 6) {
                    x += w * (rnd(strike * 7 + k, 21) - 0.5f) * 0.18f; y += h * 0.13f
                    lineTo(x, y)
                }
            }
            drawPath(bolt, Color(0xFFFFF4B0).copy(alpha = f), style = Stroke(w * 0.014f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    // 8. themed stickers and mascot
    val mascot = spec.theme.mascot
    if (mascot != null && spec.showMascot) {
        val stickers = spec.theme.stickers
        val n = 6
        for (k in 0 until n) {
            val ang = (k * 360f / n + 28f) * PI.toFloat() / 180f
            val r = 0.4f + 0.02f * wave(t, 1, k * 0.17f)
            val p = Offset(w * (0.5f + r * cos(ang)), h * (0.5f + r * sin(ang)))
            val fill = if (k % 2 == 0) spec.theme.accent else Color(0xFFFFF3C4)
            drawSticker(stickers[k % stickers.size], p, w * 0.05f, 12f * wave(t, 1, k * 0.2f) + (k - 3) * 6f, fill,
                border = Color.White.copy(alpha = 0.95f))
        }
        val u = w * 0.3f
        drawMascot(mascot, w * 0.74f, h * 0.8f, u, t, eyesFor(c, spec.isDay), accessoryFor(c, spec.isDay), spec.theme.accent)
    }
}

/** Live background. Animation reads time only in the draw phase, so it never recomposes. */
@Composable
fun WeatherScene(
    spec: SceneSpec,
    style: IconStyle,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val time = rememberLoopTime(animate = !spec.ambient, periodMs = 12_000, still = 0.2f)
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawWeatherScene(spec, time()) }
        content()
    }
}
