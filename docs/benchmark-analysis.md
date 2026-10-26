# Benchmark analysis

huff is a static Huffman coder with one code table per 256 KiB block. This report compares it with
`gzip -9` and `xz -9` on six kinds of data and measures what each implementation choice contributed.
All numbers come from `benchmark/results/report.md` (Windows 11, 16 logical cores, JDK 25, 256 KiB
blocks), except where a line says it was computed separately.

## 1. Compression ratio

Compressed size as a percentage of the original (lower is better). "Order-0 entropy" is the size if
every byte cost exactly its information content under one whole-file model, which is the floor for a
single Huffman table.

| Category | Order-0 entropy | huff | gzip -9 | xz -9 |
|---|---|---|---|---|
| text (6 files, 7.7 MB) | 57.4% | 57.9% | 30.4% | 22.7% |
| source (6 files, 8.5 MB) | 59.4% | 58.3% | 18.3% | 12.7% |
| binary (3 files, 1.6 MB) | 35.6% | 37.2% | 17.4% | 6.4% |
| DNA (E.coli, 4.6 MB) | 25.0% | 25.1% | 28.0% | 25.6% |
| already compressed (3 files) | 100.0% | 100.0% | 99.9% | 99.9% |
| random (4 MiB) | 100.0% | 100.0% | 100.0% | 100.0% |

Ok, time for a wall of text:

**Text and source: gzip and xz win by a factor of 2 or more.** Huffman coding models each byte on its
own, so it can only exploit the fact that `e` is more common than `z`. English text and code are
full of repeated words, identifiers, keywords and indentation runs. LZ77 (gzip, and the LZMA variant
in xz) replaces a repeat with a short pointer to its earlier occurrence, which removes redundancy that
no byte-frequency table can see. The gap grows with file size because a larger file has more earlier
text to point back to: on the 8 MB `jdk-java-base.java`, huff reaches 58.2% while gzip reaches 18.2%
and xz 12.6%. xz also uses a much larger window and a context-modeling entropy coder, so it beats
gzip everywhere except on the tiny files (`grammar.lsp`, `xargs.1`), where its fixed overhead shows.

**huff lands within about 1 point of the order-0 entropy.** For text it is 0.5 points above (57.9% vs
57.4%). The remaining gap is the cost of rounding every code length up to a whole number of bits, plus
the 164-byte table each block carries. It can sit slightly below the entropy column on large files
(`jdk-java-base.java`: 58.2% vs 59.4%) because the entropy column assumes one model for the whole
file, while huff builds a new table every 256 KiB and so follows the data as its statistics drift.
The block-size experiment in section 2 confirms this: with 64 KiB blocks the same file drops to
57.6%, and with 4 MiB blocks it rises to 59.3%, which is back at the one-table entropy.

**DNA: huff beats gzip and xz.** E.coli contains four symbols (a, c, g, t) with nearly equal
frequencies, so the best order-0 code is 2 bits per base, exactly 25%, and huff gets 25.06%. There
are few long repeats for LZ to exploit, so the pointer machinery adds cost without removing much
redundancy. gzip's 28.0% is most likely caused by deflate sharing a single Huffman alphabet between
literals and match lengths, which prevents the four literals from all getting 2-bit codes. (This
explanation follows from how deflate works; I did not instrument gzip to confirm it.) xz gets close
(25.6%) but still pays more than a plain 2-bit code.

**`ptt5` (fax image): 20.8% against an entropy of 15.1%.** In this file the byte `0x00` makes up 87.1%
of all bytes, so its ideal cost is about 0.2 bits. A prefix code cannot spend less than one whole bit
on any symbol, so huff pays 1 bit for it. That is the `H <= L < H + 1` bound from the algorithm
write-up showing up in practice: Huffman's penalty is at most one bit per symbol, and it is largest
when one symbol is very likely. gzip and xz avoid the penalty by coding runs of zeros as one match.
(An arithmetic coder would also avoid it by allowing fractional bits.)

**Already-compressed and random data.** A good compressor leaves no redundancy in its output, so
nothing is left to remove: all three tools stay within 0.1% of the input size, and the byte
frequencies of the `.gz` files are nearly flat (entropy 99.99%). huff handles this case by storing
the block raw whenever Huffman coding would not make it smaller, which costs 4 bytes per block. The
output is 100.00% of the input.

## 2. Effect of each optimization

| Optimization | Measured effect |
|---|---|
| Table-driven decoding vs bit-by-bit search | text 2.9x, source 2.4x faster; DNA 1.3x; `kennedy.xls` 0.64x (slower) |
| Block size 256 KiB vs 1 MiB | source 58.9% -> 58.3%, binary 37.7% -> 37.2%; text and DNA within 0.05 points |
| Stored fallback for incompressible blocks | random and gzipped data about 100.06% -> 100.00% (computed, see below) |
| Package-merge length limit | no effect on the corpus; verified optimal by exhaustive search on small alphabets |

