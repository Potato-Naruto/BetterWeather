package com.example.betterweather.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.example.betterweather.core.ComplicationMode
import com.example.betterweather.core.IconStyle
import com.example.betterweather.core.SettingsStore
import com.example.betterweather.core.Units
import com.example.betterweather.data.Condition
import com.example.betterweather.data.RefreshWorker
import com.example.betterweather.data.WeatherRepository
import com.example.betterweather.data.WeatherSnapshot
import com.example.betterweather.data.fmtTemp
import com.example.betterweather.presentation.MainActivity
import com.example.betterweather.ui.art.IconMode
import com.example.betterweather.ui.art.renderIconBitmap
import com.example.betterweather.ui.theme.themeSpec

/** Emoji glyphs let a single text line carry four forecast "pictures" (e.g. on the Ultra analog face). */
fun conditionGlyph(c: Condition, isDay: Boolean): String = when (c) {
    Condition.CLEAR -> if (isDay) "☀️" else "🌙"
    Condition.PARTLY_CLOUDY -> if (isDay) "⛅" else "☁️"
    Condition.CLOUDY -> "☁️"
    Condition.FOG -> "🌫️"
    Condition.DRIZZLE -> "🌦️"
    Condition.RAIN -> "🌧️"
    Condition.THUNDERSTORM -> "⛈️"
    Condition.SNOW -> "🌨️"
    Condition.SLEET -> "🌨️"
    Condition.WINDY -> "💨"
}

/** One short word for the "H / L / weather" line. */
fun conditionWord(c: Condition, isDay: Boolean): String = when (c) {
    Condition.CLEAR -> if (isDay) "Sunny" else "Clear"
    Condition.PARTLY_CLOUDY -> "Partly"
    Condition.CLOUDY -> "Cloudy"
    Condition.FOG -> "Fog"
    Condition.DRIZZLE -> "Drizzle"
    Condition.RAIN -> "Rain"
    Condition.THUNDERSTORM -> "Storms"
    Condition.SNOW -> "Snow"
    Condition.SLEET -> "Sleet"
    Condition.WINDY -> "Windy"
}

/**
 * Base class: answers instantly from the on-disk cache and never touches the network. If the cache is
 * stale it schedules a background refresh, which then asks us to update again.
 */
