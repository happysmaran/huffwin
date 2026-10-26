# huffwin

A simple Huffman-coding file compressor written in Kotlin, usable as a terminal command.
It streams: memory use is bounded by one block (1 MiB) regardless of input size, so it works
on large files and on pipes.

## Build

Requires JDK 17+ and Gradle.

    gradle jar test
    ./bin/winhuff --help

The launcher is a POSIX shell script, so on Windows run it from Git Bash or WSL.
To use it anywhere as `huff`, symlink the launcher onto your `PATH`:

    ln -s "$PWD/bin/winhuff" ~/.local/bin/huff

## Usage

    huff compress   notes.txt            # writes notes.txt.huff
    huff decompress notes.txt.huff       # writes notes.txt
    huff compress   notes.txt -o out.huff -f
    cat notes.txt | huff compress | huff decompress
    huff stats      notes.txt            # entropy, average code length, per-byte code lengths

Exit status is 0 on success, 1 for a bad command line, and 2 for corrupt input or I/O errors.

## File format

A stream is the magic bytes, then one or more blocks, then an end marker.

| Field | Size |
|---|---|
| Magic `HUF2` | 4 bytes |
| Block word: number of uncompressed bytes in the block (big-endian, never 0), with the top bit set for a stored block | 32 bits |
| Huffman block: code length for each of the 256 byte values, 0 meaning unused | 256 x 5 bits |
| Huffman block: coded payload, MSB first, zero-padded to a byte | variable |
| Stored block: the raw bytes, in place of the table and payload | block length bytes |
| End marker: a block word of 0 | 4 bytes |

Each block covers up to 1 MiB of input and carries its own code table, so the encoder only has
to hold one block in memory (a static Huffman code needs the symbol counts before it can emit
the first bit) and the codes follow the data if its statistics change partway through.
A block is written raw (stored) whenever Huffman coding would not make it smaller, so incompressible
input such as random or already-compressed data costs 4 bytes per block rather than a 164-byte table
plus coded data that is no shorter than the original.
The end marker lets the decoder tell a complete stream from one that was cut off at a block
boundary; bytes after the end marker are rejected.

Only code lengths are stored. Codes are assigned canonically (equal-length codes are consecutive
integers in byte order), so encoder and decoder rebuild identical codes from the lengths alone.
Code lengths are capped at 24 bits.

## Layout

- `MinHeap.kt` - binary min-heap used for tree construction
- `BitIO.kt` - bit-level writer and peek/skip reader
- `Huffman.kt` - code lengths, canonical codes, block-wise compress/decompress, table-driven decoding, stats
- `Main.kt` - command-line interface
