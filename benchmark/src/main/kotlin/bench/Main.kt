package bench

import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """usage:
  gradle run --args="prepare [--data DIR] [--repo DIR]"
  gradle run --args="measure [--data DIR] [--out DIR] [--jar FILE] [--warmup N] [--runs N] [--proc-runs N]"

prepare   builds the test corpus under data/<category>/ (downloads the Canterbury corpora when reachable)
measure   benchmarks every file under data/<category>/ and writes results/report.md and results/results.csv

options:
  --data DIR     corpus folder (default: data)
  --out DIR      where reports go (default: results)
  --repo DIR     repository root, used to find source files (default: ..)
  --jar FILE     huff.jar used for the command-line timings (default: ../build/libs/huff.jar)
  --warmup N     untimed in-process passes before timing (default: 3)
  --runs N       timed in-process passes; the median is reported (default: 7)
  --proc-runs N  timed runs for each external process; the median is reported (default: 3)"""

private fun parseOptions(args: List<String>): Map<String, String> {
    val opts = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val key = args[i]
        if (!key.startsWith("--") || i + 1 >= args.size) {
            System.err.println("bad option: $key\n$USAGE")
            exitProcess(1)
        }
        opts[key.removePrefix("--")] = args[i + 1]
        i += 2
    }
    return opts
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println(USAGE)
        exitProcess(1)
    }
    val opts = parseOptions(args.drop(1))
    val data = File(opts["data"] ?: "data")
    when (args[0]) {
        "prepare" -> prepareCorpus(data, File(opts["repo"] ?: "..").canonicalFile)
        "measure" -> {
            val config = Config(
                dataDir = data,
                outDir = File(opts["out"] ?: "results"),
                jar = File(opts["jar"] ?: "../build/libs/huff.jar"),
                warmup = (opts["warmup"] ?: "3").toInt(),
                runs = (opts["runs"] ?: "7").toInt(),
                procRuns = (opts["proc-runs"] ?: "3").toInt(),
            )
            runMeasurements(config)
        }
        else -> {
            System.err.println("unknown command '${args[0]}'\n$USAGE")
            exitProcess(1)
        }
    }
}
