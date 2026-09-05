#!/usr/bin/env python3
"""What a reader downloads, with ceilings that only go down.

The site's load time had no budget, no gate and no test. `docs/building/testing.md`
says the suite is "counted, not timed", which is the right call for a component —
a recomposition count is the same number on a JVM and on a phone — and it left the
one thing a reader actually complains about unmeasured. Nothing anywhere asserted
on the size of the bundle.

This is the cheap half of that. `docs/measure-web.mjs` opens a browser and reports
what the load *costs*; this reports what it *weighs*, needs no browser, and runs in
under a second, so it can sit in CI on every change.

Sizes are gzip, because that is what crosses the wire — GitHub Pages compresses
text and Wasm and leaves fonts alone, and the difference between the two numbers
is a factor of three. Raw sizes are printed alongside, never asserted.

Skia gets its own ceiling. `skiko.wasm` is the Compose renderer, it is the same
bytes whatever this library contains, and at roughly two thirds of the payload it
would swamp any total that mixed it with ours: a 10% growth in the application
binary is invisible inside a number Skia dominates. Splitting them is what makes
this a ratchet on our own code.

    python3 docs/check-bundle-size.py [--dist DIR] [--update]

`--update` rewrites the ceilings in this file to what was just measured. Use it
when a change is *meant* to move a number, in the same commit, so the diff shows
the cost.
"""

import argparse
import gzip
import pathlib
import re
import sys

# Ceilings in gzip bytes, with roughly 3% of headroom over the measured figure so
# that a compressor version does not fail the build. They only go down.
#
# Measured 2026-09-05, Kotlin 2.4.10 / Compose 1.12.0-rc01, `--update`:
CEILINGS = {
    "skiko": 3_430_000,
    "app": 2_160_000,
    "js": 110_000,
    "fonts": 290_000,
    "other": 10_000,
    "total": 5_980_000,
}

# The whole first load, in files. Six is index.html, styles.css, the loader, two
# binaries and one font; anything past that is a round trip a reader waits for.
MAX_FILES = 14

COMPRESSIBLE = {".html", ".css", ".js", ".mjs", ".json", ".wasm", ".svg"}


def classify(path: pathlib.Path, skiko_sizes: set[int]) -> str:
    """Which ceiling this file counts against.

    Both `.wasm` files reach the distribution under a content hash, so neither can
    be recognised by name. Skia is identified by matching the raw byte count of the
    `skiko.wasm` the build unpacked — an exact-size match on a file the same build
    produced, rather than "the bigger one", which would silently swap the two the
    day the application binary overtakes the renderer.
    """
    if path.suffix == ".wasm":
        return "skiko" if path.stat().st_size in skiko_sizes else "app"
    if path.suffix in {".ttf", ".otf", ".woff2"}:
        return "fonts"
    if path.suffix in {".js", ".mjs"}:
        return "js"
    return "other"


def transfer_size(path: pathlib.Path) -> int:
    raw = path.read_bytes()
    if path.suffix not in COMPRESSIBLE:
        return len(raw)
    return len(gzip.compress(raw, 9))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dist", default="ui-docs/build/dist/wasmJs/productionExecutable")
    parser.add_argument("--update", action="store_true")
    options = parser.parse_args()

    dist = pathlib.Path(options.dist)
    if not dist.is_dir():
        print(f"no distribution at {dist} — run :ui-docs:wasmJsBrowserDistribution first")
        return 2

    skiko_sizes = {p.stat().st_size for p in pathlib.Path(".").glob("*/build/compose/**/skiko.wasm")}
    if not skiko_sizes:
        print("could not find the unpacked skiko.wasm, so the renderer cannot be told")
        print("apart from the application binary. Build the distribution first.")
        return 2

    files = sorted((p for p in dist.rglob("*") if p.is_file()), key=lambda p: -p.stat().st_size)
    groups: dict[str, int] = {name: 0 for name in CEILINGS if name != "total"}
    for path in files:
        groups[classify(path, skiko_sizes)] += transfer_size(path)
    groups["total"] = sum(groups.values())

    print(f"  {dist}   {len(files)} files\n")
    print("        gzip         raw   file")
    for path in files:
        print(f"  {transfer_size(path):10,}  {path.stat().st_size:10,}   {path.relative_to(dist)}")

    if options.update:
        source = pathlib.Path(__file__).read_text()
        for name, measured in groups.items():
            ceiling = int(measured * 1.03 / 10_000 + 1) * 10_000
            source = re.sub(
                rf'^(    "{name}": )[\d_]+,$',
                lambda m, c=ceiling: f"{m.group(1)}{c:_},",
                source,
                flags=re.M,
            )
        pathlib.Path(__file__).write_text(source)
        print("\n  ceilings updated")
        return 0

    print()
    over = []
    for name, ceiling in CEILINGS.items():
        measured = groups[name]
        room = ceiling - measured
        flag = "OVER" if room < 0 else f"{room:,} to spare"
        print(f"  {name:>6}  {measured:10,}  of {ceiling:10,}   {flag}")
        if room < 0:
            over.append((name, measured, ceiling))

    if len(files) > MAX_FILES:
        print(f"\n  {len(files)} files in the distribution, ceiling is {MAX_FILES}.")
        over.append(("files", len(files), MAX_FILES))

    if over:
        print("\n  The bundle grew past a ceiling:\n")
        for name, measured, ceiling in over:
            print(f"    {name}: {measured:,} against {ceiling:,} — over by {measured - ceiling:,}")
        print(
            "\n  If the growth is the point of the change, re-run with --update in the\n"
            "  same commit so the diff carries the cost. If it is not, this is a\n"
            "  dependency or a resource that arrived without anyone asking for it."
        )
        return 1

    print("\n  Under every ceiling.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
