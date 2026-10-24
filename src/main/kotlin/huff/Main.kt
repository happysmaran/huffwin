package huff

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

private const val EXT = ".huff"

private const val USAGE = """usage:
  huff compress   [FILE] [-o OUT] [-f]
  huff decompress [FILE] [-o OUT] [-f]
  huff stats      [FILE]

With no FILE (or FILE of "-"), input is read from stdin and output goes to stdout.
Compressing foo.txt writes foo.txt.huff; decompressing foo.txt.huff writes foo.txt.

options:
  -o OUT   write to OUT instead of the default name ("-" for stdout)
  -f       overwrite an existing output file
  -h       show this help"""

/** Distinguishes bad invocations (exit 1) from bad data or I/O (exit 2) so scripts can react differently. */
private class UsageError(message: String) : Exception(message)

private class Options(val command: String, val input: String?, val output: String?, val force: Boolean)

private fun parseArgs(args: Array<String>): Options {
    if (args.isEmpty()) throw UsageError("missing command")
    val command = args[0]
    if (command !in setOf("compress", "decompress", "stats")) throw UsageError("unknown command '$command'")
    var input: String? = null
    var output: String? = null
    var force = false
    var i = 1
    while (i < args.size) {
        when (val a = args[i]) {
            "-o" -> {
                if (i + 1 >= args.size) throw UsageError("-o needs a value")
                output = args[++i]
            }
            "-f" -> force = true
            else -> {
                if (a.startsWith("-") && a != "-") throw UsageError("unknown option '$a'")
                if (input != null) throw UsageError("only one input file is supported")
                input = a
            }
        }
        i++
    }
    return Options(command, input, output, force)
}

private fun openInput(path: String?): InputStream =
    if (path == null || path == "-") BufferedInputStream(FileInputStream(FileDescriptor.`in`), 1 shl 16)
    else BufferedInputStream(Files.newInputStream(Paths.get(path)), 1 shl 16)

private fun defaultOutput(command: String, input: String?): String? {
    if (input == null || input == "-") return null
    return when (command) {
        "compress" -> input + EXT
        else -> {
            if (!input.endsWith(EXT) || input.length == EXT.length) {
                throw UsageError("cannot infer output name for '$input' (expected a $EXT suffix); use -o")
            }
            input.removeSuffix(EXT)
        }
    }
}

/**
 * Output is written to a sibling temp file and renamed on success, so a
 * failed or interrupted run never leaves a half-written file under the final
 * name (which a user might later mistake for a valid result).
 */
private fun withOutput(dest: String?, force: Boolean, body: (OutputStream) -> Unit) {
    if (dest == null || dest == "-") {
        val out = BufferedOutputStream(FileOutputStream(FileDescriptor.out), 1 shl 16)
        body(out)
        out.flush()
        return
    }
    val target: Path = Paths.get(dest)
    if (Files.exists(target) && !force) throw IOException("'$dest' already exists (use -f to overwrite)")
    val dir = target.toAbsolutePath().parent
    val tmp = Files.createTempFile(dir, ".huff-", ".tmp")
    try {
        BufferedOutputStream(Files.newOutputStream(tmp), 1 shl 16).use(body)
        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(tmp)
    }
}

private fun printStats(s: Stats) {
    val ratio = if (s.originalBytes == 0L) 0.0 else 100.0 * s.compressedBytes / s.originalBytes
    println("original size:      ${s.originalBytes} bytes")
    println("compressed size:    ${s.compressedBytes} bytes (${"%.2f".format(ratio)}% of original)")
    println("blocks:             ${s.blocks}")
    println("distinct symbols:   ${s.distinctSymbols}")
    println("entropy:            ${"%.4f".format(s.entropyBitsPerSymbol)} bits/symbol (whole input, order 0)")
    println("average code length:${"%.4f".format(s.averageCodeBits).padStart(8)} bits/symbol (as actually coded)")
    println("longest code:       ${s.maxCodeLength} bits")
    println()
    println("byte  count        bits (single whole-input table)")
    s.lengths.indices
        .filter { s.lengths[it] > 0 }
        .sortedByDescending { s.frequencies[it] }
        .take(16)
        .forEach { b ->
            val shown = if (b in 33..126) "'${b.toChar()}'" else "0x%02x".format(b)
            println("${shown.padEnd(5)} ${s.frequencies[b].toString().padEnd(12)} ${s.lengths[b]}")
        }
    if (s.distinctSymbols > 16) println("... ${s.distinctSymbols - 16} more")
}

private fun run(args: Array<String>) {
    if (args.any { it == "-h" || it == "--help" }) {
        println(USAGE)
        return
    }
    val opts = parseArgs(args)
    when (opts.command) {
        "stats" -> openInput(opts.input).use { printStats(computeStats(it)) }
        "compress" -> {
            val dest = opts.output ?: defaultOutput("compress", opts.input)
            openInput(opts.input).use { input ->
                withOutput(dest, opts.force) { compress(input, it) }
            }
        }
        "decompress" -> {
            val dest = opts.output ?: defaultOutput("decompress", opts.input)
            openInput(opts.input).use { input ->
                withOutput(dest, opts.force) { decompress(input, it) }
            }
        }
    }
}

fun main(args: Array<String>) {
    try {
        run(args)
    } catch (e: UsageError) {
        System.err.println("huff: ${e.message}")
        System.err.println(USAGE)
        exitProcess(1)
    } catch (e: FormatException) {
        System.err.println("huff: ${e.message}")
        exitProcess(2)
    } catch (e: IOException) {
        System.err.println("huff: ${e.message}")
        exitProcess(2)
    }
}
