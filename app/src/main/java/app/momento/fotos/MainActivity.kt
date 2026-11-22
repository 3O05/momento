package app.momento.fotos

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import app.momento.fotos.core.ExportOutput
import app.momento.fotos.core.SavedMoment
import app.momento.fotos.ui.screens.EditorScreen
import app.momento.fotos.ui.screens.EditorViewModel
import app.momento.fotos.ui.screens.HomeScreen
import app.momento.fotos.ui.screens.ResultScreen
import app.momento.fotos.ui.screens.ViewerScreen
import app.momento.fotos.ui.theme.MomentoTheme
import app.momento.fotos.ui.theme.Palette

private sealed interface Screen {
    data object Home : Screen
    data class Editor(val uri: Uri, val session: Long = System.nanoTime()) : Screen
    data class Result(val output: ExportOutput) : Screen
    data class Viewer(val moment: SavedMoment) : Screen
}

class MainActivity : ComponentActivity() {

    private var screen by mutableStateOf<Screen>(Screen.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { MomentoTheme { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Videos compartidos desde otra app (Galería → Compartir → Momento). */
    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_EDIT -> intent.data
            else -> null
        }
        if (uri != null) screen = Screen.Editor(uri)
    }

    @Composable
    private fun App() {
        var refresh by remember { mutableIntStateOf(0) }
        var showAbout by remember { mutableStateOf(false) }

        Box(Modifier.fillMaxSize().background(Palette.Bg)) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { s ->
                    when (s) {
                        is Screen.Home -> "home"
                        is Screen.Editor -> "editor${s.session}"
                        is Screen.Result -> "result"
                        is Screen.Viewer -> "viewer${s.moment.uri}"
                    }
                },
                label = "nav",
            ) { s ->
                when (s) {
                    Screen.Home -> HomeScreen(
                        refreshKey = refresh,
                        onVideoPicked = { screen = Screen.Editor(it) },
                        onOpenMoment = { screen = Screen.Viewer(it) },
                        onAbout = { showAbout = true },
                    )

                    is Screen.Editor -> {
                        val vm: EditorViewModel = viewModel(
                            key = "editor-${s.session}",
                            factory = EditorViewModel.factory(application, s.uri),
                        )
                        EditorScreen(
                            vm,
                            onClose = { screen = Screen.Home },
                            onDone = { out -> refresh++; screen = Screen.Result(out) },
                        )
                    }

                    is Screen.Result -> ResultScreen(
                        s.output,
                        onNew = { screen = Screen.Home },
                        onHome = { screen = Screen.Home },
                    )

                    is Screen.Viewer -> ViewerScreen(
                        s.moment,
                        onBack = { screen = Screen.Home },
                        onDeleted = { refresh++; screen = Screen.Home },
                    )
                }
            }
        }

        if (showAbout) AboutDialog { showAbout = false }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface2,
        title = { Text("Momento ${BuildConfigInfo.VERSION}", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(
                "Convierte videos en fotos vivas (Motion Photos) compatibles con Google Fotos y la Galería de Samsung.\n\n" +
                    "• Software libre bajo licencia GPL-3.0\n" +
                    "• Sin anuncios, sin cuentas, sin rastreo\n" +
                    "• Todo se procesa en tu teléfono; nada sale a internet",
                style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = Palette.Sky) } },
        dismissButton = {
            TextButton(onClick = { runCatching { uriHandler.openUri(BuildConfigInfo.REPO) } }) {
                Text("Código fuente", color = Palette.TextDim)
            }
        },
    )
}

object BuildConfigInfo {
    const val VERSION = "1.0.0"
    const val REPO = "https://github.com/momento-app/momento"
}
