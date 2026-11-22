package app.momento.fotos.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.momento.fotos.core.ExportOutput
import app.momento.fotos.core.Gallery
import app.momento.fotos.core.SavedMoment
import app.momento.fotos.ui.components.LiveMark
import app.momento.fotos.ui.components.LivePhoto
import app.momento.fotos.ui.components.PillButton
import app.momento.fotos.ui.components.RoundIcon
import app.momento.fotos.ui.theme.Palette
import java.io.File

/** Pantalla tras crear una foto viva. */
@Composable
fun ResultScreen(output: ExportOutput, onNew: () -> Unit, onHome: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onHome() }
    val cover = remember(output) { output.cover.asImageBitmap() }
    ScreenFrame(
        title = "¡Listo!",
        subtitle = "Guardada en Imágenes › Momento",
        onBack = onHome,
        cover = cover, video = output.clipFile,
    ) {
        PillButton("Compartir", icon = Icons.Rounded.Share, filled = false, modifier = Modifier.weight(1f)) {
            share(context, output.galleryUri)
        }
        PillButton("Galería", icon = Icons.Rounded.PhotoLibrary, filled = false, modifier = Modifier.weight(1f)) {
            openInGallery(context, output.galleryUri)
        }
        PillButton("Otra", icon = Icons.Rounded.Add, modifier = Modifier.weight(1f), onClick = onNew)
    }
}

/** Visor de una foto viva ya guardada. */
@Composable
fun ViewerScreen(moment: SavedMoment, onBack: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onBack() }
    val cover by produceState<ImageBitmap?>(null, moment.uri) {
        value = Gallery.decodeFull(context, moment.uri, 1600)?.asImageBitmap()
    }
    val video by produceState<File?>(null, moment.uri) { value = Gallery.extractVideo(context, moment.uri) }
    ScreenFrame(
        title = "Foto viva",
        subtitle = "Mantén presionado para reproducir",
        onBack = onBack,
        cover = cover, video = video,
    ) {
        RoundIcon(Icons.Rounded.DeleteOutline, "Eliminar", size = 40.dp) {
            if (Gallery.delete(context, moment.uri)) onDeleted()
            else Toast.makeText(context, "Elimínala desde la galería del teléfono.", Toast.LENGTH_SHORT).show()
        }
        PillButton("Compartir", icon = Icons.Rounded.Share, filled = false, modifier = Modifier.weight(1f)) {
            share(context, moment.uri)
        }
        PillButton("Abrir en galería", icon = Icons.Rounded.PhotoLibrary, modifier = Modifier.weight(1.4f)) {
            openInGallery(context, moment.uri)
        }
    }
}

@Composable
private fun ScreenFrame(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    cover: ImageBitmap?,
    video: File?,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
                    Spacer(Modifier.width(8.dp))
                    LiveMark(size = 18.dp)
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (cover != null) LivePhoto(cover, video, Modifier.fillMaxSize(), autoPlay = video != null)
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
        Spacer(Modifier.height(4.dp))
    }
}

private fun share(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Compartir foto viva"))
}

private fun openInGallery(context: Context, uri: Uri) {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/jpeg")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try { context.startActivity(view) } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No hay una galería instalada.", Toast.LENGTH_SHORT).show()
    }
}

@Suppress("unused")
private fun Bitmap.ratio() = width.toFloat() / height
