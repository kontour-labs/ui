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

Three rules:

  1. Every component in `componentRegistry` has a page whose title names it.
     The registry is the library's own list, so this cannot drift from what
     exists — a new component fails here before anyone notices the gap.
  2. No symbol is the title of two pages. Two pages about one component is one
     page that will not be updated.
  3. Every component page is linked from its category index. A page nothing
     points at is a page nobody reads, and the split is the moment to create
     one by accident.

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
        f"{len(component_pages)} component pages, "
        f"{len(registry_components())} registered components, all accounted for."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
