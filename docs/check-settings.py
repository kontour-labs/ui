#!/usr/bin/env python3
"""Does every component honour the reader's settings?

Three settings a reader chooses and a component has to answer to without being
asked: **high contrast**, **reduced motion**, and **where they are** — the
language, script, digits and direction their device is set to. Each has a test
suite that renders components and measures them. This is the other half: the
source patterns that are how each of those promises gets broken one reasonable
call site at a time, counted, so a new one fails here before it needs a golden
to notice.

Every rule is the same shape:

* a detector over `:ui`'s source, comments stripped;
* a named **allow-list**, each entry with the reason it is not a defect —
  written as a sentence a reviewer can disagree with, not a count;
* a **ceiling** on whatever is left, which only goes down.

A stale allow-list entry — one the detector no longer finds — is itself a
failure. An exemption that outlives its code is how a list like this turns into
a list of names nobody can explain.

Run from the repository root: `python3 docs/check-settings.py`.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

UI = Path("ui/src/commonMain/kotlin")
NAV3 = Path("ui-nav3/src/commonMain/kotlin")

BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
LINE_COMMENT = re.compile(r"(?<!:)//[^\n]*")


def source(path: Path) -> str:
    """The file with its comments blanked out, offsets kept.

    Blanked rather than removed so that a line number found in this text is the
    line number in the file, and so braces inside a KDoc sample do not count.
    """
    text = path.read_text()
    text = BLOCK_COMMENT.sub(lambda m: re.sub(r"[^\n]", " ", m.group(0)), text)
    return LINE_COMMENT.sub(lambda m: " " * len(m.group(0)), text)


def files(*roots: Path) -> list[Path]:
    return [p for root in roots for p in sorted(root.rglob("*.kt"))]


def line_of(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


# ---------------------------------------------------------------------------
# Functions, found well enough to say which one a line is in.
#
# Rule M counts per *function* rather than per file, and that needs the span of
# each function body. This is not a Kotlin parser: it finds `fun name(`, skips
# the parameter list by balanced parentheses, and takes either the braced body
# or the expression after `=`. Lambdas are not functions here, so a line inside
# `LaunchedEffect { … }` belongs to the composable that launched it — which is
# the right answer, since that composable is where `reduceMotion` would be read.
# ---------------------------------------------------------------------------

FUN = re.compile(r"\bfun\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(\w+)\s*\(")


def _balanced_end(text: str, start: int, open_: str, close: str) -> int:
    depth = 0
    i = start
    in_string = False
    while i < len(text):
        c = text[i]
        if c == '"' and text[i - 1] != "\\":
            in_string = not in_string
        elif not in_string:
            if c == open_:
                depth += 1
            elif c == close:
                depth -= 1
                if depth == 0:
                    return i
        i += 1
    return len(text) - 1


def function_spans(text: str) -> list[tuple[str, int, int]]:
    spans: list[tuple[str, int, int]] = []
    for m in FUN.finditer(text):
        params_end = _balanced_end(text, m.end() - 1, "(", ")")
        k = params_end + 1
        while k < len(text) and text[k] not in "{=":
            # An abstract or expect declaration has no body; stop at the next
            # declaration rather than borrowing its.
            if text.startswith("fun ", k) or text.startswith("val ", k):
                k = len(text)
                break
            k += 1
        if k >= len(text):
            continue
        if text[k] == "{":
            spans.append((m.group(1), m.start(), _balanced_end(text, k, "{", "}")))
            continue
        depth = 0
        e = k + 1
        while e < len(text):
            c = text[e]
            if c in "({[":
                depth += 1
            elif c in ")}]":
                if depth == 0:
                    break
                depth -= 1
            elif c == "\n" and depth == 0:
                following = text[e + 1:].lstrip(" ")
                if not following.startswith((".", "?.", "?:", "&&", "||", "+ ", "- ", "* ")):
                    break
            e += 1
        spans.append((m.group(1), m.start(), e))
    return spans


def enclosing_function(spans: list[tuple[str, int, int]], offset: int) -> tuple[str, int, int] | None:
    best = None
    for span in spans:
        if span[1] <= offset <= span[2] and (best is None or span[1] > best[1]):
            best = span
    return best


# ===========================================================================
# High contrast
# ===========================================================================

# Rule H1 — a quiet ground has an edge at high contrast.
#
# `surfaceSunken`, `surfaceRaised` and `accent.container` are fills chosen to be
# *near* their ground: a well, a lifted card, a selection tint. At the standard
# tier a shadow or a small step in lightness carries them. At high contrast the
# grounds converge — `surface` against `background` is 1.00:1 in the light
# scheme — and a container with no line round it stops existing. The fix is
# always one of three: `contrastEdge()` for a container, `selectionEdge()` for a
# selection, or a border the component already draws.
#
# Counted per file, because the edge is often applied a few lines away from the
# fill (a `Surface(border = …)` whose colour comes from a parameter), and a file
# that fills quietly and never asks about the tier is what this is after.
QUIET_FILL = re.compile(r"\b(?:surfaceSunken|surfaceRaised|accent\.container)\b")
HAS_EDGE = re.compile(
    r"contrastEdge\(|selectionEdge\(|\.border\(|highContrast\(\)|BorderStroke\(|LocalContrastLevel"
    r"|trackAtTier\("
)

QUIET_FILL_ALLOWED = {
    "RichText.kt": "an inline code span's tint, inside a line of text: the "
    "characters are the content and the monospace face is what sets them apart",
    "Skeleton.kt": "a placeholder, and deliberately faint — it stands in for "
    "content that is loading, is not interactive, and says so to a screen reader",
    "TextButton.kt": "the tint is the hover and press wash on a text-only "
    "button; its label, at 7:1, is the control",
    "TimelineList.kt": "its rows are list rows, which draw the tier's edge "
    "themselves (`edged`) and the selection's",
    "NavScope.kt": "names the default indicator colour; the bar and the rail "
    "that draw the indicator give it the selection edge",
    "NavigationSuiteScaffold.kt": "hands the rail a raised ground; `NavRail` "
    "draws the tier's edge round it",
}
MAX_QUIET_FILLS = 0


def quiet_fills() -> list[str]:
    found = []
    for path in files(UI):
        if path.parent.name in ("theme", "a11y"):
            continue
        text = source(path)
        if QUIET_FILL.search(text) and not HAS_EDGE.search(text):
            found.append(path.name)
    return found


# Rule H2 — an ink made fainter asks the tier first.
#
# `contentMuted.copy(alpha = 0.6f)` is a colour the contrast walk never sees:
# the walk checks tokens, and this is not one. At the standard tier a faded
# caption might clear 4.5:1; at high contrast it has to clear 7:1 and the same
# fraction of a darker ink does not. A file that fades an ink, an `on*` colour
# or an outline has to read the tier — `highContrast()` to drop the fade, or a
# floor that holds at both.
ALPHA_ON_INK = re.compile(
    r"\b(?:content\w*|on[A-Z]\w*|outline\w*|LocalContentColour\.current)\s*\.copy\(\s*alpha\s*="
)
READS_TIER = re.compile(r"highContrast\(\)|LocalContrastLevel|contrastEdge\(")
ALPHA_ALLOWED: dict[str, str] = {}
MAX_FADED_INKS = 0


def faded_inks() -> list[str]:
    found = []
    for path in files(UI):
        text = source(path)
        count = len(ALPHA_ON_INK.findall(text))
        if count and not READS_TIER.search(text):
            found.append(f"{path.name} ({count})")
    return found


# Rule H3 — a line is as thick as the tier says.
#
# `Sizing` thickens every line at high contrast: borders 1 to 2dp, strong
# borders 2 to 3, focus rings 2 to 3. A ring or tick drawn from a constant of
# its own does not move, and at high contrast it is a hairline beside a border
# twice its weight. Derive it from `borderWidth` or `borderWidthStrong`.
#
# Matched on the constant's *name* — `…Ring`, `…Stroke`, `…Hairline`,
# `…TickWidth` — because a stroke width is a dimension like any other and only
# its name says what it is for. A gauge's arc thickness is a size, not a line,
# and is not named like one.
FIXED_STROKE = re.compile(
    r"^\s*(?:private |internal )?(?:const )?val (\w*(?:Ring|Stroke|Hairline|HairlineWidth|TickWidth|LineWidth|RuleWidth|RingWidth))"
    r"\s*(?::\s*Dp)?\s*=\s*[0-9.]+\.dp",
    re.M,
)
STROKE_ALLOWED: dict[str, str] = {}
MAX_FIXED_STROKES = 0


def fixed_strokes() -> list[str]:
    found = []
    for path in files(UI):
        if path.name == "Sizing.kt":
            continue
        for m in FIXED_STROKE.finditer(source(path)):
            name = f"{path.name}:{m.group(1)}"
            if name not in STROKE_ALLOWED:
                found.append(name)
    return found


# Rule H4 — a button's colours come from the button.
#
# `ButtonDefaults` is where a variant's colours meet the tier: the Ghost and
# Secondary variants gain an edge at high contrast there, and nowhere else. A
# `ButtonColours(…)` built by hand skips it — Toast's action did, and at high
# contrast it was a word on a dark pill with no sign it could be pressed.
RAW_BUTTON_COLOURS = re.compile(r"\bButtonColours\(")
MAX_RAW_BUTTON_COLOURS = 0


def raw_button_colours() -> list[str]:
    found = []
    for path in files(UI):
        if path.name == "ButtonStyles.kt":
            continue
        count = len(RAW_BUTTON_COLOURS.findall(source(path)))
        if count:
            found.append(f"{path.name} ({count})")
    return found


# Rule H5 — every `Sizing` token is read.
#
# The honesty guard `ComponentDefaults` already has, for the family that carries
# the high-contrast line weights. A token nothing reads is a promise the tier
# does not keep: `selectionIndicator` thickened from 3 to 4dp at high contrast
# and no indicator in the library was drawn from it.
SIZING_FIELD = re.compile(r"^\s+val (\w+): Dp", re.M)
MAX_UNREAD_SIZING = 0


def unread_sizing() -> list[str]:
    sizing = UI / "io/kontour/ui/theme/Sizing.kt"
    fields = SIZING_FIELD.findall(sizing.read_text())
    everything = "\n".join(source(p) for p in files(UI) if p != sizing)
    return [f for f in fields if not re.search(r"\.%s\b" % re.escape(f), everything)]


# ===========================================================================
# Reduced motion
# ===========================================================================

# Rule M — movement is the reader's to refuse. (Rule 24 of `check-components.py`
# until it moved here and was rewritten.)
#
# `Motion`'s helpers shorten a movement and stop it overshooting. None of them
# makes one **smaller**, so a transform whose objection is "it moves too far"
# has to read `reduceMotion` itself — or go through `Motion.enter`, `exit`,
# `scrollSpec` or `sizeSpec`, which are the amplitude helpers and read it for
# the caller.
#
# What changed from the rule it replaces, and why:
#
# * **Per function, not per file.** One `reduceMotion` anywhere in a file used
#   to excuse every transform in it — `FabMenu.kt` read it for its stagger and
#   flew its items 120dp regardless.
# * **More kinds of movement.** It saw four `graphicsLayer` properties. It now
#   sees rotation, `Modifier.scale`/`rotate`/`offset {}`, enter and exit
#   transitions that slide or scale, animated scrolling, `animateItem`, a
#   spec-less `animateTo` (foundation's default spring, which the theme never
#   sees) and infinite transitions.
# * **Named, not counted.** Each exemption says why the movement is small, or
#   is not movement at all. A count let the tail grow by one without anybody
#   having to write down what the one was.
# * **`:ui-nav3` too**, where the page transitions live.
MOVEMENT = re.compile(
    r"\b(?:scaleX|scaleY|translationX|translationY|rotationX|rotationY|rotationZ)\s*=(?!=)"
    r"|(?<![\w])\.?(?:scale|rotate)\((?!\s*\))"
    r"|\.offset\s*\{"
    r"|\b(?:slideIn\w*|slideOut\w*|scaleIn|scaleOut)\("
    r"|\b(?:animateScrollTo\w*|animateScrollBy)\("
    r"|\banimateItem\("
    r"|\.animateTo\(\s*[^,()]+(?:\([^()]*\))?\s*\)"
    r"|\brememberInfiniteTransition\(|\binfiniteRepeatable\("
)
GATED = re.compile(r"reduceMotion|\.enter\(|\.exit\(|scrollSpec\(|sizeSpec\(")

MOVEMENT_ALLOWED = {
    # Not movement: geometry drawn at a value.
    "DialDrawing.kt:dialArcs": "rotates the canvas to draw an arc at its angle; nothing animates",
    "DialDrawing.kt:dialNeedle": "draws a needle at the value's angle; the value is the caller's",
    "Checkbox.kt:TriStateCheckbox": "scales the canvas to draw the mark at its size",
    "Indication.kt:draw": "the press wash's scale, drawn inside the control's own bounds",
    # Small by construction: inside the control's own bounds, a few pixels.
    "IconButton.kt:IconButtonSurface": "an icon's own turn inside its button, as a toggle's state",
    "LoadingSwap.kt:LoadingSwap": "a label shrinking into a spinner inside the same button",
    "WheelPicker.kt:WheelPicker": "the drum's foreshortening of rows that are already scrolling "
    "under the finger; it is how a wheel reads, and is static at rest",
    "WheelPicker.kt:InfiniteWheel": "places rows on the drum at the scroll the finger set",
    "CalendarMonth.kt:DayCell": "a day's label nudged by at most its own width while a range "
    "drag leans on it",
    "Chip.kt:FilterChip": "the check mark growing in beside the label, inside the chip",
    "ColourSwatchPicker.kt:Swatch": "the chosen swatch's ring, inside its own cell",
    "Rating.kt:Mark": "a star's press, inside its own cell",
    "SliderThumb.kt:sliderValueLabel": "the value bubble's appearance over the thumb it names",
    "SearchField.kt:SearchField": "the clear button appearing inside the field",
    "Transitions.kt:chevronTurn": "a chevron turning half a circle in its own box, as state",
    "NavDestination.kt:NavDestination": "the selected icon's emphasis, a few percent in its own box",
    "TopBar.kt:TopBar": "the collapse follows the scroll the reader is making",
    "Scrollbar.kt:Scrollbar": "the thumb follows the scroll the reader is making",
    "BottomSheet.kt:BottomSheet": "places the sheet at the offset its state holds; the "
    "animations that move that offset are what this rule checks",
    "SideSheet.kt:SideSheetPanel": "places the panel at the offset its host's progress holds",
    # The backdrop behind a modal recedes by a few percent.
    "Backdrop.kt:backdropFit": "fits the page into the backdrop's frame; static at rest",
    "Backdrop.kt:overlayBackdrop": "the page receding behind a modal; gated at its call site",
    "Backdrop.kt:backdropGround": "fits the ground to the frame; static at rest",
    # Page transitions: read by `PageMotion`, which picks a fade under reduced motion.
    "PageMotion.kt:push": "chosen by `rememberPageMotion`, which reads `reduceMotion`",
    "PageMotion.kt:predictivePop": "follows the reader's own back gesture",
    "PageMotion.kt:swipePop": "follows the reader's own back gesture",
    "PageMotion.kt:sharedAxis": "chosen by `Transitions`, which reads `reduceMotion`",
}
MAX_UNGATED_MOVEMENT = 15


def ungated_movement() -> tuple[list[str], list[str]]:
    """Movement in a function that never reads the setting; and stale exemptions."""
    found: list[str] = []
    seen_allowed: set[str] = set()
    for path in files(UI, NAV3):
        text = source(path)
        spans = function_spans(text)
        reported: set[str] = set()
        for m in MOVEMENT.finditer(text):
            fn = enclosing_function(spans, m.start())
            name = fn[0] if fn else "<top level>"
            body = text[fn[1]:fn[2]] if fn else text
            if GATED.search(body):
                continue
            key = f"{path.name}:{name}"
            if key in MOVEMENT_ALLOWED:
                seen_allowed.add(key)
                continue
            if key in reported:
                continue
            reported.add(key)
            found.append(f"{key} (line {line_of(text, m.start())}: `{m.group(0).strip()}`)")
    stale = sorted(set(MOVEMENT_ALLOWED) - seen_allowed)
    return found, stale


# ===========================================================================
# Where the reader is
# ===========================================================================

# Rule L1 — words on screen come from `Strings`.
#
# A literal in a sink — `Text("…")`, a content or state description, a `+"…"` —
# is English whatever the device says, and a template (`"$n selected"`) is
# English with the word order welded in, which a translation cannot fix by
# replacing words. Both belong in `Strings`, where a phrase with a number is a
# function that takes a `Quantity` and can put it anywhere.
#
# `joinToString(", ")` is the quiet version of the same thing: a list joined
# with an English comma. `LocaleFormats.list` joins the way the language does.
STRING = r'"((?:[^"\\]|\\.)*)"'
SINK = re.compile(
    r"(?:\bText\(\s*|contentDescription\s*=\s*|stateDescription\s*=\s*|\btext\s*=\s*"
    r"|(?:^|[{;(])\s*\+\s*|paneTitle\s*=\s*|\bjoinToString\(\s*)" + STRING,
    re.M,
)
TEMPLATE = re.compile(r'"((?:[^"\\]|\\.)*\$(?:\{[^}]*\}|\w+)(?:[^"\\]|\\.)*)"')
# The argument of a call that throws or logs, and a data class's `toString`:
# words for a developer, never for a reader.
DEVELOPER_TEXT = re.compile(
    r"\b(?:error|require|requireNotNull|check|checkNotNull|IllegalArgumentException"
    r"|IllegalStateException|println)\s*|\btoString\(\)\s*(?::\s*String\s*)?=\s*"
)
# By file, for the one file whose job is words; by literal for the rest.
LITERAL_FILES_ALLOWED = {
    "Strings.kt": "where the words live",
}
LITERAL_ALLOWED = {
    "digit$index": "a test tag, not text",
}
MAX_LITERAL_PHRASES = 11


def _strip_developer_text(text: str) -> str:
    out = []
    i = 0
    while True:
        m = DEVELOPER_TEXT.search(text, i)
        if not m:
            out.append(text[i:])
            break
        out.append(text[i:m.end()])
        j = m.end()
        # A call and its lazy message — `require(x) { "…" }` — or a string,
        # or strings joined with `+` across lines.
        for _ in range(8):
            while j < len(text) and text[j] in " \n":
                j += 1
            if j < len(text) and text[j] in "({":
                close = ")" if text[j] == "(" else "}"
                j = _balanced_end(text, j, text[j], close) + 1
            elif j < len(text) and text[j] == '"':
                j = text.index('"', j + 1) + 1
            elif j < len(text) and text[j] == "+":
                j += 1
            else:
                break
        i = j
    return "".join(out)


def _is_words(literal: str) -> bool:
    bare = re.sub(r"\$\{[^}]*\}|\$\w+", "", literal)
    return bool(re.search(r"[A-Za-z]{2,}", bare) or re.search(r"[·–/+]|[,.] ", bare))


def literal_phrases() -> list[str]:
    found = []
    for path in files(UI):
        if path.name in LITERAL_FILES_ALLOWED:
            continue
        text = _strip_developer_text(source(path))
        hits = set()
        for m in SINK.finditer(text):
            literal = m.group(1)
            joined = "joinToString" in m.group(0)
            if literal in LITERAL_ALLOWED:
                continue
            if (joined and re.search(r"\s", literal)) or (not joined and _is_words(literal)):
                hits.add(m.start(1))
        for m in TEMPLATE.finditer(text):
            if m.group(1) in LITERAL_ALLOWED:
                continue
            bare = re.sub(r"\$\{[^}]*\}|\$\w+", "", m.group(1))
            if re.search(r"[a-z]{3,}|[·–/]", bare):
                hits.add(m.start(1))
        for offset in sorted(hits):
            found.append(f"{path.name}:{line_of(text, offset)}")
    return found


# Rule L2 — changing case asks which language.
#
# `"istanbul".uppercase()` is `ISTANBUL` in English and `İSTANBUL` in Turkish,
# and a label uppercased without a locale is uppercased in English everywhere.
# Pass the locale (`LocaleFormats.uppercase`), or say why the string is not
# language at all — a hex digit, a URL scheme, a device's model name.
CASE_CHANGE = re.compile(r"\.(?:uppercase|lowercase)\(\)")
CASE_ALLOWED = {
    "ColourSpaces.kt": "hex digits, which are not language",
    "InlineMarkdown.kt": "a URL scheme, which RFC 3986 says is ASCII",
    "DeviceCorner.kt": "a device model name, matched against a table",
    "DeviceCurves.kt": "a manufacturer and codename, matched against a table",
}
MAX_CASE_CHANGES = 4


def case_changes() -> list[str]:
    found = []
    for path in files(UI):
        if path.name in CASE_ALLOWED:
            continue
        count = len(CASE_CHANGE.findall(source(path)))
        if count:
            found.append(f"{path.name} ({count})")
    return found


# Rule L3 — a number on screen is formatted.
#
# `count.toString()` is Western digits with no grouping, in every locale. The
# reader in Cairo who reads ١٢٬٣٤٥ gets 12345. `LocaleFormats.integer` and
# `decimal` are the way in.
NUMBER_TO_SINK = re.compile(
    r"(?:\bText\(\s*|contentDescription\s*=\s*|stateDescription\s*=\s*|\btext\s*=\s*"
    r"|(?:^|[{;(])\s*\+\s*)(?:([\w.]+)\.toString\(\)|\"\$\{?[\w.]+\}?\")",
    re.M,
)
NUMBER_ALLOWED = {
    "CommandPalette.kt": "the query field's own text, handed back",
}
MAX_UNFORMATTED_NUMBERS = 3


def unformatted_numbers() -> list[str]:
    found = []
    for path in files(UI):
        if path.name in NUMBER_ALLOWED:
            continue
        text = source(path)
        for m in NUMBER_TO_SINK.finditer(text):
            found.append(f"{path.name}:{line_of(text, m.start())}")
    return found


# Rule L4 — no calendar is spelled out in English.
#
# A table of month or weekday names is one language's calendar. The names come
# from `LocaleFormats.monthName` and `weekdayName`, which ask the platform.
CALENDAR_WORD = re.compile(
    r'"(?:January|February|March|April|May|June|July|August|September|October|November|December'
    r"|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Oct|Nov|Dec"
    r'|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|Mon|Tue|Wed|Thu|Fri|Sat|Sun)"'
)
MAX_CALENDAR_WORDS = 2


def calendar_words() -> list[str]:
    found = []
    for path in files(UI):
        count = len(CALENDAR_WORD.findall(source(path)))
        if count:
            found.append(f"{path.name} ({count})")
    return found


# Rule L5 — placement follows the reading direction.
#
# `place(x, y)` puts a child at an absolute x, and in a right-to-left layout
# that is the wrong side unless the code mirrored `x` itself. `placeRelative`
# mirrors for you. A file that places absolutely and never asks which way the
# layout runs is either mirroring by accident or not at all.
ABSOLUTE_PLACEMENT = re.compile(
    r"(?<![\w.])place\(|\.place\("
    r"|\bAlignment\.(?:Left|Right|TopLeft|TopRight|BottomLeft|BottomRight|CenterLeft|CenterRight)\b"
    r"|AbsoluteAlignment|Arrangement\.Absolute|\.absoluteOffset\(|absolutePadding\("
)
READS_DIRECTION = re.compile(r"layoutDirection|LayoutDirection|LocalLayoutDirection")
PLACEMENT_ALLOWED = {
    "TouchTarget.kt": "centres the child in its enlarged box, which is symmetric",
}
MAX_ABSOLUTE_PLACEMENT = 2


def absolute_placement() -> list[str]:
    found = []
    for path in files(UI):
        if path.name in PLACEMENT_ALLOWED:
            continue
        text = source(path)
        count = len(ABSOLUTE_PLACEMENT.findall(text))
        if count and not READS_DIRECTION.search(text):
            found.append(f"{path.name} ({count})")
    return found


# ===========================================================================
# The pages
# ===========================================================================

# Rules D1 and D2 — every component page says what the reader's settings do to it.
#
# The two settings are the reader's, and a reader who has turned one on is the
# one reading an Accessibility section to find out what they get. "Nothing
# changes, because nothing moves" is an answer; silence is not. Counted down
# from where the audit found them, which was almost every page.
ACCESSIBILITY_SECTION = re.compile(r"^## Accessibility\s*$(.*?)(?=^## |\Z)", re.M | re.S)
SAYS_CONTRAST = re.compile(r"high contrast|high-contrast|enhanced tier|contrastlevel\.high", re.I)
SAYS_MOTION = re.compile(r"reduced motion|reducemotion|reduce motion", re.I)
MAX_PAGES_SILENT_ON_CONTRAST = 117
MAX_PAGES_SILENT_ON_MOTION = 114


def pages_silent_on(pattern: re.Pattern) -> list[str]:
    silent = []
    for page in sorted(Path("ui-docs/content/components").glob("*.md")):
        m = ACCESSIBILITY_SECTION.search(page.read_text())
        if m and not pattern.search(m.group(1)):
            silent.append(page.stem)
    return silent


# ===========================================================================


def main() -> int:
    problems: list[str] = []

    def ratchet(name: str, found: list[str], ceiling: int, advice: str) -> None:
        if len(found) > ceiling:
            problems.append(
                f"{name}: {len(found)}, over the ceiling of {ceiling}: "
                f"{', '.join(found)} — {advice}"
            )

    def stale(name: str, allowed: dict[str, str], present: set[str]) -> None:
        for key in sorted(set(allowed) - present):
            problems.append(f"{name}: the exemption for {key} no longer matches anything; remove it")

    # High contrast.
    quiet_all = set(quiet_fills())
    stale("H1", QUIET_FILL_ALLOWED, quiet_all)
    quiet = [q for q in sorted(quiet_all) if q not in QUIET_FILL_ALLOWED]
    ratchet(
        "H1 quiet grounds with no edge at high contrast", quiet, MAX_QUIET_FILLS,
        "give the container `contrastEdge()` (or a selection `selectionEdge()`), or "
        "exempt it by name with the reason it needs none",
    )
    faded = faded_inks()
    ratchet(
        "H2 inks faded without reading the tier", faded, MAX_FADED_INKS,
        "at high contrast text has to clear 7:1, and a fraction of an ink the walk "
        "checked is a colour it never saw; read `highContrast()` and drop the fade",
    )
    strokes = fixed_strokes()
    ratchet(
        "H3 lines drawn at a fixed width", strokes, MAX_FIXED_STROKES,
        "derive it from `Theme.sizing.borderWidth` or `borderWidthStrong`, which "
        "thicken at high contrast",
    )
    raw = raw_button_colours()
    ratchet(
        "H4 button colours built by hand", raw, MAX_RAW_BUTTON_COLOURS,
        "take them from `ButtonDefaults`, where the high-contrast edge is",
    )
    unread = unread_sizing()
    ratchet(
        "H5 `Sizing` tokens nothing reads", unread, MAX_UNREAD_SIZING,
        "a token the tier thickens and no component draws from is a promise the "
        "tier does not keep; wire it or delete it",
    )

    # Reduced motion.
    moving, stale_moving = ungated_movement()
    ratchet(
        "M movement that never asks about reduced motion", moving, MAX_UNGATED_MOVEMENT,
        "read `Theme.motion.reduceMotion` in the same function, or go through "
        "`Motion.enter`/`exit`/`scrollSpec`/`sizeSpec`; or exempt it by name with "
        "the reason the movement is small",
    )
    for key in stale_moving:
        problems.append(f"M: the exemption for {key} no longer matches anything; remove it")

    # Where the reader is.
    literals = literal_phrases()
    ratchet(
        "L1 English phrases assembled in a component", literals, MAX_LITERAL_PHRASES,
        "move the phrase to `Strings`, taking a `Quantity` where it counts something, "
        "and join lists with `Theme.formats.list`",
    )
    cases = case_changes()
    ratchet(
        "L2 case changed without a locale", cases, MAX_CASE_CHANGES,
        "use `Theme.formats.uppercase`, or exempt it by name if the string is not language",
    )
    numbers = unformatted_numbers()
    ratchet(
        "L3 numbers on screen as `toString()`", numbers, MAX_UNFORMATTED_NUMBERS,
        "format it with `Theme.formats.integer` or `decimal`",
    )
    words = calendar_words()
    ratchet(
        "L4 month and weekday names in English", words, MAX_CALENDAR_WORDS,
        "ask `Theme.formats.monthName` or `weekdayName`",
    )
    placed = absolute_placement()
    ratchet(
        "L5 absolute placement in a file that never reads the direction", placed,
        MAX_ABSOLUTE_PLACEMENT,
        "use `placeRelative`, or read `layoutDirection` and mirror",
    )

    # The pages.
    silent_contrast = pages_silent_on(SAYS_CONTRAST)
    ratchet(
        "D1 component pages silent on high contrast", silent_contrast, MAX_PAGES_SILENT_ON_CONTRAST,
        "say in the page's Accessibility section what the enhanced tier changes, "
        "or that it changes nothing and why",
    )
    silent_motion = pages_silent_on(SAYS_MOTION)
    ratchet(
        "D2 component pages silent on reduced motion", silent_motion, MAX_PAGES_SILENT_ON_MOTION,
        "say in the page's Accessibility section what reduced motion changes, "
        "or that nothing moves",
    )

    if problems:
        print(f"{len(problems)} problem(s):", file=sys.stderr)
        for problem in problems:
            print(f"  · {problem}", file=sys.stderr)
        return 1

    print(
        f"high contrast: {len(quiet)} quiet grounds without an edge, {len(faded)} faded inks, "
        f"{len(strokes)} fixed lines, {len(raw)} hand-built button colours, "
        f"{len(unread)} unread sizes; "
        f"reduced motion: {len(moving)} ungated movements, {len(MOVEMENT_ALLOWED)} exempt by name; "
        f"locale: {len(literals)} English phrases, {len(cases)} case changes, "
        f"{len(numbers)} unformatted numbers, {len(words)} calendar tables, "
        f"{len(placed)} absolute placements; "
        f"pages: {len(silent_contrast)} silent on high contrast, {len(silent_motion)} on reduced motion."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
