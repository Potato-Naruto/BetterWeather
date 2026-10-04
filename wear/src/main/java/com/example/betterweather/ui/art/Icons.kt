package com.example.betterweather.ui.art

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GfxCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.betterweather.core.IconStyle
import com.example.betterweather.data.Condition
import com.example.betterweather.ui.theme.IconPalette
import com.example.betterweather.ui.theme.ThemeSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** COLOR = full colour, OUTLINE = white line-art on black (AMOLED), SILHOUETTE = white fill (tintable bitmaps). */
enum class IconMode { COLOR, OUTLINE, SILHOUETTE }

/** Seamless looping helpers: [t] is in 0..1 and every animation uses whole-number cycles per loop. */
internal fun frac(x: Float) = x - kotlin.math.floor(x)
internal fun wave(t: Float, cycles: Int = 1, phase: Float = 0f) = sin(((t * cycles) + phase) * 2f * PI.toFloat())
internal fun rnd(i: Int, salt: Int = 0): Float = frac(sin(i * 12.9898f + salt * 78.233f) * 43758.547f)

internal val cloudUnit: Path by lazy {
    val a = Path().apply { addOval(Rect(Offset(0.28f, 0.40f), 0.17f)) }
    val b = Path().apply { addOval(Rect(Offset(0.52f, 0.29f), 0.25f)) }
    val c = Path().apply { addOval(Rect(Offset(0.76f, 0.40f), 0.17f)) }
    val base = Path().apply { addRoundRect(RoundRect(0.06f, 0.38f, 0.94f, 0.60f, 0.11f, 0.11f)) }
    var p = Path.combine(PathOperation.Union, a, b)
    p = Path.combine(PathOperation.Union, p, c)
    Path.combine(PathOperation.Union, p, base)
}

private val crescentUnit: Path by lazy {
    val full = Path().apply { addOval(Rect(Offset(0.5f, 0.5f), 0.5f)) }
    val bite = Path().apply { addOval(Rect(Offset(0.74f, 0.38f), 0.42f)) }
    Path.combine(PathOperation.Difference, full, bite)
}

internal const val CLOUD_ASPECT = 0.62f

/** Drawing context shared by all icon parts. [size] is the edge of the square icon. */
class IconInk(val s: DrawScope, val size: Float, val mode: IconMode, val pal: IconPalette) {
    val sw = size * 0.045f

    private fun stroke(width: Float = sw) = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun circle(color: Color, c: Offset, r: Float) {
        when (mode) {
            IconMode.COLOR -> s.drawCircle(color, r, c)
            IconMode.SILHOUETTE -> s.drawCircle(Color.White, r, c)
            IconMode.OUTLINE -> { s.drawCircle(Color.Black, r, c); s.drawCircle(Color.White, r, c, style = stroke()) }
        }
    }

    fun path(color: Color, p: Path) {
        when (mode) {
            IconMode.COLOR -> s.drawPath(p, color)
            IconMode.SILHOUETTE -> s.drawPath(p, Color.White)
            IconMode.OUTLINE -> { s.drawPath(p, Color.Black); s.drawPath(p, Color.White, style = stroke()) }
        }
    }

    fun line(color: Color, a: Offset, b: Offset, width: Float = sw, alpha: Float = 1f) {
        val col = if (mode == IconMode.COLOR) color else Color.White
        s.drawLine(col.copy(alpha = alpha * col.alpha), a, b, width, StrokeCap.Round)
    }

    fun dot(color: Color, c: Offset, r: Float, alpha: Float = 1f) {
        val col = if (mode == IconMode.COLOR) color else Color.White
        s.drawCircle(col.copy(alpha = alpha), r, c)
    }

