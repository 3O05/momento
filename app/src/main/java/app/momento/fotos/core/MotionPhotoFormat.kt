package app.momento.fotos.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Escribe y lee "fotos vivas" de Android (Motion Photos).
 *
 * El archivo generado es compatible a la vez con:
 *  - Formato Motion Photo v1 de Google (XMP con GCamera:MotionPhoto + Container:Directory),
 *    más los campos heredados MicroVideo para versiones antiguas de Google Fotos.
 *  - Formato de Samsung (bloque "MotionPhoto_Data" + tráiler SEFH/SEFT al final del archivo).
 *
 * Estructura final:
 *   [JPEG con XMP] [00 00 30 0A][16][MotionPhoto_Data] [MP4] [SEFH ... SEFT]
 *
 * En el XMP, la longitud del video se cuenta desde el inicio del MP4 hasta el final
 * del archivo (incluye el tráiler Samsung de 32 bytes), igual que hacen los Galaxy con One UI 5+.
 */
object MotionPhotoFormat {

    private const val SAMSUNG_NAME = "MotionPhoto_Data"
    private const val SAMSUNG_TYPE: Short = 0x0A30
    private const val SAMSUNG_BLOCK_HEADER = 8 + 16 // 00 00 | tipo(2) | largo nombre(4) | nombre(16)
    private const val SEF_TRAILER_SIZE = 32         // SEFH(4)+ver(4)+n(4)+entrada(12)+tam(4)+SEFT(4)
    private const val XMP_NS = "http://ns.adobe.com/xap/1.0/\u0000"

    /**
     * Arma la foto viva.
     * @param jpeg JPEG del fotograma de portada (sin XMP).
     * @param video MP4 (H.264/AAC) del clip.
     * @param presentationTimestampUs instante (µs, relativo al inicio del clip) que corresponde a la foto.
     */
    fun write(out: OutputStream, jpeg: ByteArray, video: File, presentationTimestampUs: Long) {
        val videoLen = video.length()
        val tailFromVideoStart = videoLen + SEF_TRAILER_SIZE
        val xmp = buildXmp(tailFromVideoStart, presentationTimestampUs)
        val jpegWithXmp = insertXmp(jpeg, xmp)

        out.write(jpegWithXmp)

        // Bloque Samsung
        val blockLen = SAMSUNG_BLOCK_HEADER + videoLen
        require(blockLen < 0xFFFF_FFFFL) { "Video demasiado grande" }
        out.write(samsungBlockHeader())

        video.inputStream().use { it.copyTo(out, 256 * 1024) }

        out.write(sefTrailer(blockLen.toInt()))
        out.flush()
    }

