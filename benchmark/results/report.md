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
| binary | kennedy.xls | 1029744 | 44.67% | 44.93% | 20.11% | 20.37% | 4.77% |
| binary | ptt5 | 513216 | 15.13% | 20.79% | 10.18% | 10.21% | 8.18% |
| binary | sum | 38240 | 66.61% | 67.51% | 33.60% | 33.39% | 24.74% |
| **binary total** |  | 1581200 | **35.61%** | **37.65%** | **17.21%** | **17.38%** | **6.36%** |
| compressed | bible.txt.gz | 1191897 | 99.99% | 100.03% | 100.03% | 100.02% | 100.01% |
| compressed | jdk-java-base.java.gz | 1543093 | 99.99% | 100.02% | 99.77% | 99.76% | 99.80% |
| compressed | world192.txt.gz | 724982 | 99.97% | 100.02% | 100.03% | 100.02% | 100.01% |
| **compressed total** |  | 3459972 | **99.99%** | **100.02%** | **99.92%** | **99.90%** | **99.92%** |
| dna | E.coli | 4638690 | 25.00% | 25.02% | 28.02% | 28.00% | 25.58% |
| **dna total** |  | 4638690 | **25.00%** | **25.02%** | **28.02%** | **28.00%** | **25.58%** |
| random | random-4MiB.bin | 4194304 | 100.00% | 100.02% | 100.03% | 100.02% | 100.01% |
| **random total** |  | 4194304 | **100.00%** | **100.02%** | **100.03%** | **100.02%** | **100.01%** |
| source | cp.html | 24603 | 65.36% | 66.54% | 32.32% | 32.41% | 31.10% |
| source | fields.c | 11150 | 62.60% | 64.56% | 28.04% | 28.04% | 27.23% |
| source | grammar.lsp | 3721 | 57.90% | 62.94% | 33.16% | 33.16% | 34.94% |
| source | huffwin-src.kt | 29723 | 60.12% | 60.94% | 30.96% | 30.96% | 29.74% |
| source | jdk-java-base.java | 8388608 | 59.39% | 58.90% | 18.16% | 18.18% | 12.57% |
| source | xargs.1 | 4227 | 61.23% | 65.63% | 41.35% | 41.35% | 43.06% |
| **source total** |  | 8462032 | **59.42%** | **58.95%** | **18.28%** | **18.29%** | **12.73%** |
| text | alice29.txt | 152089 | 57.10% | 57.77% | 35.63% | 35.62% | 31.89% |
| text | asyoulik.txt | 125179 | 60.10% | 60.70% | 38.98% | 39.00% | 35.58% |
| text | bible.txt | 4047392 | 54.28% | 54.77% | 29.09% | 29.07% | 21.87% |
| text | lcet10.txt | 426754 | 58.36% | 58.75% | 33.85% | 33.84% | 28.00% |
| text | plrabn12.txt | 481861 | 56.64% | 57.23% | 40.33% | 40.32% | 34.33% |
| text | world192.txt | 2473400 | 62.48% | 62.94% | 29.19% | 29.17% | 19.71% |
| **text total** |  | 7706675 | **57.44%** | **57.92%** | **30.38%** | **30.36%** | **22.72%** |

## 2. In-process throughput

MiB of original data per second (higher is better), measured inside one warmed-up JVM so process startup is excluded. "Table speedup" is table-driven decoding divided by bit-by-bit decoding, i.e. the measured effect of that optimization. `Deflater -9` is the same algorithm as `gzip -9`, run in the same JVM as huff for a like-for-like comparison.

