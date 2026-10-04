package com.example.betterweather.data.providers

import android.content.Context
import com.example.betterweather.core.ProviderId
import com.example.betterweather.data.Condition
import com.example.betterweather.data.DayPoint
import com.example.betterweather.data.HourPoint
import com.example.betterweather.data.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ProviderException(message: String) : Exception(message)

interface WeatherProvider {
    suspend fun fetch(lat: Double, lon: Double, apiKey: String): WeatherSnapshot
}

fun providerFor(id: ProviderId, context: Context): WeatherProvider = when (id) {
    ProviderId.OPEN_METEO -> OpenMeteoProvider
    ProviderId.NWS -> NwsProvider(context.applicationContext)
    ProviderId.OPENWEATHER -> OpenWeatherProvider
    ProviderId.ACCUWEATHER -> AccuWeatherProvider(context.applicationContext)
    ProviderId.WEATHER_COMPANY -> WeatherCompanyProvider
}

/** Providers hit hard limits on free tiers, so fetch less often for the stingier ones (minutes). */
fun minRefreshMinutes(id: ProviderId): Long = when (id) {
    ProviderId.ACCUWEATHER -> 120
    ProviderId.OPENWEATHER -> 30
    else -> 20
}

/** Short timeouts: a slow provider must fail fast so we can fall back instead of hanging for 20 seconds. */
internal suspend fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String =
    withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 7_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "BetterWeather/1.0 (wear os)")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
                throw ProviderException("HTTP $code ${err.take(120)}")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

internal suspend fun httpJson(url: String, headers: Map<String, String> = emptyMap()) = JSONObject(httpGet(url, headers))

/** Strong wind overrides a calm-looking sky so "Windy" actually shows up. */
internal fun withWind(c: Condition, windKmh: Float) =
    if (windKmh >= 45f && (c == Condition.CLEAR || c == Condition.PARTLY_CLOUDY || c == Condition.CLOUDY)) Condition.WINDY else c

private fun startOfDay(ms: Long, tz: TimeZone = TimeZone.getDefault()): Long =
    Calendar.getInstance(tz).apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun isoMs(s: String): Long {
    // Handles "2026-10-04T15:00:00-04:00", "...Z" and "+0000"-style offsets.
    val clean = if (s.length > 5 && s[s.length - 3] == ':' && (s[s.length - 6] == '+' || s[s.length - 6] == '-'))
        s.substring(0, s.length - 3) + s.substring(s.length - 2) else s.replace("Z", "+0000")
    val fmt = if (clean.contains('.')) "yyyy-MM-dd'T'HH:mm:ss.SSSZ" else "yyyy-MM-dd'T'HH:mm:ssZ"
    return SimpleDateFormat(fmt, Locale.US).parse(clean)!!.time
}

// ===== Open-Meteo (no key, worldwide, single request = fastest) ===================================
object OpenMeteoProvider : WeatherProvider {
    private fun wmo(code: Int) = when (code) {
        0, 1 -> Condition.CLEAR
        2 -> Condition.PARTLY_CLOUDY
        3 -> Condition.CLOUDY
        45, 48 -> Condition.FOG
        51, 53, 55 -> Condition.DRIZZLE
        56, 57, 66, 67 -> Condition.SLEET
        61, 63, 65, 80, 81, 82 -> Condition.RAIN
        71, 73, 75, 77, 85, 86 -> Condition.SNOW
        95, 96, 99 -> Condition.THUNDERSTORM
        else -> Condition.CLOUDY
    }

    private fun label(c: Int) = when (c) {
        0 -> "Clear"; 1 -> "Mostly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
        45, 48 -> "Fog"; 51, 53, 55 -> "Drizzle"; 56, 57 -> "Freezing drizzle"
        61 -> "Light rain"; 63 -> "Rain"; 65 -> "Heavy rain"; 66, 67 -> "Freezing rain"
        71 -> "Light snow"; 73 -> "Snow"; 75 -> "Heavy snow"; 77 -> "Snow grains"
        80, 81, 82 -> "Showers"; 85, 86 -> "Snow showers"; 95 -> "Thunderstorm"; 96, 99 -> "Thunderstorm, hail"
        else -> "Cloudy"
    }

