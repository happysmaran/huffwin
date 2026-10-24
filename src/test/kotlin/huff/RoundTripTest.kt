package huff

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RoundTripTest {
    private fun pack(data: ByteArray, blockSize: Int = BLOCK_SIZE): ByteArray =
        ByteArrayOutputStream().also { compress(ByteArrayInputStream(data), it, blockSize) }.toByteArray()

    private fun unpack(packed: ByteArray, useTable: Boolean = true): ByteArray =
        ByteArrayOutputStream().also { decompress(ByteArrayInputStream(packed), it, useTable) }.toByteArray()

    private fun assertRoundTrip(data: ByteArray, blockSize: Int = BLOCK_SIZE) {
        val packed = pack(data, blockSize)
        assertContentEquals(data, unpack(packed, useTable = true))
        assertContentEquals(data, unpack(packed, useTable = false))
    }

    @Test fun emptyInput() {
        assertRoundTrip(ByteArray(0))
        assertEquals(8, pack(ByteArray(0)).size, "empty input is just the magic and the end marker")
    }

    @Test fun singleByte() = assertRoundTrip(byteArrayOf(42))

    // One distinct symbol is the case where a naive tree yields a zero-length code.
    @Test fun singleDistinctSymbol() = assertRoundTrip(ByteArray(10_000) { 7 })

    @Test fun allByteValues() = assertRoundTrip(ByteArray(256 * 40) { (it % 256).toByte() })

    @Test fun randomBinary() = assertRoundTrip(Random(1).nextBytes(100_000))

    @Test fun skewedText() {
        val text = ("the quick brown fox jumps over the lazy dog ".repeat(2000) + "zzzzqx").toByteArray()
        val packed = pack(text)
        // This text's order-0 entropy is about 4.34 bits/byte (54% of the input), so 60% is the realistic ceiling for any Huffman coder.
        assertTrue(packed.size < text.size * 0.6, "skewed text should compress close to its entropy")
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

    // Sizes straddling a block boundary catch off-by-one errors in block splitting and byte alignment.
    @Test fun blockBoundaries() {
        for (size in listOf(999, 1000, 1001, 1999, 2000, 2001, 3000)) {
            assertRoundTrip(Random(size).nextBytes(size), blockSize = 1000)
        }
    }

    @Test fun oneByteBlocks() = assertRoundTrip(Random(9).nextBytes(300), blockSize = 1)

    @Test fun blockCountMatchesInput() {
        val data = Random(4).nextBytes(10_500)
        assertEquals(11, computeStats(ByteArrayInputStream(data), blockSize = 1000).blocks)
    }

    // Per-block tables are the point of the block format: statistics that change mid-file should be coded with matching tables.
    @Test fun blocksAdaptToChangingStatistics() {
        val rng = Random(2)
        val first = ByteArray(40_000) { (rng.nextInt(4) + 'a'.code).toByte() }
        val second = ByteArray(40_000) { (rng.nextInt(4) + 'w'.code).toByte() }
        val data = first + second
        val oneTable = pack(data, blockSize = 80_000)
        val perHalf = pack(data, blockSize = 40_000)
        assertTrue(perHalf.size < oneTable.size, "separate tables should beat one shared table here")
        assertContentEquals(data, unpack(perHalf))
    }

    // The reported size is what `stats` promises, so it has to equal the real output byte for byte.
    @Test fun statsPredictActualSize() {
        val inputs = listOf(
            ByteArray(0),
            ByteArray(5_000) { 3 },
            Random(6).nextBytes(7_321),
            ("hello world ".repeat(900)).toByteArray(),
        )
        for (data in inputs) {
            for (blockSize in listOf(1000, BLOCK_SIZE)) {
                val stats = computeStats(ByteArrayInputStream(data), blockSize)
                assertEquals(pack(data, blockSize).size.toLong(), stats.compressedBytes)
            }
        }
    }

    @Test fun rejectsBadMagic() {
        assertFailsWith<FormatException> { unpack(ByteArray(40)) }
    }

    @Test fun rejectsTruncatedStream() {
        val packed = pack(Random(5).nextBytes(10_000))
        assertFailsWith<FormatException> { unpack(packed.copyOf(packed.size / 2)) }
    }

    // A cut at a block boundary leaves every remaining block intact, so only the end marker can reveal it.
    @Test fun rejectsMissingEndMarker() {
        val packed = pack(Random(7).nextBytes(5_000), blockSize = 1000)
        assertFailsWith<FormatException> { unpack(packed.copyOf(packed.size - 4)) }
        assertFailsWith<FormatException> { unpack(packed.copyOf(8)) }
    }

    @Test fun rejectsTrailingBytes() {
        val packed = pack(Random(8).nextBytes(500))
        assertFailsWith<FormatException> { unpack(packed + byteArrayOf(1)) }
    }

    @Test fun rejectsCorruptCodeLengths() {
        val packed = pack(Random(10).nextBytes(500))
        val bad = packed.copyOf()
        // Bytes 8..167 are the block's code-length table; zeroing it leaves a block with no symbols.
        for (i in 8 until 168) bad[i] = 0
        assertFailsWith<FormatException> { unpack(bad) }
    }
}
