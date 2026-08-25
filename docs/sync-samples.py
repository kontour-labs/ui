#!/usr/bin/env python3
"""Keep the examples in `ui-docs/content/` identical to compiled source.

An example that does not compile is worse than no example: it reads as a
confident answer and is wrong, and the reader finds out by pasting it into their
app. So the examples on the documentation pages are not written in the
documentation. They live in `ui-samples/`, where the Kotlin compiler reads them
against the library's real public API, and the pages hold copies.

This is what keeps the copy honest:

    python3 docs/sync-samples.py            # compare; non-zero if any drifted
    python3 docs/sync-samples.py --write    # rewrite the pages from the source

Same shape as the screenshot harness, and for the same reason — a step that
regenerates its own expectations every run checks nothing, so comparing is the
default and rewriting takes a flag.

### How a page claims a sample

An HTML comment immediately before a fenced Kotlin block:

    <!--sample:ButtonVariants-->
    ```kotlin
    …
    ```

The name is a function in `ui-samples`, by convention `fun ButtonVariants()`.
What lands in the block is that function's *body*, dedented — not its signature,
because `fun ButtonVariants()` is scaffolding the reader does not need and would
have to mentally strip out of every example on the page.

**A fenced block with no marker is left alone, deliberately.** Plenty of blocks
in these pages are not examples: a signature being discussed, a fragment with an
ellipsis in it, a shell command. Requiring every block to compile would mean
inventing a compiling context for each of those, and the useful ones would drown
in it.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from doctree import CONTENT  # noqa: E402

MARKER = re.compile(r"^<!--\s*sample:\s*([A-Za-z_][A-Za-z0-9_]*)\s*-->\s*$")
FENCE_OPEN = re.compile(r"^```kotlin\s*$")
FENCE_CLOSE = re.compile(r"^```\s*$")

SAMPLES = Path("ui-samples/src/commonMain/kotlin")
PAGES = CONTENT


def bodies() -> dict[str, str]:
    """Every sample function in the module, by name, as dedented body text.

    **A sample is a public top-level function.** Anything with a visibility
    modifier in front of it is scaffolding — the stand-in actions, the fake
    rows — and is skipped, which is the whole rule separating the two. It is
    also what makes the "compiled but no page shows it" check usable: without
    it, every helper would be reported as an orphan.

    Brace counting rather than a parser, and it can afford to be: these are
    functions written to be read, in a directory whose whole purpose is holding
    them. A string containing an unbalanced brace would defeat it, and the
    compare step is what would catch that — the block would fill with the wrong
    text and the diff would be obvious.
    """
    found: dict[str, str] = {}
    for path in sorted(SAMPLES.rglob("*.kt")):
        lines = path.read_text(encoding="utf-8").split("\n")
        for index, line in enumerate(lines):
            match = re.match(r"^(?:@Composable\s+)?fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(", line)
            if not match or not line.rstrip().endswith("{"):
                continue

            name = match.group(1)
            if name in found:
                raise SystemExit(f"{path}: two samples are called `{name}`")

            depth, body = 0, []
            for current in lines[index:]:
                if body or depth:
                    body.append(current)
                depth += current.count("{") - current.count("}")
                if depth == 0 and body:
                    body.pop()  # the closing brace, which is not part of the body
                    break
            found[name] = dedent(body)
    return found


def dedent(lines: list[str]) -> str:
    """Strips the common leading indentation, ignoring blank lines."""
    widths = [len(line) - len(line.lstrip()) for line in lines if line.strip()]
    trim = min(widths) if widths else 0
    return "\n".join(line[trim:] if line.strip() else "" for line in lines).strip("\n")


def blocks(lines: list[str]) -> list[tuple[str, int, int, int]]:
    """Every marked block in a page, as (name, marker line, first, last).

    `first` and `last` bound the block's content — the lines between the fences,
    which is what gets replaced.
    """
    found = []
    for index, line in enumerate(lines):
        marker = MARKER.match(line)
        if not marker:
            continue
        if index + 1 >= len(lines) or not FENCE_OPEN.match(lines[index + 1]):
            raise SystemExit(
                f"line {index + 1}: `{marker.group(1)}` is not followed by a ```kotlin fence"
            )
        close = next(
            (n for n in range(index + 2, len(lines)) if FENCE_CLOSE.match(lines[n])),
            None,
        )
        if close is None:
            raise SystemExit(f"line {index + 1}: `{marker.group(1)}`'s fence is never closed")
        found.append((marker.group(1), index, index + 2, close))
    return found


def main() -> int:
    write = "--write" in sys.argv[1:]
    if not SAMPLES.is_dir():
        print(f"no sample sources at {SAMPLES} — run from the repository root", file=sys.stderr)
        return 2

    available = bodies()
    stale: list[str] = []
    shown: set[str] = set()
    rewritten = 0
    total = 0

    for path in sorted(PAGES.rglob("*.md")):
        lines = path.read_text(encoding="utf-8").split("\n")
        try:
            marked = blocks(lines)
        except SystemExit as malformed:
            print(f"{path}:{malformed}", file=sys.stderr)
            return 1
        if not marked:
            continue

        # Back to front, so an earlier replacement cannot move a later block's
        # line numbers out from under it.
        for name, marker_line, first, last in reversed(marked):
            total += 1
            shown.add(name)
            if name not in available:
                stale.append(
                    f"{path}:{marker_line + 1}: no sample called `{name}` in {SAMPLES}"
                )
                continue
            expected = available[name].split("\n")
            if lines[first:last] == expected:
                continue
            if write:
                lines[first:last] = expected
                rewritten += 1
            else:
                stale.append(f"{path}:{marker_line + 1}: `{name}` has drifted from its source")

        if write:
            path.write_text("\n".join(lines), encoding="utf-8")

    # A sample nobody shows is a sample nobody reads, kept compiling forever by
    # a build that has forgotten why. Cheap to catch here, and the only thing
    # that stops this module accumulating them.
    for name in sorted(set(available) - shown):
        stale.append(f"{SAMPLES}: `{name}` is compiled but no page shows it")

    if stale:
        print(f"{len(stale)} problem(s) with the documentation samples:", file=sys.stderr)
        for problem in stale:
            print(f"  {problem}", file=sys.stderr)
        if not write:
            print(
                "\nIf the source is right and the pages are behind, run:\n"
                "  python3 docs/sync-samples.py --write",
                file=sys.stderr,
            )
        return 1

    if write:
        print(f"{total} samples checked, {rewritten} rewritten.")
    else:
        print(f"all {total} documentation samples match their source.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
