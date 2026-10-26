package bench

import java.io.File
import java.time.LocalDate
import java.util.Locale

private const val MIB = 1024.0 * 1024.0

private fun f(value: Double, digits: Int = 2) = String.format(Locale.ROOT, "%.${digits}f", value)

private fun pct(part: Double, whole: Long) = f(100.0 * part / whole) + "%"

private fun table(header: List<String>, rows: List<List<String>>): String = buildString {
    appendLine(header.joinToString(" | ", "| ", " |"))
    appendLine(header.joinToString(" | ", "| ", " |") { "---" })
    for (row in rows) appendLine(row.joinToString(" | ", "| ", " |"))
}

private fun mibPerSecond(bytes: Long, seconds: Double?) = if (seconds == null || seconds <= 0) "-" else f(bytes / MIB / seconds, 1)

fun writeReports(cfg: Config, results: List<FileResult>, tools: List<Tool>) {
    File(cfg.outDir, "results.csv").writeText(csv(results))
    File(cfg.outDir, "report.md").writeText(markdown(cfg, results, tools))
}

private fun csv(results: List<FileResult>) = buildString {
    appendLine("category,file,original_bytes,order0_entropy_bytes,contender,compressed_bytes,compress_seconds,decompress_seconds,in_process")
    for (r in results) for (c in r.contenders) {
        appendLine(listOf(
            r.category, r.name, r.size, f(r.entropyBytes, 1), c.name, c.size,
            c.compressSeconds?.let { f(it, 6) } ?: "", c.decompressSeconds?.let { f(it, 6) } ?: "", c.inProcess,
        ).joinToString(","))
    }
}

/** Sizes shown in the ratio table, in a fixed order; tool columns only appear if the tool was installed. */
private val RATIO_COLUMNS = listOf(HUFF, DEFLATER_9, "gzip -9", "xz -9")

private fun markdown(cfg: Config, results: List<FileResult>, tools: List<Tool>): String = buildString {
    val categories = results.groupBy { it.category }
    appendLine("# Benchmark report")
    appendLine()
    appendLine("Generated ${LocalDate.now()} by `benchmark/`. Raw numbers are in `results.csv`.")
    appendLine()
    appendLine("## Environment")
    appendLine()
    appendLine("- OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")}), ${Runtime.getRuntime().availableProcessors()} logical cores")
    appendLine("- JVM: ${System.getProperty("java.vendor")} ${System.getProperty("java.version")}")
    appendLine("- In-process timings: ${cfg.warmup} warmup passes, then the median of ${cfg.runs} passes")
    appendLine("- Command-line timings: median of ${cfg.procRuns} runs, process startup included")
    appendLine("- External tools used: ${tools.joinToString { it.name }.ifEmpty { "none found" }}")
    val missing = listOf("gzip -9", "xz -9").filter { name -> tools.none { it.name == name } }
    if (missing.isNotEmpty()) appendLine("- Not found on PATH, so omitted: ${missing.joinToString()}")
    appendLine()

    ratioSection(categories)
    throughputSection(categories)
    commandLineSection(categories, tools)
    blockSection(categories)
    notesSection()
}

private fun StringBuilder.ratioSection(categories: Map<String, List<FileResult>>) {
    val columns = RATIO_COLUMNS.filter { name -> categories.values.flatten().any { it.find(name) != null } }
    appendLine("## 1. Compression ratio")
    appendLine()
    appendLine("Compressed size as a percentage of the original (lower is better). The order-0 entropy column is the size if every byte cost exactly its information content under one whole-file model, the limit for a single Huffman table. huff uses one table per block, so it can land slightly below that column when the data's statistics change across the file.")
    appendLine()
    val rows = mutableListOf<List<String>>()
    for ((category, list) in categories) {
        for (r in list) {
            rows += listOf(category, r.name, r.size.toString(), pct(r.entropyBytes, r.size)) + columns.map { c -> r.find(c)?.let { pct(it.size.toDouble(), r.size) } ?: "-" }
        }
        val total = list.sumOf { it.size }
        rows += listOf("**$category total**", "", total.toString(), "**" + pct(list.sumOf { it.entropyBytes }, total) + "**") +
            columns.map { c -> if (list.all { it.find(c) != null }) "**" + pct(list.sumOf { it.find(c)!!.size.toDouble() }, total) + "**" else "-" }
    }
    appendLine(table(listOf("Category", "File", "Bytes", "Order-0 entropy") + columns, rows))
}

