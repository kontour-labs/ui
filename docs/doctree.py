#!/usr/bin/env python3
"""Where the documentation lives, and what shape it is.

Imported by `generate-doc-pages.py`, `check-components.py` and
`sync-samples.py`, which between them had three copies of the family list and
two of the content root. Three copies of a list is three chances for one of
them to be a version behind, and the failure is silent in every direction: a
family the generator knows about and the checker does not is a family whose
pages nothing verifies.
"""

from __future__ import annotations

from pathlib import Path

# The reader-facing documentation, which is also the documentation site's
# source. It sits inside `:ui-docs` rather than in `docs/` because that is what
# it is for: `docs/` is now the contributor half, and a page that is published
# is a page that belongs with the thing publishing it.
CONTENT = Path("ui-docs/content")

COMPONENTS = CONTENT / "components"

# A tuple, not a set, and the order is load-bearing.
#
# `family_of` is first-index-wins, and a page can be linked from two indexes —
# `nav-surfaces.md` from `navigation.md` and again from `foundation.md`.
# Iterating a set of strings means iterating in an order Python's hash
# randomisation changes between runs, so that page landed in Navigation or in
# Foundation depending on the process. `DocPages.kt` therefore differed run to
# run, which defeats Gradle's up-to-date check and moved a page between sidebar
# groups at random.
#
# The order here is the order `components.md` presents the families in, so the
# tie is broken by the same thing a reader would use.
INDEXES = (
    "actions", "selection", "text-editing", "date-time", "display",
    "collections", "overlays", "sheets", "navigation", "adaptive", "foundation",
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

# The pages that are about the library rather than about a component. They get
# routes on the site like everything else — before this round they had none, and
# 2,084 lines of the reasoning behind the library could only be read as raw
# markdown on GitHub.
GUIDES = ("components", "installing", "tokens", "theming", "accessibility", "dsls", "overlays", "sheets")


def content_pages() -> list[Path]:
    """Every markdown page under [CONTENT], in a stable order."""
    return sorted(CONTENT.rglob("*.md"))


def page_path(path: Path) -> str:
    """A page's route and identity: its path under [CONTENT], without the suffix.

    `components/button`, `tokens`, `components`. A file stem is not enough —
    `overlays.md` is both the guide to the overlay mechanism and the family
    index, and they are different pages.
    """
    return path.relative_to(CONTENT).with_suffix("").as_posix()
