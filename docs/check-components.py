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

Twenty-two rules:

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
  9. The README's component count is the tree's component count.
 10. Every name an accessibility section mentions exists in `:ui`.
 11. The radius scale is written down three times and executes once.
 12. Every component in the shape-families table really asks for that family.
 13. Every page agrees about which family it is in — the map and the index that
     links it. Two copies is two chances for one to be a version behind. There
     was a third, the page's own "← Family" footer, and it went with the rest
     of the writing-for-GitHub in round 22.
 14. Every enum a component takes as a parameter is on some demo's knob.
 15. Every click target sets a mouse cursor.
 16. Every boolean a component takes as a parameter is on some demo's knob.
 17. Every component page explains at least one of its parameters.
 18. No page talks to a maintainer instead of to a reader.
 19. The library performs no more haptic intents than the policy allows. Added
     in round 25, when fifty-seven call sites came down to eleven: no single
     test could have caught that drift, because every one of the fifty-seven
     was working. What catches it is the count.
 20. The shape scale is used rather than reimplemented — a ceiling on true
     circles, and a ban on hand-rolled `RoundedCornerShape`.
 21. Every `OverlayEntry` says whether it traps focus. The default is `true`
     and it was right six times out of seven; the seventh was a component that
     could not be used at all.
 22. The haptics policy and the library name the same components. Rule 19 caps
     how many sites there are; this keeps the document explaining them true,
     and it had already drifted — the table went on listing a component whose
     sites the audit itself had removed.

Rules 4, 6, 7, 14, 16 and 17 are **ratchets**: a ceiling that only goes down, rather
than a list of exempted names. You cannot exempt *your* page, only make the total
worse, and that is the difference that matters — a list of names in a test is
how a defect becomes a permanent exemption.

Rule 15 is a ratchet whose ceiling has already reached zero, which is what a
ratchet is for. It stays written as one rather than as a flat `if any` so that
the number in the message is the honest cost of a regression, the same way rule
6's is.

Run:  python3 docs/check-components.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from doctree import COMPONENTS, CONTENT, INDEXES, family_of  # noqa: E402

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

# The declaration line of a `@Composable`, with its annotations skipped: groups
# are visibility, receiver and name. Shared by `public_composables` — which is
# the definition of "a component" — and by `unswept_enums`, so both agree about
# what counts.
COMPOSABLE_HEADER = re.compile(
    r"@Composable[^\n]*\n(?:@[^\n]*\n)*"
    r"((?:internal |private |public )?)fun (?:<[^>]*> )?(?:(\w+)\.)?(\w+)\s*\("
)



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
    for path in Path("ui/src/commonMain/kotlin").rglob("*.kt"):
        for match in COMPOSABLE_HEADER.finditer(path.read_text()):
            visibility, receiver, name = match.group(1).strip(), match.group(2), match.group(3)
            if visibility in ("internal", "private") or not name[0].isupper():
                continue
            found[f"{receiver}.{name}" if receiver else name] = path
    return found


KDOC_PARAM = re.compile(r"^\s*\*\s*@(?:param|property)\s+\w+", re.MULTILINE)

# `fun`, `class` or a primary constructor, with its indent and any receiver.
DOCUMENTED_HEADER = re.compile(
    r"^([ \t]*)(?:(?:public|internal|private|abstract|open|sealed|data|value|inner|expect|actual)\s+)*"
    r"(?:fun|class)\s+(?:<[^>]*>\s*)?(?:([A-Za-z_][\w.]*)\.)?([A-Za-z_]\w*)"
)

TOP_LEVEL_TYPE = re.compile(
    r"^(?:public\s+)?(?:(?:abstract|open|sealed|data|value|expect|actual|enum|annotation)\s+)*"
    r"(?:class|interface|object)\s+([A-Za-z_]\w*)"
)


# Only goes down, and it is already at zero. See rule 18.
#
# Three things a reader cannot use, and each was on a real page:
#
#   * a **round number**. "It did not have one until Round 16 — writing this
#     page is what found it" is a fact about this repository's history, and
#     there is no round 16 anywhere a reader can look.
#   * an **internal test class**. "Enforced by `ColourSchemeContrastTest`" is
#     the right *claim* — this is checked, not hoped for — attached to an
#     identifier that means nothing to somebody who cannot run it. "Checked on
#     every build" says the same thing and says it to them.
#   * a **product they have not heard of**. `Breadcrumbs` opened with "No
#     caller in Anyways today — it is here for the admin panel", which tells a
#     reader nothing about when to reach for it.
#
# Twenty-two passages across fifteen pages, all rewritten in the round this
# rule arrived in. Zero is therefore a fact rather than an aspiration, and the
# rule exists so the next one is caught while it is being written.
MAX_INTERNAL_FACING = 0

# A development round, an internal test class, or the product this library was
# extracted from. Deliberately narrow: "used to" and "this page" are ordinary
# English and appear on plenty of pages that read perfectly well.
INTERNAL_FACING = re.compile(r"\bRound \d+\b|`[A-Z]\w*Test`|\bAnyways\b|\badmin panel\b")


def internal_facing() -> list[str]:
    """Every `page:line` that addresses a maintainer rather than a reader."""
    found: list[str] = []
    for path in sorted(CONTENT.rglob("*.md")):
        for number, line in enumerate(path.read_text().split("\n"), start=1):
            if INTERNAL_FACING.search(line):
                found.append(f"{path.relative_to(CONTENT)}:{number}")
    return found


def symbols_explaining_a_parameter() -> set[str]:
    """Every symbol whose KDoc says what at least one of its parameters is for.

    Attributed the way `:ui-docs:generateApiTables` attributes a declaration to
    a page — a member of `ListItemScope` belongs to `ListItem` — so that this
    counts the same thing the reader sees in the table rather than something
    adjacent to it.
    """
    found: set[str] = set()
    for path in Path("ui/src/commonMain/kotlin").rglob("*.kt"):
        lines = path.read_text().split("\n")
        enclosing: str | None = None
        for index, line in enumerate(lines):
            top = TOP_LEVEL_TYPE.match(line)
            if top:
                enclosing = top.group(1)
                continue
            if not line.lstrip().startswith("*/"):
                continue
            # Walk back to the `/**` this closes, and only count a KDoc that
            # actually documents a parameter.
            start = index
            while start >= 0 and not lines[start].lstrip().startswith("/**"):
                start -= 1
            if start < 0 or not KDOC_PARAM.search("\n".join(lines[start:index])):
                continue
            # Then forward over the annotations to the declaration itself.
            after = index + 1
            while after < len(lines) and (
                not lines[after].strip() or lines[after].lstrip().startswith("@")
            ):
                after += 1
            if after >= len(lines):
                continue
            header = DOCUMENTED_HEADER.match(lines[after])
            if not header:
                continue
            indent, receiver, name = len(header.group(1)), header.group(2), header.group(3)
            owner = receiver or (enclosing if indent > 0 else None)
            if owner is None:
                found.add(name)
            elif owner.endswith("Scope"):
                found.add(owner[: -len("Scope")])
            else:
                found.add(f"{owner}.{name}")
                found.add(name)
    return found