private fun StringBuilder.throughputSection(categories: Map<String, List<FileResult>>) {
    appendLine("## 2. In-process throughput")
    appendLine()
    appendLine("MiB of original data per second (higher is better), measured inside one warmed-up JVM so process startup is excluded. \"Table speedup\" is table-driven decoding divided by bit-by-bit decoding, i.e. the measured effect of that optimization. `Deflater -9` is the same algorithm as `gzip -9`, run in the same JVM as huff for a like-for-like comparison.")
    appendLine()
    val rows = mutableListOf<List<String>>()
    fun row(label: String, name: String, list: List<FileResult>): List<String> {
        val bytes = list.sumOf { it.size }
        fun total(c: String, get: (Contender) -> Double?): Double? {
            val parts = list.map { r -> r.find(c)?.let(get) }
            return if (parts.any { it == null }) null else parts.sumOf { it!! }
        }
        val comp = total(HUFF) { it.compressSeconds }
        val decT = total(HUFF) { it.decompressSeconds }
        val decP = total(HUFF_BITWISE) { it.decompressSeconds }
        val defC = total(DEFLATER_9) { it.compressSeconds }
        val defD = total(DEFLATER_9) { it.decompressSeconds }
        val speedup = if (decT != null && decP != null && decT > 0) f(decP / decT) + "x" else "-"
        return listOf(label, name, mibPerSecond(bytes, comp), mibPerSecond(bytes, decT), mibPerSecond(bytes, decP), speedup, mibPerSecond(bytes, defC), mibPerSecond(bytes, defD))
    }
    for ((category, list) in categories) {
        for (r in list) rows += row(category, r.name, listOf(r))
        if (list.size > 1) rows += row("**$category total**", "", list).map { if (it.isEmpty() || it == "-") it else "**$it**" }
    }
    appendLine(table(listOf("Category", "File", "huff compress", "huff decode (table)", "huff decode (bit-by-bit)", "Table speedup", "Deflater -9 compress", "Inflater decode"), rows))
}

private fun StringBuilder.commandLineSection(categories: Map<String, List<FileResult>>, tools: List<Tool>) {
    if (tools.isEmpty()) return
    appendLine("## 3. Command-line wall-clock time")
    appendLine()
    appendLine("Seconds for the whole process (lower is better), reading stdin and writing a file, so this includes JVM startup for `huff CLI`. It shows what a user sees at the shell, not the algorithm's speed; small files are dominated by startup.")
    appendLine()
    val names = tools.map { it.name }
    val header = listOf("Category", "File") + names.flatMap { listOf("$it compress", "$it decompress") }
    val rows = mutableListOf<List<String>>()
    for ((category, list) in categories) for (r in list) {
        rows += listOf(category, r.name) + names.flatMap { n ->
            val c = r.find(n)
            listOf(c?.compressSeconds?.let { f(it, 3) } ?: "-", c?.decompressSeconds?.let { f(it, 3) } ?: "-")
        }
    }
    appendLine(table(header, rows))
}

private fun StringBuilder.blockSection(categories: Map<String, List<FileResult>>) {
    appendLine("## 4. Effect of block size on huff's ratio")
    appendLine()
    appendLine("huff output size as a percentage of the original for different block sizes (each block carries a 164-byte table). Smaller blocks adapt faster to changing data but pay the table more often; the default is 1024 KiB.")
    appendLine()
    val header = listOf("Category", "File") + BLOCK_SIZES.map { "${it / 1024} KiB" }
    val rows = mutableListOf<List<String>>()
    for ((category, list) in categories) {
        for (r in list) rows += listOf(category, r.name) + BLOCK_SIZES.map { pct(r.blockSweep.getValue(it).toDouble(), r.size) }
        val total = list.sumOf { it.size }
        rows += listOf("**$category total**", "") + BLOCK_SIZES.map { b -> "**" + pct(list.sumOf { it.blockSweep.getValue(b).toDouble() }, total) + "**" }
    }
    appendLine(table(header, rows))
}

private fun StringBuilder.notesSection() {
    appendLine("## How the numbers were produced")
    appendLine()
    appendLine("- Every file is round-tripped through huff (both decoders), Deflater, and each external tool before its timings are recorded; a mismatch aborts the run.")
    appendLine("- In-process compression writes to a byte-counting sink and decoding is measured to a discarding sink, so memory allocation for results does not affect throughput.")
    appendLine("- External tools run as `tool -c < input > output`. Their wall time includes process creation, which is why the in-process table is the fairer view of the algorithms and the command-line table is the fairer view of the user experience.")
    appendLine("- Treat differences of a few percent between runs as noise; rerun with a larger `--runs` to tighten them.")
}