abstract class BaseWeatherComplication : SuspendingComplicationDataSourceService() {
    /** Which layout to show; the "configurable" variant follows the in-app setting. */
    abstract fun modeNow(): ComplicationMode

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, WeatherSnapshot.sample(Condition.PARTLY_CLOUDY), modeNow(), Units.FAHRENHEIT, IconStyle.ANIMATED)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val repo = WeatherRepository.get(this)
        val settings = SettingsStore.get(this).current
        if (!repo.isFresh()) RefreshWorker.refreshSoon(this)
        val snap = repo.snapshot.value ?: return noData(request.complicationType)
        return build(request.complicationType, snap, modeNow(), settings.units, settings.iconStyle, settings.theme)
    }

    private fun noData(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(text("Loading…"), text("Loading weather")).build()
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text("--°"), text("Loading weather")).build()
        else -> null
    }

    private fun text(s: String) = PlainComplicationText.Builder(s).build()

    private fun tap() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun icon(c: Condition, day: Boolean, mono: Boolean, style: IconStyle, theme: com.example.betterweather.core.ThemeId): Icon {
        val mode = if (mono) IconMode.SILHOUETTE else if (style == IconStyle.AMOLED) IconMode.OUTLINE else IconMode.COLOR
        return Icon.createWithBitmap(renderIconBitmap(c, day, 96, mode, themeSpec(theme)))
    }

    private fun build(
        type: ComplicationType, s: WeatherSnapshot, mode: ComplicationMode, u: Units, style: IconStyle,
        theme: com.example.betterweather.core.ThemeId = com.example.betterweather.core.ThemeId.CLASSIC,
    ): ComplicationData? {
        val monoIcon = MonochromaticImage.Builder(icon(s.condition, s.isDay, true, style, theme)).build()
        val colorIcon = SmallImage.Builder(icon(s.condition, s.isDay, false, style, theme), SmallImageType.ICON).build()
        val hl = "H ${s.highC.fmtTemp(u)}  L ${s.lowC.fmtTemp(u)}"
        val desc = "${s.tempC.fmtTemp(u)}, ${s.description}. High ${s.highC.fmtTemp(u)}, low ${s.lowC.fmtTemp(u)}"

        return when (type) {
            ComplicationType.LONG_TEXT -> when (mode) {
                ComplicationMode.HIGH_LOW_WEATHER ->
                    // Single line: "H 75° / L 61° / Rain" (no title, so the face keeps it on one line).
                    LongTextComplicationData.Builder(
                        text("H ${s.highC.fmtTemp(u)} / L ${s.lowC.fmtTemp(u)} / ${conditionWord(s.condition, s.isDay)}"), text(desc))
                        .setMonochromaticImage(monoIcon).setSmallImage(colorIcon).setTapAction(tap()).build()

                ComplicationMode.HIGH_LOW ->
                    LongTextComplicationData.Builder(text(hl), text(desc))
                        .setTitle(text(s.locationName.ifBlank { "Today" })).setTapAction(tap()).build()

                ComplicationMode.FORECAST_2H -> {
                    val steps = s.twoHourSteps()
                    val line = steps.joinToString(" ") { "${conditionGlyph(it.condition, it.isDay)}${it.tempC.fmtTemp(u)}" }
                    LongTextComplicationData.Builder(text(line),
                        text("Forecast every 2 hours: " + steps.joinToString { "${it.condition.label} ${it.tempC.fmtTemp(u)}" }))
                        .setTitle(text("Now · +2h · +4h · +6h")).setTapAction(tap()).build()
                }
            }

            ComplicationType.SHORT_TEXT -> {
                val (main, title) = when (mode) {
                    ComplicationMode.HIGH_LOW_WEATHER -> s.tempC.fmtTemp(u) to null
                    ComplicationMode.HIGH_LOW -> "${s.highC.fmtTemp(u)}" to "L ${s.lowC.fmtTemp(u)}"
                    ComplicationMode.FORECAST_2H -> s.twoHourSteps()[1].let { it.tempC.fmtTemp(u) to "+2h" }
                }
                ShortTextComplicationData.Builder(text(main), text(desc)).apply {
                    title?.let { setTitle(text(it)) }
                    if (mode != ComplicationMode.HIGH_LOW) setMonochromaticImage(monoIcon)
                    setTapAction(tap())
                }.build()
            }

            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData.Builder(colorIcon, text(desc)).setTapAction(tap()).build()

            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(monoIcon, text(desc)).setTapAction(tap()).build()

            else -> null
        }
    }
}

/** Follows the "Complication" choice in the watch / phone settings. */
class WeatherComplicationService : BaseWeatherComplication() {
    override fun modeNow() = SettingsStore.get(this).current.complicationMode
}

/** Fixed layouts, so different slots on a face can each show a different view at once. */
class HighLowWeatherComplicationService : BaseWeatherComplication() {
    override fun modeNow() = ComplicationMode.HIGH_LOW_WEATHER
}

class HighLowComplicationService : BaseWeatherComplication() {
    override fun modeNow() = ComplicationMode.HIGH_LOW
}

class ForecastComplicationService : BaseWeatherComplication() {
    override fun modeNow() = ComplicationMode.FORECAST_2H
}

val AllComplicationServices: List<Class<out BaseWeatherComplication>> = listOf(
    WeatherComplicationService::class.java,
    HighLowWeatherComplicationService::class.java,
    HighLowComplicationService::class.java,
    ForecastComplicationService::class.java,
)