# Only goes down. See rule 17.
#
# Thirty-two of 103, and the two thirds already on the right side of it are
# why this is a ratchet and not a sweep. `@param` in this library is selective
# on purpose: `Button` explains three of its twelve because the other nine are
# `modifier`, `enabled`, `onClick` and their like, and a sentence restating a
# parameter's name teaches nobody anything. Demanding all 1,101 would produce
# 966 of those sentences.
#
# What a page owes is *one* — the parameter a reader would otherwise have to
# guess at, which every component has. Below the line the table is three columns
# of names, types and defaults with nothing saying what any of it is for.
#
# The count is here rather than on the page deliberately. A coverage figure is a
# fact about this library's maintenance; a reader looking at `Chip` is owed the
# sentences, not the percentage of them that exist.
MAX_PAGES_WITHOUT_PARAMETER_HELP = 32


def claimed_symbols() -> set[str]:
    """Every symbol any documentation page says it is about.

    Across all of `ui-docs/content`, not only `components/`, because a handful of
    things are genuinely explained by a guide rather than by a component page —
    `KontourTheme` is a theme, not a component, and `theming.md` is where anyone
    looking for it would look.
    """
    found: set[str] = set()
    for path in CONTENT.rglob("*.md"):
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
# Only goes down. See rule 14.
#
# Nine, and what is in it is as interesting as the number. `ContrastLevel` and
# `HapticsLevel` are parameters of `KontourTheme`, which no component demo will
# ever sweep — the site's own settings panel drives them instead. Excusing those
# two would need either a list of names, which this file's whole argument is
# against, or a second derived rule with one customer. A ceiling is not a claim
# that it should be zero; it is a claim that it should not grow.
#
# The other seven are all reachable and all worth a knob: `FabPosition`,
# `OverlaySide` and `OverlayAlignment` in every direction, `NavExpandPlacement`,
# `NavigationSuiteType`, `PaneFocus` and `ScrimStyle`.
#
# `ReorderHandleSide` came off this list in Round 27, and how is the argument
# for the rule. It was named here as worth a knob for two rounds. It got one
# only when the reporter asked to see the drag handles — because `handleIcon`,
# the parameter that makes the side mean anything, had **zero call sites**
# anywhere in the repository. The side was unswept because the feature was
# unrendered, and the ceiling is what kept saying so.
MAX_UNSWEPT_ENUMS = 9

# Only goes down. See rule 16.
#
# Five, and the criterion earns every one of them. `isNewPassword` is an
# autofill hint with nothing to render; `matchHeightConstraintsFirst` and
# `propagateMinConstraints` are layout escape hatches on `AspectRatioBox` and
# `Surface`, pressed by nobody because there is nothing to look at;
# `MenuItem.multiple` changes what a screen reader announces and is set by
# `MultiSelect` at `Select.kt:233`, so it is exercised without being named; and
# `NavBarItem.showLabel` is handed down from `NavBar.showLabels`, which the
# nav-surfaces demo *does* sweep.
#
# It was sixteen before round 22. Twelve of those were features that shipped
# switched off and stayed that way — the chip morph whose own KDoc carries the
# worked example, the stepper's `AnimatedCounter` wired in and never turned on,
# the range slider's tick marks. A reader could not press one of them.
MAX_UNDEMOED_FLAGS = 4


def undemoed_flags() -> list[str]:
    """Boolean parameters of public components that no demo names.

    The mirror of `unswept_enums`, and it cannot be built the same way. A
    `Knob.Choice(…, X.entries)` names the *type*, so a regex can tie a knob to
    an enum; `Knob.Flag("Ticks")` names a human label with no mechanical link to
    the parameter it drives. So the criterion is the call site instead: the flag
    appears as a named argument somewhere in a `*Demos.kt`.

    Which means this measures exactly what rule 14 measures — that a reader can
    press it — and nothing about whether the knob is any good. That is the right
    trade: the failure it exists to catch is a parameter no call site anywhere
    passes, which is a feature that was written, documented and never once run.
    """
    flags: dict[str, set[str]] = {}
    for path in Path("ui/src/commonMain/kotlin").rglob("*.kt"):
        text = path.read_text()
        for match in COMPOSABLE_HEADER.finditer(text):
            visibility, name = match.group(1).strip(), match.group(3)
            if visibility in ("internal", "private") or not name[0].isupper():
                continue
            at, depth = match.end() - 1, 0
            while at < len(text):
                if text[at] == "(":
                    depth += 1
                elif text[at] == ")":
                    depth -= 1
                    if depth == 0:
                        break
                at += 1
            for flag in BOOLEAN_FLAG.findall(text[match.end() - 1:at]):
                flags.setdefault(flag, set()).add(name)

    demos = "\n".join(
        path.read_text()
        for path in Path("ui-catalog/src").rglob("*Demos.kt")
        if "build" not in path.parts
    )
    return sorted(
        flag for flag in flags
        if not re.search(rf"\b{re.escape(flag)}\s*=", demos)
    )


# A parameter that ships a feature switched off. `= true` is a feature switched
# *on*, which a demo turning it off is a nicety rather than a gap.
BOOLEAN_FLAG = re.compile(r"^\s*(\w+): Boolean = false,?\s*$", re.M)


# Only goes down, and it is already at the floor. See rule 15.
#
# Zero, measured: every one of the 25 files in `:ui` that installs a click,
# selection or toggle handler sets at least as many cursors as it has handlers.
#
# A ceiling on the *count* rather than a per-site pairing, because pairing a
# cursor to the handler it belongs to needs a Kotlin parser and this is Python
# — the same wall `MAX_WITHOUT_DEMO` and `unswept_enums` both name. Counting
# per file is enough for the thing that actually happens: someone adds a
# thirty-sixth clickable and forgets, and the file's totals stop matching.
MAX_UNCURSORED_CLICKS = 0


