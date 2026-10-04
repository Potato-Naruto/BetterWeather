package com.example.betterweather.data

import com.example.betterweather.core.Units
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Condition(val label: String) {
    CLEAR("Clear"),
    PARTLY_CLOUDY("Partly cloudy"),
    CLOUDY("Cloudy"),
    FOG("Fog"),
    DRIZZLE("Drizzle"),
    RAIN("Rain"),
    THUNDERSTORM("Thunderstorm"),
    SNOW("Snow"),
    SLEET("Sleet"),
    WINDY("Windy");

    val isWet get() = this == DRIZZLE || this == RAIN || this == THUNDERSTORM || this == SLEET
}

data class HourPoint(
    val timeMs: Long,
    val tempC: Float,
    val condition: Condition,
    val isDay: Boolean,
    val precipPct: Int,
)

data class DayPoint(
    val dateMs: Long,
    val highC: Float,
    val lowC: Float,
    val condition: Condition,
)

/** Everything the app, tile and complications need, in metric units. Cached as JSON. */
data class WeatherSnapshot(
    val provider: String,
    val locationName: String,
    val lat: Double,
    val lon: Double,
    val fetchedAtMs: Long,
    val tempC: Float,
    val feelsC: Float,
    val condition: Condition,
    val description: String,
    val isDay: Boolean,
    val humidity: Int,
    val windKmh: Float,
    val highC: Float,
    val lowC: Float,
    val hourly: List<HourPoint>,
    val daily: List<DayPoint>,
) {
    /** Nearest hourly entry to [timeMs]; falls back to current conditions when there is no forecast. */
    fun hourNear(timeMs: Long): HourPoint {
        val best = hourly.minByOrNull { abs(it.timeMs - timeMs) }
        return if (best == null || abs(best.timeMs - timeMs) > 4 * 3_600_000L) {
            HourPoint(timeMs, tempC, condition, isDay, 0)
        } else best
    }

    /**
     * Four samples two hours apart, starting with the current hour (now, +2h, +4h, +6h).
     * The first sample is real current conditions so it always agrees with the headline.
     */
    fun twoHourSteps(nowMs: Long = System.currentTimeMillis()): List<HourPoint> {
        val hourStart = nowMs - nowMs % 3_600_000L
        return (0 until 4).map { i ->
            if (i == 0) HourPoint(hourStart, tempC, condition, isDay, hourly.firstOrNull()?.precipPct ?: 0)
            else hourNear(hourStart + i * 2 * 3_600_000L)
        }
    }

    fun toJson(): String = JSONObject().apply {
        put("provider", provider); put("loc", locationName); put("lat", lat); put("lon", lon)
        put("at", fetchedAtMs); put("t", tempC.toDouble()); put("f", feelsC.toDouble())
        put("c", condition.name); put("d", description); put("day", isDay)
        put("h", humidity); put("w", windKmh.toDouble()); put("hi", highC.toDouble()); put("lo", lowC.toDouble())
        put("hourly", JSONArray().also { arr ->
            hourly.forEach { arr.put(JSONObject().put("ts", it.timeMs).put("t", it.tempC.toDouble())
                .put("c", it.condition.name).put("day", it.isDay).put("p", it.precipPct)) }
        })
        put("daily", JSONArray().also { arr ->
            daily.forEach { arr.put(JSONObject().put("ts", it.dateMs).put("hi", it.highC.toDouble())
                .put("lo", it.lowC.toDouble()).put("c", it.condition.name)) }
        })
    }.toString()

    companion object {
        private fun cond(s: String?) = Condition.entries.firstOrNull { it.name == s } ?: Condition.CLEAR

        fun fromJson(json: String): WeatherSnapshot? = runCatching {
            val o = JSONObject(json)
            val hourly = o.getJSONArray("hourly").let { a ->
                List(a.length()) { i ->
                    val h = a.getJSONObject(i)
                    HourPoint(h.getLong("ts"), h.getDouble("t").toFloat(), cond(h.getString("c")),
                        h.getBoolean("day"), h.getInt("p"))
                }
            }
            val daily = o.getJSONArray("daily").let { a ->
                List(a.length()) { i ->
                    val d = a.getJSONObject(i)
                    DayPoint(d.getLong("ts"), d.getDouble("hi").toFloat(), d.getDouble("lo").toFloat(),
                        cond(d.getString("c")))
                }
            }
            WeatherSnapshot(
                provider = o.getString("provider"), locationName = o.getString("loc"),
                lat = o.getDouble("lat"), lon = o.getDouble("lon"), fetchedAtMs = o.getLong("at"),
                tempC = o.getDouble("t").toFloat(), feelsC = o.getDouble("f").toFloat(),
                condition = cond(o.getString("c")), description = o.getString("d"), isDay = o.getBoolean("day"),
                humidity = o.getInt("h"), windKmh = o.getDouble("w").toFloat(),
                highC = o.getDouble("hi").toFloat(), lowC = o.getDouble("lo").toFloat(),
                hourly = hourly, daily = daily,
            )
        }.getOrNull()

        /** Sample data used for previews and before the first fetch. */
        fun sample(condition: Condition = Condition.PARTLY_CLOUDY, isDay: Boolean = true): WeatherSnapshot {
            val now = System.currentTimeMillis()
            val hourStart = now - now % 3_600_000L
            val conds = listOf(condition, condition, Condition.CLOUDY, Condition.RAIN, Condition.RAIN, Condition.CLOUDY, Condition.PARTLY_CLOUDY)
            return WeatherSnapshot(
                provider = "Sample", locationName = "Your location", lat = 0.0, lon = 0.0, fetchedAtMs = now,
                tempC = 22f, feelsC = 21f, condition = condition, description = condition.label, isDay = isDay,
                humidity = 55, windKmh = 12f, highC = 25f, lowC = 15f,
                hourly = List(24) { i ->
                    HourPoint(hourStart + i * 3_600_000L, 22f - i * 0.4f, conds[(i / 2) % conds.size], isDay, (i * 7) % 90)
                },
                daily = List(3) { DayPoint(hourStart + it * 86_400_000L, 25f - it, 15f - it, conds[it]) },
            )
        }
    }
}

// ---- display helpers -------------------------------------------------------------------------

fun Float.toUnit(units: Units): Int =
    (if (units == Units.FAHRENHEIT) this * 9f / 5f + 32f else this).roundToInt()

fun Float.fmtTemp(units: Units): String = "${toUnit(units)}°"
