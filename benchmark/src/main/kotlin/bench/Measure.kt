package bench

import huff.compress
import huff.decompress
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.ln

class Config(
    val dataDir: File,
    val outDir: File,
    val jar: File,
    val warmup: Int,
    val runs: Int,
    val procRuns: Int,
)

/** One tool's result on one file. Timings are null where a tool was not run for that direction. */
class Contender(
    val name: String,
    val size: Long,
    val compressSeconds: Double?,
    val decompressSeconds: Double?,
    /** In-process numbers exclude process startup; command-line numbers include it. */
    val inProcess: Boolean,
)

class FileResult(
    val category: String,
    val name: String,
    val size: Long,
    /** Size the file would have if each byte cost exactly its order-0 entropy; no whole-file Huffman code can beat it. */
    val entropyBytes: Double,
    val contenders: List<Contender>,
    /** Block size in bytes -> huff output size, to show what per-block tables cost or gain. */
    val blockSweep: Map<Int, Long>,
) {
    fun find(name: String): Contender? = contenders.firstOrNull { it.name == name }
}

const val HUFF = "huff"
const val HUFF_BITWISE = "huff (bit-by-bit decode)"
const val HUFF_CLI = "huff CLI"
const val DEFLATER_6 = "Deflater -6"
const val DEFLATER_9 = "Deflater -9"

val BLOCK_SIZES = listOf(64 * 1024, 256 * 1024, 1024 * 1024, 4 * 1024 * 1024)

/** Discards output while counting it, so compression speed is not distorted by allocating the result. */
class CountingSink : OutputStream() {
    var count = 0L
        private set

    override fun write(b: Int) { count++ }
    override fun write(b: ByteArray, off: Int, len: Int) { count += len }
}

/** java.util.zip's Deflater is the same zlib algorithm gzip uses, so it gives a gzip-equivalent that runs inside the same JVM as huff. */
private class LeveledGzip(out: OutputStream, level: Int) : GZIPOutputStream(out, 1 shl 16) {
    init { def.setLevel(level) }
}

/** Median rather than mean, because a single GC pause or scheduler hiccup should not move a result. */
private inline fun medianSeconds(warmup: Int, runs: Int, block: () -> Unit): Double {
    repeat(warmup) { block() }
    val times = DoubleArray(runs) {
        val start = System.nanoTime()
        block()
        (System.nanoTime() - start) / 1e9
    }
    times.sort()
    return times[times.size / 2]
}

private fun entropyBytes(data: ByteArray): Double {
    val counts = LongArray(256)
    for (b in data) counts[b.toInt() and 0xFF]++
    var bits = 0.0
    for (c in counts) if (c > 0) bits -= c * (ln(c.toDouble() / data.size) / ln(2.0))
    return bits / 8
}

private fun huffPack(data: ByteArray, blockSize: Int = huff.BLOCK_SIZE): ByteArray =
    ByteArrayOutputStream().also { compress(ByteArrayInputStream(data), it, blockSize) }.toByteArray()

private fun gzipPack(data: ByteArray, level: Int): ByteArray =
    ByteArrayOutputStream().also { sink -> LeveledGzip(sink, level).use { it.write(data) } }.toByteArray()

private fun inProcessContenders(cfg: Config, data: ByteArray): List<Contender> {
    val packed = huffPack(data)
    // A benchmark of a compressor that does not round-trip is meaningless, so every measured path is checked first.
    for (useTable in listOf(true, false)) {
        val restored = ByteArrayOutputStream().also { decompress(ByteArrayInputStream(packed), it, useTable) }.toByteArray()
        check(restored.contentEquals(data)) { "huff round trip failed (useTable=$useTable)" }
    }
    val huffCompress = medianSeconds(cfg.warmup, cfg.runs) { compress(ByteArrayInputStream(data), CountingSink()) }
    val decodeTable = medianSeconds(cfg.warmup, cfg.runs) { decompress(ByteArrayInputStream(packed), CountingSink(), true) }
    val decodePlain = medianSeconds(cfg.warmup, cfg.runs) { decompress(ByteArrayInputStream(packed), CountingSink(), false) }

    val result = mutableListOf(
        Contender(HUFF, packed.size.toLong(), huffCompress, decodeTable, inProcess = true),
        Contender(HUFF_BITWISE, packed.size.toLong(), null, decodePlain, inProcess = true),
    )
    for ((name, level) in listOf(DEFLATER_6 to 6, DEFLATER_9 to 9)) {
        val gz = gzipPack(data, level)
        check(GZIPInputStream(ByteArrayInputStream(gz)).readBytes().contentEquals(data)) { "gzip round trip failed" }
        val comp = medianSeconds(cfg.warmup, cfg.runs) { LeveledGzip(CountingSink(), level).use { it.write(data) } }
        val dec = medianSeconds(cfg.warmup, cfg.runs) { GZIPInputStream(ByteArrayInputStream(gz)).use { it.copyTo(CountingSink()) } }
        result += Contender(name, gz.size.toLong(), comp, dec, inProcess = true)
    }
    return result
}

