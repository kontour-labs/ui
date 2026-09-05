#!/usr/bin/env python3
"""Every "API reference" link on the site points at a page the reference has.

    python3 docs/check-api-links.py

Needs two build outputs and fails if either is absent, which is the point:

  * `ui-docs/build/generated/apiTables/ApiTables.kt` — written by
    `:ui-docs:generateApiTables`, and carrying `apiReferencePaths`, the map the
    site turns a symbol into a URL with.
  * `ui/build/dokka/html/scripts/pages.json` — written by
    `:ui:dokkaGenerateHtml`, and listing every page the reference published.

## Why the site derives a path instead of reading this file

Build order. CI builds the site *before* it builds the reference, so making
`:ui-docs` depend on `pages.json` would reverse the two for the sake of a
mapping that is a pure function of a package and a name. So the generator
derives it — every capital becomes a dash and a lowercase letter, a function is
a file and a type is a directory with an index in it — and this script is what
stops that being a guess: run after both, it compares all 357 derived paths
against the 1,546 the reference actually published.

## What it caught

The first run, before the top-level filter went in: 24 paths for declarations
that have no page at all. `onPreScroll` and three siblings, from an
`object : NestedScrollConnection { }` inside a function — no receiver and no
enclosing type, so they looked top-level — plus the nested `Edge`, `Fill`,
`Fixed`, `Inset`, `Gap` and `Page`. Every one would have been a 404 the day
something linked it, and the previous behaviour (a `?query=` Dokka does not
read) hid it by landing on the front page whatever it was asked for.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

TABLES = Path("ui-docs/build/generated/apiTables/ApiTables.kt")
PAGES = Path("ui/build/dokka/html/scripts/pages.json")

# The site prefixes every derived path with the reference's module directory.
MODULE = "kontour-ui"

ENTRY = re.compile(r'^\s*"([^"]+)" to "([^"]+)",$', re.MULTILINE)


def derived() -> dict[str, str]:
    """`apiReferencePaths`, read out of the generated Kotlin."""
    text = TABLES.read_text()
    marker = "internal val apiReferencePaths: Map<String, String> = mapOf("
    if marker not in text:
        raise SystemExit(f"{TABLES} has no apiReferencePaths — regenerate it")
    block = text.split(marker, 1)[1].split("\n)", 1)[0]
    return {name: path for name, path in ENTRY.findall(block)}


def published() -> set[str]:
    """Every location Dokka wrote a page for."""
    return {entry["location"] for entry in json.loads(PAGES.read_text())}


def page_symbols() -> list[tuple[str, str]]:
    """The symbol each component page links to: its title's first `code` run."""
    found = []
    for path in sorted(Path("ui-docs/content").rglob("*.md")):
        lines = path.read_text().splitlines()
        if not lines or not lines[0].startswith("# "):
            continue
        ticks = re.findall(r"`([^`]+)`", lines[0])
        if ticks:
            found.append((str(path), ticks[0].split(".")[-1]))
    return found


def main() -> int:
    for required, task in ((TABLES, ":ui-docs:generateApiTables"), (PAGES, ":ui:dokkaGenerateHtml")):
        if not required.exists():
            print(f"missing {required} — run `./gradlew {task}` first", file=sys.stderr)
            return 2

    paths = derived()
    locations = published()
    problems = []

    for symbol, path in sorted(paths.items()):
        if f"{MODULE}/{path}" not in locations:
            problems.append(f"{symbol} -> {path} is not a page the reference published")

    # A page whose symbol has no path falls back to the reference's index,
    # which is the behaviour this whole mechanism exists to stop.
    #
    # `+` is the exception and stays one: `dsls.md` is titled "Slots, and the
    # `+` that keeps them short", it is a guide rather than a component, and
    # the site already declines to draw a reference link for a page that is not
    # about a symbol. It is listed here so that a *component* page arriving in
    # the same state is not mistaken for it.
    for where, symbol in page_symbols():
        if symbol == "+":
            continue
        if symbol not in paths:
            problems.append(f"{where}: `{symbol}` has no reference path, so its link lands on the index")

    if not problems:
        print(
            f"all {len(paths)} derived reference paths resolve against "
            f"{len(locations)} published pages."
        )
        return 0

    print(f"{len(problems)} bad API reference link(s):", file=sys.stderr)
    for problem in problems:
        print(f"  {problem}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
