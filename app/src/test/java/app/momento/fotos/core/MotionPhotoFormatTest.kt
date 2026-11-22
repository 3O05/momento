package app.momento.fotos.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class MotionPhotoFormatTest {

    private fun fixtures(): Pair<ByteArray, File> {
        val dir = System.getenv("MOMENTO_FIXTURES")
        return if (dir != null) {
            File(dir, "cover.jpg").readBytes() to File(dir, "clip.mp4")
        } else {
            // JPEG mínimo (SOI, APP0, EOI) y MP4 falso con caja ftyp
            val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 4, 0, 0,
                0xFF.toByte(), 0xD9.toByte())
            val mp4 = File.createTempFile("clip", ".mp4").apply {
                writeBytes(byteArrayOf(0, 0, 0, 16) + "ftypisom".toByteArray() + byteArrayOf(0, 0, 0, 0) + ByteArray(100) { it.toByte() })
            }
            jpeg to mp4
        }
    }

    @Test
    fun roundTrip() {
        val (jpeg, video) = fixtures()
        val out = ByteArrayOutputStream()
        MotionPhotoFormat.write(out, jpeg, video, 1_500_000)
        val bytes = out.toByteArray()

        val outDir = System.getenv("MOMENTO_OUT")
        val file = if (outDir != null) File(outDir, "MVIMG_test_MOMENTO.jpg") else File.createTempFile("motion", ".jpg")
        file.writeBytes(bytes)

        // Termina en el tráiler Samsung
        assertEquals("SEFT", String(bytes.copyOfRange(bytes.size - 4, bytes.size)))
        // Sigue siendo JPEG
        assertEquals(0xFF.toByte(), bytes[0]); assertEquals(0xD8.toByte(), bytes[1])
        // Offset de Google apunta al inicio del MP4
        val text = String(bytes, Charsets.ISO_8859_1)
        val off = Regex("MicroVideoOffset=\"(\\d+)\"").find(text)!!.groupValues[1].toLong()
        val start = (bytes.size - off).toInt()
        assertEquals("ftyp", String(bytes.copyOfRange(start + 4, start + 8)))

        // Extracción
        val back = File.createTempFile("back", ".mp4")
        assertTrue(MotionPhotoFormat.extractVideo(file, back))
        val orig = video.readBytes()
        assertArrayEquals(orig, back.readBytes().copyOfRange(0, orig.size))
    }
}
