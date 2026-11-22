package app.momento.fotos.core

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedMoment(val uri: Uri, val name: String, val dateMs: Long)

/** Guardado y listado de fotos vivas en la galería (carpeta Imágenes/Momento). */
object Gallery {

    const val FOLDER = "Momento"

    /**
     * Google Fotos/Galería de Google reconocen mejor las fotos vivas cuyo nombre empieza con "MV".
     */
    fun newDisplayName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "MVIMG_${stamp}_MOMENTO.jpg"
    }

    fun saveJpeg(context: Context, displayName: String, write: (OutputStream) -> Unit): Uri {
        val resolver = context.contentResolver
        val now = System.currentTimeMillis()
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
                put(MediaStore.Images.Media.DATE_TAKEN, now)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: throw ExportFailure("No se pudo crear el archivo en la galería.")
            try {
                resolver.openOutputStream(uri)?.use { write(it) } ?: throw ExportFailure("No se pudo escribir en la galería.")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return uri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
            dir.mkdirs()
            val file = File(dir, displayName)
            file.outputStream().use { write(it) }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_TAKEN, now)
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, file.absolutePath)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
            return uri ?: Uri.fromFile(file)
        }
    }

    suspend fun list(context: Context): List<SavedMoment> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SavedMoment>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
        )
        val (selection, args) = if (Build.VERSION.SDK_INT >= 29) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?" to
                arrayOf("${Environment.DIRECTORY_PICTURES}/$FOLDER%", "%MOMENTO%")
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.Images.Media.DATA} LIKE ?" to arrayOf("%/${Environment.DIRECTORY_PICTURES}/$FOLDER/%")
        }
        runCatching {
            context.contentResolver.query(
                collection, projection, selection, args,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    result += SavedMoment(
                        ContentUris.withAppendedId(collection, id),
                        c.getString(nameCol) ?: "",
                        c.getLong(dateCol),
                    )
                }
            }
        }
        result
    }

    suspend fun thumbnail(context: Context, uri: Uri, size: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(uri, Size(size, size), null)
            } else {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                var sample = 1
                while (opts.outWidth / (sample * 2) >= size && opts.outHeight / (sample * 2) >= size) sample *= 2
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }
        }.getOrNull()
    }

    suspend fun decodeFull(context: Context, uri: Uri, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= maxSide) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull()
    }

    /** Copia la foto viva a caché y extrae su video. */
    suspend fun extractVideo(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "viewer").apply { mkdirs() }
            val src = File(dir, "src.jpg")
            context.contentResolver.openInputStream(uri)?.use { i -> src.outputStream().use { i.copyTo(it) } }
            val out = File(dir, "v_${uri.lastPathSegment}.mp4")
            if (MotionPhotoFormat.extractVideo(src, out)) out else null
        }.getOrNull()
    }

    fun delete(context: Context, uri: Uri): Boolean =
        runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
}
