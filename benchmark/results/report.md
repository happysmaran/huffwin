# Benchmark report

Generated 2026-10-25 by `benchmark/`. Raw numbers are in `results.csv`.

## Environment

- OS: Windows 11 10.0 (amd64), 16 logical cores
- JVM: Oracle Corporation 25.0.4
- In-process timings: 3 warmup passes, then the median of 7 passes
- Command-line timings: median of 3 runs, process startup included
- External tools used: huff CLI, gzip -6, gzip -9, xz -9

## 1. Compression ratio

Compressed size as a percentage of the original (lower is better). The order-0 entropy column is the size if every byte cost exactly its information content under one whole-file model, the limit for a single Huffman table. huff uses one table per block, so it can land slightly below that column when the data's statistics change across the file.

| Category | File | Bytes | Order-0 entropy | huff | Deflater -9 | gzip -9 | xz -9 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| binary | kennedy.xls | 1029744 | 44.67% | 44.31% | 20.11% | 20.37% | 4.77% |
| binary | ptt5 | 513216 | 15.13% | 20.81% | 10.18% | 10.21% | 8.18% |
| binary | sum | 38240 | 66.61% | 67.51% | 33.60% | 33.39% | 24.74% |
| **binary total** |  | 1581200 | **35.61%** | **37.24%** | **17.21%** | **17.38%** | **6.36%** |
| compressed | bible.txt.gz | 1191897 | 99.99% | 100.00% | 100.03% | 100.02% | 100.01% |
| compressed | jdk-java-base.java.gz | 1543093 | 99.99% | 100.00% | 99.77% | 99.76% | 99.80% |
| compressed | world192.txt.gz | 724982 | 99.97% | 100.00% | 100.03% | 100.02% | 100.01% |
| **compressed total** |  | 3459972 | **99.99%** | **100.00%** | **99.92%** | **99.90%** | **99.92%** |
| dna | E.coli | 4638690 | 25.00% | 25.06% | 28.02% | 28.00% | 25.58% |
| **dna total** |  | 4638690 | **25.00%** | **25.06%** | **28.02%** | **28.00%** | **25.58%** |
| random | random-4MiB.bin | 4194304 | 100.00% | 100.00% | 100.03% | 100.02% | 100.01% |
| **random total** |  | 4194304 | **100.00%** | **100.00%** | **100.03%** | **100.02%** | **100.01%** |
| source | cp.html | 24603 | 65.36% | 66.54% | 32.32% | 32.41% | 31.10% |
| source | fields.c | 11150 | 62.60% | 64.56% | 28.04% | 28.04% | 27.23% |
| source | grammar.lsp | 3721 | 57.90% | 62.94% | 33.16% | 33.16% | 34.94% |
| source | huffwin-src.kt | 29723 | 60.12% | 60.94% | 30.96% | 30.96% | 29.74% |
| source | jdk-java-base.java | 8388608 | 59.39% | 58.20% | 18.16% | 18.18% | 12.57% |
| source | xargs.1 | 4227 | 61.23% | 65.63% | 41.35% | 41.35% | 43.06% |
| **source total** |  | 8462032 | **59.42%** | **58.25%** | **18.28%** | **18.29%** | **12.73%** |
| text | alice29.txt | 152089 | 57.10% | 57.77% | 35.63% | 35.62% | 31.89% |
| text | asyoulik.txt | 125179 | 60.10% | 60.70% | 38.98% | 39.00% | 35.58% |
| text | bible.txt | 4047392 | 54.28% | 54.75% | 29.09% | 29.07% | 21.87% |
| text | lcet10.txt | 426754 | 58.36% | 58.73% | 33.85% | 33.84% | 28.00% |
| text | plrabn12.txt | 481861 | 56.64% | 57.25% | 40.33% | 40.32% | 34.33% |
| text | world192.txt | 2473400 | 62.48% | 62.90% | 29.19% | 29.17% | 19.71% |
| **text total** |  | 7706675 | **57.44%** | **57.90%** | **30.38%** | **30.36%** | **22.72%** |

## 2. In-process throughput

MiB of original data per second (higher is better), measured inside one warmed-up JVM so process startup is excluded. "Table speedup" is table-driven decoding divided by bit-by-bit decoding, i.e. the measured effect of that optimization. `Deflater -9` is the same algorithm as `gzip -9`, run in the same JVM as huff for a like-for-like comparison.

