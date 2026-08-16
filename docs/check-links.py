#!/usr/bin/env python3
"""Resolve every relative link and anchor in the documentation.

A dead link in a document about how to use something is the same class of
defect as a dead callback in a catalog: it looks like it works, and nobody
finds out until they follow it. This walks every markdown file under `docs/`,
resolves every relative link against the file that carries it, and checks that
any `#anchor` names a heading that actually exists in the file it points at.

    python3 docs/check-links.py          # the whole tree
    python3 docs/check-links.py docs/app # one subtree

Exits non-zero and prints `file:line` for each failure.

External links are not followed. Checking those needs the network, would fail
for reasons that have nothing to do with this repository, and is a different
job with a different failure mode.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

# `[text](target)` and `![alt](target)`, skipping reference-style and autolinks.
LINK = re.compile(r"!?\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
HEADING = re.compile(r"^(#{1,6})\s+(.*?)\s*#*$", re.MULTILINE)
EXTERNAL = ("http://", "https://", "mailto:", "tel:")


def slug(heading: str) -> str:
    """GitHub's heading slug: lowercase, punctuation dropped, spaces hyphenated."""
    text = re.sub(r"`([^`]*)`", r"\1", heading)          # code spans keep their text
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)  # links keep their text
    # `*` and `~` only — **not** `_`. Underscore is a word character and a
    # perfectly ordinary part of an identifier, and stripping it turns
    # `pg_cron` into `pgcron` and rejects a link that works.
    text = re.sub(r"[*~]", "", text)
    text = text.strip().lower()
    text = re.sub(r"[^\w\s-]", "", text)
    # Each space becomes a hyphen — *not* each run of them. An em dash between
    # two words leaves two spaces behind once punctuation is dropped, and
    # GitHub's anchor keeps both as `--`. Collapsing here would reject every
    # link to a heading written with one, which is most of them in this repo.
    return re.sub(r"\s", "-", text)


def anchors(path: Path) -> set[str]:
    """Every anchor a reader could link to in this file."""
    if not path.is_file():
        return set()
    found: set[str] = set()
    for _, heading in HEADING.findall(path.read_text(encoding="utf-8")):
        base = slug(heading)
        # Repeated headings get -1, -2… so a duplicate is still reachable.
        candidate, n = base, 1
        while candidate in found:
            candidate, n = f"{base}-{n}", n + 1
        found.add(candidate)
    return found


def check(root: Path) -> list[str]:
    problems: list[str] = []
    for path in sorted(root.rglob("*.md")):
        lines = path.read_text(encoding="utf-8").split("\n")
        for number, line in enumerate(lines, start=1):
            for target in LINK.findall(line):
                if target.startswith(EXTERNAL) or target.startswith("<"):
                    continue

                file_part, _, anchor = target.partition("#")
                where = f"{path}:{number}"

                if not file_part:
                    # A bare `#anchor` points inside this same file.
                    if anchor and anchor not in anchors(path):
                        problems.append(f"{where}: no heading `#{anchor}` in this file")
                    continue

                resolved = (path.parent / file_part).resolve()
                if not resolved.exists():
                    problems.append(f"{where}: `{file_part}` does not exist")
                elif anchor and resolved.suffix == ".md":
                    if anchor not in anchors(resolved):
                        problems.append(
                            f"{where}: `{file_part}` has no heading `#{anchor}`"
                        )
    return problems


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "docs")
    if not root.is_dir():
        print(f"not a directory: {root}", file=sys.stderr)
        return 2

    problems = check(root)
    if not problems:
        total = sum(1 for _ in root.rglob("*.md"))
        print(f"every link in {total} markdown files resolves.")
        return 0

    print(f"{len(problems)} broken link(s):", file=sys.stderr)
    for problem in problems:
        print(f"  {problem}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