    override suspend fun fetch(lat: Double, lon: Double, apiKey: String): WeatherSnapshot {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m" +
            "&hourly=temperature_2m,weather_code,is_day,precipitation_probability" +
            "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
            "&forecast_days=4&forecast_hours=24&timezone=auto&timeformat=unixtime"
        val o = httpJson(url)
        val cur = o.getJSONObject("current")
        val code = cur.getInt("weather_code")
        val wind = cur.optDouble("wind_speed_10m", 0.0).toFloat()
        val h = o.getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val hourly = List(times.length()) { i ->
            HourPoint(
                timeMs = times.getLong(i) * 1000,
                tempC = h.getJSONArray("temperature_2m").getDouble(i).toFloat(),
                condition = wmo(h.getJSONArray("weather_code").getInt(i)),
                isDay = h.getJSONArray("is_day").getInt(i) == 1,
                precipPct = h.optJSONArray("precipitation_probability")?.optInt(i, 0) ?: 0,
            )
        }
        val d = o.getJSONObject("daily")
        val dt = d.getJSONArray("time")
        val daily = List(dt.length()) { i ->
            DayPoint(dt.getLong(i) * 1000, d.getJSONArray("temperature_2m_max").getDouble(i).toFloat(),
                d.getJSONArray("temperature_2m_min").getDouble(i).toFloat(),
                wmo(d.getJSONArray("weather_code").getInt(i)))
        }
        return WeatherSnapshot(
            provider = "Open-Meteo", locationName = "", lat = lat, lon = lon, fetchedAtMs = System.currentTimeMillis(),
            tempC = cur.getDouble("temperature_2m").toFloat(), feelsC = cur.getDouble("apparent_temperature").toFloat(),
            condition = withWind(wmo(code), wind), description = label(code), isDay = cur.getInt("is_day") == 1,
            humidity = cur.getInt("relative_humidity_2m"), windKmh = wind,
            highC = daily.firstOrNull()?.highC ?: cur.getDouble("temperature_2m").toFloat(),
            lowC = daily.firstOrNull()?.lowC ?: cur.getDouble("temperature_2m").toFloat(),
            hourly = hourly, daily = daily,
        )
    }
}

// ===== National Weather Service (no key, US only) =================================================
class NwsProvider(context: Context) : WeatherProvider {
    private val prefs = context.getSharedPreferences("bw_nws", Context.MODE_PRIVATE)

    private fun fromText(t: String, windKmh: Float): Condition {
        val s = t.lowercase(Locale.US)
        val c = when {
            "thunder" in s -> Condition.THUNDERSTORM
            "sleet" in s || "freezing" in s || "wintry" in s || "ice" in s -> Condition.SLEET
            "snow" in s || "flurr" in s || "blizzard" in s -> Condition.SNOW
            "drizzle" in s -> Condition.DRIZZLE
            "rain" in s || "shower" in s -> Condition.RAIN
            "fog" in s || "haze" in s || "smoke" in s || "mist" in s -> Condition.FOG
            "partly" in s || "mostly sunny" in s || "mostly clear" in s || "few clouds" in s -> Condition.PARTLY_CLOUDY
            "cloud" in s || "overcast" in s -> Condition.CLOUDY
            else -> Condition.CLEAR
        }
        return withWind(c, windKmh)
    }

    private fun toC(value: Double, unit: String) = (if (unit == "F") (value - 32) * 5 / 9 else value).toFloat()