| Category | File | huff compress | huff decode (table) | huff decode (bit-by-bit) | Table speedup | Deflater -9 compress | Inflater decode |
| --- | --- | --- | --- | --- | --- | --- | --- |
| binary | kennedy.xls | 207.1 | 184.6 | 286.6 | 0.64x | 3.9 | 700.2 |
| binary | ptt5 | 287.1 | 395.3 | 309.9 | 1.28x | 10.6 | 898.9 |
| binary | sum | 187.6 | 284.0 | 118.7 | 2.39x | 5.6 | 309.3 |
| **binary total** |  | **227.0** | **225.5** | **283.8** | **0.79x** | **5.0** | **730.3** |
| compressed | bible.txt.gz | 1662.1 | 13050.3 | 16740.5 | 0.78x | 53.4 | 3032.8 |
| compressed | jdk-java-base.java.gz | 2118.0 | 15589.1 | 15739.1 | 0.99x | 53.6 | 1409.7 |
| compressed | world192.txt.gz | 1981.6 | 11242.2 | 10304.0 | 1.09x | 55.3 | 3148.4 |
| **compressed total** |  | **1910.0** | **13579.0** | **14440.6** | **0.94x** | **53.9** | **2014.1** |
| dna | E.coli | 556.5 | 395.6 | 302.1 | 1.31x | 1.5 | 424.5 |
| random | random-4MiB.bin | 2707.6 | 23460.4 | 19138.8 | 1.23x | 53.5 | 3211.0 |
| source | cp.html | 234.2 | 270.6 | 112.3 | 2.41x | 53.4 | 330.9 |
| source | fields.c | 181.1 | 241.7 | 108.6 | 2.23x | 53.6 | 344.1 |
| source | grammar.lsp | 119.9 | 181.1 | 132.4 | 1.37x | 77.8 | 427.5 |
| source | huffwin-src.kt | 210.6 | 266.4 | 111.0 | 2.40x | 24.0 | 324.0 |
| source | jdk-java-base.java | 233.8 | 282.1 | 118.9 | 2.37x | 15.7 | 543.9 |
| source | xargs.1 | 141.4 | 196.6 | 99.8 | 1.97x | 80.0 | 341.6 |
| **source total** |  | **233.4** | **281.9** | **118.9** | **2.37x** | **15.8** | **541.0** |
| text | alice29.txt | 259.5 | 293.7 | 110.5 | 2.66x | 19.0 | 296.6 |
| text | asyoulik.txt | 271.3 | 290.3 | 101.2 | 2.87x | 20.0 | 292.7 |
| text | bible.txt | 284.0 | 307.0 | 104.7 | 2.93x | 13.8 | 350.4 |
| text | lcet10.txt | 281.1 | 304.3 | 108.3 | 2.81x | 23.2 | 333.1 |
| text | plrabn12.txt | 288.4 | 295.1 | 105.5 | 2.80x | 14.0 | 286.4 |
| text | world192.txt | 232.0 | 276.0 | 96.9 | 2.85x | 27.6 | 369.8 |
| **text total** |  | **264.4** | **295.0** | **102.3** | **2.88x** | **17.1** | **348.0** |

## 3. Command-line wall-clock time

Seconds for the whole process (lower is better), reading stdin and writing a file, so this includes JVM startup for `huff CLI`. It shows what a user sees at the shell, not the algorithm's speed; small files are dominated by startup.

| Category | File | huff CLI compress | huff CLI decompress | gzip -6 compress | gzip -6 decompress | gzip -9 compress | gzip -9 decompress | xz -9 compress | xz -9 decompress |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| binary | kennedy.xls | 0.173 | 0.161 | 0.088 | 0.074 | 0.310 | 0.073 | 0.256 | 0.077 |
| binary | ptt5 | 0.161 | 0.158 | 0.078 | 0.074 | 0.114 | 0.070 | 0.155 | 0.073 |
| binary | sum | 0.154 | 0.151 | 0.071 | 0.070 | 0.078 | 0.070 | 0.091 | 0.072 |
| compressed | bible.txt.gz | 0.157 | 0.151 | 0.092 | 0.072 | 0.091 | 0.072 | 0.340 | 0.076 |
| compressed | jdk-java-base.java.gz | 0.158 | 0.152 | 0.097 | 0.074 | 0.096 | 0.073 | 0.412 | 0.075 |
| compressed | world192.txt.gz | 0.156 | 0.147 | 0.082 | 0.070 | 0.085 | 0.070 | 0.246 | 0.073 |
| dna | E.coli | 0.188 | 0.183 | 0.640 | 0.088 | 2.757 | 0.092 | 2.215 | 0.096 |
| random | random-4MiB.bin | 0.164 | 0.153 | 0.141 | 0.076 | 0.140 | 0.076 | 1.016 | 0.081 |
| source | cp.html | 0.157 | 0.150 | 0.071 | 0.070 | 0.070 | 0.071 | 0.087 | 0.071 |
| source | fields.c | 0.154 | 0.147 | 0.071 | 0.070 | 0.070 | 0.070 | 0.083 | 0.071 |
| source | grammar.lsp | 0.155 | 0.152 | 0.070 | 0.069 | 0.070 | 0.069 | 0.081 | 0.070 |
| source | huffwin-src.kt | 0.158 | 0.153 | 0.072 | 0.069 | 0.070 | 0.070 | 0.088 | 0.071 |
| source | jdk-java-base.java | 0.248 | 0.210 | 0.199 | 0.094 | 0.548 | 0.093 | 1.739 | 0.097 |
| source | xargs.1 | 0.155 | 0.147 | 0.071 | 0.070 | 0.072 | 0.069 | 0.080 | 0.071 |
| text | alice29.txt | 0.158 | 0.153 | 0.075 | 0.070 | 0.077 | 0.071 | 0.111 | 0.072 |
| text | asyoulik.txt | 0.157 | 0.154 | 0.075 | 0.070 | 0.076 | 0.070 | 0.105 | 0.072 |
| text | bible.txt | 0.200 | 0.180 | 0.200 | 0.088 | 0.346 | 0.086 | 1.225 | 0.089 |
| text | lcet10.txt | 0.164 | 0.155 | 0.084 | 0.071 | 0.088 | 0.072 | 0.165 | 0.074 |
| text | plrabn12.txt | 0.162 | 0.156 | 0.091 | 0.074 | 0.103 | 0.072 | 0.179 | 0.075 |
| text | world192.txt | 0.187 | 0.170 | 0.122 | 0.080 | 0.156 | 0.080 | 0.591 | 0.083 |

