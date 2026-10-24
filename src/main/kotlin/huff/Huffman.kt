package huff

import java.io.InputStream
import java.io.OutputStream

/** Raised for input that is not a valid .huff stream, so the CLI can report it without a stack trace. */
class FormatException(message: String) : Exception(message)

/**
 * Longest code length the format allows. Capping it keeps every code inside a
 * 32-bit write and bounds the decoder's fallback search; the cost is a
 * negligible loss of optimality on pathological (Fibonacci-like) inputs.
 */
const val MAX_CODE_LENGTH = 24

/** Width of the decoder's fast lookup table. Codes at most this long resolve in one array access. */
const val TABLE_BITS = 12

private const val ALPHABET = 256
private const val LENGTH_FIELD_BITS = 5
private val MAGIC = byteArrayOf('H'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte(), '1'.code.toByte())

private class Node(
    val weight: Long,
    /** Insertion order; makes ties deterministic so identical input always yields identical output. */
    val order: Int,
    val symbol: Int,
    val left: Node?,
    val right: Node?,
)

private val nodeOrder = Comparator<Node> { a, b ->
    if (a.weight != b.weight) a.weight.compareTo(b.weight) else a.order.compareTo(b.order)
}

fun countFrequencies(data: ByteArray): LongArray {
    val freq = LongArray(ALPHABET)
    for (b in data) freq[b.toInt() and 0xFF]++
    return freq
}

/**
 * Returns the code length per symbol (0 for symbols that never occur).
 * Only lengths are kept because canonical code assignment can rebuild the
 * exact codes from them, so the file header never has to carry the tree.
 */
fun codeLengths(frequencies: LongArray): IntArray {
    require(frequencies.size == ALPHABET)
    var freq = frequencies.copyOf()
    while (true) {
        val lengths = buildLengths(freq)
        if (lengths.max() <= MAX_CODE_LENGTH) return lengths
        // Halving flattens the distribution, which shortens the deepest leaves; repeat until the cap holds.
        freq = LongArray(ALPHABET) { if (freq[it] == 0L) 0L else (freq[it] + 1) / 2 }
    }
}

private fun buildLengths(freq: LongArray): IntArray {
    val lengths = IntArray(ALPHABET)
    val heap = MinHeap(nodeOrder)
    var order = 0
    for (s in 0 until ALPHABET) if (freq[s] > 0) heap.push(Node(freq[s], order++, s, null, null))
    when (heap.size) {
        0 -> return lengths
        // A lone symbol would get a zero-length code, which cannot be decoded; force one bit.
        1 -> { lengths[heap.pop().symbol] = 1; return lengths }
    }
    while (heap.size > 1) {
        val a = heap.pop()
        val b = heap.pop()
        heap.push(Node(a.weight + b.weight, order++, -1, a, b))
    }
    // Iterative walk: tree depth can reach 255, which is too deep to trust to recursion on small stacks.
    val stack = ArrayDeque<Pair<Node, Int>>()
    stack.addLast(heap.pop() to 0)
    while (stack.isNotEmpty()) {
        val (node, depth) = stack.removeLast()
        if (node.left == null) {
            lengths[node.symbol] = depth
        } else {
            stack.addLast(node.left to depth + 1)
            stack.addLast(node.right!! to depth + 1)
        }
    }
    return lengths
}

/**
 * Canonical assignment: codes of equal length are consecutive integers in
 * symbol order. Encoder and decoder both derive codes this way, so they only
 * ever need to agree on the lengths.
 */
class CanonicalCode(val lengths: IntArray) {
    val codes = IntArray(ALPHABET)
    val maxLength: Int = lengths.max()

    /** Symbols ordered by (length, symbol); the decoder indexes into this by rank. */
    internal val sortedSymbols: IntArray
    internal val firstCode = IntArray(MAX_CODE_LENGTH + 2)
    internal val countAtLength = IntArray(MAX_CODE_LENGTH + 2)
    internal val firstIndex = IntArray(MAX_CODE_LENGTH + 2)

    init {
        for (l in lengths) {
            if (l < 0 || l > MAX_CODE_LENGTH) throw FormatException("invalid code length $l")
            if (l > 0) countAtLength[l]++
        }
        // Kraft equality guards against corrupt headers that would make decoding ambiguous or run off the table.
        var kraft = 0L
        for (l in 1..MAX_CODE_LENGTH) kraft += countAtLength[l].toLong() shl (MAX_CODE_LENGTH - l)
        val used = lengths.count { it > 0 }
        val full = 1L shl MAX_CODE_LENGTH
        if (used > 1 && kraft != full) throw FormatException("code lengths do not form a complete prefix code")
        if (used == 1 && kraft != full / 2) throw FormatException("invalid single-symbol code")

        var code = 0
        var index = 0
        for (l in 1..MAX_CODE_LENGTH) {
            code = (code + countAtLength[l - 1]) shl 1
            firstCode[l] = code
            firstIndex[l] = index
            index += countAtLength[l]
        }
        sortedSymbols = IntArray(index)
        val next = firstIndex.copyOf()
        val nextCode = firstCode.copyOf()
        for (s in 0 until ALPHABET) {
            val l = lengths[s]
            if (l > 0) {
                codes[s] = nextCode[l]++
                sortedSymbols[next[l]++] = s
            }
        }
    }
}