    override suspend fun fetch(lat: Double, lon: Double, apiKey: String): WeatherSnapshot = coroutineScope {
        val key = "%.2f,%.2f".format(Locale.US, lat, lon)
        var hourlyUrl = prefs.getString("h_$key", null)
        var dailyUrl = prefs.getString("d_$key", null)
        var city = prefs.getString("c_$key", "") ?: ""
        if (hourlyUrl == null || dailyUrl == null) {
            val p = httpJson("https://api.weather.gov/points/$key").getJSONObject("properties")
            hourlyUrl = p.getString("forecastHourly"); dailyUrl = p.getString("forecast")
            city = p.optJSONObject("relativeLocation")?.optJSONObject("properties")?.optString("city").orEmpty()
            prefs.edit().putString("h_$key", hourlyUrl).putString("d_$key", dailyUrl).putString("c_$key", city).apply()
        }
        val hourlyReq = async { httpJson(hourlyUrl) }
        val dailyReq = async { httpJson(dailyUrl) }
        val hp = hourlyReq.await().getJSONObject("properties").getJSONArray("periods")
        val dp = dailyReq.await().getJSONObject("properties").getJSONArray("periods")

        fun windKmh(s: String) = Regex("\\d+").findAll(s).map { it.value.toFloat() }.maxOrNull()?.times(1.609f) ?: 0f
        val hourly = List(minOf(hp.length(), 24)) { i ->
            val p = hp.getJSONObject(i)
            val w = windKmh(p.optString("windSpeed"))
            HourPoint(
                timeMs = isoMs(p.getString("startTime")),
                tempC = toC(p.getDouble("temperature"), p.optString("temperatureUnit", "F")),
                condition = fromText(p.optString("shortForecast"), w),
                isDay = p.optBoolean("isDaytime", true),
                precipPct = p.optJSONObject("probabilityOfPrecipitation")?.optInt("value", 0) ?: 0,
            )
        }
        // The daily feed alternates day/night periods; pair them up for high/low.
        val days = ArrayList<DayPoint>()
        var i = 0
        while (i < dp.length() && days.size < 4) {
            val p = dp.getJSONObject(i)
            val t = toC(p.getDouble("temperature"), p.optString("temperatureUnit", "F"))
            val next = if (i + 1 < dp.length()) dp.getJSONObject(i + 1) else null
            val nt = next?.let { toC(it.getDouble("temperature"), it.optString("temperatureUnit", "F")) } ?: t
            val daytime = p.optBoolean("isDaytime", true)
            val hi = if (daytime) maxOf(t, nt) else t
            val lo = if (daytime) minOf(t, nt) else t
            days += DayPoint(startOfDay(isoMs(p.getString("startTime"))), hi, lo, fromText(p.optString("shortForecast"), 0f))
            i += if (daytime) 2 else 1
        }
        val now = hourly.first()
        val firstText = hp.getJSONObject(0).optString("shortForecast", "Clear")
        val windNow = windKmh(hp.getJSONObject(0).optString("windSpeed"))
        WeatherSnapshot(
            provider = "NWS", locationName = city, lat = lat, lon = lon, fetchedAtMs = System.currentTimeMillis(),
            tempC = now.tempC, feelsC = now.tempC, condition = now.condition, description = firstText,
            isDay = now.isDay, humidity = hp.getJSONObject(0).optJSONObject("relativeHumidity")?.optInt("value", 0) ?: 0,
            windKmh = windNow,
            highC = days.firstOrNull()?.highC?.coerceAtLeast(now.tempC) ?: now.tempC,
            lowC = days.firstOrNull()?.lowC?.coerceAtMost(now.tempC) ?: now.tempC,
            hourly = hourly, daily = days,
        )
    }
}

