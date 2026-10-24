package huff

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RoundTripTest {
    private fun pack(data: ByteArray): ByteArray = ByteArrayOutputStream().also { compress(data, it) }.toByteArray()

    private fun unpack(packed: ByteArray, useTable: Boolean = true): ByteArray =
        ByteArrayOutputStream().also { decompress(ByteArrayInputStream(packed), it, useTable) }.toByteArray()

    private fun assertRoundTrip(data: ByteArray) {
        val packed = pack(data)
        assertContentEquals(data, unpack(packed, useTable = true))
        assertContentEquals(data, unpack(packed, useTable = false))
    }

    @Test fun emptyInput() = assertRoundTrip(ByteArray(0))

    @Test fun singleByte() = assertRoundTrip(byteArrayOf(42))

    // One distinct symbol is the case where a naive tree yields a zero-length code.
    @Test fun singleDistinctSymbol() = assertRoundTrip(ByteArray(10_000) { 7 })

    @Test fun allByteValues() = assertRoundTrip(ByteArray(256 * 40) { (it % 256).toByte() })

    @Test fun randomBinary() = assertRoundTrip(Random(1).nextBytes(100_000))

    @Test fun skewedText() {
        val text = ("the quick brown fox jumps over the lazy dog ".repeat(2000) + "zzzzqx").toByteArray()
        val packed = pack(text)
        assertTrue(packed.size < text.size / 2, "skewed text should compress well")
        assertContentEquals(text, unpack(packed))
    }

    // Fibonacci frequencies give the deepest possible Huffman trees (depth 39 here), so this is the input that forces the length cap to engage.
    @Test fun lengthCapIsRespected() {
        val freq = LongArray(256)
        var a = 1L
        var b = 1L
        for (sym in 0 until 40) {
            freq[sym] = a
            val next = a + b; a = b; b = next
        }
        val lengths = codeLengths(freq)
        assertTrue(lengths.max() <= MAX_CODE_LENGTH)
        CanonicalCode(lengths) // throws if the capped lengths are not a complete prefix code
    }

    @Test fun skewedDataBeyondCapRoundTrips() {
        val out = ByteArrayOutputStream()
        var a = 1L
        var b = 1L
        for (sym in 0 until 31) {
            repeat(a.toInt()) { out.write(sym) }
            val next = a + b; a = b; b = next
        }
        assertRoundTrip(out.toByteArray())
    }

    @Test fun deterministicOutput() {
        val data = Random(3).nextBytes(5_000)
        assertContentEquals(pack(data), pack(data))
    }

    @Test fun rejectsBadMagic() {
        assertFailsWith<FormatException> { unpack(ByteArray(40)) }
    }

    @Test fun rejectsTruncatedStream() {
        val packed = pack(Random(5).nextBytes(10_000))
        assertFailsWith<FormatException> { unpack(packed.copyOf(packed.size / 2)) }
    }
}
