package app.momento.fotos.core

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Guarda el último error inesperado para mostrarlo al volver a abrir la app.
 * Nada se envía a internet: el usuario decide si lo copia y lo reporta en GitHub.
 */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "ultimo_error.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                file(app).writeText(
                    "Momento ${app.packageManager.getPackageInfo(app.packageName, 0).versionName}\n" +
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Hilo: ${t.name}\n\n$sw"
                )
            }
            previous?.uncaughtException(t, e)
        }
    }

    /** Devuelve el error guardado (y lo borra), o null. */
    fun take(ctx: Context): String? {
        val f = file(ctx)
        if (!f.exists()) return null
        return runCatching { f.readText() }.getOrNull().also { f.delete() }
    }
}