    private fun samsungBlockHeader(): ByteArray =
        ByteBuffer.allocate(SAMSUNG_BLOCK_HEADER).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0)
            putShort(SAMSUNG_TYPE)
            putInt(SAMSUNG_NAME.length)
            put(SAMSUNG_NAME.toByteArray(Charsets.US_ASCII))
        }.array()

    private fun sefTrailer(blockLen: Int): ByteArray =
        ByteBuffer.allocate(SEF_TRAILER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("SEFH".toByteArray(Charsets.US_ASCII))
            putInt(106)          // versión
            putInt(1)            // cantidad de entradas
            putShort(0)
            putShort(SAMSUNG_TYPE)
            putInt(blockLen)     // distancia desde el inicio del bloque hasta SEFH
            putInt(blockLen)     // tamaño del bloque
            putInt(24)           // tamaño del directorio SEF
            put("SEFT".toByteArray(Charsets.US_ASCII))
        }.array()

    fun buildXmp(videoTailLength: Long, presentationTimestampUs: Long): String = """
<?xpacket begin="${'﻿'}" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Momento">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
    xmlns:Container="http://ns.google.com/photos/1.0/container/"
    xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
    GCamera:MotionPhoto="1"
    GCamera:MotionPhotoVersion="1"
    GCamera:MotionPhotoPresentationTimestampUs="$presentationTimestampUs"
    GCamera:MicroVideo="1"
    GCamera:MicroVideoVersion="1"
    GCamera:MicroVideoOffset="$videoTailLength"
    GCamera:MicroVideoPresentationTimestampUs="$presentationTimestampUs">
   <Container:Directory>
    <rdf:Seq>
     <rdf:li rdf:parseType="Resource">
      <Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/>
     </rdf:li>
     <rdf:li rdf:parseType="Resource">
      <Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="$videoTailLength" Item:Padding="0"/>
     </rdf:li>
    </rdf:Seq>
   </Container:Directory>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>""".trimStart()

    /** Inserta un segmento APP1/XMP justo después de SOI y de los segmentos APP0/APP1(EXIF) existentes. */
    fun insertXmp(jpeg: ByteArray, xmp: String): ByteArray {
        require(jpeg.size > 4 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte()) { "No es un JPEG" }
        val payload = XMP_NS.toByteArray(Charsets.US_ASCII) + xmp.toByteArray(Charsets.UTF_8)
        val segLen = payload.size + 2
        require(segLen <= 0xFFFF) { "XMP demasiado grande" }

        // Avanzar sobre APP0 (JFIF) y APP1 (EXIF) para insertar después de ellos.
        var pos = 2
        while (pos + 4 <= jpeg.size && jpeg[pos] == 0xFF.toByte()) {
            val marker = jpeg[pos + 1].toInt() and 0xFF
            if (marker != 0xE0 && marker != 0xE1) break
            val len = ((jpeg[pos + 2].toInt() and 0xFF) shl 8) or (jpeg[pos + 3].toInt() and 0xFF)
            pos += 2 + len
        }

        val out = ByteArrayOutputStream(jpeg.size + segLen + 2)
        out.write(jpeg, 0, pos)
        out.write(0xFF); out.write(0xE1)
        out.write((segLen shr 8) and 0xFF); out.write(segLen and 0xFF)
        out.write(payload)
        out.write(jpeg, pos, jpeg.size - pos)
        return out.toByteArray()
    }

    /**
     * Extrae el video de una foto viva (de Momento, Google o Samsung) a [dest].
     * Devuelve true si encontró un video.
     */
    fun extractVideo(source: File, dest: File): Boolean {
        val len = source.length()
        if (len < 64) return false
        val head = ByteArray(minOf(len, 256 * 1024L).toInt())
        RandomAccessFile(source, "r").use { raf ->
            raf.readFully(head)
            val text = String(head, Charsets.ISO_8859_1)

            var start = -1L
            Regex("""Item:Semantic="MotionPhoto"[^>]*?Item:Length="(\d+)"""").find(text)?.let {
                start = len - it.groupValues[1].toLong()
            }
            if (start < 0) Regex("""Item:Length="(\d+)"[^>]*?Item:Semantic="MotionPhoto"""").find(text)?.let {
                start = len - it.groupValues[1].toLong()
            }
            if (start < 0) Regex("""MicroVideoOffset="(\d+)"""").find(text)?.let {
                start = len - it.groupValues[1].toLong()
            }
            if (start <= 0 || start >= len) {
                start = findSamsungMarker(raf, len)
            }
            if (start <= 0 || start >= len) return false

            // Verificación: un MP4 empieza con una caja "ftyp".
            raf.seek(start + 4)
            val tag = ByteArray(4); raf.readFully(tag)
            if (String(tag, Charsets.US_ASCII) != "ftyp") return false

            dest.outputStream().use { o ->
                raf.seek(start)
                val buf = ByteArray(256 * 1024)
                var remaining = len - start
                while (remaining > 0) {
                    val n = raf.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                    if (n <= 0) break
                    o.write(buf, 0, n); remaining -= n
                }
            }
            return true
        }
    }

    private fun findSamsungMarker(raf: RandomAccessFile, len: Long): Long {
        val marker = SAMSUNG_NAME.toByteArray(Charsets.US_ASCII)
        val chunk = ByteArray(1 shl 20)
        var offset = 0L
        while (offset < len) {
            raf.seek(offset)
            val n = raf.read(chunk)
            if (n <= 0) break
            val idx = indexOf(chunk, n, marker)
            if (idx >= 0) return offset + idx + marker.size
            offset += (n - marker.size).coerceAtLeast(1)
        }
        return -1
    }

    private fun indexOf(data: ByteArray, size: Int, pat: ByteArray): Int {
        outer@ for (i in 0..size - pat.size) {
            for (j in pat.indices) if (data[i + j] != pat[j]) continue@outer
            return i
        }
        return -1
    }

    fun readAll(input: InputStream): ByteArray = input.use { it.readBytes() }
}
