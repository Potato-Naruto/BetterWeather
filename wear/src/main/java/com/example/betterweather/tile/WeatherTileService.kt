package com.example.betterweather.tile

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.example.betterweather.core.IconStyle
import com.example.betterweather.core.SettingsStore
import com.example.betterweather.data.RefreshWorker
import com.example.betterweather.data.WeatherRepository
import com.example.betterweather.data.WeatherSnapshot
import com.example.betterweather.data.fmtTemp
import com.example.betterweather.presentation.MainActivity
import com.example.betterweather.ui.art.IconMode
import com.example.betterweather.ui.art.SceneSpec
import com.example.betterweather.ui.art.drawWeatherScene
import com.example.betterweather.ui.art.renderBitmap
import com.example.betterweather.ui.art.renderIconBitmap
import com.example.betterweather.ui.theme.onSky
import com.example.betterweather.ui.theme.skyColors
import com.example.betterweather.ui.theme.themeSpec
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val BG = "bg"
private fun iconId(i: Int) = "ic$i"

/**
 * The tile is built straight from the cache, so it appears immediately. Tiles cannot run frame
 * animations, so it shows a themed still of the live scene (sky, mascot, stickers) instead.
 */
class WeatherTileService : TileService() {

    private fun dark(): Boolean =
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun darkFor(appearance: com.example.betterweather.core.Appearance) = when (appearance) {
        com.example.betterweather.core.Appearance.SYSTEM -> dark()
        com.example.betterweather.core.Appearance.LIGHT -> false
        com.example.betterweather.core.Appearance.DARK -> true
    }

    private fun snapshotOrSample(): WeatherSnapshot {
        val repo = WeatherRepository.get(this)
        if (!repo.isFresh()) RefreshWorker.refreshSoon(this)
        return repo.snapshot.value ?: WeatherSnapshot.sample()
    }

    private fun version(s: WeatherSnapshot): String {
        val st = SettingsStore.get(this).current
        return "${s.fetchedAtMs}-${st.theme}-${st.iconStyle}-${st.appearance}-${st.units}"
    }

    private fun png(b: Bitmap): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    private fun image(bytes: ByteArray, w: Int, h: Int) = ResourceBuilders.ImageResource.Builder().setInlineResource(
        ResourceBuilders.InlineImageResource.Builder().setData(bytes).setWidthPx(w).setHeightPx(h).build()
    ).build()

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<Resources> {
        val snap = snapshotOrSample()
        val st = SettingsStore.get(this).current
        val theme = themeSpec(st.theme)
        val dm = requestParams.deviceConfiguration
        val wDp = dm.screenWidthDp.coerceAtLeast(150); val hDp = dm.screenHeightDp.coerceAtLeast(150)
        val scale = 1.5f
        val w = (wDp * scale).toInt(); val h = (hDp * scale).toInt()
        val spec = SceneSpec(snap.condition, snap.isDay, theme, darkFor(st.appearance), st.iconStyle == IconStyle.AMOLED)
        val bg = renderBitmap(w, h) { drawWeatherScene(spec, 0.2f) }

        val mode = if (st.iconStyle == IconStyle.AMOLED) IconMode.OUTLINE else IconMode.COLOR
        val b = Resources.Builder().setVersion(version(snap)).addIdToImageMapping(BG, image(png(bg), w, h))
        b.addIdToImageMapping(iconId(9), image(png(renderIconBitmap(snap.condition, snap.isDay, 120, mode, theme)), 120, 120))
        snap.twoHourSteps().forEachIndexed { i, hp ->
            b.addIdToImageMapping(iconId(i), image(png(renderIconBitmap(hp.condition, hp.isDay, 60, mode, theme)), 60, 60))
        }
        return Futures.immediateFuture(b.build())
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val snap = snapshotOrSample()
        val st = SettingsStore.get(this).current
        val theme = themeSpec(st.theme)
        val (_, bottom) = skyColors(theme, snap.condition, snap.isDay, darkFor(st.appearance))
        val fg = if (st.iconStyle == IconStyle.AMOLED) Color.White else onSky(theme, bottom)
        val fgArgb = argb(fg.toArgb())
        val u = st.units

        fun text(s: String, size: Float, bold: Boolean = false) = LayoutElementBuilders.Text.Builder()
            .setText(s).setMaxLines(1)
            .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(sp(size)).setColor(fgArgb)
                .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL).build())
            .build()

        fun img(id: String, size: Float) = LayoutElementBuilders.Image.Builder().setResourceId(id)
            .setWidth(dp(size)).setHeight(dp(size)).build()

        val hero = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(img(iconId(9), 50f))
            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(6f)).build())
            .addContent(text(snap.tempC.fmtTemp(u), 40f, true))
            .build()

        val hourFmt = SimpleDateFormat("ha", Locale.getDefault())
        val strip = LayoutElementBuilders.Row.Builder()
        snap.twoHourSteps().forEachIndexed { i, hp ->
            strip.addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(text(if (i == 0) "Now" else hourFmt.format(Date(hp.timeMs)).lowercase(), 10f))
                    .addContent(img(iconId(i), 24f))
                    .addContent(text(hp.tempC.fmtTemp(u), 12f, true))
                    .build()
            )
            if (i < 3) strip.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(9f)).build())
        }

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(snap.locationName.ifBlank { "Weather" }, 12f))
            .addContent(hero)
            .addContent(text("${snap.description}  H ${snap.highC.fmtTemp(u)} L ${snap.lowC.fmtTemp(u)}", 11f))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
            .addContent(strip.build())
            .build()

        val open = ModifiersBuilders.Clickable.Builder().setId("open").setOnClick(
            ActionBuilders.LaunchAction.Builder().setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder().setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name).build()
            ).build()
        ).build()

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand()).setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open).build())
            .addContent(
                LayoutElementBuilders.Image.Builder().setResourceId(BG).setWidth(expand()).setHeight(expand())
                    .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_CROP).build()
            )
            .addContent(column)
            .build()

        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(version(snap))
                .setFreshnessIntervalMillis(30 * 60_000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
                .build()
        )
    }
}


