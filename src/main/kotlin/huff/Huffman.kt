package huff

import java.io.InputStream
import java.io.OutputStream

/** Raised for input that is not a valid .huff stream, so the CLI can report it without a stack trace. */
class FormatException(message: String) : Exception(message)

/**
 * Longest code length the format allows. Capping it keeps every code inside a
 * 32-bit write, fit the 5-bit length field in the header, and bounds the
 * decoder's fallback search. It only binds on pathological (Fibonacci-like)
 * inputs, where limitedCodeLengths gives the best code possible under it.
 */
const val MAX_CODE_LENGTH = 24

/** Width of the decoder's fast lookup table. Codes at most this long resolve in one array access. */
const val TABLE_BITS = 12

/**
 * Uncompressed bytes per block. A static Huffman code needs the symbol counts
 * before the first output bit, so the encoder has to hold one block at a
 * time; this size is what bounds memory for arbitrarily large input and
 * pipes. It is large enough that the per-block table is a negligible
 * overhead and small enough to track changes in the data's statistics.
 */
const val BLOCK_SIZE = 256 * 1024

/** Largest block the decoder accepts; rejects corrupt length fields instead of looping on garbage. */
private const val MAX_BLOCK_SIZE = 1 shl 24

private const val ALPHABET = 256
private const val LENGTH_FIELD_BITS = 5
private const val BLOCK_LENGTH_BITS = 32
private const val BLOCK_HEADER_BYTES = (BLOCK_LENGTH_BITS + ALPHABET * LENGTH_FIELD_BITS) / 8

/**
 * Set in a block's length word when its payload is the raw bytes instead of
 * Huffman codes. Lengths never exceed MAX_BLOCK_SIZE (25 bits), so the top bit is free.
 */
private const val STORED_FLAG = 1 shl 31
private const val STORED_HEADER_BYTES = BLOCK_LENGTH_BITS / 8
private val MAGIC = byteArrayOf('H'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte(), '2'.code.toByte())

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

fun countFrequencies(data: ByteArray, length: Int = data.size): LongArray {
    val freq = LongArray(ALPHABET)
    for (i in 0 until length) freq[data[i].toInt() and 0xFF]++
    return freq
}

/**
 * Returns the code length per symbol (0 for symbols that never occur).
 * Only lengths are kept because canonical code assignment can rebuild the
 * exact codes from them, so the file header never has to carry the tree.
 */
fun codeLengths(frequencies: LongArray): IntArray {
    require(frequencies.size == ALPHABET)
    val lengths = buildLengths(frequencies)
    // Plain Huffman is already optimal and nearly always within the cap; the
    // slower length-limited construction only runs for pathological inputs.
    return if (lengths.max() <= MAX_CODE_LENGTH) lengths else limitedCodeLengths(frequencies, MAX_CODE_LENGTH)
}

/**
 * Optimal code lengths subject to no code being longer than [maxLength]
 * (the package-merge algorithm). Truncating or re-weighting a Huffman tree
 * to fit a cap gives a valid but needlessly longer code; this finds the
 * shortest total output that any prefix code under the cap can achieve, so
 * the cap costs only what it must.
 *
 * Idea: each symbol is a "coin" worth 2^-l for every length l it may use,
 * and a code is complete when the chosen coins sum to n-1. Level d holds the
 * symbols' weights merged with pairs ("packages") from the level below; the
 * 2n-2 cheapest items at the top level say how often each symbol is used,
 * and that count is its code length.
 */
