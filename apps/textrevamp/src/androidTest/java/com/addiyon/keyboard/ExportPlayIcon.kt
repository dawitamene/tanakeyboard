package com.addiyon.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Throwaway helper: renders drawable/ic_addiyon_icon.xml to a 512x512 PNG for the
 * Google Play store icon. Run with:
 *   ./gradlew connectedDebugAndroidTest --tests "*.ExportPlayIcon"
 * Then pull:
 *   adb pull /sdcard/Android/data/com.addiyon.keyboard/files/play_icon_512.png
 */
@RunWith(AndroidJUnit4::class)
class ExportPlayIcon {
    @Test
    fun export() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val drawable = ContextCompat.getDrawable(ctx, R.drawable.ic_addiyon_icon)!!
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 512, 512)
        drawable.draw(Canvas(bmp))
        val out = File(ctx.getExternalFilesDir(null), "play_icon_512.png")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Wrote ${out.absolutePath}")
    }
}
