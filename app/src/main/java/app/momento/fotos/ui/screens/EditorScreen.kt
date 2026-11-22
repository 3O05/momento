package app.momento.fotos.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.momento.fotos.core.ExportOutput
import app.momento.fotos.core.Quality
import app.momento.fotos.ui.components.CoverPicker
import app.momento.fotos.ui.components.LiveMark
import app.momento.fotos.ui.components.OverviewBar
import app.momento.fotos.ui.components.PillButton
import app.momento.fotos.ui.components.RoundIcon
import app.momento.fotos.ui.components.SectionLabel
import app.momento.fotos.ui.components.TrimTimeline
import app.momento.fotos.ui.components.VideoSurface
import app.momento.fotos.ui.components.formatMs
import app.momento.fotos.ui.components.formatSeconds
import app.momento.fotos.ui.theme.Palette

@Composable
fun EditorScreen(
    vm: EditorViewModel,
    onClose: () -> Unit,
    onDone: (ExportOutput) -> Unit,
) {
    val context = LocalContext.current
    val running = vm.exportState is ExportState.Running

    BackHandler(enabled = !running) { onClose() }

    LaunchedEffect(vm.exportState) {
        (vm.exportState as? ExportState.Done)?.let { onDone(it.output); vm.dismissExport() }
    }

    // Android 8/9: hace falta permiso de almacenamiento para guardar en la galería.
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.export()
    }
    fun startExport() {
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else vm.export()
    }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Barra superior
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIcon(Icons.Rounded.Close, "Cerrar", onClick = onClose)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Nueva foto viva", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    val len = vm.endMs - vm.startMs
                    Text(
                        if (vm.info != null) "Clip de ${formatSeconds(len)}" else "Cargando…",
                        style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                    )
                }
                PillButton("Crear", icon = Icons.Rounded.AutoAwesome, enabled = vm.info != null && !running) {
                    startExport()
                }
            }

            vm.loadError?.let { err ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(err, color = Palette.TextDim, style = MaterialTheme.typography.bodyLarge)
                }
                return@Column
            }

            // Vista previa
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                val info = vm.info
                if (info != null) {
                    val ratio = if (info.height > 0) info.width.toFloat() / info.height else 9f / 16f
                    Box(
                        Modifier
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                            .pointerInput(Unit) { detectTapGestures { vm.togglePlay() } },
                    ) {
                        VideoSurface(vm.player, Modifier.fillMaxSize())
                        // Indicadores
                        Row(
                            Modifier.align(Alignment.TopStart).padding(10.dp)
                                .clip(RoundedCornerShape(50)).background(Color(0x8C000000))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${formatMs(vm.playheadMs - vm.startMs)} / ${formatMs(vm.endMs - vm.startMs)}",
                                style = MaterialTheme.typography.labelSmall, color = Color.White,
                            )
                        }
                        if (vm.tab == EditorTab.COVER && !vm.isPlaying) {
                            Row(
                                Modifier.align(Alignment.TopEnd).padding(10.dp)
                                    .clip(RoundedCornerShape(50)).background(Palette.Sky)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Image, null, tint = Palette.OnSky, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("PORTADA", style = MaterialTheme.typography.labelSmall, color = Palette.OnSky)
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !vm.isPlaying,
                            modifier = Modifier.align(Alignment.Center),
                            enter = fadeIn(), exit = fadeOut(),
                        ) {
                            Box(
                                Modifier.size(54.dp).clip(RoundedCornerShape(50)).background(Color(0x66000000)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.PlayArrow, "Reproducir", tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }
                    }
                }
            }

            // Controles de reproducción + tiempos
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatMs(vm.startMs), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                Spacer(Modifier.weight(1f))
                RoundIcon(
                    if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    "Reproducir", size = 36.dp, bg = Palette.Surface2,
                ) { vm.togglePlay() }
                Spacer(Modifier.weight(1f))
                Text(formatMs(vm.endMs), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
            }

            // Panel de herramientas
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(Palette.Surface)
                    .padding(top = 14.dp, bottom = 10.dp),
            ) {
                AnimatedContent(vm.tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { tab ->
                    Column(Modifier.fillMaxWidth().height(184.dp)) {
                        when (tab) {
                            EditorTab.TRIM -> TrimPanel(vm)
                            EditorTab.COVER -> CoverPanel(vm)
                            EditorTab.SETTINGS -> SettingsPanel(vm)
                        }
                    }
                }
                TabBar(vm.tab) { vm.onTabSelected(it) }
            }
        }

        // Capa de exportación
        when (val st = vm.exportState) {
            is ExportState.Running -> ExportOverlay(st.progress, st.stage)
            is ExportState.Failed -> ErrorOverlay(st.message) { vm.dismissExport() }
            else -> {}
        }
    }
}

