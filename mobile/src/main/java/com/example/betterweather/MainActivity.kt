package com.example.betterweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.betterweather.core.Appearance
import com.example.betterweather.core.ComplicationMode
import com.example.betterweather.core.IconStyle
import com.example.betterweather.core.ProviderId
import com.example.betterweather.core.Settings
import com.example.betterweather.core.SettingsStore
import com.example.betterweather.core.ThemeId
import com.example.betterweather.core.Units
import com.example.betterweather.ui.theme.BetterWeatherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SettingsStore.get(this)
        setContent {
            BetterWeatherTheme {
                val settings by store.state.collectAsState()
                SettingsScreen(settings) { store.update(it) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize(), topBar = { TopAppBar(title = { Text("Better Weather") }) }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Changes sync to your watch automatically.", style = MaterialTheme.typography.bodyMedium)

            Group("Theme", ThemeId.entries, s.theme, { it.label }) { v -> update { it.copy(theme = v) } }
            Group("Weather icons", IconStyle.entries, s.iconStyle, { it.label }) { v -> update { it.copy(iconStyle = v) } }
            Group("Light / dark", Appearance.entries, s.appearance, { it.label }) { v -> update { it.copy(appearance = v) } }
            Group("Units", Units.entries, s.units, { it.label }) { v -> update { it.copy(units = v) } }
            Group("Complication style", ComplicationMode.entries, s.complicationMode, { it.label }) { v ->
                update { it.copy(complicationMode = v) }
            }
            Group("Weather source", ProviderId.entries, s.provider, { it.label }) { v -> update { it.copy(provider = v) } }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("API keys", style = MaterialTheme.typography.titleMedium)
                    Text("Open-Meteo and the National Weather Service (US only) need no key.", style = MaterialTheme.typography.bodySmall)
                    ProviderId.entries.filter { it.needsKey }.forEach { p ->
                        OutlinedTextField(
                            value = s.keyFor(p), onValueChange = { v -> update { it.withKey(p, v.trim()) } },
                            label = { Text(p.keyHint) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                }
            }
            Text("", Modifier.padding(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Group(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        // Simple wrapping layout without extra dependencies.
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
            }
        }
    }
}