## 4. Effect of block size on huff's ratio

huff output size as a percentage of the original for different block sizes (each block carries a 164-byte table). Smaller blocks adapt faster to changing data but pay the table more often; the default is 256 KiB.

| Category | File | 64 KiB | 256 KiB | 1024 KiB | 4096 KiB |
| --- | --- | --- | --- | --- | --- |
| binary | kennedy.xls | 43.27% | 44.31% | 44.93% | 44.93% |
| binary | ptt5 | 20.91% | 20.81% | 20.79% | 20.79% |
| binary | sum | 67.51% | 67.51% | 67.51% | 67.51% |
| **binary total** |  | **36.59%** | **37.24%** | **37.65%** | **37.65%** |
| compressed | bible.txt.gz | 100.01% | 100.00% | 100.00% | 100.00% |
| compressed | jdk-java-base.java.gz | 100.01% | 100.00% | 100.00% | 100.00% |
| compressed | world192.txt.gz | 100.01% | 100.00% | 100.00% | 100.00% |
| **compressed total** |  | **100.01%** | **100.00%** | **100.00%** | **100.00%** |
| dna | E.coli | 25.25% | 25.06% | 25.02% | 25.01% |
| **dna total** |  | **25.25%** | **25.06%** | **25.02%** | **25.01%** |
| random | random-4MiB.bin | 100.01% | 100.00% | 100.00% | 100.00% |
| **random total** |  | **100.01%** | **100.00%** | **100.00%** | **100.00%** |
| source | cp.html | 66.54% | 66.54% | 66.54% | 66.54% |
| source | fields.c | 64.56% | 64.56% | 64.56% | 64.56% |
| source | grammar.lsp | 62.94% | 62.94% | 62.94% | 62.94% |
| source | huffwin-src.kt | 60.94% | 60.94% | 60.94% | 60.94% |
| source | jdk-java-base.java | 57.55% | 58.20% | 58.90% | 59.32% |
| source | xargs.1 | 65.63% | 65.63% | 65.63% | 65.63% |
| **source total** |  | **57.60%** | **58.25%** | **58.95%** | **59.36%** |
| text | alice29.txt | 57.91% | 57.77% | 57.77% | 57.77% |
| text | asyoulik.txt | 60.81% | 60.70% | 60.70% | 60.70% |
| text | bible.txt | 54.87% | 54.75% | 54.77% | 54.82% |
| text | lcet10.txt | 58.71% | 58.73% | 58.75% | 58.75% |
| text | plrabn12.txt | 57.43% | 57.25% | 57.23% | 57.23% |
| text | world192.txt | 62.97% | 62.90% | 62.94% | 63.02% |
| **text total** |  | **58.00%** | **57.90%** | **57.92%** | **57.97%** |

## How the numbers were produced

- Every file is round-tripped through huff (both decoders), Deflater, and each external tool before its timings are recorded; a mismatch aborts the run.
- In-process compression writes to a byte-counting sink and decoding is measured to a discarding sink, so memory allocation for results does not affect throughput.
- External tools run as `tool -c < input > output`. Their wall time includes process creation, which is why the in-process table is the fairer view of the algorithms and the command-line table is the fairer view of the user experience.
- Treat differences of a few percent between runs as noise; rerun with a larger `--runs` to tighten them.
