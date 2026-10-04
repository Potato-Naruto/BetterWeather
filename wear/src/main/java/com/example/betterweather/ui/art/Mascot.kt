package com.example.betterweather.ui.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.example.betterweather.ui.theme.MascotKind
import com.example.betterweather.ui.theme.StickerType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class Eyes { OPEN, HAPPY, SLEEPY, SURPRISED }
enum class Accessory { NONE, UMBRELLA, SCARF, SUNGLASSES }

internal class Look(
    val fur: Color, val earColor: Color, val muzzle: Color?, val eye: Color, val cheek: Color, val nose: Color,
    val earLen: Float, val earW: Float, val earBase: Float, val earBend: Float,
)

// Original, simplified "cloud puppy" drawings inspired by the Cinnamoroll family.
internal fun look(kind: MascotKind) = when (kind) {
    MascotKind.CINNAMOROLL -> Look(Color(0xFFFFFFFF), Color(0xFFFFFFFF), null, Color(0xFF4A90E2), Color(0xFFFFB6C8),
        Color(0xFF8A7BA0), earLen = 0.8f, earW = 0.27f, earBase = 8f, earBend = 0.2f)
    MascotKind.MOCHA -> Look(Color(0xFFD1A47A), Color(0xFF9A6A44), Color(0xFFF7E6D2), Color(0xFF3A2A20), Color(0xFFF4A29A),
        Color(0xFF4A3328), earLen = 0.56f, earW = 0.3f, earBase = 8f, earBend = 0.15f)
    MascotKind.ESPRESSO -> Look(Color(0xFF6B4331), Color(0xFF3E2619), Color(0xFFE6CBAC), Color(0xFF140C08), Color(0xFFD98C7C),
        Color(0xFF140C08), earLen = 0.54f, earW = 0.29f, earBase = 6f, earBend = 0.15f)
}

/**
 * Draws the mascot with its head centred on ([cx],[cy]); [u] is the head width.
 * [t] loops 0..1 (ears flap, body bobs).
 */
