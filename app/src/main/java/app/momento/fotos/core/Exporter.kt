package app.momento.fotos.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class Quality(val label: String, val shortSide: Int?) {
    HD("720p", 720),
    FHD("1080p", 1080),
    ORIGINAL("Original", null),
}

data class ExportSettings(
    val startMs: Long,
    val endMs: Long,
    val coverMs: Long,      // absoluto dentro del video original
    val mute: Boolean,
    val quality: Quality,
)

data class ExportOutput(
    val galleryUri: Uri,
    val clipFile: File,
    val cover: Bitmap,
    val displayName: String,
)

class ExportFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

@OptIn(UnstableApi::class)
object Exporter {

    /**
     * Proceso completo: recortar → sacar portada → armar foto viva → guardar en galería.
     * @param onProgress 0f..1f
     */
    suspend fun export(
        context: Context,
        source: Uri,
        info: VideoInfo,
        s: ExportSettings,
        onProgress: (Float, String) -> Unit,
    ): ExportOutput {
        val clip = File(context.cacheDir, "clips").apply { mkdirs() }
            .resolve("clip_${System.currentTimeMillis()}.mp4")

        onProgress(0f, "Recortando video")
        // 1) Recorte + recodificación (H.264/AAC, SDR). Si falla el mapeo de tonos HDR, reintenta sin él.
        try {
            transcode(context, source, info, s, clip, toneMap = true) { onProgress(it * 0.85f, "Recortando video") }
        } catch (e: Exception) {
            clip.delete()
            try {
                transcode(context, source, info, s, clip, toneMap = false) { onProgress(it * 0.85f, "Recortando video") }
            } catch (e2: Exception) {
                clip.delete()
                throw ExportFailure("No se pudo procesar el video en este equipo.", e2)
            }
        }

        // 2) Foto de portada a resolución completa
        onProgress(0.88f, "Capturando la foto")
        val cover = VideoFrames(context, source).use { it.fullFrame(s.coverMs) }
            ?: throw ExportFailure("No se pudo leer el fotograma de portada.")
        val jpeg = withContext(Dispatchers.Default) {
            ByteArrayOutputStream().also { cover.compress(Bitmap.CompressFormat.JPEG, 93, it) }.toByteArray()
        }

        // 3) Armar la foto viva y 4) guardarla
        onProgress(0.94f, "Guardando en tu galería")
        val ptsUs = ((s.coverMs - s.startMs).coerceAtLeast(0)) * 1000
        val name = Gallery.newDisplayName()
        val uri = withContext(Dispatchers.IO) {
            Gallery.saveJpeg(context, name) { out ->
                MotionPhotoFormat.write(out, jpeg, clip, ptsUs)
            }
        }
        onProgress(1f, "Listo")
        return ExportOutput(uri, clip, cover, name)
    }

    private suspend fun transcode(
        context: Context,
        source: Uri,
        info: VideoInfo,
        s: ExportSettings,
        out: File,
        toneMap: Boolean,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.Main) {
        val item = MediaItem.Builder()
            .setUri(source)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(s.startMs)
                    .setEndPositionMs(s.endMs)
                    .build()
            )
            .build()

        val videoEffects = buildList {
            val target = s.quality.shortSide
            val shortSide = minOf(info.width, info.height)
            if (target != null && shortSide > target) add(Presentation.createForShortSide(target))
        }

        val edited = EditedMediaItem.Builder(item)
            .setRemoveAudio(s.mute || !info.hasAudio)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val composition = Composition.Builder(EditedMediaItemSequence.Builder(edited).build())
            .setHdrMode(
                if (toneMap) Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
                else Composition.HDR_MODE_KEEP_HDR
            )
            .build()

        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val holder = ProgressHolder()
            lateinit var transformer: Transformer
            val poll = object : Runnable {
                override fun run() {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress / 100f)
                    }
                    handler.postDelayed(this, 120)
                }
            }
            transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacks(poll)
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        handler.removeCallbacks(poll)
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()
            cont.invokeOnCancellation {
                handler.removeCallbacks(poll)
                handler.post { transformer.cancel() }
            }
            transformer.start(composition, out.absolutePath)
            handler.post(poll)
        }
    }
}
