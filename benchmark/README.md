# Benchmarks

Measures huff against gzip and xz (plus an in-JVM gzip equivalent) on text, source code,
binary files, already-compressed data, and random noise, and writes a report with the numbers
the project write-up needs.

## What it measures

| Report section | Question it answers |
|---|---|
| 1. Compression ratio | How close does huff get to the order-0 entropy, and how far behind gzip/xz is it? |
| 2. In-process throughput | How fast are the algorithms themselves, with JVM startup excluded? Includes the table-driven vs bit-by-bit decoder comparison. |
| 3. Command-line wall-clock time | What does a user see at the shell, including process startup? |
| 4. Block size | What does huff's per-block table cost or gain at 64 KiB to 4 MiB blocks? |

Every file is round-tripped through each tool before timing, so a result is only reported for
output that decompresses back to the original.

## Running it

From the repository root, build the compressor, then switch to this folder:

    gradle jar
    cd benchmark
    gradle run --args="prepare"
    gradle run --args="measure"

Results are written to `results/report.md` (tables) and `results/results.csv` (raw numbers).

`gzip` and `xz` are looked up on `PATH`; on Windows run from Git Bash or WSL so they are found.
Tools that are missing are left out of the report and noted in its environment section.

### Options

    gradle run --args="measure --runs 15 --warmup 5 --proc-runs 5"

| Option | Default | Meaning |
|---|---|---|
| `--data DIR` | `data` | Corpus folder; each subfolder is a category |
| `--out DIR` | `results` | Where the report and CSV go |
| `--jar FILE` | `../build/libs/huff.jar` | Jar used for the command-line timings |
| `--warmup N` | 3 | Untimed in-process passes before timing |
| `--runs N` | 7 | Timed in-process passes (median reported) |
| `--proc-runs N` | 3 | Timed runs per external process (median reported) |

## The corpus

`prepare` builds `data/<category>/` and is safe to re-run; files that already exist are kept.

| Category | Source |
|---|---|
| `text` | Canterbury corpus: alice29, asyoulik, lcet10, plrabn12 (cantrbry.zip) and bible, world192 (large.zip) |
| `source` | Canterbury source files, the JDK's own `java.base` sources (about 8 MB, taken from the JDK's `src.zip` when present), and this repository's Kotlin |
| `binary` | Canterbury `kennedy.xls`, `ptt5`, `sum` |
| `dna` | Canterbury `E.coli`, a four-symbol alphabet |
| `compressed` | The three largest text/source files, gzipped |
| `random` | 4 MiB of seeded uniform random bytes |

The Canterbury downloads need network access to `corpus.canterbury.ac.nz`. If they fail,
`prepare` still builds the local categories; any file you place under `data/<category>/` is
picked up automatically, so you can add your own, and a folder name becomes a category in the report.

## Reading the results

- Compare section 1 with the order-0 entropy column to see how much of the gap to gzip and xz
  is the algorithm (they model repeated strings, Huffman only models single-byte frequencies)
  rather than the implementation.
- On the `compressed` and `random` categories Huffman coding cannot shrink the data, so huff stores
  those blocks raw; the output is the input plus about 12 bytes of framing, comparable to gzip and xz.
- Section 2 is the fair comparison of algorithms, since huff and `Deflater` run in the same warmed-up JVM.
  Section 3 shows the user experience, where `huff CLI` pays JVM startup on every invocation.