# A click handler and a cursor, ignoring anything inside a comment — every
# modifier chain in this library's KDoc is a worked example and none of them is
# a call site. The lookbehind on `//` keeps a `https://` in a string from
# swallowing the rest of its line.
CLICK_HANDLER = re.compile(r"\.(?:clickable|selectable|toggleable|combinedClickable)\(")
CURSOR_CALL = re.compile(r"pointerCursor\(")
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
LINE_COMMENT = re.compile(r"(?<!:)//[^\n]*")


def uncursored_clicks() -> list[str]:
    """Files with more click handlers than mouse cursors.

    A cursor is the one affordance in this library with nothing to render into a
    golden — `ImageComposeScene` draws no pointer — so thirty-five call sites
    were about to be verified by reading. This is what replaces the reading.

    Counted per file rather than matched per site: `Modifier.pointerCursor` sits
    directly above the handler it belongs to at every call site here, and
    proving that mechanically needs a Kotlin parser. What the count does catch
    is the failure that will actually happen — a new clickable added to a file
    that already had cursors, where a per-file "does it mention one" check would
    pass and a reader would see an arrow over a button.
    """
    behind: list[str] = []
    for path in sorted(Path("ui/src/commonMain/kotlin").rglob("*.kt")):
        text = LINE_COMMENT.sub("", BLOCK_COMMENT.sub("", path.read_text()))
        clicks = len(CLICK_HANDLER.findall(text))
        if clicks == 0:
            continue
        cursors = len(CURSOR_CALL.findall(text))
        if cursors < clicks:
            behind.append(f"{path.name} ({clicks - cursors} short)")
    return behind


MAX_HAPTIC_SITES = 9


HAPTIC_CALL = re.compile(r"feedback\.perform\(")


def haptic_sites() -> list[str]:
    """Every place in `:ui` that asks for physical feedback, by file.

    A ceiling rather than a ban, and a ratchet like rules 4, 6 and 7 — the
    number is allowed to go down and nothing else. It went 11 to 9 in round 26,
    when `Slider` and `RangeSlider` stopped hand-rolling their own detent guard
    and went through the shared `DetentTicker` like everything else that snaps.
    Two fewer call sites, the same two components firing.

    It exists because this drifted once, quietly and in one direction. "Make it
    tactile" was a good instruction; fifty-seven call sites was the result of
    following it one component at a time, with nobody in a position to see the
    total. Every `clickable` fired. Every `toggleable` fired. A stepped slider
    fired on the press and again on the release, a swipe row fired four
    different intents in one gesture, and a wheel picker fired the moment it was
    composed. Each of those was defensible on its own and the sum was a
    component set that buzzes when you look at it.

    No single test could have caught that, because every one of them was
    *working*. What catches it is the count, which is why this is a count.

    The policy the survivors have to meet is written out under "Physical
    feedback" in `ui-docs/content/theming.md`, and `DetentHapticsTest` holds the
    individual components to it. Raising this number means arguing with that
    section first.
    """
    sites: list[str] = []
    for path in sorted(Path("ui/src/commonMain/kotlin").rglob("*.kt")):
        text = LINE_COMMENT.sub("", BLOCK_COMMENT.sub("", path.read_text()))
        count = len(HAPTIC_CALL.findall(text))
        if count:
            sites.append(f"{path.name} ({count})")
    return sites


MAX_POLICY_DRIFT = 0

# The `Where` column of the haptics table in `theming.md`: the components the
# policy says fire, as backticked names on a row that starts with a pipe.
HAPTIC_POLICY_ROW = re.compile(r"^\|\s*A \*\*[^|]+\|([^|]*)\|", re.M)

# A component that fires, either directly or through the shared ticker.
PERFORMS = re.compile(r"\bperform\(|\brememberDetentTicker\(")

# Two files whose component is not their filename. Written out rather than
# guessed: `Reorderable.kt` holds `ReorderableItem`, and the warning lives in
# `Dialog.kt` but belongs to `AlertDialog`.
HAPTIC_FILE_NAMES = {"Reorderable": "ReorderableItem", "Dialog": "AlertDialog"}

# The mechanism rather than a component: one defines the dispatcher, the other
# is the shared detent ticker every snapping component calls.
HAPTIC_MECHANISM = {"Feedback.kt", "Detents.kt"}


def policy_named() -> set[str]:
    """The components the haptics table names. For the summary line."""
    named = set()
    for row in HAPTIC_POLICY_ROW.findall(Path("ui-docs/content/theming.md").read_text()):
        for name in re.findall(r"`([A-Z]\w+)", row):
            named.add(name)
    return named


def haptics_policy_drift() -> list[str]:
    """Components the haptics policy names but that no longer fire, and vice versa.

    Rule 19 caps the *count* of call sites, which is what stops the library
    drifting back toward buzzing at everything. It says nothing about whether the
    document explaining the count is still true, and that document is the thing a
    reader is supposed to argue with before raising the ceiling.

    It had already drifted. The audit removed both of `PaneScaffold`'s sites —
    a pane divider is dragged with a mouse on a wide screen, which is the one
    input that cannot feel a haptic at all — and the table went on listing it
    under "a threshold passed" for the rest of the round. Nothing failed,
    because nothing was checking the prose against the code.

    Matched on component name, so a component that moves file or gains a second
    call site is not a failure; only appearing in one list and not the other is.
    """
    policy = set()
    for row in HAPTIC_POLICY_ROW.findall(Path("ui-docs/content/theming.md").read_text()):
        for name in re.findall(r"`([A-Z]\w+)", row):
            policy.add(name)

    firing = set()
    for path in sorted(Path("ui/src/commonMain/kotlin").rglob("*.kt")):
        if path.name in HAPTIC_MECHANISM:
            continue
        text = LINE_COMMENT.sub("", BLOCK_COMMENT.sub("", path.read_text()))
        if PERFORMS.search(text):
            stem = path.stem
            firing.add(HAPTIC_FILE_NAMES.get(stem, stem))

    problems = []
    for name in sorted(policy - firing):
        problems.append(f"{name} is in the table and fires nothing")
    for name in sorted(firing - policy):
        problems.append(f"{name} fires and is in no row of the table")
    return problems


MAX_CIRCLES = 24
MAX_ROUNDED_RECT_SHAPES = 0


PILL_USE = re.compile(r"shapes\.pill\b")
ROUNDED_RECT = re.compile(r"\bRoundedCornerShape\s*\(")


