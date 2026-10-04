package com.example.betterweather.tools

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import com.example.betterweather.core.ThemeId
import com.example.betterweather.ui.art.drawLogoBackground
import com.example.betterweather.ui.art.drawLogoForeground
import com.example.betterweather.ui.art.renderBitmap
import java.io.File

/**
 * Regenerates the launcher icon layers:
 *   adb shell am start -n com.example.betterweather/.tools.IconExportActivity
 *   adb pull /sdcard/Android/data/com.example.betterweather/files/icons <dest>
 * Copy the PNGs into core/src/main/res/drawable-nodpi/.
 */
class IconExportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dir = File(getExternalFilesDir(null), "icons").apply { mkdirs() }
        for (t in ThemeId.entries) {
            val name = t.name.lowercase()
            save(renderBitmap(432, 432) { drawLogoBackground(t) }, File(dir, "ic_bg_$name.png"))
            save(renderBitmap(432, 432) { drawLogoForeground(t) }, File(dir, "ic_fg_$name.png"))
        }
        finish()
    }

    private fun save(b: Bitmap, f: File) = f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
