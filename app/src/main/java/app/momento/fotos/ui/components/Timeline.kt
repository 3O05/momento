package app.momento.fotos.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.momento.fotos.ui.theme.Palette

@Composable
fun ThumbStrip(thumbs: List<Bitmap?>, modifier: Modifier = Modifier) {
    Row(modifier.background(Palette.Surface2)) {
        thumbs.forEach { b ->
            Box(Modifier.weight(1f).fillMaxHeight().background(Palette.Surface3)) {
                if (b != null) {
                    val img = remember(b) { b.asImageBitmap() }
                    Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}

private enum class DragMode { START, END, MOVE, NONE }

/**
 * Línea de tiempo de recorte: miniaturas + ventana seleccionada con asas a cada lado.
 * Muestra el rango [viewStart, viewStart+span] del video.
 */
@Composable
fun TrimTimeline(
    thumbs: List<Bitmap?>,
    viewStart: Long,
    span: Long,
    start: Long,
    end: Long,
    cover: Long,
    playhead: Long,
    onStart: (Long) -> Unit,
    onEnd: (Long) -> Unit,
    onMove: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val handleW = with(density) { 16.dp.toPx() }
    val grab = with(density) { 30.dp.toPx() }
    val sView by rememberUpdatedState(viewStart)
    val sSpan by rememberUpdatedState(span)
    val sStart by rememberUpdatedState(start)
    val sEnd by rememberUpdatedState(end)

    BoxWithConstraints(modifier.fillMaxWidth().height(68.dp).padding(horizontal = 18.dp)) {
        val w = with(density) { maxWidth.toPx() }
        fun xOf(ms: Long) = ((ms - sView).toFloat() / sSpan) * w
        fun msOf(x: Float) = sView + ((x / w) * sSpan).toLong()

        ThumbStrip(
            thumbs,
            Modifier.fillMaxSize().padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp)),
        )

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(w) {
                    detectTapGestures { p ->
                        val ms = msOf(p.x)
                        if (ms in sStart..sEnd) onSeek(ms)
                        else onMove(ms - (sEnd - sStart) / 2).also { onDragEnd() }
                    }
                }
                .pointerInput(w) {
                    var mode = DragMode.NONE
                    var anchorMs = 0L
                    var anchorStart = 0L
                    var x = 0f
                    detectDragGestures(
                        onDragStart = { p ->
                            x = p.x
                            val sx = xOf(sStart); val ex = xOf(sEnd)
                            mode = when {
                                kotlin.math.abs(p.x - (sx - handleW / 2)) < grab && p.x < (sx + ex) / 2 -> DragMode.START
                                kotlin.math.abs(p.x - (ex + handleW / 2)) < grab -> DragMode.END
                                p.x in sx..ex -> DragMode.MOVE
                                else -> DragMode.NONE
                            }
                            anchorMs = msOf(p.x); anchorStart = sStart
                        },
                        onDragEnd = { mode = DragMode.NONE; onDragEnd() },
                        onDragCancel = { mode = DragMode.NONE; onDragEnd() },
                        onDrag = { change, drag ->
                            change.consume()
                            x += drag.x
                            when (mode) {
                                DragMode.START -> onStart(msOf(x))
                                DragMode.END -> onEnd(msOf(x))
                                DragMode.MOVE -> onMove(anchorStart + (msOf(x) - anchorMs))
                                DragMode.NONE -> {}
                            }
                        },
                    )
                }
        ) {
            val top = 6.dp.toPx()
            val h = size.height - top * 2
            val sx = xOf(start).coerceIn(-handleW, w + handleW)
            val ex = xOf(end).coerceIn(-handleW, w + handleW)
            val dim = Color(0xB3000000)
            // Zonas fuera del recorte
            if (sx > 0) drawRect(dim, Offset(0f, top), Size(sx, h))
            if (ex < w) drawRect(dim, Offset(ex, top), Size(w - ex, h))

            // Marco de selección
            val r = CornerRadius(10.dp.toPx())
            drawRoundRect(
                Palette.Sky,
                topLeft = Offset(sx - handleW, top - 2.dp.toPx()),
                size = Size(ex - sx + handleW * 2, h + 4.dp.toPx()),
                cornerRadius = r,
                style = Stroke(width = 3.dp.toPx()),
            )
            // Asas
            for ((hx) in listOf(sx - handleW to 0, ex to 1)) {
                drawRoundRect(
                    Palette.Sky,
                    topLeft = Offset(hx, top - 2.dp.toPx()),
                    size = Size(handleW, h + 4.dp.toPx()),
                    cornerRadius = r,
                )
                val cx = hx + handleW / 2
                drawLine(
                    Palette.OnSky, Offset(cx, top + h * 0.34f), Offset(cx, top + h * 0.66f),
                    strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
                )
            }
            // Marca de portada
            val cxCover = xOf(cover)
            if (cxCover in sx..ex) {
                drawCircle(Color.White, 4.dp.toPx(), Offset(cxCover, size.height - 1.dp.toPx()))
                drawCircle(Palette.Sky, 2.5.dp.toPx(), Offset(cxCover, size.height - 1.dp.toPx()))
            }
            // Cabezal
            val px = xOf(playhead)
            if (px in 0f..w) {
                drawLine(
                    Color.White, Offset(px, top - 4.dp.toPx()), Offset(px, top + h + 4.dp.toPx()),
                    strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Barra de vista general para videos largos: arrastra para ubicar el recorte. */
@Composable
fun OverviewBar(
    thumbs: List<Bitmap?>,
    duration: Long,
    start: Long,
    end: Long,
    viewStart: Long,
    span: Long,
    onCenterAt: (Long) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dur by rememberUpdatedState(duration)
    BoxWithConstraints(modifier.fillMaxWidth().height(34.dp).padding(horizontal = 34.dp)) {
        val w = with(density) { maxWidth.toPx() }
        fun msOf(x: Float) = ((x / w).coerceIn(0f, 1f) * dur).toLong()
        ThumbStrip(thumbs, Modifier.fillMaxSize().padding(vertical = 4.dp).clip(RoundedCornerShape(7.dp)))
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(w) {
                    detectTapGestures { onCenterAt(msOf(it.x)); onDragEnd() }
                }
                .pointerInput(w) {
                    detectDragGestures(
                        onDragStart = { onCenterAt(msOf(it.x)) },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                        onDrag = { c, _ -> c.consume(); onCenterAt(msOf(c.position.x)) },
                    )
                }
        ) {
            val top = 4.dp.toPx(); val h = size.height - top * 2
            fun x(ms: Long) = ms.toFloat() / duration * w
            drawRect(Color(0x99000000), Offset(0f, top), Size(w, h))
            // Rango visible en la línea de tiempo grande
            drawRoundRect(
                Color.White.copy(alpha = 0.35f), Offset(x(viewStart), top), Size(x(viewStart + span) - x(viewStart), h),
                CornerRadius(6.dp.toPx()), style = Stroke(1.5.dp.toPx()),
            )
            val sx = x(start); val ex = maxOf(x(end), sx + 4.dp.toPx())
            drawRoundRect(Palette.Sky.copy(alpha = 0.35f), Offset(sx, top), Size(ex - sx, h), CornerRadius(4.dp.toPx()))
            drawRoundRect(Palette.Sky, Offset(sx, top), Size(ex - sx, h), CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()))
        }
    }
}

/** Selector del fotograma de portada dentro del recorte. */
@Composable
fun CoverPicker(
    thumbs: List<Bitmap?>,
    start: Long,
    end: Long,
    cover: Long,
    coverThumb: Bitmap?,
    onCover: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val s by rememberUpdatedState(start)
    val e by rememberUpdatedState(end)
    BoxWithConstraints(modifier.fillMaxWidth().height(76.dp).padding(horizontal = 18.dp)) {
        val w = with(density) { maxWidth.toPx() }
        val frameW = with(density) { 46.dp.toPx() }
        fun msOf(x: Float) = s + (((x - frameW / 2) / (w - frameW)).coerceIn(0f, 1f) * (e - s - 1)).toLong()

        ThumbStrip(
            thumbs,
            Modifier.fillMaxSize().padding(vertical = 14.dp).clip(RoundedCornerShape(8.dp)),
        )
        val frac = if (end - start > 1) (cover - start).toFloat() / (end - start - 1) else 0f
        val left = with(density) { (frac.coerceIn(0f, 1f) * (w - frameW)).toDp() }
        Box(
            Modifier
                .padding(start = left)
                .width(46.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(Palette.Sky)
                .padding(3.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Palette.Surface3)
        ) {
            if (coverThumb != null) {
                val img = remember(coverThumb) { coverThumb.asImageBitmap() }
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(w) { detectTapGestures { onCover(msOf(it.x)) } }
                .pointerInput(w) {
                    detectDragGestures(
                        onDragStart = { onCover(msOf(it.x)) },
                        onDrag = { c, _ -> c.consume(); onCover(msOf(c.position.x)) },
                    )
                }
        )
    }
}
