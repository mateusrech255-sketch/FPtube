package com.example.fptube

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.FileOutputStream
import androidx.core.content.res.ResourcesCompat
import br.com.fpagency.fptube.R

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class SaveLogoRobolectricTest {
    @Test
    fun saveLogo() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val drawable = ResourcesCompat.getDrawable(context.resources, R.mipmap.ic_launcher, null)!!
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, 512, 512)
        drawable.draw(canvas)
        val out = FileOutputStream("/home/fpagency/Downloads/fptube_logo.png")
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.close()
    }
}
