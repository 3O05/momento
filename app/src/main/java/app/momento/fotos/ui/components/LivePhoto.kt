package app.momento.fotos.ui.components

import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.momento.fotos.ui.theme.Palette
import kotlinx.coroutines.delay
import java.io.File

/**
 * Muestra una foto viva: la foto fija y, al mantener presionado (o al abrir), reproduce el clip
 * y vuelve a la foto al terminar. Igual que en la galería del teléfono.
 */
@OptIn(UnstableApi::class)
@Composable
fun LivePhoto(
    cover: ImageBitmap?,
    video: File?,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
) {
    val context = LocalContext.current
    var showVideo by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }

    val player = remember(video) {
        video?.let { f ->
            ExoPlayer.Builder(context)
                .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .build().apply {
                setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(f)))
                prepare()
                val self = this
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            playing = false; showVideo = false
                            self.pause(); self.seekTo(0) // deja listo el primer fotograma
                        }
                    }
                })
            }
        }
    }
    DisposableEffect(player) { onDispose { player?.stop(); player?.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        player?.pause(); playing = false; showVideo = false
    }

    fun play() {
        val p = player ?: return
        playing = true
        p.seekTo(0); p.play()
        showVideo = true
    }

    LaunchedEffect(player) {
        if (autoPlay && player != null) { delay(450); play() }
    }

    val videoAlpha by animateFloatAsState(if (showVideo) 1f else 0f, tween(220), label = "va")
    val ratio = cover?.let { it.width.toFloat() / it.height } ?: (9f / 16f)

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.Surface)
                .pointerInput(player) {
                    detectTapGestures(onPress = { play() })
                }
        ) {
            if (cover != null) Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (player != null) {
                VideoSurface(player, Modifier.fillMaxSize().graphicsLayer { alpha = videoAlpha })
            }
            LiveBadge(Modifier.align(Alignment.TopStart).padding(12.dp), playing = playing)
        }
    }
}