**Table-driven decoding.** The decoder peeks at 12 bits and finds the symbol with one array read. On
text and source this is 2.4x to 2.9x faster than the bit-by-bit canonical search, because common
letters have 3 to 5 bit codes and the search has to test several code lengths before it finds a
match. The table is a loss on `kennedy.xls` (0.6x to 0.7x on every run). That file is a spreadsheet
where 44% of bytes are `0x00`, so the most common symbols have 1 to 3 bit codes, and the search
finishes after one or two comparisons. Building a 4096-entry table for every block and then reading
it is extra work in that case. Because this file is most of the `binary` category by size, the
category's total (0.79x) is dominated by it and shifts from run to run. The table helps most when
code lengths are spread widely, and not at all when a few short codes dominate.

**Block size.** Smaller blocks adapt to local statistics but pay the 164-byte table more often.
Which effect wins depends on the file. Mixed-content files gain from small blocks (`kennedy.xls`
43.3% at 64 KiB against 44.9% at 1 MiB), while stationary data such as DNA gains from large ones
(25.25% at 64 KiB against 25.02% at 1 MiB, since a 4-symbol table is rarely worth refreshing). The
default of 256 KiB sits between the two: the table costs 0.06% of a full block, and no category
moves by more than 0.7 points compared with its best setting.

**Stored fallback.** The report shows 100.00% for these files because they are always stored. To
quantify the optimization, I computed the output size of random and gzipped input under the file
format with and without the fallback: without it, each block would add a 164-byte table to payload
that is already 8 bits per byte, giving about 100.06% to 100.07% (a 4 MiB file grows by about 2.6 KB).
With it, the output is 100.002%. The gain is small, but it guarantees that huff never expands its
input by more than 4 bytes per block.

**Package-merge.** This does not change any number here, because Huffman codes on these files stay
well under the 24-bit cap (the longest on the binary files is 16 to 17 bits). Its job is to keep the
format valid and optimal on inputs with Fibonacci-like frequencies; the unit tests check it against
an exhaustive search, and check that it matches plain Huffman when the cap does not bind.

**Throughput of stored blocks.** The decode figures for the `compressed` and `random` categories
(10,000+ MiB/s) measure copying bytes from input to output, not Huffman decoding, and should not be
compared with the other rows.

## 3. Throughput and command-line time

| | huff | gzip -9 (same JVM, Deflater) |
|---|---|---|
| compress, text | 264 MiB/s | 17 MiB/s |
| decompress, text | 295 MiB/s | 348 MiB/s |

Whole-process time, including JVM startup: huff takes about 0.15 to 0.2 s per call. On the 4.6 MB DNA
file huff takes 0.19 s, against 2.8 s for gzip -9 and 2.2 s for xz -9. On small files gzip finishes
in under 0.1 s.

**Compression is 15x faster than gzip -9, decompression slightly slower.** Huffman coding is a
count pass, a tiny tree build, and an encode pass of table lookups. Deflate at level 9 spends most
of its time searching earlier data for the longest match at each position, and that search is what
buys its better ratio. On the way back the situation reverses: Inflater reads a pointer and copies
a whole run of bytes at once, whereas huff decodes every output byte as a separate symbol. On text
that is 295 vs 348 MiB/s; on source code, where matches are longer, the gap widens to 282 vs 541.

**What the command-line table adds.** The in-process figures exclude startup, so they measure the
algorithms; they are the right view for comparing designs, or for a program that compresses many
buffers inside one long-running process. The command-line figures include about 0.15 s of JVM
startup, so they are the right view for someone running `huff` at a shell, and they show what the
in-process table hides: on files up to a few hundred KB huff is slower than gzip, because startup
dominates and gzip is a native binary. huff only pulls ahead once a file is large enough that
gzip's match search costs more than startup (a few MB at level 9).

## 4. Limits of this study

All measurements come from one machine and one configuration. In-process numbers are the median of 7
runs after 3 warmup passes, and command-line numbers are the median of 3 runs, so differences of a
few percent between runs are noise. The corpus is mostly English text and source code plus one DNA
file, one image, one spreadsheet and some synthetic data, so the ratios say little about, for
example, logs, JSON, or media formats. The external tools run as separate native processes while
huff's in-process numbers exclude startup, so the two throughput tables answer different questions
and should not be mixed. Finally, the stored-fallback figure in section 2 is computed from the file
format rather than measured by running a build without the fallback.
