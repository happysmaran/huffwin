# huffwin

A simple Huffman-coding file compressor written in Kotlin, usable as a terminal command.
It streams: memory use is bounded by one block (1 MiB) regardless of input size, so it works
on large files and on pipes.

## Build

Requires JDK 17+ and Gradle.

    gradle jar test
    ./bin/huff --help

To use it anywhere, symlink the launcher onto your `PATH`:

    ln -s "$PWD/bin/huff" ~/.local/bin/huff

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
| Block: number of uncompressed bytes in the block (big-endian, never 0) | 32 bits |
| Block: code length for each of the 256 byte values, 0 meaning unused | 256 x 5 bits |
| Block: Huffman-coded payload, MSB first, zero-padded to a byte | variable |
| End marker: a block length of 0 | 4 bytes |

Each block covers up to 1 MiB of input and carries its own code table, so the encoder only has
to hold one block in memory (a static Huffman code needs the symbol counts before it can emit
the first bit) and the codes follow the data if its statistics change partway through.
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