private fun writeLong(out: OutputStream, v: Long) {
    for (i in 7 downTo 0) out.write(((v ushr (8 * i)) and 0xFF).toInt())
}

private fun readLong(input: InputStream): Long {
    var v = 0L
    repeat(8) {
        val b = input.read()
        if (b < 0) throw FormatException("truncated header")
        v = (v shl 8) or b.toLong()
    }
    return v
}

/** Writes a complete .huff stream. Takes the whole input because the code table needs global frequencies before the first output bit. */
fun compress(data: ByteArray, out: OutputStream) {
    out.write(MAGIC)
    writeLong(out, data.size.toLong())
    if (data.isEmpty()) return

    val code = CanonicalCode(codeLengths(countFrequencies(data)))
    val writer = BitWriter(out)
    for (s in 0 until ALPHABET) writer.write(code.lengths[s], LENGTH_FIELD_BITS)
    val codes = code.codes
    val lens = code.lengths
    for (b in data) {
        val s = b.toInt() and 0xFF
        writer.write(codes[s], lens[s])
    }
    writer.finish()
}

/**
 * Streams decoded bytes to [out] so memory stays flat for large archives.
 * [useTable] exists so benchmarks can compare the lookup-table path with the
 * plain canonical search.
 */
fun decompress(input: InputStream, out: OutputStream, useTable: Boolean = true) {
    val magic = ByteArray(MAGIC.size)
    var got = 0
    while (got < magic.size) {
        val n = input.read(magic, got, magic.size - got)
        if (n < 0) throw FormatException("not a huff file (too short)")
        got += n
    }
    if (!magic.contentEquals(MAGIC)) throw FormatException("not a huff file (bad magic)")
    val total = readLong(input)
    if (total < 0) throw FormatException("invalid length in header")
    if (total == 0L) return

    val reader = BitReader(input)
    val lengths = IntArray(ALPHABET) { reader.read(LENGTH_FIELD_BITS) }
    if (reader.overran) throw FormatException("truncated header")
    val code = CanonicalCode(lengths)

    val table = if (useTable) buildTable(code) else null
    val outBuf = ByteArray(1 shl 16)
    var outPos = 0
    var remaining = total
    val maxLen = code.maxLength
    val tableBits = minOf(TABLE_BITS, maxLen)

    while (remaining > 0) {
        var symbol = -1
        var len = 0
        if (table != null) {
            val entry = table[reader.peek(tableBits)]
            if (entry != 0) {
                symbol = entry ushr 8
                len = entry and 0xFF
            }
        }
        if (symbol < 0) {
            val window = reader.peek(maxLen)
            for (l in 1..maxLen) {
                val c = window ushr (maxLen - l)
                val offset = c - code.firstCode[l]
                if (offset >= 0 && offset < code.countAtLength[l]) {
                    symbol = code.sortedSymbols[code.firstIndex[l] + offset]
                    len = l
                    break
                }
            }
            if (symbol < 0) throw FormatException("corrupt data: no matching code")
        }
        reader.skip(len)
        outBuf[outPos++] = symbol.toByte()
        if (outPos == outBuf.size) {
            out.write(outBuf, 0, outPos)
            outPos = 0
        }
        remaining--
    }
    if (reader.overran) throw FormatException("truncated data")
    out.write(outBuf, 0, outPos)
}

/**
 * Entry layout: (symbol shl 8) or length, or 0 when the code is longer than
 * the table width. Length is never 0 for a real entry, so 0 is a safe "miss".
 */
private fun buildTable(code: CanonicalCode): IntArray {
    val bits = minOf(TABLE_BITS, code.maxLength)
    val table = IntArray(1 shl bits)
    for (s in 0 until ALPHABET) {
        val l = code.lengths[s]
        if (l in 1..bits) {
            val base = code.codes[s] shl (bits - l)
            val span = 1 shl (bits - l)
            val entry = (s shl 8) or l
            for (i in 0 until span) table[base + i] = entry
        }
    }
    return table
}

/** Summary used by the `stats` command to show how close Huffman gets to the entropy bound. */
data class Stats(
    val originalBytes: Long,
    val compressedBytes: Long,
    val entropyBitsPerSymbol: Double,
    val averageCodeBits: Double,
    val distinctSymbols: Int,
    val maxCodeLength: Int,
    val lengths: IntArray,
    val frequencies: LongArray,
)

fun computeStats(data: ByteArray): Stats {
    val freq = countFrequencies(data)
    val n = data.size.toLong()
    if (n == 0L) return Stats(0, 12, 0.0, 0.0, 0, 0, IntArray(ALPHABET), freq)
    val lengths = codeLengths(freq)
    var entropy = 0.0
    var bits = 0L
    for (s in 0 until ALPHABET) {
        if (freq[s] > 0) {
            val p = freq[s].toDouble() / n
            entropy -= p * (Math.log(p) / Math.log(2.0))
            bits += freq[s] * lengths[s]
        }
    }
    val headerBytes = 4L + 8L + (ALPHABET * LENGTH_FIELD_BITS + 7) / 8
    return Stats(
        originalBytes = n,
        compressedBytes = headerBytes + (bits + 7) / 8,
        entropyBitsPerSymbol = entropy,
        averageCodeBits = bits.toDouble() / n,
        distinctSymbols = freq.count { it > 0 },
        maxCodeLength = lengths.max(),
        lengths = lengths,
        frequencies = freq,
    )
}
