package app.momento.fotos.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VideoInfo(
    val durationMs: Long,
    val width: Int,      // ya rotado (como se ve en pantalla)
    val height: Int,
    val rotation: Int,
    val hasAudio: Boolean,
)

/** Lectura de metadatos y fotogramas usando MediaMetadataRetriever. */
class VideoFrames(private val context: Context, private val uri: Uri) : AutoCloseable {

    private val retriever = MediaMetadataRetriever().apply { setDataSource(context, uri) }
    @Volatile private var closed = false

    /** MediaMetadataRetriever no es seguro entre hilos: todo pasa por un solo hilo. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)

    val info: VideoInfo by lazy {
        fun key(k: Int) = retriever.extractMetadata(k)
        val rot = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val w = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val swap = rot == 90 || rot == 270
        VideoInfo(
            durationMs = key(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
            width = if (swap) h else w,
            height = if (swap) w else h,
            rotation = rot,
            hasAudio = key(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
        )
    }

    /** Miniatura pequeña para la línea de tiempo. */
    suspend fun thumbnail(timeMs: Long, maxSide: Int): Bitmap? = withContext(io) {
        if (closed) return@withContext null
        run {
            runCatching {
                val us = timeMs * 1000
                val bmp = if (Build.VERSION.SDK_INT >= 27) {
                    // Mantiene la proporción y cabe dentro de maxSide x maxSide.
                    retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSide, maxSide)
                } else {
                    retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { scaleDown(it, maxSide) }
                }
                bmp?.let { fixOrientation(it) }
            }.getOrNull()
        }
    }

    /** Fotograma exacto a resolución completa (para la foto de portada). */
    suspend fun fullFrame(timeMs: Long): Bitmap? = withContext(io) {
        if (closed) return@withContext null
        run {
            runCatching {
                val us = timeMs * 1000
                (retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
                    ?.let { fixOrientation(it) }
            }.getOrNull()
        }
    }

    /** Algunos equipos devuelven el fotograma sin aplicar la rotación del video; lo corregimos. */
    private fun fixOrientation(bmp: Bitmap): Bitmap {
        val rot = info.rotation
        if (rot != 90 && rot != 270) return bmp
        val expectedPortrait = info.height > info.width
        val gotPortrait = bmp.height > bmp.width
        if (expectedPortrait == gotPortrait || info.width == info.height) return bmp
        val m = Matrix().apply { postRotate(rot.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private fun scaleDown(bmp: Bitmap, maxSide: Int): Bitmap {
        val (w, h) = fit(bmp.width, bmp.height, maxSide)
        return if (w == bmp.width) bmp else Bitmap.createScaledBitmap(bmp, w, h, true)
    }

    private fun fit(w: Int, h: Int, maxSide: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return maxSide to maxSide
        val s = maxSide.toFloat() / maxOf(w, h)
        return if (s >= 1f) w to h else (w * s).toInt().coerceAtLeast(1) to (h * s).toInt().coerceAtLeast(1)
    }

    /** Se libera en el mismo hilo, después de cualquier miniatura en curso. */
    override fun close() {
        if (closed) return
        closed = true
        kotlinx.coroutines.CoroutineScope(io).launch { runCatching { retriever.release() } }
    }
}
