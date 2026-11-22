package app.momento.fotos.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import app.momento.fotos.core.ExportOutput
import app.momento.fotos.core.ExportSettings
import app.momento.fotos.core.Exporter
import app.momento.fotos.core.Quality
import app.momento.fotos.core.VideoFrames
import app.momento.fotos.core.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class EditorTab(val label: String) { TRIM("Recorte"), COVER("Portada"), SETTINGS("Ajustes") }

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float, val stage: String) : ExportState
    data class Done(val output: ExportOutput) : ExportState
    data class Failed(val message: String) : ExportState
}

@OptIn(UnstableApi::class)
/**
 * Estado del editor. Su vida está atada a la pantalla del editor: al salir se llama [release],
 * que detiene el audio y libera el reproductor y el lector de fotogramas.
 */
class EditorViewModel(private val app: Application, val source: Uri) {

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var released = false


    companion object {
        const val MIN_CLIP = 500L
        const val MAX_CLIP = 10_000L
        const val DEFAULT_CLIP = 3_000L
        const val DETAIL_SPAN = 15_000L
        const val STRIP_FRAMES = 9
    }

    private var frames: VideoFrames? = null

    var loadError by mutableStateOf<String?>(null); private set
    var info by mutableStateOf<VideoInfo?>(null); private set
    val duration get() = info?.durationMs ?: 0L

    var startMs by mutableLongStateOf(0L); private set
    var endMs by mutableLongStateOf(0L); private set
    var coverMs by mutableLongStateOf(0L); private set
    var playheadMs by mutableLongStateOf(0L); private set
    var isPlaying by mutableStateOf(false); private set

    var viewStartMs by mutableLongStateOf(0L); private set
    val viewSpanMs get() = minOf(duration, DETAIL_SPAN).coerceAtLeast(1L)
    val hasOverview get() = duration > DETAIL_SPAN

    val detailThumbs = mutableStateListOf<Bitmap?>()
    val overviewThumbs = mutableStateListOf<Bitmap?>()
    val clipThumbs = mutableStateListOf<Bitmap?>()
    var coverThumb by mutableStateOf<Bitmap?>(null); private set
    private var coverJob: Job? = null

    var tab by mutableStateOf(EditorTab.TRIM)
    var mute by mutableStateOf(false)
    var quality by mutableStateOf(Quality.FHD)
    var exportState by mutableStateOf<ExportState>(ExportState.Idle); private set
    var exportProgress by mutableFloatStateOf(0f); private set