fun DrawScope.drawMascot(
    kind: MascotKind, cx: Float, cy0: Float, u: Float, t: Float,
    eyes: Eyes = Eyes.OPEN, accessory: Accessory = Accessory.NONE, accent: Color = Color(0xFFFF8FB8),
) {
    val cy = cy0 + u * 0.025f * wave(t, 2)
    MascotArt.get(kind)?.let { img ->
        val wPx = u * 1.25f
        val hPx = wPx * img.height / img.width
        drawImage(img, dstOffset = IntOffset((cx - wPx / 2).toInt(), (cy - hPx / 2).toInt()), dstSize = IntSize(wPx.toInt(), hPx.toInt()))
        return
    }
    val l = look(kind)
    val shade = lerp(l.fur, Color.Black, 0.14f)
    val line = Stroke(u * 0.022f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // tail: a cinnamon-roll spiral, wagging
    val tailC = Offset(cx + u * 0.36f, cy + u * 0.6f)
    val tr = u * 0.17f
    rotate(10f * wave(t, 2), tailC) {
        drawCircle(l.fur, tr, tailC)
        drawCircle(shade, tr, tailC, style = line)
        val spiral = Path()
        val turns = 2.4f
        val steps = 48
        for (i in 0..steps) {
            val f = i / steps.toFloat()
            val ang = f * turns * 2f * PI.toFloat()
            val rr = tr * 0.9f * f
            val pt = Offset(tailC.x + cos(ang) * rr, tailC.y + sin(ang) * rr)
            if (i == 0) spiral.moveTo(pt.x, pt.y) else spiral.lineTo(pt.x, pt.y)
        }
        drawPath(spiral, shade, style = Stroke(u * 0.03f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // body + feet
    drawOval(l.fur, Offset(cx - u * 0.24f, cy + u * 0.34f), Size(u * 0.48f, u * 0.44f))
    drawOval(shade, Offset(cx - u * 0.24f, cy + u * 0.34f), Size(u * 0.48f, u * 0.44f), style = line)
    drawOval(l.fur, Offset(cx - u * 0.22f, cy + u * 0.69f), Size(u * 0.17f, u * 0.1f))
    drawOval(l.fur, Offset(cx + u * 0.05f, cy + u * 0.69f), Size(u * 0.17f, u * 0.1f))

    // ears: hockey-stick / paddle shape. A smooth rounded top, a straight floppy shaft, then the
    // bottom curls outward into a rounded blade. The lower part sways a little behind the head.
    for (side in intArrayOf(-1, 1)) {
        val px = cx + side * u * 0.45f
        val top = cy - u * 0.3f
        val len = u * l.earLen
        val wt = u * l.earW
        val bend = u * l.earBend
        val lean = u * (l.earBase / 100f) + u * 0.04f * wave(t, 2, if (side < 0) 0f else 0.12f)
        // x offset grows toward the bottom so the ear leans outward and flops
        val ear = earOutline(side, px, top, len, wt, bend, lean)
        drawPath(ear, l.earColor)
        drawPath(ear, shade, style = line)
    }

    // head
    val headTl = Offset(cx - u * 0.5f, cy - u * 0.38f)
    val headSize = Size(u, u * 0.8f)
    drawOval(l.fur, headTl, headSize)
    drawOval(shade, headTl, headSize, style = line)
    l.muzzle?.let { drawOval(it, Offset(cx - u * 0.23f, cy + u * 0.02f), Size(u * 0.46f, u * 0.3f)) }

    // Mocha's flower, tucked on the top of her head
    if (kind == MascotKind.MOCHA) {
        val fc = Offset(cx - u * 0.26f, cy - u * 0.34f)
        val pr = u * 0.085f
        val petal = Color(0xFFFF8FB8)
        for (i in 0 until 5) {
            val a = (-PI / 2 + i * 2 * PI / 5).toFloat()
            val pc = fc + Offset(cos(a) * pr * 1.05f, sin(a) * pr * 1.05f)
            drawCircle(Color.White, pr * 1.12f, pc)
            drawCircle(petal, pr, pc)
        }
        drawCircle(Color(0xFFFFE27A), pr * 0.75f, fc)
        drawCircle(Color(0xFFE0A93B), pr * 0.75f, fc, style = Stroke(u * 0.012f))
    }

    // eyes
    val ex = u * 0.22f; val ey = cy + u * 0.0f
    for (side in intArrayOf(-1, 1)) {
        val c = Offset(cx + side * ex, ey)
        when (eyes) {
            Eyes.OPEN, Eyes.SURPRISED -> {
                val big = if (eyes == Eyes.SURPRISED) 1.3f else 1f
                drawOval(l.eye, c - Offset(u * 0.055f * big, u * 0.07f * big), Size(u * 0.11f * big, u * 0.14f * big))
                drawCircle(Color.White, u * 0.022f * big, c + Offset(-u * 0.012f, -u * 0.03f * big))
            }
            Eyes.HAPPY -> {
                val p = Path().apply { moveTo(c.x - u * 0.06f, c.y + u * 0.02f); quadraticTo(c.x, c.y - u * 0.09f, c.x + u * 0.06f, c.y + u * 0.02f) }
                drawPath(p, l.eye, style = Stroke(u * 0.03f, cap = StrokeCap.Round))
            }
            Eyes.SLEEPY -> {
                val p = Path().apply { moveTo(c.x - u * 0.06f, c.y - u * 0.01f); quadraticTo(c.x, c.y + u * 0.07f, c.x + u * 0.06f, c.y - u * 0.01f) }
                drawPath(p, l.eye, style = Stroke(u * 0.03f, cap = StrokeCap.Round))
            }
        }
        drawOval(l.cheek.copy(alpha = 0.75f), Offset(c.x + side * u * 0.12f - u * 0.07f, ey + u * 0.1f), Size(u * 0.14f, u * 0.085f))
    }
    // nose + mouth
    drawOval(l.nose, Offset(cx - u * 0.03f, cy + u * 0.085f), Size(u * 0.06f, u * 0.04f))
    val mouth = Path().apply {
        moveTo(cx - u * 0.05f, cy + u * 0.155f); quadraticTo(cx - u * 0.025f, cy + u * 0.19f, cx, cy + u * 0.155f)
        quadraticTo(cx + u * 0.025f, cy + u * 0.19f, cx + u * 0.05f, cy + u * 0.155f)
    }
    drawPath(mouth, l.nose, style = Stroke(u * 0.016f, cap = StrokeCap.Round))

    // accessories
    when (accessory) {
        Accessory.NONE -> Unit
        Accessory.SCARF -> {
            drawRoundRect(accent, Offset(cx - u * 0.3f, cy + u * 0.33f), Size(u * 0.6f, u * 0.12f), androidx.compose.ui.geometry.CornerRadius(u * 0.06f))
            drawRoundRect(accent, Offset(cx + u * 0.12f, cy + u * 0.4f), Size(u * 0.1f, u * 0.2f), androidx.compose.ui.geometry.CornerRadius(u * 0.04f))
            drawRect(Color.White.copy(alpha = 0.7f), Offset(cx + u * 0.12f, cy + u * 0.52f), Size(u * 0.1f, u * 0.025f))
        }
        Accessory.SUNGLASSES -> {
            for (side in intArrayOf(-1, 1)) {
                drawRoundRect(Color(0xFF1B1B25), Offset(cx + side * ex - u * 0.1f, ey - u * 0.07f), Size(u * 0.2f, u * 0.14f),
                    androidx.compose.ui.geometry.CornerRadius(u * 0.05f))
            }
            drawLine(Color(0xFF1B1B25), Offset(cx - u * 0.12f, ey - u * 0.02f), Offset(cx + u * 0.12f, ey - u * 0.02f), u * 0.02f)
        }
        Accessory.UMBRELLA -> {
            val top = cy - u * 0.5f
            val dome = Path().apply {
                moveTo(cx - u * 0.62f, top + u * 0.2f)
                quadraticTo(cx, top - u * 0.42f, cx + u * 0.62f, top + u * 0.2f)
                quadraticTo(cx + u * 0.42f, top + u * 0.1f, cx + u * 0.21f, top + u * 0.2f)
                quadraticTo(cx, top + u * 0.1f, cx - u * 0.21f, top + u * 0.2f)
                quadraticTo(cx - u * 0.42f, top + u * 0.1f, cx - u * 0.62f, top + u * 0.2f)
                close()
            }
            drawLine(Color(0xFF8A7B8F), Offset(cx + u * 0.0f, top + u * 0.2f), Offset(cx + u * 0.0f, cy - u * 0.28f), u * 0.025f, StrokeCap.Round)
            drawPath(dome, accent)
            drawPath(dome, Color.White.copy(alpha = 0.55f), style = Stroke(u * 0.02f, join = StrokeJoin.Round))
        }
    }
    if (eyes == Eyes.SLEEPY) {
        // floating "z"
        val zc = Offset(cx + u * 0.5f, cy - u * 0.38f - u * 0.05f * wave(t, 1))
        val z = Path().apply {
            moveTo(zc.x - u * 0.06f, zc.y - u * 0.05f); lineTo(zc.x + u * 0.06f, zc.y - u * 0.05f)
            lineTo(zc.x - u * 0.06f, zc.y + u * 0.05f); lineTo(zc.x + u * 0.06f, zc.y + u * 0.05f)
        }
        drawPath(z, Color.White, style = Stroke(u * 0.03f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ---- stickers ------------------------------------------------------------------------------------

/** Die-cut sticker: a thick white border, then the colour fill. [r] is the sticker radius in px. */
fun DrawScope.drawSticker(type: StickerType, c: Offset, r: Float, rotation: Float, fill: Color, border: Color = Color.White) {
    withTransform({ translate(c.x, c.y); rotate(rotation, Offset.Zero); scale(r, r, Offset.Zero) }) {
        val bw = 0.34f
        fun filled(p: Path, color: Color = fill) {
            drawPath(p, border, style = Stroke(bw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(p, color)
        }
        when (type) {
            StickerType.HEART -> filled(Path().apply {
                moveTo(0f, 0.8f); cubicTo(-1.1f, 0.1f, -0.7f, -0.9f, 0f, -0.35f); cubicTo(0.7f, -0.9f, 1.1f, 0.1f, 0f, 0.8f); close()
            })
            StickerType.STAR -> filled(Path().apply {
                for (i in 0 until 10) {
                    val a = (-PI / 2 + i * PI / 5).toFloat(); val rr = if (i % 2 == 0) 1f else 0.46f
                    if (i == 0) moveTo(cos(a) * rr, sin(a) * rr) else lineTo(cos(a) * rr, sin(a) * rr)
                }
                close()
            })
            StickerType.SPARKLE -> filled(Path().apply {
                moveTo(0f, -1f); quadraticTo(0.1f, -0.1f, 1f, 0f); quadraticTo(0.1f, 0.1f, 0f, 1f)
                quadraticTo(-0.1f, 0.1f, -1f, 0f); quadraticTo(-0.1f, -0.1f, 0f, -1f); close()
            })
            StickerType.PAW -> {
                val pads = listOf(Offset(-0.62f, -0.2f) to 0.2f, Offset(-0.22f, -0.62f) to 0.21f,
                    Offset(0.22f, -0.62f) to 0.21f, Offset(0.62f, -0.2f) to 0.2f)
                val main = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(0f, 0.3f), 0.5f)) }
                drawPath(main, border, style = Stroke(bw))
                pads.forEach { (o, rr) -> drawCircle(border, rr + bw / 2, o) }
                drawPath(main, fill)
                pads.forEach { (o, rr) -> drawCircle(fill, rr, o) }
            }
            StickerType.BEAN -> {
                val bean = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(-0.62f, -0.85f), Size(1.24f, 1.7f))) }
                withTransform({ rotate(35f, Offset.Zero) }) {
                    drawPath(bean, border, style = Stroke(bw))
                    drawPath(bean, fill)
                    val groove = Path().apply { moveTo(0f, -0.8f); cubicTo(-0.45f, -0.3f, 0.45f, 0.3f, 0f, 0.8f) }
                    drawPath(groove, lerp(fill, Color.Black, 0.45f), style = Stroke(0.14f, cap = StrokeCap.Round))
                }
            }
            StickerType.MINI_CLOUD -> withTransform({ translate(-1f, -0.62f); scale(2f, 2f, Offset.Zero) }) {
                drawPath(cloudUnit, border, style = Stroke(bw / 2f, join = StrokeJoin.Round))
                drawPath(cloudUnit, fill)
            }
        }
    }
}

/**
 * Optional user-supplied mascot artwork. If a PNG named mascot_cinnamoroll / mascot_mocha / mascot_espresso
 * exists in wear/src/main/res/drawable-nodpi it is drawn instead of the built-in vector character.
 */
object MascotArt {
    private val images = HashMap<MascotKind, androidx.compose.ui.graphics.ImageBitmap>()

    fun load(ctx: android.content.Context) {
        for (kind in MascotKind.entries) {
            val id = ctx.resources.getIdentifier("mascot_${kind.name.lowercase()}", "drawable", ctx.packageName)
            if (id == 0) continue
            val bmp = runCatching { android.graphics.BitmapFactory.decodeResource(ctx.resources, id) }.getOrNull() ?: continue
            images[kind] = bmp.asImageBitmap()
        }
    }

    fun get(kind: MascotKind) = images[kind]
}

/**
 * Hockey-stick ear: rounded top, straight floppy shaft, bottom curls outward into a blade.
 * (px, top) is the top-centre of the ear; [side] is -1 (left) or +1 (right); [lean] pushes the lower part outward.
 */
internal fun earOutline(side: Int, px: Float, top: Float, len: Float, wt: Float, bend: Float, lean: Float): Path {
    fun x(v: Float, yFrac: Float) = px + side * (v + lean * yFrac * yFrac)
    fun y(f: Float) = top + len * f
    val cap = wt * 0.5f
    return Path().apply {
        moveTo(x(-wt / 2, 0f), y(0f) + cap)
        cubicTo(x(-wt / 2, 0f), y(0f) - cap * 0.55f, x(wt / 2, 0f), y(0f) - cap * 0.55f, x(wt / 2, 0f), y(0f) + cap)
        lineTo(x(wt / 2, 0.55f), y(0.55f))
        cubicTo(x(wt / 2, 0.75f), y(0.75f), x(wt / 2 + bend * 0.35f, 0.82f), y(0.8f), x(wt / 2 + bend * 0.8f, 0.82f), y(0.8f))
        cubicTo(x(wt / 2 + bend * 1.25f, 0.82f), y(0.8f), x(wt / 2 + bend * 1.25f, 1f), y(1.04f), x(wt / 2 + bend * 0.7f, 1f), y(1.04f))
        cubicTo(x(wt * 0.1f, 1f), y(1.06f), x(-wt / 2, 0.95f), y(0.98f), x(-wt / 2, 0.75f), y(0.75f))
        lineTo(x(-wt / 2, 0f), y(0f) + cap)
        close()
    }
}