def circles() -> list[str]:
    """Files still asking for `Shapes.pill`, and files hand-rolling a rounded rect.

    Two ceilings for one rule: **a corner in this library is a squircle unless
    the thing it is on is a circle.**

    `pill` is the circle. It is a true arc and it is right for an avatar, a
    status dot, the ring round a radio button, a scrollbar thumb, an icon button,
    a colour swatch, a day cell — things that are round because of what they
    *are*, on a box that is square. Everything else that was reaching for it
    wanted a *lozenge*, and a lozenge with circular ends beside a family of
    squircles is the mismatch the shape scale exists to remove; `Shapes.capsule`
    is the same silhouette with the family's curvature.

    **22 to 24, and it is the same sweep finishing rather than a new argument.**
    Round 26 moved "the ten circles" onto `pill` so the 18dp cap could not reach
    them, and it missed two of the places that needed it most — both of which the
    reporter then found:

    * `ExtendedFloatingActionButton` kept `control`, so a collapsed one was a
      rounded square sitting beside a plain FAB that is a circle. Measured as the
      corner's radius over half the box's height: **0.67 at Medium, 0.52 at
      Large, 1.0 after**. `FabShapeTest` could not see it, because it asked
      whether the box was *square* and a rounded square is.
    * `NavBar`'s `Floating` container kept `control` too — an 18dp box holding
      40dp `pill` circles. **0.68 before, 1.0 after.** Its own documentation had
      said "a capsule inset from every edge" the whole time.

    Both are square-box sites by the test below, so both meet the bar this
    docstring already sets. Neither is a lozenge.

    **The ceiling went up from 11 to 22 before that, and it is worth saying why
    rather than quietly bumping it.** Round 25 set it to stop drift *back* to circular arcs,
    when the failure mode was a lozenge with round ends. Round 26 caps the
    height-derived corners, and that gives the name a second job: a capped
    `capsule` on a 50dp box is an 18dp rounded square, while a `pill` on the same
    box is still a circle. So the ten sites that moved are the ones that must not
    be capped, and naming them is what exempts them.

    On a square box the two draw the same picture — the corner is saturated on
    both edges, so there is no straight run for the smoothing to ease into and
    the squircle collapses onto the arc. Moving all ten moved 29 goldens and not
    one of them by more than a one-pixel rim, which is the cubic path's
    approximation of an arc and nothing else. That is the check to repeat if this
    ceiling ever moves again: a legitimate `pill` site is one where the box is
    square, and the evidence is that switching it changes no pixel by more than
    a rim.

    The second count is stricter and is a ban rather than a ratchet.
    `RoundedCornerShape` appears exactly once in `:ui`, to define `pill` itself.
    A literal anywhere else is a component that has stopped tracking the scale —
    which is how the last drift started, one reasonable-looking call site at a
    time.

    Not counted, and worth naming so the gap is deliberate rather than missed:
    the seventeen `drawRoundRect` calls. A `CornerRadius` on a `RoundRect` cannot
    carry smoothing at all, so those are round-rects by construction. All but two
    are on something 3-8dp in its short dimension, where the smoothing works out
    under half a pixel; the two that are not — the slider thumb and the switch
    thumb — change size on every frame of a gesture, so a generic path there is a
    path rebuilt sixty times a second. Both are written up under "Two kinds of
    corner" in `ui-docs/content/tokens.md`.
    """
    problems: list[str] = []
    for path in sorted(Path("ui/src/commonMain/kotlin").rglob("*.kt")):
        text = LINE_COMMENT.sub("", BLOCK_COMMENT.sub("", path.read_text()))
        pills = len(PILL_USE.findall(text))
        if pills:
            problems.append(f"{path.name} ({pills})")
    return problems


def hand_rolled_rounded_rects() -> list[str]:
    """`RoundedCornerShape` literals outside the one that defines `pill`."""
    offenders: list[str] = []
    for path in sorted(Path("ui/src/commonMain/kotlin").rglob("*.kt")):
        if path.name == "Shapes.kt":
            continue
        text = LINE_COMMENT.sub("", BLOCK_COMMENT.sub("", path.read_text()))
        count = len(ROUNDED_RECT.findall(text))
        if count:
            offenders.append(f"{path.name} ({count})")
    return offenders


MAX_SILENT_FOCUS_TRAPS = 0


# Geometry a brand adjusts now lives on `ComponentDefaults`. What is left in the
# `*Defaults` objects is meant to be facts about a component — a menu's minimum
# width, a rating's five stars, the seven columns of a calendar — and the number
# only goes down.
#
# It was **137** before the sweep that created `ComponentDefaults`, spread over
# forty-five objects with `ButtonDefaults`' ten paddings and gaps hidden inside
# `metrics()`. Function bodies are counted for exactly that reason, which means
# the floor is not zero: `ScrollbarDefaults` returns a fallback `0.dp` from a
# `remember` and `TextFieldDefaults` fades a container by `0.5f`, and neither is
# a dial anybody wants.
MAX_DEFAULTS_LITERALS = 78

# Every field on `ComponentDefaults` is read by something.
#
# The honesty guard on a wide object of defaulted fields: at its default, a field
# nothing reads is indistinguishable from one that is wired. Two fields are read
# twice on purpose — `uppercaseLabels` by the row-shaped slot and by a field's
# label, and `navIndicatorWidth` by the pill and by the glyph box that is as wide
# as it — so this counts fields read *never*.
#
# What it catches that a value check cannot: a forward dropped during the sweep.
# Fifteen of the fifty-eight defaults are shared by two or more fields, so wiring
# the wrong one of a pair passes every assertion about values; it shows up here
# as one field with no reader at all.
MAX_UNREAD_COMPONENT_DEFAULTS = 0

DEFAULTS_OBJECT = re.compile(r"^(?:public )?object (\w*Defaults) \{", re.M)
# A dimension, duration, fraction or count written as a literal.
DEFAULTS_LITERAL = re.compile(r"(?<![\w.])\d+(?:\.\d+)?(?:\.dp|\.sp|[fL]\b|\b)")
COMPONENT_DEFAULTS_FIELD = re.compile(r"^    val (\w+): ", re.M)


def defaults_literals() -> int:
    """Hardcoded numbers still sitting inside a `*Defaults` object."""
    total = 0
    for path in sorted(Path("ui/src/commonMain/kotlin").rglob("*.kt")):
        lines = path.read_text().split("\n")
        for index, line in enumerate(lines):
            if not DEFAULTS_OBJECT.match(line):
                continue
            depth = 0
            for at in range(index, len(lines)):
                depth += lines[at].count("{") - lines[at].count("}")
                body = lines[at].strip()
                if not body.startswith(("*", "//", "/*")):
                    total += len(DEFAULTS_LITERAL.findall(lines[at]))
                if depth == 0 and at > index:
                    break
    return total