// ===== OpenWeather (free 2.5 endpoints: current + 3-hour forecast) ================================
object OpenWeatherProvider : WeatherProvider {
    private fun fromId(id: Int, windKmh: Float): Condition {
        val c = when (id) {
            in 200..299 -> Condition.THUNDERSTORM
            in 300..399 -> Condition.DRIZZLE
            511 -> Condition.SLEET
            in 500..599 -> Condition.RAIN
            in 611..616 -> Condition.SLEET
            in 600..699 -> Condition.SNOW
            771, 781 -> Condition.WINDY
            in 700..799 -> Condition.FOG
            800 -> Condition.CLEAR
            801, 802 -> Condition.PARTLY_CLOUDY
            else -> Condition.CLOUDY
        }
        return withWind(c, windKmh)
    }

    override suspend fun fetch(lat: Double, lon: Double, apiKey: String): WeatherSnapshot = coroutineScope {
        if (apiKey.isBlank()) throw ProviderException("OpenWeather API key missing")
        val base = "https://api.openweathermap.org/data/2.5"
        val q = "lat=$lat&lon=$lon&units=metric&appid=$apiKey"
        val curReq = async { httpJson("$base/weather?$q") }
        val fcReq = async { httpJson("$base/forecast?$q&cnt=24") }
        val cur = curReq.await()
        val fc = fcReq.await().getJSONArray("list")
        val main = cur.getJSONObject("main")
        val w0 = cur.getJSONArray("weather").getJSONObject(0)
        val windKmh = (cur.optJSONObject("wind")?.optDouble("speed", 0.0) ?: 0.0).toFloat() * 3.6f
        val sys = cur.optJSONObject("sys")
        val nowMs = System.currentTimeMillis()
        val isDay = sys == null || !sys.has("sunrise") || (nowMs / 1000 in sys.getLong("sunrise")..sys.getLong("sunset"))
        val tzOffsetMs = cur.optLong("timezone", 0L) * 1000

        val hourly = List(fc.length()) { i ->
            val e = fc.getJSONObject(i)
            HourPoint(
                timeMs = e.getLong("dt") * 1000,
                tempC = e.getJSONObject("main").getDouble("temp").toFloat(),
                condition = fromId(e.getJSONArray("weather").getJSONObject(0).getInt("id"), 0f),
                isDay = e.optJSONObject("sys")?.optString("pod", "d") != "n",
                precipPct = (e.optDouble("pop", 0.0) * 100).toInt(),
            )
        }
        // Group 3-hour slots into local calendar days for high/low.
        val byDay = hourly.groupBy { (it.timeMs + tzOffsetMs) / 86_400_000L }
        val days = byDay.entries.sortedBy { it.key }.map { (day, hs) ->
            DayPoint(day * 86_400_000L - tzOffsetMs, hs.maxOf { it.tempC }, hs.minOf { it.tempC },
                hs.groupingBy { it.condition }.eachCount().maxByOrNull { it.value }!!.key)
        }
        val temp = main.getDouble("temp").toFloat()
        val todays = byDay.entries.minByOrNull { it.key }?.value.orEmpty()
        WeatherSnapshot(
            provider = "OpenWeather", locationName = cur.optString("name"), lat = lat, lon = lon,
            fetchedAtMs = nowMs, tempC = temp, feelsC = main.optDouble("feels_like", temp.toDouble()).toFloat(),
            condition = fromId(w0.getInt("id"), windKmh),
            description = w0.optString("description").replaceFirstChar { it.uppercase() },
            isDay = isDay, humidity = main.optInt("humidity"), windKmh = windKmh,
            highC = maxOf(main.optDouble("temp_max", temp.toDouble()).toFloat(), todays.maxOfOrNull { it.tempC } ?: temp),
            lowC = minOf(main.optDouble("temp_min", temp.toDouble()).toFloat(), todays.minOfOrNull { it.tempC } ?: temp),
            hourly = hourly, daily = days,
        )
    }
}

// ===== AccuWeather (key; 4 requests, location key is cached to save the free-tier quota) ========
class AccuWeatherProvider(context: Context) : WeatherProvider {
    private val prefs = context.getSharedPreferences("bw_accu", Context.MODE_PRIVATE)

