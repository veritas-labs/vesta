package uk.veritaslabs.kits.blobs

import kotlin.io.encoding.Base64
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A decoded placeholder: [argb] holds `w * h` non-premultiplied `0xAARRGGBB` pixels, row by row. */
class PlaceholderPixels(val w: Int, val h: Int, val argb: IntArray)

/**
 * The tiny blurred stand-in for a photo that lives in the document next to its hash (ADR-023, Decision on `blur`): a 25-30 byte
 * [ThumbHash](https://evanw.github.io/thumbhash/) as text. A stored value is `<scheme>:<payload>`; the only scheme is `t:` (ThumbHash bytes in
 * unpadded base64), and the prefix is there so another algorithm can be adopted later without breaking documents that hold this one.
 *
 * A port of the reference JavaScript encoder and decoder. Rounding is `floor(x + 0.5)` like JS `Math.round`.
 */
object Placeholder {

    private const val Scheme = "t:"
    private const val MaxEncodeEdge = 100
    private const val DecodeBase = 32
    private val base64 = Base64.Default.withPadding(Base64.PaddingOption.ABSENT)

    /**
     * Hashes an image of [w] x [h] `0xAARRGGBB` pixels, which must be at most 100 x 100 (a larger image gains nothing and is slow:
     * scale it down first). Throws [IllegalArgumentException] for sizes outside that or a pixel array of the wrong length.
     */
    fun encode(argb: IntArray, w: Int, h: Int): String {
        require(w in 1..MaxEncodeEdge && h in 1..MaxEncodeEdge) { "placeholder source must be 1..$MaxEncodeEdge pixels a side, got ${w}x$h" }
        require(argb.size == w * h) { "expected ${w * h} pixels, got ${argb.size}" }
        return Scheme + base64.encode(encodeBytes(argb, w, h))
    }

    /** The pixels a stored value stands for (about 32 on the long edge), or null for anything that isn't a valid placeholder. Never throws. */
    fun decode(value: String): PlaceholderPixels? {
        if (!value.startsWith(Scheme)) return null
        val bytes = runCatching { base64.decode(value.removePrefix(Scheme)) }.getOrNull() ?: return null
        return runCatching { decodeBytes(bytes) }.getOrNull()
    }

    /** Width over height of the photo the value was made from, as best the hash knows it; null for an invalid value. */
    fun aspectRatio(value: String): Double? {
        if (!value.startsWith(Scheme)) return null
        val bytes = runCatching { base64.decode(value.removePrefix(Scheme)) }.getOrNull()?.takeIf { it.size >= 5 } ?: return null
        return aspectRatio(bytes)
    }

    // --- Encoding ---

    private class Channel(val dc: Double, val ac: DoubleArray, val scale: Double)

