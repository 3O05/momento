package app.momento.fotos.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.momento.fotos.core.Gallery
import app.momento.fotos.core.SavedMoment
import app.momento.fotos.ui.components.LiveMark
import app.momento.fotos.ui.components.PillButton
import app.momento.fotos.ui.components.RoundIcon
import app.momento.fotos.ui.components.SectionLabel
import app.momento.fotos.ui.theme.Palette

@Composable
fun HomeScreen(
    refreshKey: Int,
    onVideoPicked: (Uri) -> Unit,
    onOpenMoment: (SavedMoment) -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onVideoPicked(uri)
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))

    var moments by remember { mutableStateOf<List<SavedMoment>?>(null) }
    LaunchedEffect(refreshKey) { moments = Gallery.list(context) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().background(Palette.Bg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildAnnotatedString {
                            append("momento")
                            withStyle(SpanStyle(color = Palette.Sky)) { append(".") }
                        },
                        style = MaterialTheme.typography.displaySmall, color = Palette.Text,
                    )
                    Spacer(Modifier.weight(1f))
                    RoundIcon(Icons.Rounded.Info, "Acerca de", onClick = onAbout)
                }
                Text(
                    "Convierte cualquier video en una foto viva.",
                    style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim,
                )
                Spacer(Modifier.height(22.dp))
                HeroCard(onClick = ::pick)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Fact("1", "Elige un video")
                    Fact("2", "Recorta y elige la foto")
                    Fact("3", "Se guarda en tu galería")
                }
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Tus momentos")
                    Spacer(Modifier.width(8.dp))
                    moments?.let {
                        if (it.isNotEmpty()) Text("${it.size}", style = MaterialTheme.typography.labelSmall, color = Palette.Sky)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        val list = moments
        if (list != null && list.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyState() }
        } else if (list != null) {
            items(list, key = { it.uri.toString() }) { m -> MomentTile(m) { onOpenMoment(m) } }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Código abierto · Sin anuncios · Sin cuentas",
                Modifier.fillMaxWidth().padding(top = 26.dp).navigationBarsPadding(),
                style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HeroCard(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Line, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        // Fondo: ondas concéntricas que salen desde la esquina (motivo "latido")
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width * 0.86f, size.height * 0.30f)
            drawCircle(
                Brush.radialGradient(listOf(Palette.Sky.copy(alpha = 0.28f), Color.Transparent), c, size.height * 0.9f),
                radius = size.height * 0.9f, center = c,
            )
            for (i in 1..5) {
                drawCircle(
                    Palette.Sky.copy(alpha = 0.22f - i * 0.035f),
                    radius = i * 34.dp.toPx(), center = c, style = Stroke(1.2.dp.toPx()),
                )
            }
            drawCircle(Palette.Sky, 7.dp.toPx(), c)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Surface3),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.VideoLibrary, null, tint = Palette.Sky, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.height(14.dp))
            Text("Crear foto viva", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
            Spacer(Modifier.height(2.dp))
            Text("Elige un video de tu galería", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        }
        PillButton(
            "Elegir", icon = Icons.Rounded.Add,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            onClick = onClick,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Fact(n: String, text: String) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Palette.Surface).padding(12.dp),
    ) {
        Text(n, style = MaterialTheme.typography.titleMedium, color = Palette.Sky)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(18.dp)).padding(vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LiveMark(size = 34.dp, active = false, color = Palette.TextFaint)
        Spacer(Modifier.height(10.dp))
        Text("Aún no hay fotos vivas", style = MaterialTheme.typography.titleSmall, color = Palette.TextDim)
        Text("Las que crees aparecerán aquí", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
    }
}

@Composable
private fun MomentTile(m: SavedMoment, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(null, m.uri) { value = Gallery.thumbnail(context, m.uri, 320) }
    Box(
        Modifier.aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp)).background(Palette.Surface)
            .clickable(onClick = onClick),
    ) {
        thumb?.let {
            val img = remember(it) { it.asImageBitmap() }
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        LiveMark(Modifier.align(Alignment.TopStart).padding(6.dp), active = false, size = 14.dp, color = Color.White)
    }
}
