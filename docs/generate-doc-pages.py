#!/usr/bin/env python3
"""Turns the component pages into Kotlin the documentation site can render.

The site ships no markdown parser. That is the point of doing this here: a
malformed page fails the build, where a runtime parser would fail in somebody's
browser, and the bundle a reader downloads carries structured content rather
than a parser and a pile of strings.

Usage:  python3 docs/generate-doc-pages.py <output-directory>

Emits one file, `DocPages.kt`, holding every page under
`docs/using/components/` as a list of blocks.

### What it understands

Headings, paragraphs, fenced code, tables, blockquotes, bullet and numbered
lists, and horizontal rules — which is everything the pages use. Inline: code
spans, bold, italic and links. Images are **dropped**: on this site the live
specimen sits where the render would, and a picture of a component beside the
component is a picture nobody looks at.

Anything it does not understand is an error rather than a silent drop, so a page
that grows a new construct is noticed by the person adding it.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

COMPONENTS = Path("docs/using/components")

# A tuple, not a set, and the order is load-bearing.
#
# `family_of` below is first-index-wins, and a page can be linked from two of
# them — `nav-surfaces.md` is linked from `navigation.md` and again from
# `foundation.md`. Iterating a set of strings means iterating in an order that
# Python's hash randomisation changes between runs, so that page landed in
# Navigation or in Foundation depending on the process. `DocPages.kt` therefore
# differed run to run, which defeats Gradle's up-to-date check on
# `generateDocPages` and moved a page between sidebar groups at random.
#
# The order here is the order `components.md` presents the families in, so the
# tie is broken by the same thing a reader would use.
INDEXES = (
    "actions", "selection", "text-editing", "date-time", "display",
    "collections", "navigation", "adaptive", "foundation",
    "overlays", "sheets",
)

FAMILY = {
    "actions": "Actions",
    "selection": "Selection",
    "text-editing": "Text editing",
    "date-time": "Date and time",
    "display": "Display",
    "collections": "Collections",
    "overlays": "Overlays",
    "sheets": "Sheets",
    "navigation": "Navigation",
    "adaptive": "Adaptive",
    "foundation": "Foundation",
}


def kotlin_string(text: str) -> str:
    out = text.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    return '"' + out.replace("\n", "\\n") + '"'


INLINE = re.compile(
    r"(?P<code>`[^`]+`)"
    # Before `link`, and matching an empty alt text, which `link` does not.
    # A render inside a table cell — `![](../../…png)` — is neither at the
    # start of a line, so the block-level image drop never saw it, nor a legal
    # link, so it fell through to `Span.Plain` and the reader got the file path
    # as literal text. `button.md`'s variant table showed seven of them.
    r"|(?P<image>!\[[^\]]*\]\([^)]+\))"
    r"|(?P<link>\[[^\]]+\]\([^)]+\))"
    r"|(?P<strong>\*\*[^*]+\*\*)"
    r"|(?P<em>\*[^*]+\*)"
)


def spans(text: str) -> str:
    """One line of prose, as a list of `Span`s."""
    out = []
    pos = 0
    for m in INLINE.finditer(text):
        if m.start() > pos:
            out.append(f"Span.Plain({kotlin_string(text[pos:m.start()])})")
        if m.group("code"):
            out.append(f"Span.Code({kotlin_string(m.group('code')[1:-1])})")
        elif m.group("image"):
            # Dropped, for the reason the block-level branch drops them: the
            # live demo stands where the render would.
            pass
        elif m.group("link"):
            label, target = re.match(r"\[([^\]]+)\]\(([^)]+)\)", m.group("link")).groups()
            out.append(f"Span.Link({kotlin_string(label)}, {kotlin_string(target)})")
        elif m.group("strong"):
            out.append(f"Span.Strong({kotlin_string(m.group('strong')[2:-2])})")
        else:
            out.append(f"Span.Emphasis({kotlin_string(m.group('em')[1:-1])})")
        pos = m.end()
    if pos < len(text):
        out.append(f"Span.Plain({kotlin_string(text[pos:])})")
    return "listOf(" + ", ".join(out) + ")"


def blocks(lines: list[str], where: str) -> list[str]:
    out = []
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        if not stripped:
            i += 1
        elif stripped.startswith("```"):
            language = stripped[3:].strip() or "text"
            i += 1
            code = []
            while i < len(lines) and not lines[i].strip().startswith("```"):
                code.append(lines[i])
                i += 1
            i += 1
            out.append(f"Block.Code({kotlin_string(language)}, {kotlin_string(chr(10).join(code))})")
        elif stripped.startswith("#"):
            level = len(stripped) - len(stripped.lstrip("#"))
            out.append(f"Block.Heading({level}, {spans(stripped[level:].strip())})")
            i += 1
        elif stripped == "---":
            out.append("Block.Rule")
            i += 1
        elif stripped.startswith("<!--"):
            # A sample marker. `sync-samples.py` owns it; the site does not
            # show it, and the code fence it precedes is already the sample.
            i += 1
        elif stripped.startswith("!["):
            # Dropped on purpose — the live specimen stands where this would.
            i += 1
        elif stripped.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                cells = [c.strip() for c in lines[i].strip().strip("|").split("|")]
                if not all(set(c) <= set("-: ") for c in cells):
                    rows.append(cells)
                i += 1
            body = ", ".join(
                "listOf(" + ", ".join(spans(c) for c in row) + ")" for row in rows
            )
            out.append(f"Block.Table(listOf({body}))")
        elif stripped.startswith(">"):
            quoted = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                quoted.append(lines[i].strip().lstrip(">").strip())
                i += 1
            out.append(f"Block.Quote({spans(' '.join(quoted))})")
        elif re.match(r"^[-*]\s+", stripped) or re.match(r"^\d+\.\s+", stripped):
            items = []
            ordered = bool(re.match(r"^\d+\.\s+", stripped))
            while i < len(lines):
                s = lines[i].strip()
                if re.match(r"^[-*]\s+", s) or re.match(r"^\d+\.\s+", s):
                    items.append(re.sub(r"^([-*]|\d+\.)\s+", "", s))
                    i += 1
                elif s and not s.startswith(("#", "|", "```", ">")) and items:
                    # A wrapped continuation of the item above it.
                    items[-1] += " " + s
                    i += 1
                else:
                    break
            body = ", ".join(spans(item) for item in items)
            out.append(f"Block.Bullets({str(ordered).lower()}, listOf({body}))")
        else:
            paragraph = []
            while i < len(lines) and lines[i].strip() and not lines[i].strip().startswith(
                ("#", "|", "```", ">", "- ", "* ", "![", "<!--", "---")
            ):
                paragraph.append(lines[i].strip())
                i += 1
            if not paragraph:
                raise SystemExit(f"{where}: cannot parse line {i + 1}: {lines[i]!r}")
            out.append(f"Block.Paragraph({spans(' '.join(paragraph))})")
    return out


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    out_dir = Path(sys.argv[1])
    out_dir.mkdir(parents=True, exist_ok=True)

    # Which family each component page belongs to, from the index that links it.
    family_of = {}
    for stem in INDEXES:
        index = COMPONENTS / f"{stem}.md"
        if not index.exists():
            continue
        for target in re.findall(r"\(([a-z0-9-]+\.md)(?:#[^)]*)?\)", index.read_text()):
            family_of.setdefault(Path(target).stem, stem)

    pages = []
    for path in sorted(COMPONENTS.glob("*.md")):
        if path.stem in INDEXES:
            continue
        text = path.read_text()
        title = re.search(r"^#\s+(.+)$", text, re.M)
        if not title:
            raise SystemExit(f"{path}: no title")
        # The footer is navigation the site provides itself.
        body = re.sub(r"\n---\n\n←.*$", "\n", text, flags=re.S)
        lines = body.split("\n")
        # Drop the title line; the site draws it from `title`.
        lines = lines[lines.index(title.group(0)) + 1:]
        symbols = re.findall(r"`([^`]+)`", title.group(1))
        pages.append(
            "    DocPage(\n"
            f"        slug = {kotlin_string(path.stem)},\n"
            f"        title = {kotlin_string(title.group(1))},\n"
            f"        symbols = listOf({', '.join(kotlin_string(s) for s in symbols)}),\n"
            f"        family = {kotlin_string(FAMILY.get(family_of.get(path.stem, ''), 'Other'))},\n"
            "        blocks = listOf(\n"
            + "".join(f"            {b},\n" for b in blocks(lines, str(path)))
            + "        ),\n"
            "    ),"
        )

    header = '''// Generated by docs/generate-doc-pages.py — do not edit.
//
// One entry per page under `docs/using/components/`, as structured blocks. The
// site ships no markdown parser: a malformed page fails the build here rather
// than in a reader's browser.

package io.kontour.ui.docs

val docPages: List<DocPage> = listOf(
'''
    (out_dir / "DocPages.kt").write_text(header + "\n".join(pages) + "\n)\n")
    print(f"generated {len(pages)} pages into {out_dir}/DocPages.kt")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
