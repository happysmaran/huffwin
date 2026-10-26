package bench

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipInputStream
import kotlin.random.Random

/**
 * The corpus covers the input classes the project's report compares: natural
 * text, source code, binary files, data that is already compressed, and
 * incompressible noise. Each class lives in its own folder so the report can
 * group results by it.
 */
private const val CANTERBURY = "https://corpus.canterbury.ac.nz/resources"

// Canterbury file name -> category folder.
private val CANTRBRY_FILES = mapOf(
    "alice29.txt" to "text", "asyoulik.txt" to "text", "lcet10.txt" to "text", "plrabn12.txt" to "text",
    "cp.html" to "source", "fields.c" to "source", "grammar.lsp" to "source", "xargs.1" to "source",
    "kennedy.xls" to "binary", "ptt5" to "binary", "sum" to "binary",
)
private val LARGE_FILES = mapOf("bible.txt" to "text", "world192.txt" to "text", "E.coli" to "dna")

private const val SOURCE_SAMPLE_BYTES = 8 * 1024 * 1024
private const val RANDOM_BYTES = 4 * 1024 * 1024

fun prepareCorpus(dataDir: File, repoRoot: File) {
    dataDir.mkdirs()
    fetchCanterbury("cantrbry.zip", CANTRBRY_FILES, dataDir)
    fetchCanterbury("large.zip", LARGE_FILES, dataDir)
    addJdkSources(dataDir)
    addRepoSources(dataDir, repoRoot)
    addRandom(dataDir)
    addAlreadyCompressed(dataDir)

    val files = dataDir.walkTopDown().filter { it.isFile }.groupBy { it.parentFile.name }
    println("\ncorpus in ${dataDir.path}:")
    for ((category, list) in files.toSortedMap()) println("  %-11s %d file(s), %,d bytes".format(category, list.size, list.sumOf { it.length() }))
    if (files["text"].isNullOrEmpty()) {
        println("\nno natural-language text was downloaded; drop .txt files into ${File(dataDir, "text").path} to include that category")
    }
}

private fun fetchCanterbury(zipName: String, wanted: Map<String, String>, dataDir: File) {
    if (wanted.all { (name, cat) -> File(File(dataDir, cat), name).exists() }) return
    val bytes = download("$CANTERBURY/$zipName")
    if (bytes == null) {
        println("could not download $CANTERBURY/$zipName; continuing with locally generated data only")
        return
    }
    ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val category = wanted[File(entry.name).name] ?: continue
            val target = File(File(dataDir, category).also { it.mkdirs() }, File(entry.name).name)
            target.writeBytes(zip.readBytes())
        }
    }
    println("downloaded $zipName")
}

private fun download(url: String): ByteArray? = try {
    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(20)).build()
    val response = client.send(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofMinutes(2)).build(), HttpResponse.BodyHandlers.ofByteArray())
    if (response.statusCode() == 200) response.body() else null
} catch (e: Exception) {
    null
}

/** Real, large source code with no download: the JDK ships its own library sources next to the runtime. */
private fun addJdkSources(dataDir: File) {
    val target = File(File(dataDir, "source").also { it.mkdirs() }, "jdk-java-base.java")
    if (target.exists()) return
    val srcZip = File(System.getProperty("java.home"), "lib/src.zip")
    if (!srcZip.exists()) {
        println("no src.zip in this JDK; skipping the large source-code sample")
        return
    }
    val out = ByteArrayOutputStream()
    ZipInputStream(srcZip.inputStream().buffered()).use { zip ->
        while (out.size() < SOURCE_SAMPLE_BYTES) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory || !entry.name.endsWith(".java") || !entry.name.startsWith("java.base/")) continue
            out.write(zip.readBytes())
        }
    }
    target.writeBytes(out.toByteArray().copyOf(minOf(out.size(), SOURCE_SAMPLE_BYTES)))
}

private fun addRepoSources(dataDir: File, repoRoot: File) {
    val target = File(File(dataDir, "source").also { it.mkdirs() }, "huffwin-src.kt")
    if (target.exists()) return
    val sources = File(repoRoot, "src").walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList()
    if (sources.isEmpty()) return
    target.writeBytes(sources.fold(ByteArray(0)) { acc, f -> acc + f.readBytes() })
}

/** Uniform noise has no redundancy, so it shows the worst case for any compressor: the cost of the container alone. */
private fun addRandom(dataDir: File) {
    val target = File(File(dataDir, "random").also { it.mkdirs() }, "random-4MiB.bin")
    if (!target.exists()) target.writeBytes(Random(42).nextBytes(RANDOM_BYTES))
}

/** Gzipping the largest text files gives realistic already-compressed input without needing another download. */
private fun addAlreadyCompressed(dataDir: File) {
    val outDir = File(dataDir, "compressed").also { it.mkdirs() }
    val candidates = listOf("text", "source")
        .flatMap { File(dataDir, it).listFiles()?.toList() ?: emptyList() }
        .filter { it.isFile }
        .sortedByDescending { it.length() }
        .take(3)
    for (src in candidates) {
        val target = File(outDir, src.name + ".gz")
        if (target.exists()) continue
        target.outputStream().use { raw -> GZIPOutputStream(raw).use { it.write(src.readBytes()) } }
    }
}
