package com.example.betterweather.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.betterweather.core.SettingsStore
import com.example.betterweather.data.UpdateStatus
import com.example.betterweather.data.Updater
import com.example.betterweather.data.WeatherRepository
import com.example.betterweather.ui.screens.HomeScreen
import com.example.betterweather.ui.screens.SettingsScreen
import com.example.betterweather.ui.screens.UiModel
import com.example.betterweather.ui.theme.BetterWeatherTheme
import com.example.betterweather.ui.theme.resolveDark
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var ambient by mutableStateOf(false)
    private var batteryUnrestricted by mutableStateOf(false)

    private val askBattery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        batteryUnrestricted = isIgnoringBatteryOptimizations()
    }

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        lifecycleScope.launch { WeatherRepository.get(this@MainActivity).refresh(force = true) }
        maybeAskBatteryOnce()
    }

    private fun isIgnoringBatteryOptimizations() =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    /** Opens the system "let this app run in the background" prompt. */
    @SuppressLint("BatteryLife")
    private fun requestBatteryUnrestricted() {
        if (isIgnoringBatteryOptimizations()) return
        val direct = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        try {
            askBattery.launch(direct)
        } catch (e: Exception) {
            runCatching { askBattery.launch(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    /** Asked automatically one time; afterwards it's available as a row in Settings. */
    private fun maybeAskBatteryOnce() {
        val prefs = getSharedPreferences("bw_ui", MODE_PRIVATE)
        if (prefs.getBoolean("asked_battery", false) || isIgnoringBatteryOptimizations()) return
        prefs.edit().putBoolean("asked_battery", true).apply()
        requestBatteryUnrestricted()
    }

    override fun onResume() {
        super.onResume()
        batteryUnrestricted = isIgnoringBatteryOptimizations()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = WeatherRepository.get(this)

        lifecycle.addObserver(AmbientLifecycleObserver(this, object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) { ambient = true }
            override fun onExitAmbient() { ambient = false }
        }))

        if (!repo.hasLocationPermission()) {
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        } else {
            maybeAskBatteryOnce()
        }

        // The cached snapshot is already on screen; refresh quietly if it is stale.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { repo.refresh(force = false) }
        }

        setContent {
            val nav = rememberSwipeDismissableNavController()
            BetterWeatherTheme {
                AppScaffold {
                    // NOTE: each destination builds its own UiModel. The nav graph's lambdas are remembered,
                    // so anything captured from out here would be stale and settings would only appear to
                    // apply after leaving the screen.
                    SwipeDismissableNavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(rememberUi(), onSettings = { nav.navigate("settings") },
                                onRefresh = { lifecycleScope.launch { repo.refresh(force = true) } })
                        }
                        composable("settings") {
                            SettingsScreen(rememberUi(), onChange = { f -> SettingsStore.get(this@MainActivity).update(f) },
                                onRefresh = { lifecycleScope.launch { repo.refresh(force = true) } },
                                onBattery = { requestBatteryUnrestricted() },
                                onUpdate = {
                                    lifecycleScope.launch {
                                        val st = Updater.status.value
                                        if (st is UpdateStatus.Available) Updater.downloadAndInstall(this@MainActivity, st)
                                        else Updater.check(this@MainActivity)
                                    }
                                },
                                onBack = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun rememberUi(): UiModel {
        val repo = WeatherRepository.get(this)
        val settings by SettingsStore.get(this).state.collectAsState()
        val snapshot by repo.snapshot.collectAsState()
        val refreshing by repo.refreshing.collectAsState()
        val message by repo.message.collectAsState()
        val update by Updater.status.collectAsState()
        val dark = resolveDark(settings.appearance, isSystemInDarkTheme())
        return UiModel(settings, snapshot, refreshing, message, dark, ambient, batteryUnrestricted,
            update, Updater.installedVersion(this))
    }
}
