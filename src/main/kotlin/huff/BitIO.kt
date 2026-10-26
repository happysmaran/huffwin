package huff

import java.io.InputStream
import java.io.OutputStream

/**
 * Huffman codes are not byte-aligned, so output has to be assembled bit by
 * bit. MSB-first ordering is used so that canonical codes compare as
 * integers, which the decoder's lookup table relies on.
 */
internal class BitWriter(private val out: OutputStream) {
    private var acc = 0L
    private var nbits = 0

    fun write(value: Int, count: Int) {
        require(count in 0..32)
        if (count == 0) return
        acc = (acc shl count) or (value.toLong() and ((1L shl count) - 1))
        nbits += count
        while (nbits >= 8) {
            nbits -= 8
            out.write(((acc ushr nbits) and 0xFF).toInt())
        }
    }

    /**
     * Copies raw bytes through for blocks that are stored uncompressed. Stored
     * blocks start on a byte boundary, so bypassing the bit accumulator keeps
     * incompressible data from being slowed down by per-byte bit shuffling.
     */
    fun writeBytes(data: ByteArray, offset: Int, length: Int) {
        check(nbits == 0) { "raw bytes must start on a byte boundary" }
        out.write(data, offset, length)
    }

    /**
     * Pads the final partial byte with zeros. Blocks end on a byte boundary so
     * the decoder can resynchronise without tracking bit offsets across
     * blocks; the padding is ignored because each block states its symbol count.
     */
    fun finish() {
        if (nbits > 0) {
            out.write(((acc shl (8 - nbits)) and 0xFF).toInt())
            nbits = 0
        }
    }
}

/**
 * Decoding needs to look at the next few bits without committing to
 * consuming them, because a code's length is only known once the lookup has
 * matched it. That is why this reader exposes peek/skip rather than a plain read.
 */
internal class BitReader(private val input: InputStream) {
    private val buf = ByteArray(1 shl 16)
    private var pos = 0
    private var lim = 0
    private var acc = 0L
    private var nbits = 0
    private var padBits = 0

    /**
     * True once the decoder has consumed zero bits that were synthesized past
     * the end of input, i.e. the stream was truncated. Padding is synthesized
     * so peek() can always look a full window ahead near the end.
     */
    val overran: Boolean get() = nbits < padBits

    private fun nextByte(): Int {
        if (pos == lim) {
            lim = input.read(buf, 0, buf.size)
            pos = 0
            if (lim <= 0) {
                lim = 0
                return -1
            }
        }
        return buf[pos++].toInt() and 0xFF
    }

    private fun fill() {
        while (nbits <= 48) {
            val b = nextByte()
            if (b < 0) {
                acc = acc shl 8
                padBits += 8
            } else {
                acc = (acc shl 8) or b.toLong()
            }
            nbits += 8
        }
    }

    fun peek(count: Int): Int {
        if (nbits < count) fill()
        return ((acc ushr (nbits - count)) and ((1L shl count) - 1)).toInt()
    }

    fun skip(count: Int) {
        nbits -= count
    }

    fun read(count: Int): Int {
        val v = peek(count)
        skip(count)
        return v
    }

    /**
     * Drops the zero padding the writer added to finish a block. Read-ahead
     * only ever holds whole bytes beyond the current one, so the leftover
     * bits modulo 8 are exactly that padding.
     */
    fun alignToByte() {
        nbits -= nbits % 8
    }

    /**
     * Reads the raw payload of a stored block. The bit accumulator is drained
     * first because read-ahead may already hold the start of the payload; the
     * rest is copied straight from the input buffer, which keeps stored data
     * (typically large and incompressible) off the slow bit-by-bit path.
     * Must be called on a byte boundary.
     */
    fun readBytes(dest: ByteArray, offset: Int, length: Int) {
        var out = offset
        var left = length
        while (left > 0 && nbits >= 8) {
            dest[out++] = read(8).toByte()
            left--
        }
        while (left > 0) {
            if (pos == lim) {
                lim = input.read(buf, 0, buf.size)
                pos = 0
                if (lim <= 0) {
                    lim = 0
                    throw FormatException("truncated data")
                }
            }
            val n = minOf(left, lim - pos)
            System.arraycopy(buf, pos, dest, out, n)
            pos += n
            out += n
            left -= n
        }
    }

    /** Used after the end marker to reject files with extra bytes appended. */
    fun hasMoreData(): Boolean = nbits > padBits || nextByte() >= 0
}