    internal fun encodeBytes(argb: IntArray, w: Int, h: Int): ByteArray {
        val n = w * h
        // The average colour, weighted by alpha.
        var avgR = 0.0
        var avgG = 0.0
        var avgB = 0.0
        var avgA = 0.0
        for (i in 0 until n) {
            val p = argb[i]
            val alpha = ((p ushr 24) and 255) / 255.0
            avgR += alpha / 255.0 * ((p shr 16) and 255)
            avgG += alpha / 255.0 * ((p shr 8) and 255)
            avgB += alpha / 255.0 * (p and 255)
            avgA += alpha
        }
        if (avgA > 0.0) {
            avgR /= avgA
            avgG /= avgA
            avgB /= avgA
        }

        val hasAlpha = avgA < n
        val lLimit = if (hasAlpha) 5 else 7
        val longest = max(w, h)
        val lx = max(1, round(lLimit.toDouble() * w / longest))
        val ly = max(1, round(lLimit.toDouble() * h / longest))
        val l = DoubleArray(n)
        val p = DoubleArray(n)
        val q = DoubleArray(n)
        val a = DoubleArray(n)

        // RGBA to LPQA, with transparent pixels filled in with the average colour.
        for (i in 0 until n) {
            val px = argb[i]
            val alpha = ((px ushr 24) and 255) / 255.0
            val r = avgR * (1 - alpha) + alpha / 255.0 * ((px shr 16) and 255)
            val g = avgG * (1 - alpha) + alpha / 255.0 * ((px shr 8) and 255)
            val b = avgB * (1 - alpha) + alpha / 255.0 * (px and 255)
            l[i] = (r + g + b) / 3
            p[i] = (r + g) / 2 - b
            q[i] = r - g
            a[i] = alpha
        }

        val lChannel = encodeChannel(l, w, h, max(3, lx), max(3, ly))
        val pChannel = encodeChannel(p, w, h, 3, 3)
        val qChannel = encodeChannel(q, w, h, 3, 3)
        val aChannel = if (hasAlpha) encodeChannel(a, w, h, 5, 5) else null

        val isLandscape = w > h
        var header24 = round(63 * lChannel.dc)
        header24 = header24 or (round(31.5 + 31.5 * pChannel.dc) shl 6)
        header24 = header24 or (round(31.5 + 31.5 * qChannel.dc) shl 12)
        header24 = header24 or (round(31 * lChannel.scale) shl 18)
        if (hasAlpha) header24 = header24 or (1 shl 23)
        var header16 = if (isLandscape) ly else lx
        header16 = header16 or (round(63 * pChannel.scale) shl 3)
        header16 = header16 or (round(63 * qChannel.scale) shl 9)
        if (isLandscape) header16 = header16 or (1 shl 15)

        val acs = listOfNotNull(lChannel.ac, pChannel.ac, qChannel.ac, aChannel?.ac)
        val acStart = if (hasAlpha) 6 else 5
        val acCount = acs.sumOf { it.size }
        val hash = ByteArray(acStart + (acCount + 1) / 2)
        hash[0] = header24.toByte()
        hash[1] = (header24 shr 8).toByte()
        hash[2] = (header24 shr 16).toByte()
        hash[3] = header16.toByte()
        hash[4] = (header16 shr 8).toByte()
        aChannel?.let { hash[5] = (round(15 * it.dc) or (round(15 * it.scale) shl 4)).toByte() }
        var index = 0
        for (ac in acs) for (f in ac) {
            val at = acStart + (index shr 1)
            hash[at] = (hash[at].toInt() or (round(15 * f) shl ((index and 1) shl 2))).toByte()
            index++
        }
        return hash
    }

    /** The DCT of [channel] as a constant term and the normalised varying terms in the triangle `cx * ny < nx * (ny - cy)`. */
    private fun encodeChannel(channel: DoubleArray, w: Int, h: Int, nx: Int, ny: Int): Channel {
        var dc = 0.0
        val ac = ArrayList<Double>()
        var scale = 0.0
        val fx = DoubleArray(w)
        for (cy in 0 until ny) {
            var cx = 0
            while (cx * ny < nx * (ny - cy)) {
                var f = 0.0
                for (x in 0 until w) fx[x] = cos(PI / w * cx * (x + 0.5))
                for (y in 0 until h) {
                    val fy = cos(PI / h * cy * (y + 0.5))
                    for (x in 0 until w) f += channel[x + y * w] * fx[x] * fy
                }
                f /= (w * h)
                if (cx != 0 || cy != 0) {
                    ac += f
                    scale = max(scale, abs(f))
                } else {
                    dc = f
                }
                cx++
            }
        }
        if (scale != 0.0) for (i in ac.indices) ac[i] = 0.5 + 0.5 / scale * ac[i]
        return Channel(dc, ac.toDoubleArray(), scale)
    }

    // --- Decoding ---

