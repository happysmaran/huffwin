# huffwin

A Huffman-coding file compressor written in Kotlin. It runs as a command, `huff`, and works on
files and on pipes. It reads the input one 256 KiB block at a time, so memory use stays small
even on huge files.

## Build

You need JDK 17 or newer and Gradle.

    gradle jar test
    ./bin/winhuff --help

`winhuff` is a shell script, so on Windows run it from Git Bash or WSL. To call it as `huff`
from anywhere, symlink it onto your `PATH`:

    ln -s "$PWD/bin/winhuff" ~/.local/bin/huff

## Usage

    huff compress   notes.txt            # writes notes.txt.huff
    huff decompress notes.txt.huff       # writes notes.txt
    huff compress   notes.txt -o out.huff -f
    cat notes.txt | huff compress | huff decompress
    huff stats      notes.txt            # entropy, average code length, per-byte code lengths

Exit status is 0 on success, 1 for a bad command line, and 2 for corrupt input or an I/O error.

## File format

A file is the magic bytes, then one or more blocks, then an end marker.

| Field | Size |
|---|---|
| Magic `HUF2` | 4 bytes |
| Block word: number of uncompressed bytes in the block (big-endian, never 0). The top bit marks a stored block. | 32 bits |
| Huffman block: code length for each of the 256 byte values, 0 for unused | 256 x 5 bits |
| Huffman block: coded payload, MSB first, zero-padded to a byte | variable |
| Stored block: the raw bytes, in place of the table and payload | block length bytes |
| End marker: a block word of 0 | 4 bytes |

Each block covers up to 256 KiB of input and has its own code table. The encoder has to count
symbols before it writes the first bit, so one block at a time keeps its memory bounded. The
separate tables also let the codes follow the data when its statistics change partway through.

If Huffman coding wouldn't make a block smaller, huff stores the block raw. Random and
already-compressed data then cost 4 bytes per block instead of a 164-byte table plus coded
data that is no shorter than the original.

The end marker tells the decoder the file is complete, so a file cut off at a block boundary
gets rejected. Extra bytes after the marker get rejected too.

huff stores only the code lengths, not the tree. The decoder rebuilds the same codes from the
lengths: equal-length codes are consecutive integers in byte order (canonical Huffman). Codes
are at most 24 bits long. When plain Huffman would go longer, huff finds the best code that
fits the limit using package-merge.

## Benchmarks

The `benchmark/` folder compares huff with gzip and xz. Its README explains how to run it.

## Documentation

`docs/algorithm.md` explains how the algorithm works, why it is optimal, and the choices in this implementation.

## Layout

- `MinHeap.kt` - binary min-heap for building the tree
- `BitIO.kt` - bit-level writer and peek/skip reader
- `Huffman.kt` - code lengths, canonical codes, block-wise compress/decompress, table-driven decoding, stats
- `Main.kt` - command-line interface