| Category | File | huff compress | huff decode (table) | huff decode (bit-by-bit) | Table speedup | Deflater -9 compress | Inflater decode |
| --- | --- | --- | --- | --- | --- | --- | --- |
| binary | kennedy.xls | 335.9 | 183.8 | 250.1 | 0.73x | 4.0 | 741.2 |
| binary | ptt5 | 273.0 | 209.2 | 251.0 | 0.83x | 10.8 | 875.3 |
| binary | sum | 125.1 | 276.1 | 120.5 | 2.29x | 5.5 | 307.5 |
| ****binary total**** |  | **301.1** | **193.0** | **244.0** | **0.79x** | **5.0** | **752.9** |
| compressed | bible.txt.gz | 430.5 | 362.5 | 173.8 | 2.09x | 56.8 | 2841.0 |
| compressed | jdk-java-base.java.gz | 439.0 | 362.2 | 163.6 | 2.21x | 53.8 | 1493.6 |
| compressed | world192.txt.gz | 409.3 | 362.7 | 171.3 | 2.12x | 53.5 | 3082.5 |
| ****compressed total**** |  | **429.6** | **362.4** | **168.6** | **2.15x** | **54.7** | **2049.9** |
| dna | E.coli | 528.1 | 418.6 | 488.6 | 0.86x | 1.5 | 426.8 |
| random | random-4MiB.bin | 473.0 | 383.1 | 166.0 | 2.31x | 52.8 | 3230.0 |
| source | cp.html | 153.4 | 272.8 | 123.1 | 2.22x | 54.8 | 339.6 |
| source | fields.c | 98.8 | 235.3 | 112.6 | 2.09x | 52.5 | 362.9 |
| source | grammar.lsp | 44.9 | 176.5 | 123.2 | 1.43x | 80.1 | 412.6 |
| source | huffwin-src.kt | 150.1 | 213.0 | 112.1 | 1.90x | 23.7 | 316.7 |
| source | jdk-java-base.java | 238.2 | 277.0 | 120.1 | 2.31x | 15.8 | 545.6 |
| source | xargs.1 | 51.2 | 187.5 | 96.4 | 1.94x | 77.8 | 333.2 |
| ****source total**** |  | **236.0** | **276.5** | **120.0** | **2.30x** | **15.9** | **542.6** |
| text | alice29.txt | 230.4 | 290.0 | 103.6 | 2.80x | 19.2 | 301.3 |
| text | asyoulik.txt | 239.2 | 304.5 | 104.8 | 2.91x | 20.1 | 277.5 |
| text | bible.txt | 276.2 | 310.2 | 114.3 | 2.71x | 13.8 | 369.8 |
| text | lcet10.txt | 267.9 | 294.0 | 113.5 | 2.59x | 22.5 | 315.7 |
| text | plrabn12.txt | 265.3 | 290.9 | 97.5 | 2.98x | 13.7 | 290.8 |
| text | world192.txt | 230.2 | 281.5 | 99.7 | 2.82x | 26.8 | 348.4 |
| ****text total**** |  | **256.9** | **297.8** | **107.7** | **2.77x** | **16.9** | **350.2** |

## 3. Command-line wall-clock time

Seconds for the whole process (lower is better), reading stdin and writing a file, so this includes JVM startup for `huff CLI`. It shows what a user sees at the shell, not the algorithm's speed; small files are dominated by startup.

