package com.example.betterweather.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.example.betterweather.complication.AllComplicationServices
import com.example.betterweather.core.ProviderId
import com.example.betterweather.core.SettingsStore
import com.example.betterweather.data.providers.ProviderException
import com.example.betterweather.data.providers.httpJson
import com.example.betterweather.data.providers.minRefreshMinutes
import com.example.betterweather.data.providers.providerFor
import com.example.betterweather.tile.WeatherTileService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class Coords(val lat: Double, val lon: Double, val name: String = "")

/**
 * Cache-first weather source.
 *
 * Why the app is instant: the last good snapshot is read from disk synchronously when the process
 * starts, so the first frame already shows real data. Network work happens afterwards, in parallel,
 * with short timeouts, and never blocks the UI, the tile or the complications.
 */
class WeatherRepository private constructor(private val ctx: Context) {
    private val cacheFile = File(ctx.filesDir, "weather_cache.json")
    private val meta = ctx.getSharedPreferences("bw_cache_meta", Context.MODE_PRIVATE)
    private val locPrefs = ctx.getSharedPreferences("bw_location", Context.MODE_PRIVATE)
    private val settings = SettingsStore.get(ctx)
    private val mutex = Mutex()

    private val _snapshot = MutableStateFlow(readCache())
    val snapshot: StateFlow<WeatherSnapshot?> = _snapshot

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private fun readCache(): WeatherSnapshot? =
        runCatching { if (cacheFile.exists()) WeatherSnapshot.fromJson(cacheFile.readText()) else null }.getOrNull()

    private fun writeCache(s: WeatherSnapshot, provider: ProviderId) {
        runCatching {
            val tmp = File(cacheFile.parentFile, "weather_cache.tmp")
            tmp.writeText(s.toJson())
            tmp.renameTo(cacheFile)
        }
        meta.edit().putString("provider", provider.name).apply()
    }

    fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** True when the cached data was made by the selected provider and is recent enough. */
    fun isFresh(maxAgeMs: Long = minRefreshMinutes(settings.current.provider) * 60_000L): Boolean {
        val s = _snapshot.value ?: return false
        if (meta.getString("provider", null) != settings.current.provider.name) return false
        return System.currentTimeMillis() - s.fetchedAtMs < maxAgeMs
    }

    // ---- location -------------------------------------------------------------------------------

    private fun savedCoords(): Coords? =
        if (locPrefs.contains("lat")) Coords(
            locPrefs.getString("lat", "0")!!.toDouble(), locPrefs.getString("lon", "0")!!.toDouble(),
            locPrefs.getString("name", "") ?: "",
        ) else null

    private fun saveCoords(c: Coords) {
        locPrefs.edit().putString("lat", c.lat.toString()).putString("lon", c.lon.toString()).putString("name", c.name).apply()
    }