    private fun fromIcon(i: Int, windKmh: Float): Condition {
        val c = when (i) {
            1, 2, 30, 31, 33 -> Condition.CLEAR
            3, 4, 5, 34, 35, 36, 37 -> Condition.PARTLY_CLOUDY
            6, 7, 8, 38 -> Condition.CLOUDY
            11 -> Condition.FOG
            12, 13, 14, 18, 39, 40 -> Condition.RAIN
            15, 16, 17, 41, 42 -> Condition.THUNDERSTORM
            19, 20, 21, 22, 23, 43, 44 -> Condition.SNOW
            24, 25, 26, 29 -> Condition.SLEET
            32 -> Condition.WINDY
            else -> Condition.CLOUDY
        }
        return withWind(c, windKmh)
    }

    override suspend fun fetch(lat: Double, lon: Double, apiKey: String): WeatherSnapshot = coroutineScope {
        if (apiKey.isBlank()) throw ProviderException("AccuWeather API key missing")
        val b = "https://dataservice.accuweather.com"
        val cacheKey = "%.2f,%.2f".format(Locale.US, lat, lon)
        var locKey = prefs.getString("k_$cacheKey", null)
        var name = prefs.getString("n_$cacheKey", "") ?: ""
        if (locKey == null) {
            val loc = httpJson("$b/locations/v1/cities/geoposition/search?apikey=$apiKey&q=$lat,$lon")
            locKey = loc.getString("Key"); name = loc.optString("LocalizedName")
            prefs.edit().putString("k_$cacheKey", locKey).putString("n_$cacheKey", name).apply()
        }
        val curReq = async { JSONArray(httpGet("$b/currentconditions/v1/$locKey?apikey=$apiKey&details=true")) }
        val hrReq = async { JSONArray(httpGet("$b/forecasts/v1/hourly/12hour/$locKey?apikey=$apiKey&metric=true")) }
        val dyReq = async { httpJson("$b/forecasts/v1/daily/5day/$locKey?apikey=$apiKey&metric=true") }
        val cur = curReq.await().getJSONObject(0)
        val wind = cur.optJSONObject("Wind")?.optJSONObject("Speed")?.optJSONObject("Metric")?.optDouble("Value", 0.0)?.toFloat() ?: 0f
        val temp = cur.getJSONObject("Temperature").getJSONObject("Metric").getDouble("Value").toFloat()
        val hr = hrReq.await()
        val hourly = List(hr.length()) { i ->
            val e = hr.getJSONObject(i)
            HourPoint(isoMs(e.getString("DateTime")), e.getJSONObject("Temperature").getDouble("Value").toFloat(),
                fromIcon(e.getInt("WeatherIcon"), 0f), e.optBoolean("IsDaylight", true), e.optInt("PrecipitationProbability", 0))
        }
        val dys = dyReq.await().getJSONArray("DailyForecasts")
        val daily = List(dys.length()) { i ->
            val e = dys.getJSONObject(i)
            val t = e.getJSONObject("Temperature")
            DayPoint(isoMs(e.getString("Date")), t.getJSONObject("Maximum").getDouble("Value").toFloat(),
                t.getJSONObject("Minimum").getDouble("Value").toFloat(),
                fromIcon(e.getJSONObject("Day").getInt("Icon"), 0f))
        }
        WeatherSnapshot(
            provider = "AccuWeather", locationName = name, lat = lat, lon = lon, fetchedAtMs = System.currentTimeMillis(),
            tempC = temp,
            feelsC = cur.optJSONObject("RealFeelTemperature")?.optJSONObject("Metric")?.optDouble("Value", temp.toDouble())?.toFloat() ?: temp,
            condition = fromIcon(cur.getInt("WeatherIcon"), wind), description = cur.optString("WeatherText"),
            isDay = cur.optBoolean("IsDayTime", true), humidity = cur.optInt("RelativeHumidity"), windKmh = wind,
            highC = maxOf(daily.firstOrNull()?.highC ?: temp, temp), lowC = minOf(daily.firstOrNull()?.lowC ?: temp, temp),
            hourly = hourly, daily = daily,
        )
    }
}

