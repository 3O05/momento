package app.momento.fotos.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.momento.fotos.ui.screens.EditorScreen
import app.momento.fotos.ui.screens.EditorTab
import app.momento.fotos.ui.screens.EditorViewModel
import app.momento.fotos.ui.screens.HomeScreen
import app.momento.fotos.ui.theme.MomentoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun frame(i: Int): Bitmap {
        val b = Bitmap.createBitmap(120, 200, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val hues = listOf(0xFF2B4C7E, 0xFF3F6E8C, 0xFF6C8EA0, 0xFFB08D6E, 0xFFD6A472, 0xFF8C5E58, 0xFF4E3B53, 0xFF2F5D62, 0xFF5E8B7E)
        val p = Paint().apply {
            shader = LinearGradient(0f, 0f, 120f, 200f, hues[i % hues.size].toInt(), hues[(i + 3) % hues.size].toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, 120f, 200f, p)
        c.drawCircle(60f, 120f, 34f, Paint().apply { color = 0x55FFFFFF })
        return b
    }

    @Test
    fun home() {
        rule.setContent { MomentoTheme { HomeScreen(0, {}, {}, {}) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("screenshots/01_inicio.png")
    }

    private fun editor(tab: EditorTab, name: String) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val uri = Uri.parse("file:///tmp/demo.mp4")
        val ds = DataSource.toDataSource(app, uri)
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_DURATION, "24000")
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, "1080")
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, "1920")
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO, "yes")
        val vm = EditorViewModel(app, uri)
        rule.mainClock.autoAdvance = false
        rule.setContent { MomentoTheme { EditorScreen(vm, {}, {}) } }
        rule.mainClock.advanceTimeBy(500)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        vm.setStart(2000); vm.setEnd(5000); vm.setCover(3400)
        vm.onTabSelected(tab)
        for (i in vm.detailThumbs.indices) vm.detailThumbs[i] = frame(i)
        for (i in vm.overviewThumbs.indices) vm.overviewThumbs[i] = frame(i + 2)
        for (i in vm.clipThumbs.indices) vm.clipThumbs[i] = frame(i + 1)
        rule.mainClock.advanceTimeBy(1000)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        rule.mainClock.advanceTimeBy(500)
        rule.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun editorTrim() = editor(EditorTab.TRIM, "02_editor_recorte")
    @Test fun editorCover() = editor(EditorTab.COVER, "03_editor_portada")
    @Test fun editorSettings() = editor(EditorTab.SETTINGS, "04_editor_ajustes")
}