internal fun limitedCodeLengths(frequencies: LongArray, maxLength: Int): IntArray {
    val symbols = (0 until ALPHABET)
        .filter { frequencies[it] > 0 }
        .sortedWith(compareBy({ frequencies[it] }, { it }))
    val n = symbols.size
    val lengths = IntArray(ALPHABET)
    if (n == 0) return lengths
    if (n == 1) { lengths[symbols[0]] = 1; return lengths }
    require(maxLength >= 1 && n.toLong() <= (1L shl maxLength)) { "$n symbols cannot fit in codes of at most $maxLength bits" }

    // levels[0] is the deepest level (codes of maxLength bits); each later level merges in packages from the one before.
    val levelWeights = ArrayList<LongArray>()
    val levelIsLeaf = ArrayList<BooleanArray>()
    for (d in 0 until maxLength) {
        val packages = if (d == 0) 0 else levelWeights[d - 1].size / 2
        val w = LongArray(n + packages)
        val leaf = BooleanArray(n + packages)
        var li = 0
        var pi = 0
        for (k in w.indices) {
            val packageWeight = if (pi < packages) levelWeights[d - 1][2 * pi] + levelWeights[d - 1][2 * pi + 1] else Long.MAX_VALUE
            // Leaves win ties so the result is the same on every run.
            if (li < n && frequencies[symbols[li]] <= packageWeight) {
                w[k] = frequencies[symbols[li++]]
                leaf[k] = true
            } else {
                w[k] = packageWeight
                pi++
            }
        }
        levelWeights.add(w)
        levelIsLeaf.add(leaf)
    }

    var take = 2 * n - 2
    for (d in maxLength - 1 downTo 0) {
        val leaf = levelIsLeaf[d]
        var leaves = 0
        for (k in 0 until take) if (leaf[k]) leaves++
        for (i in 0 until leaves) lengths[symbols[i]]++
        take = 2 * (take - leaves)
    }
    return lengths
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
        if (used == 0) throw FormatException("block has no symbols")
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

/**
 * Reads up to a full block from [input]. A pipe can deliver short reads, and
 * a block that is not filled would waste a table header on a few bytes, so
 * this keeps reading until the block is full or the input ends.
 */
private fun readBlock(input: InputStream, buf: ByteArray): Int = input.readNBytes(buf, 0, buf.size)

/**
 * Compresses [input] block by block, so memory stays bounded by [blockSize]
 * no matter how much data (or how slow a pipe) is behind the stream.
 * [blockSize] is a parameter so tests and benchmarks can exercise block
 * boundaries and block-size effects on small data.
 */
fun compress(input: InputStream, out: OutputStream, blockSize: Int = BLOCK_SIZE) {
    require(blockSize in 1..MAX_BLOCK_SIZE)
    out.write(MAGIC)
    val writer = BitWriter(out)
    val buf = ByteArray(blockSize)
    while (true) {
        val n = readBlock(input, buf)
        if (n == 0) break
        writeBlock(writer, buf, n)
    }
    // An explicit end marker is what lets the decoder tell a complete stream from one cut short at a block boundary.
    writer.write(0, BLOCK_LENGTH_BITS)
    writer.finish()
}

/**
 * How one block will be written. The encoder and `stats` both go through this
 * so the size `stats` reports is always the size compress produces.
 */
private class BlockPlan(
    val length: Int,
    val lengths: IntArray,
    val frequencies: LongArray,
    val payloadBits: Long,
    val stored: Boolean,
) {
    /** Bytes the block occupies in the stream, header included. */
    val encodedBytes: Long
        get() = if (stored) STORED_HEADER_BYTES + length.toLong() else BLOCK_HEADER_BYTES + (payloadBits + 7) / 8
}

private fun planBlock(data: ByteArray, n: Int): BlockPlan {
    val freq = countFrequencies(data, n)
    val lengths = codeLengths(freq)
    var bits = 0L
    for (s in 0 until ALPHABET) bits += freq[s] * lengths[s]
    val huffmanBytes = BLOCK_HEADER_BYTES + (bits + 7) / 8
    val storedBytes = STORED_HEADER_BYTES + n.toLong()
    // Data that Huffman coding cannot shrink (already compressed, encrypted, random)
    // would otherwise grow by the code table; storing it caps the damage at the block header.
    return BlockPlan(n, lengths, freq, bits, stored = storedBytes <= huffmanBytes)
}

private fun writeBlock(writer: BitWriter, data: ByteArray, n: Int) {
    val plan = planBlock(data, n)
    if (plan.stored) {
        writer.write(n or STORED_FLAG, BLOCK_LENGTH_BITS)
        writer.writeBytes(data, 0, n)
        return
    }
    val code = CanonicalCode(plan.lengths)
    writer.write(n, BLOCK_LENGTH_BITS)
    for (s in 0 until ALPHABET) writer.write(code.lengths[s], LENGTH_FIELD_BITS)
    val codes = code.codes
    val lens = code.lengths
    for (i in 0 until n) {
        val s = data[i].toInt() and 0xFF
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

    val reader = BitReader(input)
    val outBuf = ByteArray(1 shl 16)
    var outPos = 0
    while (true) {
        val word = reader.read(BLOCK_LENGTH_BITS)
        if (reader.overran) throw FormatException("truncated data (missing end marker)")
        if (word == 0) break
        val stored = word and STORED_FLAG != 0
        val blockLength = word and STORED_FLAG.inv()
        if (blockLength == 0 || blockLength > MAX_BLOCK_SIZE) throw FormatException("invalid block length")

        if (stored) {
            // Written bytes must stay in order, so anything still buffered goes out before the raw payload.
            out.write(outBuf, 0, outPos)
            outPos = 0
            var left = blockLength
            while (left > 0) {
                val n = minOf(left, outBuf.size)
                reader.readBytes(outBuf, 0, n)
                out.write(outBuf, 0, n)
                left -= n
            }
            if (reader.overran) throw FormatException("truncated data")
            continue
        }

        val lengths = IntArray(ALPHABET) { reader.read(LENGTH_FIELD_BITS) }
        if (reader.overran) throw FormatException("truncated header")
        val code = CanonicalCode(lengths)

        val table = if (useTable) buildTable(code) else null
        val maxLen = code.maxLength
        val tableBits = minOf(TABLE_BITS, maxLen)
        var remaining = blockLength
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
        reader.alignToByte()
    }
    if (reader.hasMoreData()) throw FormatException("unexpected data after end marker")
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

/**
 * Summary used by the `stats` command to show how close Huffman gets to the
 * entropy bound. Sizes and [averageCodeBits] describe the real block-wise
 * output; [lengths] is the code a single whole-input table would use, which
 * is the order-0 reference the entropy figure applies to.
 */
data class Stats(
    val originalBytes: Long,
    val compressedBytes: Long,
    val blocks: Long,
    /** Blocks written raw because Huffman coding would not have made them smaller. */
    val storedBlocks: Long,
    val entropyBitsPerSymbol: Double,
    val averageCodeBits: Double,
    val distinctSymbols: Int,
    val maxCodeLength: Int,
    val lengths: IntArray,
    val frequencies: LongArray,
)

/** Streams [input] block by block, mirroring compress(), so stats on a pipe use the same bounded memory. */
fun computeStats(input: InputStream, blockSize: Int = BLOCK_SIZE): Stats {
    require(blockSize in 1..MAX_BLOCK_SIZE)
    val total = LongArray(ALPHABET)
    val buf = ByteArray(blockSize)
    var n = 0L
    var blocks = 0L
    var storedBlocks = 0L
    var payloadBits = 0L
    var blockBytes = 0L
    var maxLen = 0
    while (true) {
        val got = readBlock(input, buf)
        if (got == 0) break
        val plan = planBlock(buf, got)
        for (s in 0 until ALPHABET) total[s] += plan.frequencies[s]
        n += got
        blocks++
        blockBytes += plan.encodedBytes
        if (plan.stored) {
            storedBlocks++
            payloadBits += 8L * got
        } else {
            payloadBits += plan.payloadBits
            maxLen = maxOf(maxLen, plan.lengths.max())
        }
    }
    val fixedBytes = MAGIC.size + BLOCK_LENGTH_BITS / 8L
    var entropy = 0.0
    for (s in 0 until ALPHABET) {
        if (total[s] > 0) {
            val p = total[s].toDouble() / n
            entropy -= p * (Math.log(p) / Math.log(2.0))
        }
    }
    return Stats(
        originalBytes = n,
        compressedBytes = fixedBytes + blockBytes,
        blocks = blocks,
        storedBlocks = storedBlocks,
        entropyBitsPerSymbol = entropy,
        averageCodeBits = if (n == 0L) 0.0 else payloadBits.toDouble() / n,
        distinctSymbols = total.count { it > 0 },
        maxCodeLength = maxLen,
        lengths = if (n == 0L) IntArray(ALPHABET) else codeLengths(total),
        frequencies = total,
    )
}