    val player: ExoPlayer = ExoPlayer.Builder(app)
        .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
        .setHandleAudioBecomingNoisy(true)
        .build().apply {
        setSeekParameters(SeekParameters.EXACT)
        repeatMode = Player.REPEAT_MODE_OFF
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { this@EditorViewModel.isPlaying = playing }
        })
    }

    private var detailJob: Job? = null
    private var clipJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val f = VideoFrames(app, source)
                frames = f
                val i = f.info
                if (i.durationMs <= 0) throw IllegalStateException("Duración inválida")
                if (i.durationMs < MIN_CLIP + 100) {
                    loadError = "El video es demasiado corto (mínimo 0,6 s)."
                    return@launch
                }
                info = i
                endMs = minOf(i.durationMs, DEFAULT_CLIP)
                coverMs = endMs / 2
                player.setMediaItem(MediaItem.fromUri(source))
                player.prepare()
                player.seekTo(coverMs)
                loadOverview()
                loadDetail()
                loadClipThumbs()
                loadCoverThumb()
                loopWatcher()
            } catch (e: Exception) {
                loadError = "No se pudo abrir este video."
            }
        }
    }

    private fun loopWatcher() = viewModelScope.launch {
        while (isActive) {
            val pos = player.currentPosition
            playheadMs = pos
            if (player.isPlaying && (pos >= endMs || pos < startMs - 250)) {
                player.seekTo(startMs)
            }
            delay(33)
        }
    }

    // ---------- Reproducción ----------
    fun togglePlay() = safe {
        if (player.isPlaying) player.pause()
        else {
            if (player.currentPosition !in startMs until endMs - 50) player.seekTo(startMs)
            player.play()
        }
    }

    private var lastSeekAt = 0L
    private var dragging = false

    private fun scrubTo(ms: Long) {
        player.pause()
        playheadMs = ms
        val now = android.os.SystemClock.uptimeMillis()
        if (dragging) {
            if (now - lastSeekAt < 70) return   // no saturar el decodificador
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        } else {
            player.setSeekParameters(SeekParameters.EXACT)
        }
        lastSeekAt = now
        player.seekTo(ms)
    }

    fun dragStarted() { dragging = true }

    fun endDrag() = safe { settle() }

    /** Al soltar: búsqueda exacta en la posición final. */
    private fun settle() {
        dragging = false
        player.setSeekParameters(SeekParameters.EXACT)
        player.seekTo(playheadMs)
        lastSeekAt = 0L
    }

    fun seek(ms: Long) = safe { scrubTo(ms.coerceIn(startMs, endMs)) }

    // ---------- Recorte ----------
    fun setStart(ms: Long) = safe {
        val v = ms.coerceIn(maxOf(0L, endMs - MAX_CLIP), endMs - MIN_CLIP)
        startMs = v; clampCover(); scrubTo(v)
    }

    fun setEnd(ms: Long) = safe {
        val v = ms.coerceIn(startMs + MIN_CLIP, minOf(duration, startMs + MAX_CLIP))
        endMs = v; clampCover(); scrubTo(v)
    }

    fun moveSelection(newStart: Long) = safe {
        val len = endMs - startMs
        val s = newStart.coerceIn(0L, duration - len)
        val coverRel = coverMs - startMs
        startMs = s; endMs = s + len
        coverMs = s + coverRel
        loadCoverThumb()
        ensureVisible()
        scrubTo(s)
    }

    /** Ajusta la duración manteniendo el inicio (o retrocediéndolo si no cabe). */
    fun setLength(len: Long) = safe {
        val l = len.coerceIn(MIN_CLIP, minOf(MAX_CLIP, duration))
        var s = startMs
        if (s + l > duration) s = duration - l
        startMs = s; endMs = s + l
        coverMs = s + l / 2
        loadCoverThumb()
        ensureVisible()
        scrubTo(coverMs)
        loadClipThumbs()
    }

    fun commitTrim() = safe {
        settle()
        loadClipThumbs()
    }

    fun setViewStart(ms: Long) = safe {
        val v = ms.coerceIn(0L, (duration - viewSpanMs).coerceAtLeast(0L))
        if (v != viewStartMs) { viewStartMs = v; loadDetail() }
    }

    private fun ensureVisible() {
        if (startMs < viewStartMs) setViewStart(startMs)
        else if (endMs > viewStartMs + viewSpanMs) setViewStart(endMs - viewSpanMs)
    }

    private fun clampCover() {
        val c = coverMs.coerceIn(startMs, endMs - 1)
        if (c != coverMs) { coverMs = c; loadCoverThumb() }
    }

    // ---------- Portada ----------
    fun setCover(ms: Long) = safe {
        coverMs = ms.coerceIn(startMs, endMs - 1)
        scrubTo(coverMs)
        loadCoverThumb()
    }

    private fun loadCoverThumb() {
        val f = frames ?: return
        coverJob?.cancel()
        coverJob = viewModelScope.launch {
            delay(60)
            coverThumb = f.thumbnail(coverMs, 200)
        }
    }

    fun coverFromPlayhead() = safe { setCover(player.currentPosition) }

    fun onTabSelected(t: EditorTab) = safe {
        tab = t
        if (t == EditorTab.COVER) scrubTo(coverMs)
    }

    // ---------- Miniaturas ----------
    private fun loadOverview() {
        val f = frames ?: return
        overviewThumbs.clear(); repeat(STRIP_FRAMES) { overviewThumbs.add(null) }
        viewModelScope.launch {
            for (i in 0 until STRIP_FRAMES) {
                val t = duration * (2 * i + 1) / (2 * STRIP_FRAMES)
                overviewThumbs[i] = f.thumbnail(t, 160)
            }
        }
    }

    private fun loadDetail() {
        val f = frames ?: return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            delay(120)
            val start = viewStartMs
            val span = viewSpanMs
            val list = MutableList<Bitmap?>(STRIP_FRAMES) { detailThumbs.getOrNull(it) }
            if (detailThumbs.size != STRIP_FRAMES) { detailThumbs.clear(); detailThumbs.addAll(list) }
            for (i in 0 until STRIP_FRAMES) {
                val t = start + span * (2 * i + 1) / (2 * STRIP_FRAMES)
                detailThumbs[i] = f.thumbnail(t, 200)
            }
        }
    }

    private fun loadClipThumbs() {
        val f = frames ?: return
        clipJob?.cancel()
        clipJob = viewModelScope.launch {
            delay(150)
            val n = 7
            if (clipThumbs.size != n) { clipThumbs.clear(); repeat(n) { clipThumbs.add(null) } }
            val s = startMs; val len = endMs - startMs
            for (i in 0 until n) clipThumbs[i] = f.thumbnail(s + len * (2 * i + 1) / (2 * n), 200)
        }
    }

    // ---------- Exportar ----------
    fun export() {
        val i = info ?: return
        if (exportState is ExportState.Running) return
        player.pause()
        exportState = ExportState.Running(0f, "Preparando")
        viewModelScope.launch {
            exportState = try {
                val out = Exporter.export(
                    app, source, i,
                    ExportSettings(startMs, endMs, coverMs, mute, quality),
                ) { p, stage ->
                    exportProgress = p
                    exportState = ExportState.Running(p, stage)
                }
                ExportState.Done(out)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ExportState.Failed(e.message ?: "Algo salió mal al crear la foto viva.")
            }
        }
    }

    fun dismissExport() { exportState = ExportState.Idle }

    /** Un gesto nunca debe cerrar la app: si algo falla, se registra y se ignora. */
    private inline fun safe(block: () -> Unit) {
        if (released || info == null) return
        try { block() } catch (e: Exception) {
            android.util.Log.w("Momento", "Editor", e)
        }
    }

    /** Pausa (al salir de la app o al cambiar de pantalla). */
    fun pause() {
        if (!released) player.pause()
    }

    fun release() {
        if (released) return
        released = true
        viewModelScope.cancel()
        player.stop()
        player.release()
        frames?.close()
    }
}
