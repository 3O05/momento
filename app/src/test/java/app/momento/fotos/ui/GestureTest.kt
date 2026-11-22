package app.momento.fotos.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import app.momento.fotos.ui.screens.EditorScreen
import app.momento.fotos.ui.screens.EditorTab
import app.momento.fotos.ui.screens.EditorViewModel
import app.momento.fotos.ui.theme.MomentoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class GestureTest {
    @get:Rule val rule = createComposeRule()

    private fun run(durationMs: Long) {
        ShadowLog.clear()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val uri = Uri.parse("file:///tmp/g$durationMs.mp4")
        val ds = DataSource.toDataSource(app, uri)
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_DURATION, "$durationMs")
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, "1920")
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, "1080")
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION, "90")
        val vm = EditorViewModel(app, uri)
        rule.mainClock.autoAdvance = false
        rule.setContent { MomentoTheme { EditorScreen(vm, {}, {}) } }
        rule.mainClock.advanceTimeBy(300)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        rule.mainClock.advanceTimeBy(300)

        val sizes = rule.onRoot().fetchSemanticsNode().size
        val w = sizes.width.toFloat(); val h = sizes.height.toFloat()
        // Recorre la zona inferior con muchos arrastres horizontales a distintas alturas.
        val ys = (0 until 30).map { h * (0.66f + it * 0.009f) }
        val xs = listOf(0.02f, 0.06f, 0.12f, 0.2f, 0.35f, 0.5f, 0.8f, 0.97f)
        for (tab in EditorTab.entries) {
            vm.onTabSelected(tab)
            rule.mainClock.advanceTimeBy(400)
            for (y in ys) for (x0 in xs) {
                for (x1 in listOf(0.0f, 0.5f, 1.0f)) {
                    rule.onRoot().performTouchInput { swipe(Offset(w * x0, y), Offset(w * x1, y), 120) }
                    rule.mainClock.advanceTimeBy(40)
                }
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        }
        val errors = ShadowLog.getLogsForTag("Momento")
        errors.forEach { println("ERROR CAPTURADO: ${it.msg} ${it.throwable}"); it.throwable?.printStackTrace() }
        assertTrue("Excepciones en gestos: ${errors.size}", errors.isEmpty())
        assertTrue(vm.endMs - vm.startMs in EditorViewModel.MIN_CLIP..EditorViewModel.MAX_CLIP)
        vm.release()
    }

    @Test fun shortVideo() = run(2_000)
    @Test fun normalVideo() = run(9_000)
    @Test fun longVideo() = run(185_000)
    @Test fun tinyVideo() = run(700)
}
