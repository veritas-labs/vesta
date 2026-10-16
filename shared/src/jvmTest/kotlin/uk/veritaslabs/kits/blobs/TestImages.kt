package uk.veritaslabs.kits.blobs

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** A JPEG of [w] x [h] whose pixels come from [colour], optionally with an EXIF block holding [orientation] and [secret] text. */
fun testJpeg(w: Int, h: Int, orientation: Int? = null, secret: String? = null, colour: (Int, Int) -> Int): ByteArray {
    val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until h) for (x in 0 until w) image.setRGB(x, y, colour(x, y))
    val plain = ByteArrayOutputStream().also { check(ImageIO.write(image, "jpg", it)) }.toByteArray()
    if (orientation == null && secret == null) return plain
    // TIFF header, one IFD entry (Orientation, SHORT), then the secret text, inside an APP1 "Exif" segment right after SOI.
    val tiff = ByteArrayOutputStream().apply {
        write(byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), 0x2A, 0, 8, 0, 0, 0))
        write(byteArrayOf(1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0, (orientation ?: 1).toByte(), 0, 0, 0, 0, 0, 0, 0))
        write((secret ?: "").encodeToByteArray())
    }.toByteArray()
    val payload = "Exif\u0000\u0000".encodeToByteArray() + tiff
    val length = payload.size + 2
    val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    return plain.copyOfRange(0, 2) + segment + plain.copyOfRange(2, plain.size)
}
