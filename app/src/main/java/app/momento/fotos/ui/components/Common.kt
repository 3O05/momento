package app.momento.fotos.ui.components

import android.view.LayoutInflater
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import app.momento.fotos.R
import app.momento.fotos.ui.theme.Palette
import java.util.Locale

fun formatMs(ms: Long): String {
    val totalTenths = (ms.coerceAtLeast(0) + 50) / 100
    val tenths = totalTenths % 10
    val totalSec = totalTenths / 10
    return String.format(Locale.US, "%d:%02d.%d", totalSec / 60, totalSec % 60, tenths)
}

fun formatSeconds(ms: Long): String {
    val s = ms / 1000.0
    return if (ms % 1000 == 0L) "${ms / 1000} s" else String.format(Locale("es"), "%.1f s", s)
}

/** Botón principal tipo píldora. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = true,
    enabled: Boolean = true,
    height: Dp = 40.dp,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> Palette.Surface3
        filled -> Palette.Sky
        else -> Palette.Surface2
    }
    val fg = when {
        !enabled -> Palette.TextFaint
        filled -> Palette.OnSky
        else -> Palette.Text
    }
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(PaddingValues(horizontal = 18.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

/** Botón redondo de icono. */
@Composable
fun RoundIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    bg: Color = Palette.Surface2,
    tint: Color = Palette.Text,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Marca "VIVA": un punto con ondas, el mismo motivo del icono. */
@Composable
fun LiveMark(modifier: Modifier = Modifier, active: Boolean = true, size: Dp = 16.dp, color: Color = Palette.Sky) {
    val t = rememberInfiniteTransition(label = "pulse")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "p")
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension / 2
        drawCircle(color, radius = r * 0.32f, center = c)
        if (active) {
            drawCircle(
                color.copy(alpha = (1f - phase) * 0.8f),
                radius = r * (0.45f + 0.55f * phase),
                center = c,
                style = Stroke(width = r * 0.14f),
            )
        } else {
            drawCircle(color.copy(alpha = 0.5f), radius = r * 0.75f, center = c, style = Stroke(width = r * 0.14f))
        }
    }
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier, playing: Boolean) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x99000000))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveMark(active = playing, size = 14.dp, color = if (playing) Palette.Sky else Color.White)
        Spacer(Modifier.width(5.dp))
        Text("VIVA", style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

/** Vista de video de Media3 (TextureView para poder redondear esquinas). */
@Composable
fun VideoSurface(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).apply {
                this.player = player
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.ROOT),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = Palette.TextFaint,
    )
}
