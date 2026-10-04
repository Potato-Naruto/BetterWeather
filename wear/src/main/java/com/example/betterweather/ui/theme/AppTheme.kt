package com.example.betterweather.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.betterweather.core.Appearance
import com.example.betterweather.core.ThemeId
import com.example.betterweather.data.Condition

enum class StickerType { HEART, STAR, SPARKLE, PAW, BEAN, MINI_CLOUD }

enum class MascotKind { CINNAMOROLL, MOCHA, ESPRESSO }

class IconPalette(
    val sun: Color, val ray: Color, val moon: Color,
    val cloud: Color, val cloudShade: Color, val cloudDark: Color,
    val rain: Color, val snow: Color, val bolt: Color,
)

class ThemeSpec(
    val id: ThemeId,
    val dayTop: Color, val dayBottom: Color,
    val nightTop: Color, val nightBottom: Color,
    val accent: Color,
    val darkText: Color,
    val mascot: MascotKind?,
    val stickers: List<StickerType>,
    val icons: IconPalette,
)

private fun c(hex: Long) = Color(0xFF000000 or hex)

private val Classic = ThemeSpec(
    ThemeId.CLASSIC,
    dayTop = c(0x3A8DFF), dayBottom = c(0x8FD3FF), nightTop = c(0x0B1230), nightBottom = c(0x25346B),
    accent = c(0xFFD54F), darkText = c(0x16233F), mascot = null, stickers = emptyList(),
    icons = IconPalette(c(0xFFC93C), c(0xFFAA00), c(0xF7E7A0), c(0xFFFFFF), c(0xCFD8E6), c(0xA9B4C6),
        c(0x4DA3FF), c(0xE8F4FF), c(0xFFD84D)),
)

private val Cinnamoroll = ThemeSpec(
    ThemeId.CINNAMOROLL,
    dayTop = c(0x8EC9FF), dayBottom = c(0xFFE3F0), nightTop = c(0x3B4A8C), nightBottom = c(0x9C8FD6),
    accent = c(0xFF8FB8), darkText = c(0x3B4A7A), mascot = MascotKind.CINNAMOROLL,
    stickers = listOf(StickerType.HEART, StickerType.STAR, StickerType.MINI_CLOUD, StickerType.SPARKLE, StickerType.PAW),
    icons = IconPalette(c(0xFFD36B), c(0xFFB3C9), c(0xFFF3C4), c(0xFFFFFF), c(0xD6E8FF), c(0xB8CDF0),
        c(0x7CC4FF), c(0xFFFFFF), c(0xFFE27A)),
)

private val Mocha = ThemeSpec(
    ThemeId.MOCHA,
    dayTop = c(0xD9B99B), dayBottom = c(0xF6E7D8), nightTop = c(0x3B2A22), nightBottom = c(0x7A5A48),
    accent = c(0xE58FA3), darkText = c(0x4A3328), mascot = MascotKind.MOCHA,
    stickers = listOf(StickerType.PAW, StickerType.BEAN, StickerType.HEART, StickerType.MINI_CLOUD, StickerType.SPARKLE),
    icons = IconPalette(c(0xF4B26B), c(0xD98B4E), c(0xFCE9C8), c(0xFFF6EA), c(0xE5CDB4), c(0xC4A688),
        c(0x9C7A62), c(0xFFFFFF), c(0xFFD27A)),
)

private val Espresso = ThemeSpec(
    ThemeId.ESPRESSO,
    dayTop = c(0x6B4A3A), dayBottom = c(0xC9A98B), nightTop = c(0x130C09), nightBottom = c(0x3A261C),
    accent = c(0xE0A06A), darkText = c(0x2A1B14), mascot = MascotKind.ESPRESSO,
    stickers = listOf(StickerType.BEAN, StickerType.PAW, StickerType.STAR, StickerType.SPARKLE, StickerType.HEART),
    icons = IconPalette(c(0xE8A04C), c(0xC47A2A), c(0xEAD7B8), c(0xE9D8C6), c(0xB99D85), c(0x8D7260),
        c(0xC9A07E), c(0xF3E9DF), c(0xFFC857)),
)

fun themeSpec(id: ThemeId): ThemeSpec = when (id) {
    ThemeId.CLASSIC -> Classic
    ThemeId.CINNAMOROLL -> Cinnamoroll
    ThemeId.MOCHA -> Mocha
    ThemeId.ESPRESSO -> Espresso
}

fun resolveDark(appearance: Appearance, systemDark: Boolean) = when (appearance) {
    Appearance.SYSTEM -> systemDark
    Appearance.LIGHT -> false
    Appearance.DARK -> true
}

/** Sky gradient (top, bottom) for the current weather, time of day and light/dark preference. */
fun skyColors(spec: ThemeSpec, condition: Condition, isDay: Boolean, dark: Boolean): Pair<Color, Color> {
    var top = if (isDay) spec.dayTop else spec.nightTop
    var bottom = if (isDay) spec.dayBottom else spec.nightBottom
    val grey = if (isDay) c(0x6C7A90) else c(0x2A3142)
    val (tint, amount) = when (condition) {
        Condition.THUNDERSTORM -> c(0x3C4458) to 0.65f
        Condition.RAIN -> grey to 0.5f
        Condition.DRIZZLE, Condition.SLEET -> grey to 0.4f
        Condition.CLOUDY -> grey to 0.35f
        Condition.FOG -> c(0xAEB6C2) to 0.5f
        Condition.SNOW -> c(0xC9D6E8) to 0.3f
        else -> Color.Black to 0f
    }
    if (amount > 0f) { top = lerp(top, tint, amount); bottom = lerp(bottom, tint, amount * 0.8f) }
    // Dark mode deepens the sky; light mode brightens it a touch.
    if (dark) { top = lerp(top, Color.Black, if (isDay) 0.38f else 0.15f); bottom = lerp(bottom, Color.Black, if (isDay) 0.3f else 0.12f) }
    else if (isDay) { top = lerp(top, Color.White, 0.08f); bottom = lerp(bottom, Color.White, 0.12f) }
    return top to bottom
}

/** Readable text colour on top of [bottom]. */
fun onSky(spec: ThemeSpec, bottom: Color): Color = if (bottom.luminance() > 0.5f) spec.darkText else Color.White