    @SuppressLint("MissingPermission")
    private suspend fun acquireCoords(): Coords? {
        val saved = savedCoords()
        if (hasLocationPermission()) {
            val client = LocationServices.getFusedLocationProviderClient(ctx)
            // lastLocation answers in tens of milliseconds; only wait briefly for it.
            val last: Location? = runCatching { withTimeoutOrNull(1_500) { client.lastLocation.await() } }.getOrNull()
            if (last != null) {
                val keepName = saved != null && distanceKm(saved.lat, saved.lon, last.latitude, last.longitude) < 5.0
                return Coords(last.latitude, last.longitude, if (keepName) saved!!.name else "").also { saveCoords(it) }
            }
            if (saved != null) return saved
            val fresh: Location? = runCatching {
                withTimeoutOrNull(6_000) {
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
                }
            }.getOrNull()
            if (fresh != null) return Coords(fresh.latitude, fresh.longitude).also { saveCoords(it) }
        }
        if (saved != null) return saved
        // Last resort when location permission is denied: coarse IP-based location.
        return runCatching {
            withTimeoutOrNull(4_000) {
                val o = httpJson("https://ipwho.is/")
                if (!o.optBoolean("success", true)) null
                else Coords(o.getDouble("latitude"), o.getDouble("longitude"), o.optString("city"))
            }
        }.getOrNull()?.also { saveCoords(it) }
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val out = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, out)
        return out[0] / 1000.0
    }

    private suspend fun reverseGeocode(c: Coords): String =
        withTimeoutOrNull(3_000) {
            runCatching {
                val o = httpJson(
                    "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${c.lat}&longitude=${c.lon}&localityLanguage=en"
                )
                o.optString("city").ifBlank { o.optString("locality") }.ifBlank { o.optString("principalSubdivision") }
            }.getOrNull()
        }.orEmpty()

    // ---- refresh --------------------------------------------------------------------------------

    /**
     * Fetches new data if [force] or the cache is stale. Safe to call from anywhere; concurrent
     * calls are coalesced. Returns true if a fresh snapshot was stored.
     */
    suspend fun refresh(force: Boolean = false): Boolean {
        if (!force && isFresh()) return true
        if (!mutex.tryLock()) return false // somebody else is already refreshing
        _refreshing.value = true
        try {
            val s = settings.current
            val coords = acquireCoords()
            if (coords == null) {
                _message.value = if (_snapshot.value == null) "Need location" else null
                return false
            }
            val result = coroutineScope {
                val nameJob = async { if (coords.name.isNotBlank()) coords.name else reverseGeocode(coords) }
                val data = fetchWithFallback(s.provider, coords, s.keyFor(s.provider))
                val name = nameJob.await()
                data?.let { (snap, used) -> Triple(snap, used, name) }
            }
            if (result == null) return false
            val (snap, used, name) = result
            val finalName = snap.locationName.ifBlank { name }
            if (name.isNotBlank() && coords.name.isBlank()) saveCoords(coords.copy(name = name))
            val out = snap.copy(locationName = finalName)
            writeCache(out, used)
            _snapshot.value = out
            requestSurfaceUpdates(ctx)
            return true
        } finally {
            _refreshing.value = false
            mutex.unlock()
        }
    }

    private suspend fun fetchWithFallback(
        preferred: ProviderId, c: Coords, key: String,
    ): Pair<WeatherSnapshot, ProviderId>? {
        val chain = buildList {
            if (preferred != ProviderId.OPEN_METEO && (!preferred.needsKey || key.isNotBlank())) add(preferred to key)
            add(ProviderId.OPEN_METEO to "")
        }
        var firstFailure: String? = null
        for ((id, k) in chain) {
            try {
                val snap = providerFor(id, ctx).fetch(c.lat, c.lon, k)
                _message.value = when {
                    id == preferred -> null
                    preferred.needsKey && key.isBlank() -> "${preferred.label}: add key on phone. Using ${id.label}"
                    else -> "${preferred.label} failed. Using ${id.label}"
                }
                return snap to id
            } catch (e: ProviderException) {
                firstFailure = firstFailure ?: e.message
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                firstFailure = firstFailure ?: e.javaClass.simpleName
            }
        }
        _message.value = if (_snapshot.value != null) "Offline: showing last update" else "Couldn't load weather ($firstFailure)"
        return null
    }

    companion object {
        @Volatile
        private var instance: WeatherRepository? = null

        fun get(context: Context): WeatherRepository =
            instance ?: synchronized(this) {
                instance ?: WeatherRepository(context.applicationContext).also { instance = it }
            }

        /** Ask the tile and every complication to re-render from the cache. */
        fun requestSurfaceUpdates(context: Context) {
            runCatching { TileService.getUpdater(context).requestUpdate(WeatherTileService::class.java) }
            for (cls in AllComplicationServices) {
                runCatching {
                    ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, cls)).requestUpdateAll()
                }
            }
        }
    }
}
