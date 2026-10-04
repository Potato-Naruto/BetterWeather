package com.example.betterweather.core

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeId(val label: String) {
    CLASSIC("Classic"),
    CINNAMOROLL("Cinnamoroll"),
    MOCHA("Mocha"),
    ESPRESSO("Espresso"),
}

enum class IconStyle(val label: String) {
    ANIMATED("Animated"),
    AMOLED("Black & white"),
    STATIC("Still"),
}

enum class Appearance(val label: String) {
    SYSTEM("Same as system"),
    LIGHT("Light"),
    DARK("Dark"),
}

enum class Units(val label: String) { FAHRENHEIT("°F"), CELSIUS("°C") }

enum class ProviderId(val label: String, val needsKey: Boolean, val keyHint: String) {
    OPEN_METEO("Open-Meteo", false, ""),
    NWS("National Weather Service (US)", false, ""),
    OPENWEATHER("OpenWeather", true, "OpenWeather API key"),
    ACCUWEATHER("AccuWeather", true, "AccuWeather API key"),
    WEATHER_COMPANY("The Weather Company", true, "weather.com API key"),
}

enum class ComplicationMode(val label: String) {
    HIGH_LOW_WEATHER("High / Low / Weather"),
    HIGH_LOW("High / Low"),
    FORECAST_2H("Next 6h, every 2h"),
}

data class Settings(
    val theme: ThemeId = ThemeId.CLASSIC,
    val iconStyle: IconStyle = IconStyle.ANIMATED,
    val appearance: Appearance = Appearance.SYSTEM,
    val units: Units = Units.FAHRENHEIT,
    val provider: ProviderId = ProviderId.OPEN_METEO,
    val complicationMode: ComplicationMode = ComplicationMode.HIGH_LOW_WEATHER,
    val openWeatherKey: String = "",
    val accuWeatherKey: String = "",
    val weatherCompanyKey: String = "",
    /** Epoch millis of the last local change; the newest edit wins when syncing. */
    val modifiedAt: Long = 0L,
) {
    fun keyFor(p: ProviderId): String = when (p) {
        ProviderId.OPENWEATHER -> openWeatherKey
        ProviderId.ACCUWEATHER -> accuWeatherKey
        ProviderId.WEATHER_COMPANY -> weatherCompanyKey
        else -> ""
    }

    fun withKey(p: ProviderId, key: String): Settings = when (p) {
        ProviderId.OPENWEATHER -> copy(openWeatherKey = key)
        ProviderId.ACCUWEATHER -> copy(accuWeatherKey = key)
        ProviderId.WEATHER_COMPANY -> copy(weatherCompanyKey = key)
        else -> this
    }

    /** A provider is usable when it needs no key or the key has been entered. */
    fun isUsable(p: ProviderId): Boolean = !p.needsKey || keyFor(p).isNotBlank()
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    enumValues<E>().firstOrNull { it.name == this } ?: default

/**
 * Settings shared by the phone and the watch. Stored locally in SharedPreferences and mirrored to the
 * paired device through the Wearable Data Layer. Reads are synchronous and cheap so the UI,
 * tile and complications never wait on I/O.
 */
class SettingsStore private constructor(private val appContext: Context) {
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("bw_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())

    init {
        // Keep the launcher icon in step with the saved theme (also covers first run).
        LauncherIcons.apply(appContext, _state.value.theme)
    }
    val state: StateFlow<Settings> = _state

    /** Called after settings arrive from the other device (e.g. to refresh tiles/complications). */
    @Volatile
    var onRemoteApplied: ((Settings) -> Unit)? = null

    val current: Settings get() = _state.value

    fun update(transform: (Settings) -> Settings) {
        val before = _state.value
        val next = transform(before).copy(modifiedAt = System.currentTimeMillis())
        save(next)
        _state.value = next
        if (next.theme != before.theme) LauncherIcons.apply(appContext, next.theme)
        pushToPeer(next)
    }

    internal fun applyRemote(remote: Settings) {
        if (remote.modifiedAt <= _state.value.modifiedAt) return
        val themeChanged = remote.theme != _state.value.theme
        save(remote)
        _state.value = remote
        if (themeChanged) LauncherIcons.apply(appContext, remote.theme)
        onRemoteApplied?.invoke(remote)
    }

    private fun load() = Settings(
        theme = prefs.getString("theme", null).toEnum(ThemeId.CLASSIC),
        iconStyle = prefs.getString("iconStyle", null).toEnum(IconStyle.ANIMATED),
        appearance = prefs.getString("appearance", null).toEnum(Appearance.SYSTEM),
        units = prefs.getString("units", null).toEnum(Units.FAHRENHEIT),
        provider = prefs.getString("provider", null).toEnum(ProviderId.OPEN_METEO),
        complicationMode = prefs.getString("complicationMode", null).toEnum(ComplicationMode.HIGH_LOW_WEATHER),
        openWeatherKey = prefs.getString("owKey", "") ?: "",
        accuWeatherKey = prefs.getString("accuKey", "") ?: "",
        weatherCompanyKey = prefs.getString("twcKey", "") ?: "",
        modifiedAt = prefs.getLong("modifiedAt", 0L),
    )

    private fun save(s: Settings) {
        prefs.edit()
            .putString("theme", s.theme.name)
            .putString("iconStyle", s.iconStyle.name)
            .putString("appearance", s.appearance.name)
            .putString("units", s.units.name)
            .putString("provider", s.provider.name)
            .putString("complicationMode", s.complicationMode.name)
            .putString("owKey", s.openWeatherKey)
            .putString("accuKey", s.accuWeatherKey)
            .putString("twcKey", s.weatherCompanyKey)
            .putLong("modifiedAt", s.modifiedAt)
            .apply()
    }

    private fun pushToPeer(s: Settings) {
        runCatching {
            val req = PutDataMapRequest.create(PATH).apply {
                dataMap.apply {
                    putString("theme", s.theme.name)
                    putString("iconStyle", s.iconStyle.name)
                    putString("appearance", s.appearance.name)
                    putString("units", s.units.name)
                    putString("provider", s.provider.name)
                    putString("complicationMode", s.complicationMode.name)
                    putString("owKey", s.openWeatherKey)
                    putString("accuKey", s.accuWeatherKey)
                    putString("twcKey", s.weatherCompanyKey)
                    putLong("modifiedAt", s.modifiedAt)
                }
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(appContext).putDataItem(req)
        }
    }

    companion object {
        const val PATH = "/bw/settings"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}

class SettingsListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val store = SettingsStore.get(this)
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED || event.dataItem.uri.path != SettingsStore.PATH) continue
            val m = DataMapItem.fromDataItem(event.dataItem).dataMap
            store.applyRemote(
                Settings(
                    theme = m.getString("theme").toEnum(ThemeId.CLASSIC),
                    iconStyle = m.getString("iconStyle").toEnum(IconStyle.ANIMATED),
                    appearance = m.getString("appearance").toEnum(Appearance.SYSTEM),
                    units = m.getString("units").toEnum(Units.FAHRENHEIT),
                    provider = m.getString("provider").toEnum(ProviderId.OPEN_METEO),
                    complicationMode = m.getString("complicationMode").toEnum(ComplicationMode.HIGH_LOW_WEATHER),
                    openWeatherKey = m.getString("owKey") ?: "",
                    accuWeatherKey = m.getString("accuKey") ?: "",
                    weatherCompanyKey = m.getString("twcKey") ?: "",
                    modifiedAt = m.getLong("modifiedAt"),
                )
            )
        }
    }
}