def unread_component_defaults() -> list[str]:
    """`ComponentDefaults` fields no component reads."""
    root = Path("ui/src/commonMain/kotlin")
    declaration = root / "io/kontour/ui/theme/ComponentDefaults.kt"
    fields = COMPONENT_DEFAULTS_FIELD.findall(declaration.read_text())
    body = "\n".join(
        path.read_text() for path in root.rglob("*.kt") if path != declaration
    )
    return [
        field for field in fields
        # `Theme.componentDefaults.x` at the point of use, or `d.x` / `it.x`
        # after the object has been pulled into a local — both are house style
        # and both appear in the sweep.
        if not re.search(rf"(?:componentDefaults|\bd|\bit)\.{field}\b", body)
    ]


# `OverlayEntry(` and everything up to the matching close, so `trapFocus` can be
# looked for among *this* entry's arguments rather than anywhere in the file.
OVERLAY_ENTRY = re.compile(r"\bOverlayEntry\s*\(")


def silent_focus_traps() -> list[str]:
    """`OverlayEntry` sites that never say whether they trap focus.

    `OverlayEntry.trapFocus` defaults to `true`, and the host ORs it across every
    visible entry — one trapping overlay makes the whole tree behind it
    unfocusable. That is right for a dialog and catastrophic for something that
    floats *over* a control the user is still using.

    The selection toolbar was the second kind and took the first kind's default.
    It published a `Menu`-layer entry over a focused text field, the field lost
    focus, its selection collapsed, Compose called `hide()`, and the entry was
    torn down before the button the user had just pressed could run its
    `onClick`. The toolbar destroyed itself by existing, and it did it by saying
    nothing.

    Seven of the library's fourteen entries were silent when this was written.
    Six of them wanted the default; that is exactly what makes a default like
    this dangerous, because it is right often enough to be adopted without
    thought.

    So the rule is **state it**, not "compute it". Deriving from `scrim` looks
    tempting and does not work: `Menu` and the selection toolbar are both
    `ScrimStyle.Transparent` and want opposite answers.
    """
    silent: list[str] = []
    for path in sorted(Path("ui/src/commonMain/kotlin").rglob("*.kt")):
        # Block comments are blanked *keeping their newlines*, so the line
        # number below is the one in the file rather than the one in a string
        # this function invented. A line comment cannot contain a newline, so
        # deleting those outright is safe.
        text = LINE_COMMENT.sub(
            "",
            BLOCK_COMMENT.sub(lambda m: "\n" * m.group(0).count("\n"), path.read_text()),
        )
        for match in OVERLAY_ENTRY.finditer(text):
            depth, i = 1, match.end()
            while i < len(text) and depth:
                if text[i] == "(":
                    depth += 1
                elif text[i] == ")":
                    depth -= 1
                i += 1
            if "trapFocus" not in text[match.end():i]:
                line = text.count("\n", 0, match.start()) + 1
                silent.append(f"{path.name}:{line}")
    return silent


def unswept_enums() -> list[str]:
    """Enums a component takes as a parameter and no demo's knob sweeps.

    The demos are what a reader presses, and `DemoRenderTest.every knob setting
    draws` renders each setting of each knob — so a `Knob.Choice` built from an
    enum's `entries` is the only construct that cannot fall behind the enum. An
    enum with no knob is a parameter the generated table tells the reader exists
    and the page shows one value of. Round 20 shipped `NavBarStyle` with three
    variants and `SheetHeaderStyle` with three, and the site rendered one of
    each, for six months, with every gate green.

    **The criterion does the work a list of exemptions would otherwise do.** A
    *named parameter whose declared type is the enum*, on a *TitleCase public
    `@Composable`* — the same cut `public_composables` makes, where the
    lowercase ones are modifiers and factories. That is enough to leave out, with
    no clause written for any of them: `InputModality`, which only ever comes
    back from `rememberInputModalityState`; `SwipeValue`, which is a gesture's
    state rather than an argument; `RangePosition`, which appears only as a
    lambda's return type; and `RevealVariant`, which belongs to
    `Modifier.revealOnScroll`.

    What it cannot see, said rather than left to be discovered: a choice handed
    to a slot DSL. `StatTrend` reaches `Stat` through `StatScope.trend`, so it is
    swept and is not in the denominator either. `KotlinSignatures` in `buildSrc`
    reads receivers and enclosing types and would see them — it is Kotlin and
    this is Python, the same wall `MAX_WITHOUT_DEMO` names above.
    """
    enums: set[str] = set()
    for path in Path("ui/src/commonMain/kotlin").rglob("*.kt"):
        enums |= set(re.findall(r"^\s*(?:public\s+)?enum class (\w+)", path.read_text(), re.M))

    takes: set[str] = set()
    for path in Path("ui/src/commonMain/kotlin").rglob("*.kt"):
        text = path.read_text()
        for match in COMPOSABLE_HEADER.finditer(text):
            visibility, name = match.group(1).strip(), match.group(3)
            if visibility in ("internal", "private") or not name[0].isupper():
                continue
            # To the paren that closes the one the header ended on, so a default
            # value containing brackets does not truncate the parameter list.
            at, depth = match.end() - 1, 0
            while at < len(text):
                if text[at] == "(":
                    depth += 1
                elif text[at] == ")":
                    depth -= 1
                    if depth == 0:
                        break
                at += 1
            parameters = text[match.end():at]
            for enum in enums:
                if re.search(rf"^\s*\w+:\s*{enum}\s*(?:=|,|$)", parameters, re.M):
                    takes.add(enum)

    swept: set[str] = set()
    for path in Path("ui-catalog/src").rglob("*Demos.kt"):
        if "build" in path.parts:
            continue
        swept |= set(re.findall(r"Knob\.Choice[^\n]*?(\w+)\.entries", path.read_text()))

    return sorted(takes - swept)


MAX_WITHOUT_SAMPLE = 0