    /** Cloud with its left edge at [x], top at [y] and width [w] (all in fractions of [size]). */
    fun cloud(x: Float, y: Float, w: Float, top: Color, bottom: Color) {
        val px = x * size; val py = y * size; val pw = w * size
        s.withTransform({ translate(px, py); scale(pw, pw, Offset.Zero) }) {
            when (mode) {
                IconMode.COLOR -> drawPath(cloudUnit, Brush.verticalGradient(listOf(top, bottom), 0.2f, CLOUD_ASPECT))
                IconMode.SILHOUETTE -> drawPath(cloudUnit, Color.White)
                IconMode.OUTLINE -> {
                    drawPath(cloudUnit, Color.Black)
                    drawPath(cloudUnit, Color.White, style = Stroke(sw / pw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }

    fun pt(x: Float, y: Float) = Offset(x * size, y * size)
}

// ---- parts ---------------------------------------------------------------------------------------

private fun IconInk.sun(cx: Float, cy: Float, r: Float, t: Float) {
    val c = pt(cx, cy)
    val rr = r * size * (1f + 0.04f * wave(t, 2))
    val spin = t * 2f * PI.toFloat()
    for (i in 0 until 8) {
        val a = spin + i * (PI.toFloat() / 4f)
        val inner = rr * 1.32f
        val outer = rr * (1.62f + 0.08f * wave(t, 4, i * 0.125f))
        line(pal.ray, c + Offset(cos(a) * inner, sin(a) * inner), c + Offset(cos(a) * outer, sin(a) * outer), sw * 0.9f)
    }
    circle(pal.sun, c, rr)
}

private fun IconInk.moon(cx: Float, cy: Float, r: Float, t: Float) {
    val d = r * 2f * size; val ox = cx * size - d / 2; val oy = cy * size - d / 2
    s.withTransform({ translate(ox, oy); scale(d, d, Offset.Zero); rotate(-18f, Offset(0.5f, 0.5f)) }) {
        when (mode) {
            IconMode.COLOR -> drawPath(crescentUnit, pal.moon)
            IconMode.SILHOUETTE -> drawPath(crescentUnit, Color.White)
            IconMode.OUTLINE -> {
                drawPath(crescentUnit, Color.Black)
                drawPath(crescentUnit, Color.White, style = Stroke(sw / d, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

private fun IconInk.stars(t: Float, spots: List<Triple<Float, Float, Float>>) {
    spots.forEachIndexed { i, (x, y, r) ->
        val a = 0.55f + 0.45f * wave(t, 2, i * 0.37f)
        val c = pt(x, y); val rr = r * size
        line(pal.moon, c - Offset(rr, 0f), c + Offset(rr, 0f), sw * 0.45f, a)
        line(pal.moon, c - Offset(0f, rr), c + Offset(0f, rr), sw * 0.45f, a)
    }
}

private fun IconInk.flake(c: Offset, r: Float, alpha: Float) {
    for (k in 0 until 3) {
        val a = k * (PI.toFloat() / 3f)
        line(pal.snow, c - Offset(cos(a) * r, sin(a) * r), c + Offset(cos(a) * r, sin(a) * r), sw * 0.5f, alpha)
    }
}

/** Falling streaks that loop seamlessly and fade in/out so nothing pops. */
private fun IconInk.drops(t: Float, n: Int, x0: Float, x1: Float, y0: Float, y1: Float, cycles: Int, len: Float = 0.1f, slant: Float = 0.04f) {
    for (i in 0 until n) {
        val phase = frac(t * cycles + i / n.toFloat() + (i % 2) * 0.13f)
        val x = x0 + (x1 - x0) * (i + 0.5f) / n + (i % 2) * 0.02f - slant * phase
        val y = y0 + (y1 - y0) * phase
        val a = sin(PI.toFloat() * phase).coerceIn(0f, 1f)
        line(pal.rain, pt(x, y), pt(x - slant * 0.6f, y + len), sw * 0.8f, a)
    }
}

private fun IconInk.snowfall(t: Float, n: Int, x0: Float, x1: Float, y0: Float, y1: Float) {
    for (i in 0 until n) {
        val phase = frac(t + i / n.toFloat() + (i % 3) * 0.11f)
        val x = x0 + (x1 - x0) * (i + 0.5f) / n + 0.025f * wave(t, 2, i * 0.3f)
        val y = y0 + (y1 - y0) * phase
        val a = sin(PI.toFloat() * phase).coerceIn(0f, 1f)
        if (i % 2 == 0) flake(pt(x, y), size * 0.032f, a) else dot(pal.snow, pt(x, y), size * 0.022f, a)
    }
}

private fun IconInk.bolt(flash: Float) {
    val p = Path().apply {
        moveTo(size * 0.54f, size * 0.50f); lineTo(size * 0.40f, size * 0.70f); lineTo(size * 0.51f, size * 0.70f)
        lineTo(size * 0.44f, size * 0.92f); lineTo(size * 0.64f, size * 0.64f); lineTo(size * 0.52f, size * 0.64f)
        lineTo(size * 0.60f, size * 0.50f); close()
    }
    val lit = 0.45f + 0.55f * flash
    when (mode) {
        IconMode.COLOR -> s.drawPath(p, lerp(pal.bolt.copy(alpha = 0.6f), pal.bolt, lit).copy(alpha = lit))
        IconMode.SILHOUETTE -> s.drawPath(p, Color.White)
        IconMode.OUTLINE -> { s.drawPath(p, Color.Black); s.drawPath(p, Color.White.copy(alpha = lit), style = Stroke(sw * 0.8f, join = StrokeJoin.Round)) }
    }
}

/** 1 during a short double flash, 0 otherwise; two flashes per loop. */
internal fun lightning(t: Float): Float {
    val f = frac(t * 2f)
    return when {
        f < 0.07f -> 1f
        f < 0.12f -> 0.1f
        f < 0.19f -> 0.8f
        else -> 0f
    }
}

private fun IconInk.windLine(y: Float, x0: Float, x1: Float, curl: Float, t: Float, i: Int) {
    val shift = 0.04f * wave(t, 1, i * 0.2f)
    val p = Path().apply {
        moveTo((x0 + shift) * size, y * size)
        lineTo((x1 + shift) * size, y * size)
        val r = kotlin.math.abs(curl) * size
        arcTo(Rect(Offset((x1 + shift) * size, y * size + (if (curl < 0) -r * 2 else 0f)), Size(r * 2, r * 2)), if (curl < 0) 90f else -90f, if (curl < 0) -240f else 240f, false)
    }
    val col = if (mode == IconMode.COLOR) pal.cloudShade else Color.White
    s.drawPath(p, col.copy(alpha = if (mode == IconMode.COLOR) 1f else 1f), style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Draws [condition] into the square (0,0)-(size,size) of this scope. [t] in 0..1 drives the loop.
 */
fun DrawScope.drawWeatherIcon(
    condition: Condition,
    isDay: Boolean,
    t: Float,
    mode: IconMode,
    pal: IconPalette,
    size: Float = this.size.minDimension,
) {
    val ink = IconInk(this, size, mode, pal)
    with(ink) {
        val drift = 0.025f * wave(t)
        when (condition) {
            Condition.CLEAR ->
                if (isDay) sun(0.5f, 0.5f, 0.2f, t) else {
                    moon(0.5f, 0.5f, 0.34f, t)
                    stars(t, listOf(Triple(0.2f, 0.25f, 0.05f), Triple(0.82f, 0.22f, 0.04f), Triple(0.78f, 0.78f, 0.035f)))
                }

            Condition.PARTLY_CLOUDY -> {
                if (isDay) sun(0.36f, 0.34f, 0.15f, t) else {
                    moon(0.34f, 0.32f, 0.26f, t)
                    stars(t, listOf(Triple(0.74f, 0.2f, 0.04f), Triple(0.15f, 0.7f, 0.03f)))
                }
                cloud(0.12f + drift, 0.38f, 0.8f, pal.cloud, pal.cloudShade)
            }

            Condition.CLOUDY -> {
                cloud(0.34f - drift, 0.14f, 0.6f, pal.cloudShade, pal.cloudDark)
                cloud(0.06f + drift, 0.32f, 0.82f, pal.cloud, pal.cloudShade)
            }

            Condition.DRIZZLE, Condition.RAIN -> {
                val n = if (condition == Condition.RAIN) 5 else 3
                drops(t, n, 0.26f, 0.74f, 0.56f, 0.88f, if (condition == Condition.RAIN) 2 else 1,
                    len = if (condition == Condition.RAIN) 0.11f else 0.07f)
                cloud(0.08f + drift, 0.14f, 0.84f, pal.cloudShade, pal.cloudDark)
            }

            Condition.THUNDERSTORM -> {
                drops(t, 3, 0.2f, 0.8f, 0.58f, 0.9f, 2)
                cloud(0.08f + drift, 0.1f, 0.84f, pal.cloudDark, pal.cloudDark.copy(alpha = 1f).let { lerp(it, Color.Black, 0.25f) })
                bolt(lightning(t))
            }

            Condition.SNOW -> {
                snowfall(t, 6, 0.2f, 0.8f, 0.52f, 0.92f)
                cloud(0.08f + drift, 0.12f, 0.84f, pal.cloud, pal.cloudShade)
            }

            Condition.SLEET -> {
                drops(t, 3, 0.24f, 0.5f, 0.56f, 0.9f, 2)
                snowfall(t, 3, 0.55f, 0.8f, 0.52f, 0.92f)
                cloud(0.08f + drift, 0.12f, 0.84f, pal.cloudShade, pal.cloudDark)
            }

            Condition.FOG -> {
                cloud(0.2f + drift, 0.1f, 0.6f, pal.cloud, pal.cloudShade)
                for (i in 0 until 3) {
                    val y = 0.62f + i * 0.13f
                    val sh = 0.05f * wave(t, 1, i * 0.33f)
                    line(pal.cloudShade, pt(0.16f + sh + i * 0.04f, y), pt(0.84f + sh - i * 0.04f, y), sw * 1.1f)
                }
            }

            Condition.WINDY -> {
                windLine(0.34f, 0.12f, 0.58f, -0.075f, t, 0)
                windLine(0.52f, 0.2f, 0.78f, 0.075f, t, 1)
                windLine(0.7f, 0.1f, 0.5f, -0.06f, t, 2)
            }
        }
    }
}

// ---- composable + bitmap rendering ----------------------------------------------------------------

/** Loop time shared by every icon: 0..1 over [periodMs]. Returns a constant when not [animate]. */
@Composable
fun rememberLoopTime(animate: Boolean, periodMs: Int = 4000, still: Float = 0.3f): () -> Float {
    if (!animate) return { still }
    val transition = rememberInfiniteTransition(label = "loop")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "t")
    return { t }
}

fun IconStyle.toMode() = if (this == IconStyle.AMOLED) IconMode.OUTLINE else IconMode.COLOR

@Composable
fun WeatherIcon(
    condition: Condition,
    isDay: Boolean,
    style: IconStyle,
    theme: ThemeSpec,
    modifier: Modifier = Modifier,
    animate: Boolean = style == IconStyle.ANIMATED,
) {
    val time = rememberLoopTime(animate)
    Canvas(modifier) {
        drawWeatherIcon(condition, isDay, time(), style.toMode(), theme.icons)
    }
}

/** Renders any drawing to a bitmap off-screen (used for complications and the tile). */
fun renderBitmap(width: Int, height: Int, block: DrawScope.() -> Unit): Bitmap {
    val image = ImageBitmap(width, height)
    val canvas = GfxCanvas(image)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) { block() }
    return image.asAndroidBitmap()
}

fun renderIconBitmap(
    condition: Condition, isDay: Boolean, px: Int, mode: IconMode, theme: ThemeSpec, t: Float = 0.3f,
): Bitmap = renderBitmap(px, px) { drawWeatherIcon(condition, isDay, t, mode, theme.icons) }
