# huffwin

A simple Huffman-coding file compressor written in Kotlin, usable as a terminal command.

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

| Field | Size |
|---|---|
| Magic `HUF1` | 4 bytes |
| Original length (big-endian) | 8 bytes |
| Code length for each of the 256 byte values, 0 meaning unused | 256 x 5 bits |
| Huffman-coded payload, MSB first, zero-padded to a byte | variable |

Only code lengths are stored. Codes are assigned canonically (equal-length codes are consecutive
integers in byte order), so encoder and decoder rebuild identical codes from the lengths alone.
Code lengths are capped at 24 bits.

## Layout

- `MinHeap.kt` - binary min-heap used for tree construction
- `BitIO.kt` - bit-level writer and peek/skip reader
- `Huffman.kt` - code lengths, canonical codes, compress/decompress, table-driven decoding, stats
- `Main.kt` - command-line interface