# Only goes down. See rule 7.
#
# **Zero.** It arrived at 102 of 103 in the same round it was paid off: the debt
# was written down as a ceiling first, and then the 102 sections were written
# from what the source actually does, which is the only way to write them. Two
# real defects turned up while reading — `TimeField`'s label was not its
# accessible name, and `MultiSelect` announced its options as radio buttons when
# any number of them can be on.
#
# Not every page owes the same thing. `accessibility.md` carries the rules that
# apply to all of them; what belongs on a page is what is specific to it — which
# role the component takes, what it announces, what its live region does.
MAX_WITHOUT_ACCESSIBILITY = 0

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


# The README's claim about how big the library is, between markers so this can
# read it without a regex over prose.
#
# It said "138 components" for four rounds and matched nothing measurable —
# not the registry (49), not the pages (103), not the public composables (138 as
# it happens, but by coincidence rather than because anybody counted). A number
# in a README is read as a fact, and the only kind worth writing is one that
# fails the build when it stops being true.
README = Path("README.md")
COUNTS = re.compile(r"<!--counts-->(.*?)<!--/counts-->", re.DOTALL)


ACCESSIBILITY_BODY = re.compile(
    r"^#+\s*Accessibility\s*\n(.*?)(?=\n---\n|\Z)", re.MULTILINE | re.DOTALL | re.IGNORECASE
)
MARKDOWN_LINK = re.compile(r"\[[^\]]*\]\([^)]*\)")
BACKTICKED = re.compile(r"`([A-Za-z_][\w.]*)`")