/** A command-line compressor that reads stdin and writes stdout, so every tool is driven the same way. */
class Tool(val name: String, val compress: List<String>, val decompress: List<String>)

private fun isAvailable(exe: String): Boolean = try {
    ProcessBuilder(exe, "--version")
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start().waitFor() == 0
} catch (e: IOException) {
    false
}

fun availableTools(jar: File): List<Tool> {
    val tools = mutableListOf<Tool>()
    if (jar.exists()) {
        val java = File(System.getProperty("java.home"), "bin/java").path
        tools += Tool(HUFF_CLI, listOf(java, "-jar", jar.path, "compress"), listOf(java, "-jar", jar.path, "decompress"))
    }
    if (isAvailable("gzip")) {
        tools += Tool("gzip -6", listOf("gzip", "-6", "-c"), listOf("gzip", "-d", "-c"))
        tools += Tool("gzip -9", listOf("gzip", "-9", "-c"), listOf("gzip", "-d", "-c"))
    }
    if (isAvailable("xz")) {
        tools += Tool("xz -9", listOf("xz", "-9", "-c"), listOf("xz", "-d", "-c"))
    }
    return tools
}

private fun wallSeconds(cmd: List<String>, input: File, output: File): Double? {
    val start = System.nanoTime()
    val code = ProcessBuilder(cmd)
        .redirectInput(input)
        .redirectOutput(output)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start().waitFor()
    return if (code == 0) (System.nanoTime() - start) / 1e9 else null
}

private fun medianWall(runs: Int, cmd: List<String>, input: File, output: File): Double? {
    val times = DoubleArray(runs)
    for (i in 0 until runs) times[i] = wallSeconds(cmd, input, output) ?: return null
    times.sort()
    return times[times.size / 2]
}

private fun toolContender(cfg: Config, tool: Tool, original: File, scratch: File): Contender? {
    val packed = File(scratch, "packed.bin")
    val restored = File(scratch, "restored.bin")
    val comp = medianWall(cfg.procRuns, tool.compress, original, packed) ?: return null
    val dec = medianWall(cfg.procRuns, tool.decompress, packed, restored) ?: return null
    check(Files.mismatch(restored.toPath(), original.toPath()) == -1L) { "${tool.name} did not restore ${original.name}" }
    return Contender(tool.name, packed.length(), comp, dec, inProcess = false)
}

fun runMeasurements(cfg: Config) {
    val files = cfg.dataDir.walkTopDown()
        .filter { it.isFile && it.length() > 0 && it.parentFile != cfg.dataDir }
        .sortedWith(compareBy({ it.parentFile.name }, { it.name }))
        .toList()
    if (files.isEmpty()) {
        System.err.println("no files under ${cfg.dataDir.path}; run the prepare command first")
        return
    }
    val tools = availableTools(cfg.jar)
    println("tools: " + (tools.joinToString { it.name }.ifEmpty { "none" }))
    val scratch = Files.createTempDirectory("huff-bench").toFile()
    val results = mutableListOf<FileResult>()
    try {
        for (file in files) {
            val category = file.parentFile.name
            print("%-11s %-28s ".format(category, file.name))
            System.out.flush()
            val data = file.readBytes()
            val contenders = inProcessContenders(cfg, data) + tools.mapNotNull { toolContender(cfg, it, file, scratch) }
            val sweep = BLOCK_SIZES.associateWith { huffPack(data, it).size.toLong() }
            results += FileResult(category, file.name, data.size.toLong(), entropyBytes(data), contenders, sweep)
            println("huff %.2f%% of original".format(100.0 * contenders.first().size / data.size))
        }
    } finally {
        scratch.deleteRecursively()
    }
    cfg.outDir.mkdirs()
    writeReports(cfg, results, tools)
    println("\nwrote ${File(cfg.outDir, "report.md").path} and ${File(cfg.outDir, "results.csv").path}")
}
