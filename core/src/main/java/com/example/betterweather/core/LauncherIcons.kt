package com.example.betterweather.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Swaps the launcher icon to match the theme. Each theme has an <activity-alias> in the app manifest
 * (com.example.betterweather.alias.<Theme>); exactly one is enabled at a time.
 */
object LauncherIcons {
    private fun alias(ctx: Context, t: ThemeId) = ComponentName(
        ctx.packageName, "com.example.betterweather.alias." + t.name.lowercase().replaceFirstChar { it.uppercase() },
    )

    fun apply(ctx: Context, theme: ThemeId) {
        runCatching {
            val pm = ctx.packageManager
            fun isOn(t: ThemeId): Boolean {
                val s = pm.getComponentEnabledSetting(alias(ctx, t))
                return s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    (s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && t == ThemeId.CLASSIC)
            }
            if (isOn(theme)) {
                // already correct; make sure no other alias is also on
                ThemeId.entries.filter { it != theme && isOn(it) }.forEach { set(pm, ctx, it, false) }
                return
            }
            set(pm, ctx, theme, true) // enable first so the app never has zero launcher entries
            ThemeId.entries.filter { it != theme }.forEach { set(pm, ctx, it, false) }
        }
    }

    private fun set(pm: PackageManager, ctx: Context, t: ThemeId, on: Boolean) {
        pm.setComponentEnabledSetting(
            alias(ctx, t),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
