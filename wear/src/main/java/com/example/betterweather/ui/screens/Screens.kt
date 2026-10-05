package com.example.betterweather.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.example.betterweather.core.Appearance
import com.example.betterweather.core.ComplicationMode
import com.example.betterweather.core.IconStyle
import com.example.betterweather.core.ProviderId
import com.example.betterweather.core.Settings
import com.example.betterweather.core.ThemeId
import com.example.betterweather.core.Units
import com.example.betterweather.data.UpdateStatus
import com.example.betterweather.data.WeatherSnapshot
import com.example.betterweather.data.fmtTemp
import com.example.betterweather.ui.art.Eyes
import com.example.betterweather.ui.art.SceneSpec
import com.example.betterweather.ui.art.WeatherIcon
import com.example.betterweather.ui.art.WeatherScene
import com.example.betterweather.ui.art.drawMascot
import com.example.betterweather.ui.art.drawSticker
import com.example.betterweather.ui.art.rememberLoopTime
import com.example.betterweather.ui.theme.ThemeSpec
import com.example.betterweather.ui.theme.onSky
import com.example.betterweather.ui.theme.skyColors
import com.example.betterweather.ui.theme.themeSpec
import kotlin.math.roundToInt

/** Everything the screens render; assembled once in the activity. */
data class UiModel(
    val settings: Settings,
    val snapshot: WeatherSnapshot?,
    val refreshing: Boolean,
    val message: String?,
    val dark: Boolean,
    val ambient: Boolean,
    val batteryUnrestricted: Boolean = false,
    val update: UpdateStatus = UpdateStatus.Idle,
    val version: String = "",
)

private val readable = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.28f), Offset(0f, 1.5f), 4f))

private fun hourLabel(ctx: android.content.Context, ms: Long): String {
    val pattern = if (DateFormat.is24HourFormat(ctx)) "H" else "ha"
    return DateFormat.format(pattern, ms).toString().lowercase()
}

private fun dayLabel(ms: Long) = DateFormat.format("EEE", ms).toString()

private fun ageLabel(ms: Long): String {
    val m = ((System.currentTimeMillis() - ms) / 60_000L).coerceAtLeast(0)
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        else -> "${m / 60}h ago"
    }
}

