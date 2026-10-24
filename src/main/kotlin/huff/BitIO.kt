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

    /** Pads the final partial byte with zeros; the decoder ignores them because the symbol count is in the header. */
    fun finish() {
        if (nbits > 0) {
            out.write(((acc shl (8 - nbits)) and 0xFF).toInt())
            nbits = 0
        }
    }
}

/**
 * Decoding needs to look at the next few bits without committing to
 * consuming them (the code length is unknown until the lookup of course), 
 * so this reader exposes peek/skip rather.
 */
internal class BitReader(private val input: InputStream) {
    private val buf = ByteArray(1 shl 16)
    private var pos = 0
    private var lim = 0
    private var acc = 0L
    private var nbits = 0
    private var padBits = 0

    /** Becomes True once the decoder has consumed zero-padding that was 
     * synthesized past end of input (in other words it was truncated) */
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
}