| Category | File | huff CLI compress | huff CLI decompress | gzip -6 compress | gzip -6 decompress | gzip -9 compress | gzip -9 decompress | xz -9 compress | xz -9 decompress |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| binary | kennedy.xls | 0.174 | 0.161 | 0.096 | 0.076 | 0.309 | 0.074 | 0.254 | 0.076 |
| binary | ptt5 | 0.164 | 0.156 | 0.077 | 0.071 | 0.113 | 0.072 | 0.156 | 0.074 |
| binary | sum | 0.157 | 0.152 | 0.074 | 0.070 | 0.076 | 0.071 | 0.094 | 0.072 |
| compressed | bible.txt.gz | 0.182 | 0.162 | 0.092 | 0.073 | 0.090 | 0.072 | 0.342 | 0.075 |
| compressed | jdk-java-base.java.gz | 0.194 | 0.166 | 0.097 | 0.075 | 0.102 | 0.073 | 0.407 | 0.075 |
| compressed | world192.txt.gz | 0.172 | 0.160 | 0.085 | 0.072 | 0.083 | 0.072 | 0.243 | 0.076 |
| dna | E.coli | 0.192 | 0.181 | 0.641 | 0.089 | 2.753 | 0.093 | 2.271 | 0.096 |
| random | random-4MiB.bin | 0.233 | 0.184 | 0.140 | 0.079 | 0.143 | 0.077 | 1.025 | 0.082 |
| source | cp.html | 0.157 | 0.151 | 0.071 | 0.070 | 0.072 | 0.071 | 0.087 | 0.072 |
| source | fields.c | 0.158 | 0.149 | 0.072 | 0.070 | 0.071 | 0.070 | 0.082 | 0.072 |
| source | grammar.lsp | 0.156 | 0.151 | 0.071 | 0.071 | 0.072 | 0.070 | 0.080 | 0.072 |
| source | huffwin-src.kt | 0.156 | 0.150 | 0.072 | 0.070 | 0.072 | 0.071 | 0.089 | 0.073 |
| source | jdk-java-base.java | 0.255 | 0.214 | 0.198 | 0.095 | 0.552 | 0.094 | 1.788 | 0.100 |
| source | xargs.1 | 0.159 | 0.149 | 0.071 | 0.070 | 0.070 | 0.070 | 0.082 | 0.072 |
| text | alice29.txt | 0.158 | 0.153 | 0.075 | 0.072 | 0.078 | 0.072 | 0.111 | 0.075 |
| text | asyoulik.txt | 0.159 | 0.153 | 0.076 | 0.071 | 0.076 | 0.071 | 0.107 | 0.073 |
| text | bible.txt | 0.203 | 0.183 | 0.200 | 0.088 | 0.348 | 0.088 | 1.214 | 0.091 |
| text | lcet10.txt | 0.166 | 0.154 | 0.085 | 0.073 | 0.089 | 0.072 | 0.167 | 0.074 |
| text | plrabn12.txt | 0.163 | 0.158 | 0.095 | 0.073 | 0.105 | 0.074 | 0.179 | 0.075 |
| text | world192.txt | 0.191 | 0.174 | 0.124 | 0.080 | 0.157 | 0.081 | 0.596 | 0.084 |

## 4. Effect of block size on huff's ratio

huff output size as a percentage of the original for different block sizes (each block carries a 164-byte table). Smaller blocks adapt faster to changing data but pay the table more often; the default is 1024 KiB.

| Category | File | 64 KiB | 256 KiB | 1024 KiB | 4096 KiB |
| --- | --- | --- | --- | --- | --- |
| binary | kennedy.xls | 43.27% | 44.31% | 44.93% | 44.93% |
| binary | ptt5 | 20.91% | 20.81% | 20.79% | 20.79% |
| binary | sum | 67.51% | 67.51% | 67.51% | 67.51% |
| **binary total** |  | **36.59%** | **37.24%** | **37.65%** | **37.65%** |
| compressed | bible.txt.gz | 100.26% | 100.07% | 100.03% | 100.01% |
| compressed | jdk-java-base.java.gz | 100.25% | 100.06% | 100.02% | 100.01% |
| compressed | world192.txt.gz | 100.27% | 100.07% | 100.02% | 100.02% |
| **compressed total** |  | **100.26%** | **100.07%** | **100.02%** | **100.01%** |
| dna | E.coli | 25.25% | 25.06% | 25.02% | 25.01% |
| **dna total** |  | **25.25%** | **25.06%** | **25.02%** | **25.01%** |
| random | random-4MiB.bin | 100.25% | 100.06% | 100.02% | 100.00% |
| **random total** |  | **100.25%** | **100.06%** | **100.02%** | **100.00%** |
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
