package com.example.betterweather

import android.app.Application
import com.example.betterweather.core.SettingsStore
import com.example.betterweather.data.RefreshWorker
import com.example.betterweather.data.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class BetterWeatherApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val settings = SettingsStore.get(this)
        // Touching the repository loads the disk cache now, before any UI exists.
        val repo = WeatherRepository.get(this)
        com.example.betterweather.ui.art.MascotArt.load(this)
        RefreshWorker.schedulePeriodic(this)

        // Any settings change (made on the watch or synced from the phone) re-renders tile + complications,
        // and re-fetches when the data source (provider / its key) changed.
        scope.launch {
            settings.state.drop(1).collect { WeatherRepository.requestSurfaceUpdates(this@BetterWeatherApp) }
        }
        scope.launch {
            settings.state.map { it.provider to it.keyFor(it.provider) }.distinctUntilChanged().drop(1)
                .collect { repo.refresh(force = true) }
        }
    }
}