# Words that read as identifiers and are not ones — callback names used
# generically, and Compose's own vocabulary.
PROSE = {"onCheckedChange", "onClick", "onValueChange", "onDismissRequest",
         "onSelectedChange", "null", "true", "false", "labelledBy"}


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

    # Rule 9 — the README says how big the library is, and is right.
    claimed = COUNTS.search(README.read_text()) if README.exists() else None
    if claimed:
        expected = (
            f"{len(public_composables())} public components across "
            f"{len(component_pages)} pages"
        )
        if claimed.group(1).strip() != expected:
            problems.append(
                f"README.md claims “{claimed.group(1).strip()}” and the tree has "
                f"“{expected}” — update the text between the <!--counts--> markers"
            )

    # Rule 10 — an accessibility section names things that exist.
    #
    # Deliberately a whole-library check rather than a per-page one. Per page it
    # would be sharper and it would also be wrong three times in a hundred:
    # `date-picker.md` is right to say each day reports `Role.Button` even though
    # that line lives in `CalendarMonth.kt`, and `segmented-control.md` is right
    # to mention `Role.Tab` while explaining what it is not.
    #
    # What this catches is the drift that actually happens — a parameter renamed
    # in `:ui` while the prose describing it stays as it was. These sections are
    # dense with parameter names by design, which is what makes them worth
    # checking and what would otherwise make them rot fastest.
    library = "".join(
        path.read_text() for path in Path("ui/src/commonMain/kotlin").rglob("*.kt")
    )
    for page in component_pages:
        section = ACCESSIBILITY_BODY.search(page.read_text())
        if not section:
            continue
        body = MARKDOWN_LINK.sub("", section.group(1))
        for token in BACKTICKED.findall(body):
            name = token.split(".")[-1]
            if name in PROSE or len(name) < 4:
                continue
            if not re.search(rf"\b{re.escape(name)}\b", library):
                problems.append(
                    f"{page.name}'s accessibility section names `{token}` and "
                    f"nothing in :ui is called that — it has been renamed, or the "
                    f"prose describing it was written from memory"
                )

    # Rule 3 — every page is reachable from its index.
    linked = {f"{stem}.md" for stem in family_of()}
    for page in component_pages:
        if page.name not in linked:
            problems.append(
                f"{page.name} is linked from no category index — a page nothing "
                f"points at is a page nobody reads"
            )

    # Rule 11 — the radius scale is written down three times and executes once.
    #
    # `Shapes.kt`'s defaults are the only copy the build runs. Its own KDoc table
    # and the one in `tokens.md` are prose, and prose drifts: for most of this
    # library's life both of them said `extraLarge` was what bottom sheets used,
    # which was never true — sheets have always had their own token. Checking the
    # two tables against the code is cheap and it is the only thing that would
    # have caught that.
    shapes_source = Path("ui/src/commonMain/kotlin/io/kontour/ui/theme/Shapes.kt")
    if shapes_source.exists():
        source = shapes_source.read_text()
        # The kind of corner used to be checked alongside the radius, because the
        # scale had two of them and which rung changed over was a real fact worth
        # pinning. Every rung is a squircle now, so a "corner" column would say
        # the same word eight times, and a column that cannot disagree with the
        # code cannot catch the code changing.
        #
        # `control` and `field` are deliberately absent: they resolve from the
        # height of whatever they are put on, so they have no radius to check.
        actual = dict(
            re.findall(
                r"val (\w+): CornerBasedShape = SquircleShape\((\d+)\.dp\)",
                source,
            )
        )
        tables = {
            "Shapes.kt": (source, re.compile(r"\|\s*\[(\w+)\]\s*\|\s*(\d+)dp\s*\|")),
            "tokens.md": (
                (CONTENT / "tokens.md").read_text(),
                re.compile(r"\|\s*`(\w+)`\s*\|\s*(\d+)dp\s*\|"),
            ),
        }
        for where, (text, pattern) in tables.items():
            documented = dict(pattern.findall(text))
            for name, radius in actual.items():
                if name not in documented:
                    problems.append(f"{where} has no row for the `{name}` radius token")
                elif documented[name] != radius:
                    problems.append(
                        f"{where} says `{name}` is {documented[name]}dp, but "
                        f"Shapes.kt builds it at {radius}dp"
                    )

    # Rule 12 — every component in the shape-families table really asks for
    # that family.
    #
    # The families are the answer to "why do these two things have different
    # corners": components name what they *are* — a control, a field, a
    # container, a panel — and the theme decides the radius once. That only holds
    # while the table and the source agree, and a table of component names is
    # precisely the kind of prose that rots the first time somebody adds a
    # component and forgets the row.
    #
    # Not the other way round. A component may legitimately be absent from the
    # table — an avatar is a circle because it is an avatar, not because it
    # belongs to a family — so this checks the claims made, not the omissions.
    families = re.compile(r"\|\s*`(control|field|container|panel)`\s*\|[^|]*\|([^|]*)\|")
    composables = public_composables()
    for family, members in families.findall((CONTENT / "tokens.md").read_text()):
        for name in BACKTICKED.findall(members):
            path = composables.get(name)
            if path is None:
                problems.append(
                    f"tokens.md lists `{name}` under the `{family}` shape family, "
                    f"but there is no such public composable in :ui"
                )
            elif f"Theme.shapes.{family}" not in path.read_text():
                problems.append(
                    f"tokens.md says `{name}` uses the `{family}` shape, but "
                    f"{path.name} never asks for `Theme.shapes.{family}`"
                )

    # Rule 13 — the map agrees with the indexes it summarises.
    #
    # `components.md` is the one page claiming to show the whole shape of the
    # library, and it is a hand-written summary of eleven hand-written indexes:
    # two chances to forget the same row, and nothing comparing them. It had
    # drifted in both directions at once. Fifteen components — among them
    # `ExtendedFloatingActionButton`, `TriStateCheckbox`, `PageIndicator` and
    # `OverlayHost` — had a page and an index and no line on the map. `Divider`
    # was *on* the map under a name nothing has been called since it split into
    # `HorizontalDivider` and `VerticalDivider`. And `DragHandle` sat on the
    # Sheets row while the Collections index owned the page, so the map and the
    # sidebar disagreed about where it lived.
    #
    # This used to compare a third copy, the "← Family" footer at the bottom of
    # every page. That footer only ever reached a GitHub reader — the site cuts
    # it and draws its own navigation — and round 22 removed it along with the
    # screenshots, for the same reason. Two independent copies is still two.
    #
    # Asymmetric on purpose. A summary row is a summary — it should not have to
    # name `SkeletonText` and `BadgedBox` — so the second half asks only that
    # every page get *a* mention. The first half is strict, because a name on
    # the map that leads nowhere is the failure a reader actually hits.
    #
    # `Motion.*` and its like need no exemption: `BACKTICKED` will not match a
    # name containing a `*`, so a wildcard on the map is not a claim about a page.
    # From `doctree` rather than re-derived, so this checks what the site does
    # rather than something that resembles it.
    owner_family = {stem: claim[0] for stem, claim in family_of().items()}

    symbol_page: dict[str, Path] = {}
    for page in component_pages:
        for symbol in page_symbols(page):
            symbol_page.setdefault(symbol, page)

    family_row = re.compile(
        r"^\|\s*\[\*\*.+?\*\*\]\(components/([a-z0-9-]+)\.md\)\s*\|[^|]*\|(.+?)\|\s*$",
        re.M,
    )
    for stem, members in family_row.findall((CONTENT / "components.md").read_text()):
        mentioned: set[str] = set()
        for symbol in BACKTICKED.findall(members):
            page = symbol_page.get(symbol)
            if page is None:
                problems.append(
                    f"components.md lists `{symbol}` under {stem} and no component "
                    f"page is titled that — it has been renamed, or its page was "
                    f"never written"
                )
            elif owner_family.get(page.stem) != stem:
                problems.append(
                    f"components.md lists `{symbol}` under {stem}, but "
                    f"{page.name} is indexed by "
                    f"{owner_family.get(page.stem, 'no index')} — the map and the "
                    f"sidebar disagree about where it lives"
                )
            else:
                mentioned.add(page.stem)
        for page_stem, family in sorted(owner_family.items()):
            if family == stem and page_stem not in mentioned:
                problems.append(
                    f"components.md's {stem} row names nothing on {page_stem}.md "
                    f"— a component the map never mentions is one nobody finds "
                    f"from the map"
                )

    # Rule 14 — a component's choices are things a reader can press.
    #
    # Rule 4 counts demos per page; nothing looked inside one. So Round 20 could
    # add `NavBarStyle` with three variants and `SheetHeaderStyle` with three,
    # ship both, and have the site render one of each with every gate green — the
    # generated parameter table told a reader the choice existed and the live
    # demo showed a single value of it.
    #
    # A ratchet, like rules 4, 6 and 7, and named rather than only counted: the
    # point of a ceiling is that lowering it is the fix, and nobody can lower it
    # without knowing what is inside.
    # Rule 15 — a click target sets a mouse cursor.
    #
    # An arrow over a button is the oldest tell that something is not really a
    # button, and every one of the thirty-five call sites this round touched had
    # one. The reason it went unnoticed for twenty-one rounds is the reason it
    # needs a rule rather than a review: `ImageComposeScene` draws no pointer, so
    # not one of the 204 goldens could show it, and there is no frame anywhere in
    # this repository in which a cursor appears.
    #
    # So the check is structural. See `uncursored_clicks` for why it counts per
    # file rather than pairing each handler with its own cursor.
    uncursored = uncursored_clicks()
    if uncursored:
        short = sum(int(entry.split("(")[1].split()[0]) for entry in uncursored)
        problems.append(
            f"{short} click handler(s) in :ui set no mouse cursor, over the "
            f"ceiling of {MAX_UNCURSORED_CLICKS}: {', '.join(uncursored)} — a "
            f"`Modifier.pointerCursor()` above the handler is the fix, and "
            f"nothing renders a pointer so no golden will tell you"
        )

    # Rule 16 — a component's booleans are things a reader can press.
    #
    # Rule 14 does this for enums, and the gap it left was the larger one: an
    # enum at least renders one of its values, while a `Boolean = false` nobody
    # sets renders nothing at all. Twelve features were in that state — written,
    # documented, and never run by anything in this repository.
    undemoed = undemoed_flags()
    if len(undemoed) > MAX_UNDEMOED_FLAGS:
        problems.append(
            f"{len(undemoed)} component boolean parameters are on no demo's "
            f"knob, over the ceiling of {MAX_UNDEMOED_FLAGS}: "
            f"{', '.join(undemoed)} — a `Knob.Flag` passed at the call site "
            f"puts one in front of a reader and under `DemoRenderTest`"
        )

    # Rule 17 — a page explains at least one of the parameters it lists.
    #
    # The table under every component page is generated from `:ui`'s own
    # signatures, so it cannot drift — but for a round it was three columns of
    # names, types and defaults with nothing anywhere saying what any of them
    # was *for*. The KDoc has that sentence for 138 of the library's 1,214
    # parameters, and the site now shows it; this counts the pages where there
    # is not one to show.
    explained = symbols_explaining_a_parameter()
    unexplained = sorted(
        page.stem
        for page in component_pages
        if not any(
            symbol in explained or symbol.split(".")[-1] in explained
            for symbol in page_symbols(page)
        )
    )
    if len(unexplained) > MAX_PAGES_WITHOUT_PARAMETER_HELP:
        problems.append(
            f"{len(unexplained)} component pages explain none of their "
            f"parameters, and the ceiling is "
            f"{MAX_PAGES_WITHOUT_PARAMETER_HELP}. One `@param` sentence on the "
            f"parameter a reader would otherwise guess at is what clears a page; "
            f"the table picks it up on the next build. Without: "
            f"{', '.join(unexplained)}"
        )

    # Rule 18 — the pages are written for the person reading them.
    #
    # The documentation grew alongside the library, so a good deal of it was
    # written by somebody holding both in their head at once. That shows up as
    # prose which is true, useful to a maintainer, and unusable by anybody
    # else — see the note above `MAX_INTERNAL_FACING`.
    internal = internal_facing()
    if len(internal) > MAX_INTERNAL_FACING:
        problems.append(
            f"{len(internal)} passage(s) address a maintainer rather than a "
            f"reader, and the ceiling is {MAX_INTERNAL_FACING}. Keep the claim "
            f"and drop the identifier — \"checked on every build\" says what a "
            f"test class name says, to somebody who cannot run it. At: "
            f"{', '.join(internal)}"
        )

    # Rule 19 — the library buzzes for eleven things, and no more.
    #
    # See `haptic_sites`. A ratchet on a total nobody was in a position to see
    # while it grew from a good instruction to fifty-seven call sites.
    haptics = haptic_sites()
    felt = sum(int(entry.rsplit("(", 1)[1].rstrip(")")) for entry in haptics)
    if felt > MAX_HAPTIC_SITES:
        problems.append(
            f"{felt} haptic call sites in :ui, over the ceiling of "
            f"{MAX_HAPTIC_SITES}: {', '.join(haptics)} — a haptic reports "
            f"something the user could not otherwise tell, and the four cases "
            f"that qualify are listed under \"Physical feedback\" in "
            f"ui-docs/content/theming.md. A press they are watching is not one "
            f"of them"
        )

    # Rule 20 — a corner is a squircle unless the thing it is on is a circle.
    #
    # See `circles`. `pill` survives for the twenty-two places that are genuinely
    # round — square boxes, exempt from the capsule cap; a `RoundedCornerShape`
    # literal anywhere but the token that defines it is a component that has
    # stopped tracking the scale.
    round_shapes = circles()
    circular = sum(int(e.rsplit("(", 1)[1].rstrip(")")) for e in round_shapes)
    if circular > MAX_CIRCLES:
        problems.append(
            f"{circular} uses of `Shapes.pill` in :ui, over the ceiling of "
            f"{MAX_CIRCLES}: {', '.join(round_shapes)} — `pill` is a true "
            f"circular arc and belongs on things that are round because of what "
            f"they are, on a box that is square. A lozenge wants "
            f"`Shapes.capsule`, which is the same silhouette with the family's "
            f"own curvature — and which is capped, where `pill` is not"
        )

    literals = hand_rolled_rounded_rects()
    if len(literals) > MAX_ROUNDED_RECT_SHAPES:
        problems.append(
            f"{len(literals)} file(s) build a `RoundedCornerShape` by hand: "
            f"{', '.join(literals)} — the only one in :ui defines `Shapes.pill`. "
            f"A literal elsewhere is a corner that has stopped tracking the scale"
        )

    # Rule 21 — an overlay says whether it takes focus away from the app.
    #
    # See `silent_focus_traps`. A default that is right six times in seven, and
    # whose seventh was a component that could not be used at all.
    silent = silent_focus_traps()
    if len(silent) > MAX_SILENT_FOCUS_TRAPS:
        problems.append(
            f"{len(silent)} `OverlayEntry` site(s) do not say whether they trap "
            f"focus: {', '.join(silent)} — the default is `true`, and the host "
            f"applies it to everything behind *every* visible entry. An overlay "
            f"that floats over a control the user is still using has to say "
            f"`trapFocus = false`, and one that owns the screen has to say it "
            f"means to"
        )

    drift = haptics_policy_drift()
    if len(drift) > MAX_POLICY_DRIFT:
        problems.append(
            f"the haptics policy in `theming.md` and the library disagree: "
            f"{'; '.join(drift)} — rule 19 caps how many sites there are and "
            f"this is what keeps the document explaining them true, which is "
            f"the thing anyone raising that cap has to argue with first"
        )

    unswept = unswept_enums()
    if len(unswept) > MAX_UNSWEPT_ENUMS:
        problems.append(
            f"{len(unswept)} component parameter enums are on no demo's knob, "
            f"over the ceiling of {MAX_UNSWEPT_ENUMS}: {', '.join(unswept)} — "
            f"a `Knob.Choice(…, X.entries.toList())` puts every value of one in "
            f"front of a reader and under `DemoRenderTest`"
        )

    # Rule 22 — the geometry bag only shrinks.
    #
    # See `MAX_DEFAULTS_LITERALS`. Growing `ComponentDefaults` is only possible
    # by lowering this, so neither number can drift upward quietly.
    literals_left = defaults_literals()
    if literals_left > MAX_DEFAULTS_LITERALS:
        problems.append(
            f"{literals_left} hardcoded number(s) sit in `*Defaults` objects, "
            f"over the ceiling of {MAX_DEFAULTS_LITERALS}. A constant a *brand* "
            f"would change and no token family carries belongs on "
            f"`ComponentDefaults`; one that is a fact about the component stays "
            f"where it is, and the ceiling comes down when one moves"
        )

    # Rule 23 — nothing on `ComponentDefaults` is unwired.
    #
    # See `MAX_UNREAD_COMPONENT_DEFAULTS`. The check a wide, fully-defaulted
    # object needs, and the one a value assertion cannot make.
    unread = unread_component_defaults()
    if len(unread) > MAX_UNREAD_COMPONENT_DEFAULTS:
        problems.append(
            f"{len(unread)} `ComponentDefaults` field(s) are read by nothing: "
            f"{', '.join(unread)} — a field at its default that no component "
            f"reads is API that ships dead, and looks identical to one that "
            f"works. Wire it through the component's `*Defaults` object, or "
            f"take it off"
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
        f"{len(unswept)} parameter enums and {len(undemoed)} booleans on no knob, "
        f"{len(component_pages) - len(unexplained)} pages explaining a parameter, "
        f"{len(internal)} written for a maintainer, "
        f"{felt} haptic call sites, "
        f"{circular} deliberate circles, "
        f"{len(policy_named())} components named by the haptics policy, "
        f"{len(silent)} silent focus traps, "
        f"{literals_left} literals left in `*Defaults`, "
        f"{len(unread)} of them unwired, "
        f"all accounted for."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