// ===== The Weather Company (weather.com, key) =====================================================
object WeatherCompanyProvider : WeatherProvider {
    private fun fromIcon(i: Int, windKmh: Float): Condition {
        val c = when (i) {
            0, 1, 2, 23, 24 -> Condition.WINDY
            3, 4, 37, 38, 47 -> Condition.THUNDERSTORM
            5, 6, 7, 8, 10, 17, 18, 35 -> Condition.SLEET
            9 -> Condition.DRIZZLE
            11, 12, 39, 40, 45 -> Condition.RAIN
            13, 14, 15, 16, 41, 42, 43, 46 -> Condition.SNOW
            19, 20, 21, 22 -> Condition.FOG
            26, 27, 28 -> Condition.CLOUDY
            29, 30 -> Condition.PARTLY_CLOUDY
            else -> Condition.CLEAR
        }
        return withWind(c, windKmh)
    }

    override suspend fun fetch(lat: Double, lon: Double, apiKey: String): WeatherSnapshot = coroutineScope {
        if (apiKey.isBlank()) throw ProviderException("Weather Company API key missing")
        val q = "geocode=$lat,$lon&units=m&language=en-US&format=json&apiKey=$apiKey"
        val b = "https://api.weather.com/v3/wx"
        val curReq = async { httpJson("$b/observations/current?$q") }
        val hrReq = async { httpJson("$b/forecast/hourly/2day?$q") }
        val dyReq = async { httpJson("$b/forecast/daily/5day?$q") }
        val cur = curReq.await(); val hr = hrReq.await(); val dy = dyReq.await()
        val wind = cur.optDouble("windSpeed", 0.0).toFloat()
        val temp = cur.getDouble("temperature").toFloat()
        val times = hr.getJSONArray("validTimeLocal")
        val hourly = List(minOf(times.length(), 24)) { i ->
            HourPoint(isoMs(times.getString(i)), hr.getJSONArray("temperature").getDouble(i).toFloat(),
                fromIcon(hr.getJSONArray("iconCode").getInt(i), 0f),
                hr.getJSONArray("dayOrNight").getString(i) == "D", hr.optJSONArray("precipChance")?.optInt(i, 0) ?: 0)
        }
        val dTimes = dy.getJSONArray("validTimeLocal")
        val dIcons = dy.optJSONArray("daypart")?.optJSONObject(0)?.optJSONArray("iconCode")
        val daily = List(dTimes.length()) { i ->
            DayPoint(isoMs(dTimes.getString(i)),
                dy.getJSONArray("calendarDayTemperatureMax").optDouble(i, temp.toDouble()).toFloat(),
                dy.getJSONArray("calendarDayTemperatureMin").optDouble(i, temp.toDouble()).toFloat(),
                // daypart arrays hold [day0, night0, day1, night1, ...]; entries can be null.
                fromIcon(dIcons?.optInt(i * 2, 32) ?: 32, 0f))
        }
        WeatherSnapshot(
            provider = "The Weather Company", locationName = "", lat = lat, lon = lon,
            fetchedAtMs = System.currentTimeMillis(), tempC = temp,
            feelsC = cur.optDouble("temperatureFeelsLike", temp.toDouble()).toFloat(),
            condition = fromIcon(cur.optInt("iconCode", 32), wind), description = cur.optString("wxPhraseLong", "Weather"),
            isDay = cur.optString("dayOrNight", "D") == "D", humidity = cur.optInt("relativeHumidity"), windKmh = wind,
            highC = maxOf(daily.firstOrNull()?.highC ?: temp, temp), lowC = minOf(daily.firstOrNull()?.lowC ?: temp, temp),
            hourly = hourly, daily = daily,
        )
    }
}
