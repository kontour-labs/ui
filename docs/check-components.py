#!/usr/bin/env python3
"""Every component has a page of its own, and no page claims another's subject.

The category pages used to carry everything: `actions.md` held ten components
and 441 lines, and a reader after `SplitButton` got the whole of Button,
IconButton and the FAB on the way. They are indexes now, and each component has
a file — which is also what lets the documentation site have a route per
component rather than a route per category with an anchor.

That split is mechanical, and mechanical is exactly when things go quietly
missing: a section that lands in two files, a component whose page was never
made, a page nothing links to. So the arrangement is checked rather than
trusted.

Eight rules:

  1. Every component in `componentRegistry` has a page whose title names it.
     The registry is the library's own list, so this cannot drift from what
     exists — a new component fails here before anyone notices the gap.
  2. No symbol is the title of two pages. Two pages about one component is one
     page that will not be updated.
  3. Every component page is linked from its category index. A page nothing
     points at is a page nobody reads, and the split is the moment to create
     one by accident.
  4. Every page has an interactive demo, and every demo has a page.
  5. Every public `@Composable` in `:ui` is claimed by some page — the one rule
     anchored to the library rather than to a list, and so the only one that
     could have caught a component nobody remembered to register.
  6. Every page shows an example that compiles.
  7. Every page says what is particular about its accessibility.
  8. Every page title names a declaration that exists in `:ui`.

Rules 4, 6 and 7 are **ratchets**: a ceiling that only goes down, rather than a
list of exempted names. You cannot exempt *your* page, only make the total
worse, and that is the difference that matters — a list of names in a test is
how a defect becomes a permanent exemption.

Run:  python3 docs/check-components.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

COMPONENTS = Path("docs/using/components")

# Found rather than named. The registry has already moved source set once — out
# of `commonTest` and into `commonMain`, so the documentation site could read
# the same list the contract suite does — and a hardcoded path turned this guard
# into a stack trace rather than a finding. Where the file *is* is not the
# subject of this check.
REGISTRY_NAME = "ComponentRegistry.kt"

# `ComponentSpec("Button ($variant)", Role.Button)` and `name = "FabMenu",`.
SPEC_POSITIONAL = re.compile(r'ComponentSpec\(\s*"([^"]+)"')
SPEC_NAMED = re.compile(r'name\s*=\s*"([^"]+)"')

# A page's subject, from its `# ` title. Several are allowed: a page titled
# "`Avatar` / `AvatarGroup`" is the page for both, because they are one idea and
# splitting them would leave two half-pages that each say "see the other".
TITLE = re.compile(r"^#\s+(.+)$", re.MULTILINE)
SYMBOL = re.compile(r"`([^`]+)`")

# A family page can name the rest of its subjects on one line under the title:
#
#     *Also on this page: `NavBarItem`, `NavRailItem`, …*
#
# `nav-surfaces.md` is the case. Five item types, each a real component, and the
# page that explains them is written as a comparison of the three surfaces they
# belong to — three pages each saying "see the other two" would not be a split.
# Putting all eight symbols in the title makes a title nobody can read, so the
# page says so in a line a reader sees and this can parse.
#
# Deliberately *not* "any heading on the page": `button-group.md` has a section
# called "Not a `SegmentedControl`", and a page is not the owner of everything
# it mentions.
ALSO = re.compile(r"^\*Also on this page:\s*(.+?)\*$", re.MULTILINE)

# The pages that are *about* a category rather than a component.
INDEXES = {
    "actions",
    "adaptive",
    "collections",
    "date-time",
    "display",
    "foundation",
    "navigation",
    "selection",
    "text-editing",
    "overlays", "sheets",
}


def registry_path() -> Path:
    found = [
        p for p in Path("ui-catalog/src").rglob(REGISTRY_NAME)
        if "build" not in p.parts
    ]
    if len(found) != 1:
        raise SystemExit(
            f"expected exactly one {REGISTRY_NAME} under ui-catalog/src, found "
            f"{[p.as_posix() for p in found]}"
        )
    return found[0]


def registry_components() -> list[str]:
    """Every spec name, reduced to the symbol it is a specimen of."""
    text = registry_path().read_text()
    names = set(SPEC_POSITIONAL.findall(text)) | set(SPEC_NAMED.findall(text))
    reduced = set()
    for name in names:
        # "Button ($variant)" and "Button (Primary)" are both Button.
        reduced.add(re.sub(r"\s*\(.*\)\s*$", "", name).strip())
    return sorted(reduced)


def page_symbols(path: Path) -> list[str]:
    """What this page is about, from its title."""
    text = path.read_text()
    title = TITLE.search(text)
    if not title:
        return []
    symbols = [s.strip() for s in SYMBOL.findall(title.group(1))]
    also = ALSO.search(text)
    if also:
        symbols += [s.strip() for s in SYMBOL.findall(also.group(1))]
    return symbols


# Only goes down. See rule 4.
#
# One, and it is `date-time-formats` — `DateTimeFormats` is a data class holding
# a set of patterns, so there is nothing to press. That exemption wants deriving
# rather than counting, from "this page's symbols include no public @Composable",
# and it will be once the signature parser moves somewhere both this and the
# build can read it. Until then a ceiling of one is honest and a list of one name
# would be the start of a list.
MAX_WITHOUT_DEMO = 1  # unchanged: the overlay and sheet pages arrived with theirs


def demo_slugs() -> set[str]:
    """Every `ComponentDemo(slug = ...)` in `:ui-catalog`.

    Found by walking rather than by a hardcoded path, for the reason
    `registry_components` does: the registry file has already moved once, and a
    hardcoded path turns this guard into a stack trace the day it moves again.
    """
    found: set[str] = set()
    for path in Path("ui-catalog/src").rglob("*Demos.kt"):
        if "build" in path.parts:
            continue
        found |= set(re.findall(r'ComponentDemo\(\s*slug\s*=\s*"([^"]+)"', path.read_text()))
        found |= set(re.findall(r'ComponentDemo\(\s*"([^"]+)"', path.read_text()))
    return found


def public_composables() -> dict[str, Path]:
    """Every public `@Composable` in `:ui`, by name.

    TitleCase only: a composable is a component, and the lowercase ones are
    modifiers and `remember*` factories, which are documented on the page of
    whatever they attach to rather than on pages of their own.
    """
    found: dict[str, Path] = {}
    pattern = re.compile(
        r"@Composable[^\n]*\n(?:@[^\n]*\n)*"
        r"((?:internal |private |public )?)fun (?:<[^>]*> )?(?:(\w+)\.)?(\w+)\s*\("
    )
    for path in Path("ui/src/commonMain/kotlin").rglob("*.kt"):
        for match in pattern.finditer(path.read_text()):
            visibility, receiver, name = match.group(1).strip(), match.group(2), match.group(3)
            if visibility in ("internal", "private") or not name[0].isupper():
                continue
            found[f"{receiver}.{name}" if receiver else name] = path
    return found


def claimed_symbols() -> set[str]:
    """Every symbol any documentation page says it is about.

    Across all of `docs/using`, not only `components/`, because a handful of
    things are genuinely explained by a guide rather than by a component page —
    `KontourTheme` is a theme, not a component, and `theming.md` is where anyone
    looking for it would look.
    """
    found: set[str] = set()
    for path in Path("docs/using").rglob("*.md"):
        text = path.read_text()
        title = TITLE.search(text)
        if title:
            found |= set(SYMBOL.findall(title.group(1)))
        for also in ALSO.findall(text):
            found |= set(SYMBOL.findall(also))
    return found


# Only goes down. See rule 6.
#
# **Zero**, and that is a fact rather than an aspiration: every one of the 103
# component pages carries a compiled example as of this round. It stays a
# ratchet rather than a flat `if any` because the number is the honest way to
# say what a regression costs, and because the accessibility ceiling below it
# is nowhere near zero and reads the same way.
MAX_WITHOUT_SAMPLE = 0

# Only goes down. See rule 7.
#
# 102 of 103, which looks like a gate that does nothing and is not. A *new* page
# lands at 103 and fails, so from here on every component page arrives with
# accessibility notes — and the existing debt is written down in a number that
# can only be paid off rather than in a list of names to inherit.
#
# Not every page owes the same thing. `accessibility.md` carries the rules that
# apply to all of them; what belongs here is what is specific — which role a
# component takes, what it announces, what its live region does.
MAX_WITHOUT_ACCESSIBILITY = 102

ACCESSIBILITY = re.compile(r"^#+\s*Accessibility", re.MULTILINE | re.IGNORECASE)
SAMPLE = re.compile(r"^<!--\s*sample:\s*\w+\s*-->$", re.MULTILINE)


def public_declarations() -> set[str]:
    """Every public top-level name in `:ui`, bare and qualified.

    Coarser than `public_composables` on purpose: rule 8 asks whether a page's
    title names *something real*, and `WindowSizeClass` is a class,
    `Modifier.marquee` a modifier and `DateTimeFormats` a data class. All three
    are legitimate page subjects and none is a composable.
    """
    found: set[str] = set()
    patterns = [
        re.compile(r"^[ \t]*(?:(?:public|internal|private)\s+)?"
                   r"(?:(?:override|suspend|inline|operator|infix|expect|actual|tailrec)\s+)*"
                   r"fun\s+(?:<[^>]*>\s*)?(?:([A-Za-z_][\w.]*)\.)?(\w+)\s*\(", re.MULTILINE),
        re.compile(r"^[ \t]*(?:(?:public|internal|private|abstract|open|sealed|data|value|inner"
                   r"|expect|actual|enum|annotation)\s+)*"
                   r"(?:class|interface|object)\s+()(\w+)", re.MULTILINE),
        re.compile(r"^[ \t]*(?:(?:public|internal|private)\s+)?"
                   r"va[lr]\s+(?:<[^>]*>\s*)?(?:([A-Za-z_][\w.]*)\.)?(\w+)", re.MULTILINE),
    ]
    for path in Path("ui/src/commonMain/kotlin").rglob("*.kt"):
        text = path.read_text()
        for pattern in patterns:
            for receiver, name in pattern.findall(text):
                found.add(name)
                if receiver:
                    found.add(f"{receiver}.{name}")
    return found


def main() -> int:
    if not COMPONENTS.is_dir():
        print(f"{COMPONENTS} is not a directory", file=sys.stderr)
        return 1

    pages = sorted(COMPONENTS.glob("*.md"))
    component_pages = [p for p in pages if p.stem not in INDEXES]
    index_pages = [p for p in pages if p.stem in INDEXES]

    problems: list[str] = []

    # Rule 2 — one page per symbol.
    owner: dict[str, Path] = {}
    for page in component_pages:
        for symbol in page_symbols(page):
            if symbol in owner:
                problems.append(
                    f"`{symbol}` is the subject of both {owner[symbol].name} and "
                    f"{page.name} — two pages about one component is one page "
                    f"that will not be kept current"
                )
            owner[symbol] = page

    # Rule 1 — every registered component has one.
    #
    # Matched on the bare symbol and on `Modifier.x`, since a handful of these
    # are modifiers and their pages are titled as such.
    documented = set(owner)
    documented |= {s.split(".", 1)[1] for s in owner if "." in s}
    for component in registry_components():
        if component not in documented:
            problems.append(
                f"`{component}` is in componentRegistry and has no page of its "
                f"own — it is a component the library builds, draws and tests, "
                f"and does not explain"
            )

    # Rule 4 — a page has an interactive demo, and a demo has a page.
    #
    # The demos are what a reader presses. They live beside `componentRegistry`
    # in `:ui-catalog` and are hand-written, one per page, because the registry's
    # specimens are stateless by design and cannot respond to anything.
    #
    # The page half is a **ratchet** rather than a list of exemptions. Not every
    # page has one yet — writing eighty demos is the round this rule arrived in —
    # and a list of names would freeze whichever ones happened to be unfinished
    # on the day. The number below only goes down. You cannot exempt *your* page,
    # only make the total worse, which is the difference that matters.
    demos = demo_slugs()
    for slug in sorted(demos):
        if slug not in {page.stem for page in component_pages}:
            problems.append(
                f"there is a demo for `{slug}` and no page of that name — a demo "
                f"nobody can reach is a demo nobody maintains"
            )
    without = sorted(page.stem for page in component_pages if page.stem not in demos)
    if len(without) > MAX_WITHOUT_DEMO:
        problems.append(
            f"{len(without)} pages have no demo, and the ceiling is "
            f"{MAX_WITHOUT_DEMO}. Lower the ceiling when you add one; raising it "
            f"is going backwards. Without: {', '.join(without)}"
        )

    # Rule 5 — every public component in the library is documented somewhere.
    #
    # This is the one that would have caught the whole of Round 16. Rules 1-4
    # chain `registry → pages → indexes → demos`, and every link in that chain
    # is a *list* — so a component nobody put in the registry was invisible to
    # all of them. Nineteen overlays and sheets, five foundation primitives and
    # six adaptive components had no page, and every gate in the repository was
    # green.
    #
    # Anchored to the compiled surface instead: the public composables in `:ui`
    # are the source, and the pages have to cover them. Adding a component and
    # not documenting it now fails the build, which is the only version of this
    # rule that does anything.
    documented_symbols = claimed_symbols()
    bare_symbols = {symbol.split(".")[-1] for symbol in documented_symbols}
    for symbol, path in sorted(public_composables().items()):
        if symbol in documented_symbols or symbol.split(".")[-1] in bare_symbols:
            continue
        problems.append(
            f"`{symbol}` is public in {path.name} and no page claims it — either "
            f"give it a page, name it in an *Also on this page* line, or make it "
            f"internal if callers were never meant to reach it"
        )

    # Rule 6 — every page shows an example that compiles.
    #
    # A page that describes a component without showing one being called is a
    # page a reader leaves to go and guess. The example is not written in the
    # page: it lives in `:ui-samples`, where the compiler reads it against the
    # real public API, and `sync-samples.py` keeps the copy identical. An
    # example that does not compile is worse than none, because it reads as a
    # confident answer and is wrong.
    without_sample = sorted(page.stem for page in component_pages if not SAMPLE.search(page.read_text()))
    if len(without_sample) > MAX_WITHOUT_SAMPLE:
        problems.append(
            f"{len(without_sample)} pages show no compiled example, and the "
            f"ceiling is {MAX_WITHOUT_SAMPLE}. Add one to `ui-samples/`, mark it "
            f"with `<!--sample:Name-->` and run `python3 docs/sync-samples.py "
            f"--write`. Without: {', '.join(without_sample)}"
        )

    # Rule 7 — a page says what is specific about using it without sight,
    # without a mouse, or with the type at 200%.
    without_a11y = sorted(
        page.stem for page in component_pages if not ACCESSIBILITY.search(page.read_text())
    )
    if len(without_a11y) > MAX_WITHOUT_ACCESSIBILITY:
        problems.append(
            f"{len(without_a11y)} pages have no Accessibility section, and the "
            f"ceiling is {MAX_WITHOUT_ACCESSIBILITY}. The general rules live in "
            f"`using/accessibility.md`; what goes on a component page is what is "
            f"particular to it. Without: {', '.join(without_a11y)}"
        )

    # Rule 8 — a page's title names something that exists.
    #
    # The cheapest of the eight and the only one pointing this direction. Rules
    # 1-4 ask "does everything in the library have a page"; this asks whether the
    # page is about anything, which catches a component renamed in `:ui` whose
    # page kept the old spelling. Nothing here could detect that before: the
    # chain ran registry → page and never page → library.
    declared = public_declarations()
    for page in component_pages:
        for symbol in page_symbols(page):
            if symbol in declared or symbol.split(".")[-1] in declared:
                continue
            problems.append(
                f"{page.name} is titled `{symbol}` and nothing in :ui is called "
                f"that — either the page is about a component that has been "
                f"renamed, or the title has a typo"
            )

    # Rule 3 — every page is reachable from its index.
    linked = set()
    for index in index_pages:
        for target in re.findall(r"\(([a-z0-9-]+\.md)(?:#[^)]*)?\)", index.read_text()):
            linked.add(target)
    for page in component_pages:
        if page.name not in linked:
            problems.append(
                f"{page.name} is linked from no category index — a page nothing "
                f"points at is a page nobody reads"
            )

    if problems:
        print(f"{len(problems)} problem(s):", file=sys.stderr)
        for problem in sorted(problems):
            print(f"  · {problem}", file=sys.stderr)
        return 1

    print(
        f"{len(public_composables())} public composables, "
        f"{len(component_pages)} component pages, "
        f"{len(registry_components())} registered components, "
        f"{len(demos)} demos ({len(without)} pages still without one), "
        f"{len(component_pages) - len(without_sample)} compiled examples, "
        f"{len(component_pages) - len(without_a11y)} accessibility sections, "
        f"all accounted for."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
