# Huffman coding: algorithm, optimality, and implementation

## The problem

A file is a sequence of bytes. A fixed-length code spends 8 bits per byte. If some bytes are far
more common than others, a variable-length code can spend fewer bits on the common ones. To
decode a stream of variable-length codes without separators, no code may be a prefix of another
(a prefix code). Every prefix code corresponds to a binary tree whose leaves are the symbols,
and a symbol's code length is its leaf's depth.

Given symbol frequencies `f(s)`, the goal is a prefix code that minimizes the total output size

    cost = sum over symbols of f(s) * length(s)

A prefix code with lengths `l(s)` exists exactly when the Kraft inequality holds:
`sum 2^(-l(s)) <= 1`. A complete code (every internal node has two children) meets it with equality.

## Huffman's greedy construction

1. Put every symbol that occurs into a min-heap, keyed by frequency.
2. Remove the two lightest nodes, `x` and `y`. Make them the children of a new node whose weight is `f(x) + f(y)`. Put the new node back.
3. Repeat until one node is left. That node is the root.

With `n` distinct symbols, the loop runs `n - 1` times. Each iteration does two removals and one
insertion, each `O(log n)`, so the whole construction is `O(n log n)`. Here `n <= 256`, so the
cost is negligible next to coding the data. The implementation breaks weight ties by insertion
order, which makes the output identical on every run.

An input with a single distinct symbol would get a zero-length code, which cannot be decoded.
The implementation gives that symbol a 1-bit code instead.

## Why the result is optimal

Write `B(T)` for the cost of tree `T`.

**Lemma 1: an optimal tree is full.** If an internal node had one child, replacing that node by
its child would shorten every code below it and keep the code a prefix code. That lowers the
cost, so the original was not optimal.

**Lemma 2: the two least frequent symbols can be siblings at the deepest level.** Let `x` and `y`
be the two least frequent symbols, and take an optimal tree `T`. Because `T` is full, its deepest
level holds two sibling leaves `a` and `b`. Swap `x` with `a`. The cost changes by

    (f(x) - f(a)) * (depth(a) - depth(x))

which is `<= 0`, because `f(x) <= f(a)` and `a` is at the maximum depth. The same argument swaps
`y` with `b`. The new tree costs no more than `T`, so it is also optimal, and in it `x` and `y`
are siblings.

**Lemma 3: merging two siblings reduces the problem.** Let `T` be a tree in which `x` and `y` are
sibling leaves. Remove them and let their parent `z` become a leaf with `f(z) = f(x) + f(y)`. Call
the new tree `T'`. Each of `x` and `y` was one level deeper than `z`, so

    B(T) = B(T') + f(x) + f(y)

**Theorem: Huffman's algorithm produces an optimal tree.** Use induction on the number of symbols.
With two symbols, the only full tree is optimal. For more, let `x` and `y` be the two lightest
symbols and let `C'` be the alphabet with them replaced by `z`. By the induction hypothesis,
Huffman's algorithm builds an optimal tree `T'` for `C'` (this is exactly the step the algorithm
takes). By Lemma 2, some optimal tree for `C` has `x` and `y` as siblings. By Lemma 3, among
trees where `x` and `y` are siblings, `B(T) = B(T') + f(x) + f(y)`, so minimizing `B(T)` means
minimizing `B(T')`. Huffman's tree minimizes it, so the tree the algorithm builds for `C` is
optimal. Ties between equally light nodes can be broken either way; every choice is optimal.

## How good is optimal, anyway?

Let `H = -sum p(s) log2 p(s)` be the order-0 entropy, with `p(s) = f(s) / total`. For any prefix
code, the average length `L` satisfies `L >= H`. Huffman coding also satisfies `L < H + 1`: giving
each symbol the length `ceil(-log2 p(s))` meets the Kraft inequality and averages less than
`H + 1`, and Huffman's code is at least as good as that one.

The `+1` matters when one symbol is very common. Every symbol needs at least one whole bit, so a
symbol with probability 0.9 (ideal cost about 0.15 bits) still pays 1 bit. In the benchmark, the
fax image `ptt5` shows this: the order-0 entropy is 15.1% of the original size, but Huffman
coding cannot get below 20.8%.

Huffman coding only models single bytes, with no context. That is why gzip and xz, which also
find repeated strings, compress text to roughly half (gzip) or less (xz) of what Huffman alone achieves.

## Canonical codes: storing lengths instead of the tree

Many different trees have the same set of code lengths. The decoder only needs one of them, so
the file stores just the length of each byte's code (5 bits each, 160 bytes for all 256) and both
sides rebuild the same codes:

- Sort symbols by (length, symbol value).
- The first code of length `l` is `first[l] = (first[l-1] + count[l-1]) << 1`, where `count[l]` is
  the number of symbols with length `l`.
- Symbols of the same length get consecutive integers starting at `first[l]`.

This produces codes that, read as integers, increase with length and symbol, which also gives the
decoder a fast way to find a code's length (see below). The decoder checks the Kraft sum of the
stored lengths before using them, so a corrupt table is rejected rather than decoded into garbage.

## Limiting code length

In the worst case a Huffman tree over 256 symbols can be 255 levels deep. That happens when
frequencies grow like the Fibonacci numbers. Such codes would not fit a 32-bit write, the 5-bit
length field, or the decoder's lookup window, so the format caps lengths at 24 bits.

Plain Huffman exceeds the cap only on contrived inputs. When it does, the implementation switches
to the **package-merge** algorithm (Larmore and Hirschberg), which finds the code with the lowest
cost among all prefix codes whose lengths are at most `L`. It works level by level:

1. Sort the symbols by weight. Level `L` (the deepest) holds one item per symbol.
2. Each shallower level holds the symbols again, merged with *packages*: pairs of consecutive
   items from the level below, with their weights added.
3. At level 1, take the `2n - 2` lightest items. Each symbol's code length is the number of times
   it appears inside those items.

This is the coin collector problem in disguise, and it runs in `O(nL)` time and space, which is
256 x 24 here. A test compares the result with an exhaustive search over every length assignment
for small alphabets, and checks that it matches plain Huffman when the cap does not bind.

## Decoding

Reading one bit at a time and walking the tree is slow. The decoder instead peeks at the next 12
bits and looks them up in a table. Each table entry holds a symbol and its code length, and codes
shorter than 12 bits fill every slot that starts with their bits. Codes longer than 12 bits
(rare) fall back to a search: since canonical codes of each length are consecutive integers, the
decoder finds the length `l` where the code falls in `[first[l], first[l] + count[l])`.

The table makes each common symbol one array read. In the benchmark it decodes 2.4 to 2.9x faster
than the search on text and source code. It does not help when a few very short codes dominate:
on the spreadsheet `kennedy.xls` it is slower (0.64x), because the search ends after one or two
comparisons, and on DNA the gain is only 1.3x.

## File format

The input is split into blocks of up to 256 KiB. Each block gets its own code table, for two
reasons. Static Huffman needs the symbol counts before it writes the first bit, so processing
one block at a time bounds memory and makes pipes work. And separate tables let the code follow
the data when its statistics change. The cost is a 164-byte table per block, about 0.06% of a
full block.

If Huffman coding would not make a block smaller, the block is stored raw. Random and
already-compressed data then cost 4 bytes per block instead of growing. The README documents the
exact byte layout.
