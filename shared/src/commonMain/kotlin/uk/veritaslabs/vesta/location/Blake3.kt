package uk.veritaslabs.vesta.location

/**
 * BLAKE3 (default hash mode, 32-byte output), written out here so chunk addresses can be checked on every target without a
 * dependency. An Autonomi chunk address is the BLAKE3 hash of the chunk's bytes. Follows the reference implementation:
 * 1024-byte chunks of 64-byte blocks, joined by a binary tree of parent nodes.
 */
object Blake3 {
    private const val CHUNK_LEN = 1024
    private const val BLOCK_LEN = 64
    private const val CHUNK_START = 1
    private const val CHUNK_END = 2
    private const val PARENT = 4
    private const val ROOT = 8

    private val IV = intArrayOf(
        0x6A09E667, 0xBB67AE85.toInt(), 0x3C6EF372, 0xA54FF53A.toInt(),
        0x510E527F, 0x9B05688C.toInt(), 0x1F83D9AB, 0x5BE0CD19,
    )
    private val PERMUTATION = intArrayOf(2, 6, 3, 10, 7, 0, 4, 13, 1, 11, 12, 5, 9, 14, 15, 8)

    /** A compression that is not yet run: it becomes a chaining value, or the root hash when finalised. */
    private class Output(
        val cv: IntArray,
        val block: IntArray,
        val counter: Long,
        val blockLen: Int,
        val flags: Int,
    ) {
        fun chainingValue(): IntArray = compress(cv, block, counter, blockLen, flags).copyOf(8)
        fun root(): ByteArray {
            val words = compress(cv, block, 0L, blockLen, flags or ROOT)
            return ByteArray(32) { (words[it / 4] ushr (8 * (it % 4))).toByte() }
        }
    }

    fun hash(input: ByteArray): ByteArray {
        val chunkCount = maxOf(1, (input.size + CHUNK_LEN - 1) / CHUNK_LEN)
        val stack = ArrayList<IntArray>()
        for (index in 0 until chunkCount - 1) {
            var cv = chunkOutput(input, index).chainingValue()
            var total = index + 1
            while (total and 1 == 0) {
                cv = parentOutput(stack.removeAt(stack.lastIndex), cv).chainingValue()
                total = total shr 1
            }
            stack.add(cv)
        }
        var output = chunkOutput(input, chunkCount - 1)
        while (stack.isNotEmpty()) {
            output = parentOutput(stack.removeAt(stack.lastIndex), output.chainingValue())
        }
        return output.root()
    }

    private fun chunkOutput(input: ByteArray, index: Int): Output {
        val start = index * CHUNK_LEN
        val end = minOf(input.size, start + CHUNK_LEN)
        val blocks = maxOf(1, (end - start + BLOCK_LEN - 1) / BLOCK_LEN)
        var cv = IV
        for (b in 0 until blocks) {
            val from = start + b * BLOCK_LEN
            val len = minOf(BLOCK_LEN, end - from)
            val words = IntArray(16)
            for (i in 0 until len) words[i / 4] = words[i / 4] or ((input[from + i].toInt() and 0xFF) shl (8 * (i % 4)))
            val flags = (if (b == 0) CHUNK_START else 0) or (if (b == blocks - 1) CHUNK_END else 0)
            val output = Output(cv, words, index.toLong(), len, flags)
            if (b == blocks - 1) return output
            cv = output.chainingValue()
        }
        error("unreachable")
    }

    private fun parentOutput(left: IntArray, right: IntArray): Output =
        Output(IV, left + right, 0L, BLOCK_LEN, PARENT)

    private fun compress(cv: IntArray, block: IntArray, counter: Long, blockLen: Int, flags: Int): IntArray {
        val v = intArrayOf(
            cv[0], cv[1], cv[2], cv[3], cv[4], cv[5], cv[6], cv[7],
            IV[0], IV[1], IV[2], IV[3],
            counter.toInt(), (counter ushr 32).toInt(), blockLen, flags,
        )
        var m = block
        repeat(7) { round ->
            g(v, 0, 4, 8, 12, m[0], m[1])
            g(v, 1, 5, 9, 13, m[2], m[3])
            g(v, 2, 6, 10, 14, m[4], m[5])
            g(v, 3, 7, 11, 15, m[6], m[7])
            g(v, 0, 5, 10, 15, m[8], m[9])
            g(v, 1, 6, 11, 12, m[10], m[11])
            g(v, 2, 7, 8, 13, m[12], m[13])
            g(v, 3, 4, 9, 14, m[14], m[15])
            if (round < 6) m = IntArray(16) { m[PERMUTATION[it]] }
        }
        for (i in 0 until 8) {
            v[i] = v[i] xor v[i + 8]
            v[i + 8] = v[i + 8] xor cv[i]
        }
        return v
    }

    private fun g(v: IntArray, a: Int, b: Int, c: Int, d: Int, x: Int, y: Int) {
        v[a] = v[a] + v[b] + x
        v[d] = (v[d] xor v[a]).rotateRight(16)
        v[c] = v[c] + v[d]
        v[b] = (v[b] xor v[c]).rotateRight(12)
        v[a] = v[a] + v[b] + y
        v[d] = (v[d] xor v[a]).rotateRight(8)
        v[c] = v[c] + v[d]
        v[b] = (v[b] xor v[c]).rotateRight(7)
    }
}

/** Accepts a chunk only when its BLAKE3 hash is its address. */
object Blake3ChunkVerifier : ChunkVerifier {
    override fun matches(addressHex: String, bytes: ByteArray): Boolean =
        Blake3.hash(bytes).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
            .equals(addressHex, ignoreCase = true)
}
