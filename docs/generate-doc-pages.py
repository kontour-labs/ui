#!/usr/bin/env python3
"""Turns the component pages into Kotlin the documentation site can render.

The site ships no markdown parser. That is the point of doing this here: a
malformed page fails the build, where a runtime parser would fail in somebody's
browser, and the bundle a reader downloads carries structured content rather
than a parser and a pile of strings.

Usage:  python3 docs/generate-doc-pages.py <output-directory>

Emits one file, `DocPages.kt`, holding every page under `ui-docs/content/` as a
list of blocks — the component pages, the family indexes and the guides, all of
which get a route on the site.

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

sys.path.insert(0, str(Path(__file__).parent))
from doctree import (  # noqa: E402
    COMPONENTS, FAMILY, GUIDES, INDEXES, content_pages, family_of, page_path,
)


def kotlin_string(text: str) -> str:
    out = text.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    return '"' + out.replace("\n", "\\n") + '"'


# Kotlin's hard keywords, plus the soft ones that read as keywords in a
# signature. `it` is here because every lambda in these samples uses it, and a
# reader scanning a block for the receiver is looking for exactly that word.
KEYWORDS = {
    "as", "break", "by", "catch", "class", "companion", "const", "continue", "crossinline",
    "data", "do", "else", "enum", "false", "finally", "for", "fun", "get", "if", "import",
    "in", "infix", "init", "inline", "interface", "internal", "is", "it", "lateinit",
    "noinline", "null", "object", "open", "operator", "out", "override", "package",
    "private", "protected", "public", "reified", "return", "sealed", "set", "super",
    "suspend", "this", "throw", "true", "try", "typealias", "val", "var", "vararg",
    "when", "while",
}

# One pass, in precedence order, and the order is the correctness argument: a
# `//` inside a string does not open a comment and a `"` inside a comment does
# not open a string, so whichever starts first takes its whole run.
CODE_TOKEN = re.compile(
    r"(?P<comment>//[^\n]*|/\*.*?\*/)"
    r"|(?P<string>\"\"\"(?:.|\n)*?\"\"\"|\"(?:\\.|[^\"\\\n])*\"|'(?:\\.|[^'\\\n])*')"
    r"|(?P<annotation>@[A-Za-z_]\w*)"
    r"|(?P<number>\b0[xXbB][0-9a-fA-F_]+[uUlL]*\b"
    r"|\b\d[\d_]*(?:\.\d[\d_]*)?(?:[eE][+-]?\d+)?[fFdDuUlL]*\b)"
    r"|(?P<word>[A-Za-z_]\w*)",
    re.S,
)

# Which kind each capture paints. An annotation reads as a keyword because
# `@Composable` is a declaration's most important word, not a decoration.
TOKEN_KIND = {"comment": "c", "string": "s", "number": "s", "annotation": "k"}


def highlight(code: str, language: str) -> str:
    """Run-length highlighting for a fenced block — see `Block.Code.spans`.

    Kotlin only, which is not a limitation worth apologising for: of the 167
    fenced blocks in this tree, 163 are Kotlin, two are bare, one is
    `properties` and one is `yaml`. Anything else gets an empty string and is
    drawn in one colour, exactly as everything was before this existed.

    Done here rather than in the browser because the site ships no parser: a
    reader downloads content, not a program for turning text into content.
    """
    if language != "kotlin" or not code:
        return ""

    kinds = ["p"] * len(code)
    for match in CODE_TOKEN.finditer(code):
        kind = TOKEN_KIND.get(match.lastgroup)
        if kind is None:
            if match.lastgroup != "word" or match.group() not in KEYWORDS:
                continue
            kind = "k"
        for index in range(match.start(), match.end()):
            kinds[index] = kind

    # Nothing worth colouring is worth no string at all.
    if set(kinds) == {"p"}:
        return ""

    runs = []
    start = 0
    while start < len(kinds):
        end = start
        while end < len(kinds) and kinds[end] == kinds[start]:
            end += 1
        runs.append(f"{end - start}{kinds[start]}")
        start = end
    return "".join(runs)


# `strong` and `em` come first, and their bodies are re-scanned by `spans`.
#
# Before that they came last, so `**Reach for a [`Chip`](chip.md) instead**` was
# matched by `code` on its first backtick and the surrounding `**…**` never
# matched at all — the reader got the literal markdown, brackets and filename
# included. Twelve pages, and every one of them was a "reach for this instead"
# line, which is the single most useful link on the page.
#
# `[^*]` in both bodies is what keeps them from swallowing the rest of the
# paragraph, and is also why `**a *b* c**` is not supported. Nobody writes that.
INLINE = re.compile(
    r"(?P<strong>\*\*[^*]+\*\*)"
    r"|(?P<em>\*[^*]+\*)"
    r"|(?P<code>`[^`]+`)"
    # Before `link`, and matching an empty alt text, which `link` does not.
    # A render inside a table cell — `![](../../…png)` — is neither at the
    # start of a line, so the block-level image drop never saw it, nor a legal
    # link, so it fell through to `Span.Plain` and the reader got the file path
    # as literal text. `button.md`'s variant table showed seven of them.
    r"|(?P<image>!\[[^\]]*\]\([^)]+\))"
    r"|(?P<link>\[[^\]]+\]\([^)]+\))"
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
            # The label is scanned too: `[`Chip`](chip.md)` is how a cross
            # reference is written here, 259 times, and a flat string put the
            # backticks on the page.
            out.append(f"Span.Link({spans(label)}, {kotlin_string(target)})")
        elif m.group("strong"):
            out.append(f"Span.Strong({spans(m.group('strong')[2:-2])})")
        else:
            out.append(f"Span.Emphasis({spans(m.group('em')[1:-1])})")
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
            body = chr(10).join(code)
            out.append(
                f"Block.Code({kotlin_string(language)}, {kotlin_string(body)}, "
                f"{kotlin_string(highlight(body, language))})"
            )
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
            # These used to be dropped in silence. They were screenshots of the
            # component, checked into `ui-catalog/screenshots/` and linked by a
            # relative path that resolves in a repository browser and nowhere
            # else — so every one of them was a picture only a GitHub reader
            # ever saw, standing in for the live specimen the site draws two
            # inches further down. Round 22 removed all 72 of them.
            #
            # Raising rather than skipping, because the site has no renderer for
            # an image and swallowing one puts a hole in a page that nothing
            # reports. If a page ever genuinely needs a picture, this is the
            # place to teach the site how to draw it.
            raise SystemExit(
                f"{where}: line {i + 1} is an image, and the site cannot draw "
                f"one: {stripped[:60]!r}"
            )
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


# Small enough that no page's chunk can approach the JVM's 64 KB method limit,
# large enough that a page is not hundreds of functions.
BLOCKS_PER_FUNCTION = 30


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    out_dir = Path(sys.argv[1])
    out_dir.mkdir(parents=True, exist_ok=True)

    # Which family each component page belongs to, and where it sits in it —
    # both from the index that links it, and from the order that index links it.
    #
    # The order is the half that used to be thrown away. The site sorted each
    # family by the page's raw markdown title, which is a string beginning with
    # a backtick: U+0060 sorts after the uppercase letters and before the
    # lowercase ones, so `ButtonGroup` came out ahead of `Button`,
    # `rememberImeChain` fell to the bottom of Text editing, and
    # `NavigationSuiteScaffold` — which `navigation.md` introduces with **Start
    # here.** — was fifth of eight. `DocModel.kt` warns against exactly that one
    # line above where it did it: "a list sorted by a fact about spelling".
    #
    # These tables are already an editorial order, written by whoever knows
    # which component a reader should meet first. Keeping it costs nothing.
    claimed = family_of()

    pages = []
    bodies = []
    for path in content_pages():
        text = path.read_text()
        title = re.search(r"^#\s+(.+)$", text, re.M)
        if not title:
            raise SystemExit(f"{path}: no title")
        # Every page used to end with "\u2190 [Family](family.md) \u00b7 [All
        # components](../components.md)", stripped here because the site draws
        # its own navigation \u2014 so, like the screenshots above, it was writing
        # only a GitHub reader ever saw. Round 22 removed them; there is
        # nothing left to strip, and a new one would now show up on the page as
        # the stray line it is.
        lines = text.split("\n")
        # Drop the title line; the site draws it from `title`.
        lines = lines[lines.index(title.group(0)) + 1:]
        symbols = re.findall(r"`([^`]+)`", title.group(1))

        # Three kinds, and the site treats each differently: a component page
        # carries a live demo and a parameter table, a family page is the index
        # of its components, and a guide is prose about the library.
        in_components = path.parent == COMPONENTS
        if in_components and path.stem in INDEXES:
            # A family page is carried beside its family rather than inside it,
            # so its order is never read. -1 says so rather than pretending.
            kind, family, order = "DocKind.Family", FAMILY[path.stem], -1
        elif in_components:
            kind = "DocKind.Component"
            # A page no index links falls to "Other" and to the end of it.
            # `check-components.py` rule 3 is what stops that happening.
            claim = claimed.get(path.stem)
            family = FAMILY.get(claim[0], "Other") if claim else "Other"
            order = claim[1] if claim else len(claimed)
        else:
            # `GUIDES` is reading order — install it, learn what a component is
            # allowed to look like, then meet them. Alphabetical filed the `+`
            # DSL guide under S, its title being "Slots, and the `+` that keeps
            # them short".
            #
            # A guide missing from the tuple used to sort to the end, which is
            # a silent wrong answer for a page that has simply been forgotten —
            # and reshuffling this tuple is exactly when that happens. It is an
            # error now: `GUIDES` is the order, so a page it does not name has
            # no place in it.
            kind, family = "DocKind.Guide", "Guides"
            if path.stem not in GUIDES:
                raise SystemExit(
                    f"{path} is a guide and is not in doctree.GUIDES, which is "
                    f"what orders them. Add it where a reader should meet it."
                )
            order = GUIDES.index(path.stem)

        page_blocks = blocks(lines, str(path))
        # One function per page, and one per 30 blocks inside it.
        #
        # Not tidiness. A top-level `val` initialises in `<clinit>`, and the JVM
        # caps a method at 64 KB of bytecode — which 122 pages of prose in a
        # single `listOf` exceeds, with an error that names a generated file and
        # explains nothing. Splitting it means every method stays small however
        # much documentation is written, which is the property worth having:
        # the next long page should not be the one that discovers this again.
        chunks = [page_blocks[at:at + BLOCKS_PER_FUNCTION]
                  for at in range(0, len(page_blocks), BLOCKS_PER_FUNCTION)] or [[]]
        name = f"p{len(pages)}"
        for index, chunk in enumerate(chunks):
            bodies.append(
                f"private fun {name}b{index}(): List<Block> = listOf(\n"
                + "".join(f"    {b},\n" for b in chunk)
                + ")\n"
            )
        bodies.append(
            f"private fun {name}(): DocPage = DocPage(\n"
            f"    path = {kotlin_string(page_path(path))},\n"
            f"    title = {kotlin_string(title.group(1))},\n"
            f"    symbols = listOf({', '.join(kotlin_string(s) for s in symbols)}),\n"
            f"    family = {kotlin_string(family)},\n"
            f"    kind = {kind},\n"
            f"    order = {order},\n"
            "    content = { " + " + ".join(f"{name}b{i}()" for i in range(len(chunks))) + " },\n"
            ")\n"
        )
        pages.append(f"    {name}(),")

    header = '''// Generated by docs/generate-doc-pages.py — do not edit.
//
// One entry per page under `ui-docs/content/` — the component pages, the family
// indexes and the guides — as structured blocks. The site ships no markdown
// parser: a malformed page fails the build here rather than in a reader's
// browser.

package io.kontour.ui.docs

val docPages: List<DocPage> = listOf(
'''
    (out_dir / "DocPages.kt").write_text(
        header + "\n".join(pages) + "\n)\n\n" + "\n".join(bodies)
    )
    print(f"generated {len(pages)} pages into {out_dir}/DocPages.kt")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