@Composable
fun HomeScreen(ui: UiModel, onSettings: () -> Unit, onRefresh: () -> Unit) {
    val s = ui.settings
    val snap = ui.snapshot ?: WeatherSnapshot.sample()
    val loading = ui.snapshot == null
    val theme = themeSpec(s.theme)
    val amoled = s.iconStyle == IconStyle.AMOLED
    val spec = SceneSpec(snap.condition, snap.isDay, theme, ui.dark, amoled, ui.ambient)
    val (_, bottom) = skyColors(theme, snap.condition, snap.isDay, ui.dark)
    val fg = if (amoled || ui.ambient) Color.White else onSky(theme, bottom)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val u = s.units
    val iconStyle = if (ui.ambient) IconStyle.AMOLED else s.iconStyle

    WeatherScene(spec, s.iconStyle) {
        val listState = rememberTransformingLazyColumnState()
        ScreenScaffold(scrollState = listState) { padding ->
            TransformingLazyColumn(contentPadding = padding, state = listState) {
                item {
                    Text(
                        if (loading) (ui.message ?: "Locating…") else snap.locationName.ifBlank { "Weather" },
                        color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = readable,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth().clickable(onClick = onRefresh), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        WeatherIcon(snap.condition, snap.isDay, iconStyle, theme, Modifier.size(58.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (loading) "--°" else snap.tempC.fmtTemp(u), color = fg, fontSize = 46.sp,
                            fontWeight = FontWeight.SemiBold, style = readable)
                    }
                }
                item {
                    Text(
                        if (loading) "" else "${snap.description}  ·  H ${snap.highC.fmtTemp(u)}  L ${snap.lowC.fmtTemp(u)}",
                        color = fg, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center, style = readable,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!loading) {
                    item { Spacer(Modifier.height(6.dp)) }
                    item {
                        // Next 24 hours, horizontally scrollable.
                        val hours = snap.next24Hours()
                        LazyRow(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(fg.copy(alpha = 0.12f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(hours) { i, h ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (i == 0) "Now" else hourLabel(ctx, h.timeMs), color = fg, fontSize = 10.sp)
                                    WeatherIcon(h.condition, h.isDay, iconStyle, theme, Modifier.size(26.dp).padding(vertical = 2.dp),
                                        animate = false)
                                    Text(h.tempC.fmtTemp(u), color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    item {
                        val wind = if (u == Units.FAHRENHEIT) "${(snap.windKmh / 1.609f).roundToInt()} mph" else "${snap.windKmh.roundToInt()} km/h"
                        Text("Feels ${snap.feelsC.fmtTemp(u)}  ·  ${snap.humidity}%  ·  $wind", color = fg, fontSize = 11.sp,
                            textAlign = TextAlign.Center, style = readable, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    snap.daily.drop(1).take(6).forEach { d ->
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(dayLabel(d.dateMs), color = fg, fontSize = 13.sp, modifier = Modifier.width(44.dp))
                                WeatherIcon(d.condition, true, iconStyle, theme, Modifier.size(26.dp), animate = false)
                                Spacer(Modifier.weight(1f))
                                Text("${d.highC.fmtTemp(u)}  ${d.lowC.fmtTemp(u)}", color = fg, fontSize = 13.sp)
                            }
                        }
                    }
                    item {
                        Text(
                            (if (ui.refreshing) "Updating…" else "Updated ${ageLabel(snap.fetchedAtMs)}") + " · ${snap.provider}" +
                                (ui.message?.let { "\n$it" } ?: ""),
                            color = fg.copy(alpha = 0.8f), fontSize = 10.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
                item { Pill("Settings", fg, onSettings, Modifier.padding(top = 8.dp, bottom = 22.dp)) }
            }
        }
    }
}

@Composable
private fun Pill(text: String, fg: Color, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, accent: Color = fg) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(if (selected) accent.copy(alpha = 0.32f) else fg.copy(alpha = 0.13f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = fg, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 2)
        if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
    }
}


// ---- settings --------------------------------------------------------------------------------------

@Composable
fun SettingsScreen(ui: UiModel, onChange: ((Settings) -> Settings) -> Unit, onRefresh: () -> Unit, onBattery: () -> Unit, onUpdate: () -> Unit, onBack: () -> Unit) {
    val s = ui.settings
    val theme = themeSpec(s.theme)
    val bg = if (ui.dark) theme.nightTop.copy(alpha = 1f).let { androidx.compose.ui.graphics.lerp(it, Color.Black, 0.55f) }
    else androidx.compose.ui.graphics.lerp(theme.dayBottom, Color.White, 0.5f)
    val fg = if (ui.dark) Color.White else theme.darkText
    val header = fg.copy(alpha = 0.7f)
    val listState = rememberTransformingLazyColumnState()

    Box(Modifier.fillMaxSize().background(bg)) {
        ScreenScaffold(scrollState = listState) { padding ->
            TransformingLazyColumn(contentPadding = padding, state = listState) {
                item { MascotHeader(theme, fg) }
                item { Section("Theme", header) }
                ThemeId.entries.forEach { id ->
                    item { Pill(id.label, fg, { onChange { it.copy(theme = id) } }, selected = s.theme == id, accent = themeSpec(id).accent) }
                }
                item { Section("Weather icons", header) }
                IconStyle.entries.forEach { st ->
                    item { Pill(st.label, fg, { onChange { it.copy(iconStyle = st) } }, selected = s.iconStyle == st, accent = theme.accent) }
                }
                item { Section("Light / dark", header) }
                Appearance.entries.forEach { a ->
                    item { Pill(a.label, fg, { onChange { it.copy(appearance = a) } }, selected = s.appearance == a, accent = theme.accent) }
                }
                item { Section("Units", header) }
                Units.entries.forEach { un ->
                    item { Pill(un.label, fg, { onChange { it.copy(units = un) } }, selected = s.units == un, accent = theme.accent) }
                }
                item { Section("Weather source", header) }
                ProviderId.entries.forEach { p ->
                    val usable = s.isUsable(p)
                    item {
                        Pill(if (usable) p.label else "${p.label}\n(add key on phone)", if (usable) fg else fg.copy(alpha = 0.5f),
                            { onChange { it.copy(provider = p) } }, selected = s.provider == p, accent = theme.accent)
                    }
                }
                item { Section("Complication", header) }
                ComplicationMode.entries.forEach { m ->
                    item { Pill(m.label, fg, { onChange { it.copy(complicationMode = m) } }, selected = s.complicationMode == m, accent = theme.accent) }
                }
                item { Section("Updates", header) }
                item {
                    val st = ui.update
                    val label = when (st) {
                        UpdateStatus.Idle -> "Check for updates\n(v${ui.version})"
                        UpdateStatus.Checking -> "Checking…"
                        is UpdateStatus.UpToDate -> "Up to date (v${st.version})\nTap to check again"
                        is UpdateStatus.Available -> "Update to v${st.version}\nTap to download & install"
                        is UpdateStatus.Downloading -> "Downloading v${st.version}…"
                        UpdateStatus.NeedsPermission -> "Allow installs for this app,\nthen tap again"
                        is UpdateStatus.Failed -> "${st.message}\nTap to retry"
                    }
                    Pill(label, fg, onUpdate, selected = st is UpdateStatus.Available, accent = theme.accent)
                }
                item { Section("Data", header) }
                item { Pill(if (ui.refreshing) "Refreshing…" else "Refresh now", fg, onRefresh) }
                item { Pill(if (ui.batteryUnrestricted) "Background: always on" else "Allow background (battery)", fg, onBattery, selected = ui.batteryUnrestricted, accent = theme.accent) }
                item { Pill("Back", fg, onBack, Modifier.padding(bottom = 24.dp)) }
            }
        }
    }
}

@Composable
private fun Section(text: String, color: Color) {
    Text(text.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun MascotHeader(theme: ThemeSpec, fg: Color) {
    val time = rememberLoopTime(true, 3000)
    Canvas(Modifier.fillMaxWidth().height(86.dp)) {
        val t = time()
        val m = theme.mascot
        if (m != null) {
            drawMascot(m, size.width / 2f, size.height * 0.45f, size.height * 0.72f, t, Eyes.HAPPY, accent = theme.accent)
            theme.stickers.take(4).forEachIndexed { i, st ->
                val x = if (i % 2 == 0) size.width * (0.22f - 0.06f * (i / 2)) else size.width * (0.78f + 0.06f * (i / 2))
                drawSticker(st, Offset(x, size.height * (0.3f + 0.25f * (i / 2))), size.height * 0.15f, (i - 1.5f) * 14f, theme.accent)
            }
        }
    }
    if (theme.mascot == null) {
        Text("Better Weather", color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
    }
}