    internal fun decodeBytes(hash: ByteArray): PlaceholderPixels? {
        if (hash.size < 5) return null
        fun byte(i: Int) = hash[i].toInt() and 255
        val header24 = byte(0) or (byte(1) shl 8) or (byte(2) shl 16)
        val header16 = byte(3) or (byte(4) shl 8)
        val lDc = (header24 and 63) / 63.0
        val pDc = ((header24 shr 6) and 63) / 31.5 - 1
        val qDc = ((header24 shr 12) and 63) / 31.5 - 1
        val lScale = ((header24 shr 18) and 31) / 31.0
        val hasAlpha = (header24 shr 23) != 0
        val pScale = ((header16 shr 3) and 63) / 63.0
        val qScale = ((header16 shr 9) and 63) / 63.0
        val isLandscape = (header16 shr 15) != 0
        val lMax = if (hasAlpha) 5 else 7
        val lx = max(3, if (isLandscape) lMax else header16 and 7)
        val ly = max(3, if (isLandscape) header16 and 7 else lMax)
        if (hasAlpha && hash.size < 6) return null
        val aDc = if (hasAlpha) (byte(5) and 15) / 15.0 else 1.0
        val aScale = if (hasAlpha) (byte(5) shr 4) / 15.0 else 0.0

        val acStart = if (hasAlpha) 6 else 5
        var index = 0
        var truncated = false
        fun decodeChannel(nx: Int, ny: Int, scale: Double): DoubleArray {
            val ac = ArrayList<Double>()
            for (cy in 0 until ny) {
                var cx = if (cy == 0) 1 else 0
                while (cx * ny < nx * (ny - cy)) {
                    val at = acStart + (index shr 1)
                    if (at >= hash.size) {
                        truncated = true
                        ac += 0.0
                    } else {
                        ac += (((byte(at) shr ((index and 1) shl 2)) and 15) / 7.5 - 1) * scale
                    }
                    index++
                    cx++
                }
            }
            return ac.toDoubleArray()
        }
        val lAc = decodeChannel(lx, ly, lScale)
        val pAc = decodeChannel(3, 3, pScale * 1.25)
        val qAc = decodeChannel(3, 3, qScale * 1.25)
        val aAc = if (hasAlpha) decodeChannel(5, 5, aScale) else DoubleArray(0)
        if (truncated) return null

        val ratio = aspectRatio(hash)
        val w = round(if (ratio > 1) DecodeBase.toDouble() else DecodeBase * ratio)
        val h = round(if (ratio > 1) DecodeBase / ratio else DecodeBase.toDouble())
        if (w < 1 || h < 1) return null
        val out = IntArray(w * h)
        val fx = DoubleArray(max(lx, if (hasAlpha) 5 else 3))
        val fy = DoubleArray(max(ly, if (hasAlpha) 5 else 3))
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                var l = lDc
                var p = pDc
                var q = qDc
                var a = aDc
                for (cx in fx.indices) fx[cx] = cos(PI / w * (x + 0.5) * cx)
                for (cy in fy.indices) fy[cy] = cos(PI / h * (y + 0.5) * cy)

                var j = 0
                for (cy in 0 until ly) {
                    val fy2 = fy[cy] * 2
                    var cx = if (cy != 0) 0 else 1
                    while (cx * ly < lx * (ly - cy)) {
                        l += lAc[j] * fx[cx] * fy2
                        cx++
                        j++
                    }
                }
                j = 0
                for (cy in 0 until 3) {
                    val fy2 = fy[cy] * 2
                    var cx = if (cy != 0) 0 else 1
                    while (cx < 3 - cy) {
                        val f = fx[cx] * fy2
                        p += pAc[j] * f
                        q += qAc[j] * f
                        cx++
                        j++
                    }
                }
                if (hasAlpha) {
                    j = 0
                    for (cy in 0 until 5) {
                        val fy2 = fy[cy] * 2
                        var cx = if (cy != 0) 0 else 1
                        while (cx < 5 - cy) {
                            a += aAc[j] * fx[cx] * fy2
                            cx++
                            j++
                        }
                    }
                }

                val b = l - 2.0 / 3.0 * p
                val r = (3 * l - b + q) / 2
                val g = r - q
                out[i++] = (channel(a) shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
            }
        }
        return PlaceholderPixels(w, h, out)
    }

    private fun aspectRatio(hash: ByteArray): Double {
        val hasAlpha = (hash[2].toInt() and 0x80) != 0
        val lMax = if (hasAlpha) 5 else 7
        val lMin = hash[3].toInt() and 7
        val isLandscape = (hash[4].toInt() and 0x80) != 0
        val lx = if (isLandscape) lMax else lMin
        val ly = if (isLandscape) lMin else lMax
        return if (ly == 0) 1.0 else lx.toDouble() / ly
    }

    private fun channel(value: Double): Int = max(0, round(255 * min(1.0, value)))

    private fun round(value: Double): Int = floor(value + 0.5).toInt()
}