@Composable
private fun TrimPanel(vm: EditorViewModel) {
    if (vm.hasOverview) {
        OverviewBar(
            vm.overviewThumbs, vm.duration, vm.startMs, vm.endMs, vm.viewStartMs, vm.viewSpanMs,
            onCenterAt = { c ->
                val len = vm.endMs - vm.startMs
                vm.setViewStart(c - vm.viewSpanMs / 2)
                vm.moveSelection(c - len / 2)
            },
            onDragEnd = vm::commitTrim,
        )
        Spacer(Modifier.height(6.dp))
    } else Spacer(Modifier.height(20.dp))

    TrimTimeline(
        thumbs = vm.detailThumbs,
        viewStart = vm.viewStartMs, span = vm.viewSpanMs,
        start = vm.startMs, end = vm.endMs, cover = vm.coverMs, playhead = vm.playheadMs,
        onStart = vm::setStart, onEnd = vm::setEnd, onMove = vm::moveSelection,
        onSeek = vm::seek, onDragEnd = vm::commitTrim,
    )
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel("Duración")
        Spacer(Modifier.width(4.dp))
        val len = vm.endMs - vm.startMs
        listOf(1500L, 3000L, 5000L, 10_000L).filter { it <= vm.duration }.forEach { d ->
            Chip(formatSeconds(d), selected = kotlin.math.abs(len - d) < 60) { vm.setLength(d) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "Arrastra los bordes para recortar o mueve el recuadro.",
        Modifier.padding(horizontal = 20.dp),
        style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint,
    )
}

@Composable
private fun CoverPanel(vm: EditorViewModel) {
    Spacer(Modifier.height(10.dp))
    CoverPicker(vm.clipThumbs, vm.startMs, vm.endMs, vm.coverMs, vm.coverThumb, onCover = vm::setCover)
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Foto principal", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            Text(
                "Es la imagen que verás en la galería. En ${formatMs(vm.coverMs - vm.startMs)} del clip.",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
            )
        }
        Spacer(Modifier.width(10.dp))
        PillButton("Usar actual", filled = false, height = 36.dp) { vm.coverFromPlayhead() }
    }
}

@Composable
private fun SettingsPanel(vm: EditorViewModel) {
    val hasAudio = vm.info?.hasAudio == true
    Column(Modifier.padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(6.dp))
        SettingRow(
            if (vm.mute || !hasAudio) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
            "Sonido",
            if (!hasAudio) "Este video no tiene audio" else if (vm.mute) "La foto viva será muda" else "Se conserva el audio del clip",
        ) {
            Switch(
                checked = hasAudio && !vm.mute,
                enabled = hasAudio,
                onCheckedChange = { vm.mute = !it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Palette.OnSky, checkedTrackColor = Palette.Sky,
                    uncheckedThumbColor = Palette.TextDim, uncheckedTrackColor = Palette.Surface3,
                    uncheckedBorderColor = Palette.Line,
                ),
            )
        }
        Spacer(Modifier.height(10.dp))
        SettingRow(Icons.Rounded.HighQuality, "Calidad del video", "La foto siempre se guarda en resolución completa") {}
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Quality.entries.forEach { q ->
                Chip(q.label, selected = vm.quality == q, modifier = Modifier.weight(1f)) { vm.quality = q }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveMark(size = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                "Compatible con Google Fotos y Galería Samsung",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint,
            )
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, sub: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Palette.Surface2),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Palette.Sky, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
        trailing()
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Palette.SkySoft else Palette.Surface2)
            .border(1.dp, if (selected) Palette.Sky else Color.Transparent, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) Palette.Sky else Palette.TextDim)
    }
}

@Composable
private fun TabBar(current: EditorTab, onSelect: (EditorTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        EditorTab.entries.forEach { t ->
            val sel = t == current
            val icon = when (t) {
                EditorTab.TRIM -> Icons.Rounded.ContentCut
                EditorTab.COVER -> Icons.Rounded.Image
                EditorTab.SETTINGS -> Icons.Rounded.Tune
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(t) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, null, tint = if (sel) Palette.Sky else Palette.TextFaint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(3.dp))
                Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (sel) Palette.Text else Palette.TextFaint)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.width(16.dp).height(3.dp).clip(RoundedCornerShape(50))
                        .background(if (sel) Palette.Sky else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun ExportOverlay(progress: Float, stage: String) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE6101012))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
            LiveMark(size = 64.dp)
            Spacer(Modifier.height(22.dp))
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.displaySmall, color = Palette.Text)
            Spacer(Modifier.height(4.dp))
            Text(stage, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.width(220.dp).height(5.dp).clip(RoundedCornerShape(50)),
                color = Palette.Sky, trackColor = Palette.Surface3, strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "No cierres la app mientras se crea tu foto viva.",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ErrorOverlay(message: String, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp).clip(RoundedCornerShape(20.dp)).background(Palette.Surface2).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Ups", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "Prueba con calidad 720p o con un clip más corto.",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            PillButton("Entendido", icon = Icons.AutoMirrored.Rounded.ArrowBack, onClick = onDismiss)
        }
    }
}
