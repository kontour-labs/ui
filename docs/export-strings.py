#!/usr/bin/env python3
"""Writes the library's English as an Android `strings.xml`, for a translator.

    python3 docs/export-strings.py > values/kontour_strings.xml
    python3 docs/export-strings.py --check

Every field of `Strings`, and of the charts' `ChartStrings`, becomes a
resource named `kontour_<field>`: a plain word as a `<string>`, a phrase with
a number in it as `<plurals>` so a translation can give every form its
language has. Placeholders are `%1$s`, `%2$s`, in the order the field's own
parameters list them, which is the order `Strings.from` and `ChartStrings.from`
fill them in. Translate the file, drop it in an app's `values-de/`, and the two
`from`s with the Android lookup `localisation.md` gives read it back.

The charts' words are in the same file, under the same prefix, because an app
gives both `from`s the same lookup. They were left out, so a translator was
never sent them and a chart said its own words in English in every language.
`--check` fails if the two ever ask for the same key.

The plain words are read out of `Strings.kt` and `ChartStrings.kt` themselves.
The phrases are lambdas, so their English is written out below — and `--check`
fails if a field has been added to either without one, or one here names a
field that is gone.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path
from xml.sax.saxutils import escape

STRINGS = Path("ui/src/commonMain/kotlin/io/kontour/ui/theme/Strings.kt")
CHART_STRINGS = Path("ui-charts/src/commonMain/kotlin/io/kontour/ui/charts/ChartStrings.kt")
PREFIX = "kontour_"

# Phrases, by key: a plain template, or a dict of plural forms. The keys that
# are not fields are the ones `Strings.from` asks for besides.
PHRASES: dict[str, str | dict[str, str]] = {
    "pageNumber": {"one": "Page %1$s", "other": "Page %1$s"},
    "itemOfCount": {"one": "%1$s of %2$s", "other": "%1$s of %2$s"},
    "pageOfCount": {"one": "Page %1$s of %2$s", "other": "Page %1$s of %2$s"},
    "outOf": {"one": "%1$s out of %2$s", "other": "%1$s out of %2$s"},
    "stepOfCount": {"one": "Step %1$s of %2$s", "other": "Step %1$s of %2$s"},
    "stepOfCountInProgress": {"one": "Step %1$s of %2$s, in progress", "other": "Step %1$s of %2$s, in progress"},
    "newCount": {"one": "%1$s new", "other": "%1$s new"},
    "moreThan": {"one": "More than %1$s", "other": "More than %1$s"},
    "cappedCount": {"one": "%1$s+", "other": "%1$s+"},
    "moreCount": {"one": "+%1$s", "other": "+%1$s"},
    "selectedCount": {"one": "%1$s selected", "other": "%1$s selected"},
    "peopleCount": {"one": "%1$s person", "other": "%1$s people"},
    "tourPosition": {"one": "%1$s / %2$s", "other": "%1$s / %2$s"},
    "activityOnDay": {"one": "%1$s activity on %2$s", "other": "%1$s activities on %2$s"},
    "activityOnDayNone": "No activity on %1$s",
    "weekOf": "Week of %1$s",
    "moveTo": "Move to %1$s",
    "dragPickedUp": "Picked up %1$s",
    "dragPosition": "%1$s, %2$s",
    "dragDropped": "%1$s dropped in %2$s",
    "dragCancelled": "%1$s put back",
    "dragRefused": "%1$s can't take this",
    "fileCount": {"one": "%1$s file", "other": "%1$s files"},
    "fileBytes": {"one": "%1$s byte", "other": "%1$s bytes"},
    "fileSize": "%1$s %2$s",
    "removeFile": "Remove %1$s",
    "closeTab": "Close %1$s",
    "timeRange": "%1$s – %2$s",
    "eventCount": {"one": "%1$s event", "other": "%1$s events"},
    "moreEvents": {"one": "%1$s more event", "other": "%1$s more events"},
    "eventMoved": "%1$s moved to %2$s",
    "eventDeleted": "%1$s deleted",
    "endDayOffset": {"one": "+%1$s", "other": "+%1$s"},
    "endDaysLater": {"one": "%1$s the next day", "other": "%1$s, %2$s days later"},
    "untilTime": "Until %1$s",
    "labelledValue": "%1$s, %2$s",
    "sentenceSeparator": ". ",
    "colourFormatName_Hex": "Hex",
    "colourFormatName_Rgb": "RGB",
    "colourFormatName_Hsv": "HSV",
    "colourFormatName_Hsl": "HSL",
    **{f"colourChannel_{name}": letter for name, letter in [
        ("Red", "R"), ("Green", "G"), ("Blue", "B"), ("Hue", "H"), ("Saturation", "S"),
        ("Value", "V"), ("Lightness", "L"), ("Alpha", "A"),
    ]},
    **{f"colourChannelName_{name}": word for name, word in [
        ("Red", "Red"), ("Green", "Green"), ("Blue", "Blue"), ("Hue", "Hue"), ("Saturation", "Saturation"),
        ("Value", "Brightness"), ("Lightness", "Lightness"), ("Alpha", "Opacity"),
    ]},
}

# The charts' phrases, by key, as `ChartStrings.from` asks for them.
CHART_PHRASES: dict[str, str | dict[str, str]] = {
    "seriesCount": {"one": "%1$s series", "other": "%1$s series"},
    "axisRange": "%1$s, %2$s to %3$s",
    "seriesSummary": "%1$s, %2$s to %3$s, %4$s",
    "valuesSummary": "%1$s to %2$s, %3$s",
    "seriesValue": "%1$s, %2$s",
    "atPosition": "%1$s: %2$s",
    "valueRange": "%1$s to %2$s",
    "sectorShare": "%1$s, %2$s",
    "boxSummary": "median %3$s, quartiles %2$s to %4$s, range %1$s to %5$s",
    "boxOutliers": "outliers %1$s",
    "seriesColumn": "%1$s, %2$s",
    "selectedRange": "Selected %1$s to %2$s",
    "selectedBox": "Selected %1$s to %2$s, %3$s to %4$s",
}

# Fields whose phrase lives under other keys above.
FIELD_KEYS = {
    "sentences": ["sentenceSeparator"],
    "colourFormatName": [k for k in PHRASES if k.startswith("colourFormatName_")],
    "colourChannel": [k for k in PHRASES if k.startswith("colourChannel_")],
    "colourChannelName": [k for k in PHRASES if k.startswith("colourChannelName_")],
    "stepOfCount": ["stepOfCount", "stepOfCountInProgress"],
    "activityOnDay": ["activityOnDay", "activityOnDayNone"],
}


def fields(source: Path, name: str) -> list[tuple[str, str, str]]:
    """(name, type, default) for every field of the data class [name] in [source]."""
    text = source.read_text()
    body = text[text.index(f"data class {name}("):text.index("companion object")]
    found = []
    for match in re.finditer(r'^    val (\w+): (.+?) =\s*(.*)$', body, re.M):
        found.append((match.group(1), match.group(2), match.group(3)))
    return found


def android_escape(value: str) -> str:
    """Android's own escaping on top of XML's: apostrophes, quotes, and a leading @ or ?."""
    value = escape(value).replace("'", "\\'").replace('"', '\\"')
    if value[:1] in ("@", "?"):
        value = "\\" + value
    return value


def resources(source: Path, name: str, phrases: dict, field_keys: dict) -> tuple[list[str], list[str], list[str]]:
    """The resource lines for the data class [name], the keys they are under, and what is wrong with them."""
    problems = []
    lines = []
    keys_written = []
    covered = set()
    for field, kind, default in fields(source, name):
        if kind == "String":
            literal = re.fullmatch(r'"((?:[^"\\]|\\.)*)",', default.strip())
            if not literal:
                problems.append(f"{name}.{field}: its default is not a plain string literal")
                continue
            lines.append(f'    <string name="{PREFIX}{field}">{android_escape(literal.group(1))}</string>')
            keys_written.append(field)
            continue
        keys = field_keys.get(field, [field])
        for key in keys:
            if key not in phrases:
                problems.append(f"{name}.{field}: no English for key `{key}` in export-strings.py")
                continue
            covered.add(key)
    for key, value in phrases.items():
        if key not in covered:
            problems.append(f"`{key}` is exported but no field of {name} asks for it")
            continue
        keys_written.append(key)
        if isinstance(value, dict):
            lines.append(f'    <plurals name="{PREFIX}{key}">')
            for quantity, form in value.items():
                lines.append(f'        <item quantity="{quantity}">{android_escape(form)}</item>')
            lines.append("    </plurals>")
        else:
            lines.append(f'    <string name="{PREFIX}{key}">{android_escape(value)}</string>')
    return lines, keys_written, problems


def build() -> tuple[str, list[str]]:
    library, library_keys, problems = resources(STRINGS, "Strings", PHRASES, FIELD_KEYS)
    charts, chart_keys, chart_problems = resources(CHART_STRINGS, "ChartStrings", CHART_PHRASES, {})
    problems += chart_problems
    # One lookup answers both, so one key cannot mean two things.
    for key in sorted(set(library_keys) & set(chart_keys)):
        problems.append(f"`{key}` is asked for by both Strings and ChartStrings")
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- Generated by docs/export-strings.py. -->", "<resources>"]
    lines += library
    lines.append("    <!-- The charts' own words: ChartStrings, from io.kontour:ui-charts. -->")
    lines += charts
    lines.append("</resources>")
    return "\n".join(lines) + "\n", problems


def main() -> int:
    xml, problems = build()
    if problems:
        print("export-strings: " + "; ".join(problems), file=sys.stderr)
        return 1
    if "--check" in sys.argv:
        print(f"export-strings: every field of Strings and ChartStrings has its English "
              f"({xml.count('<string ')} strings, {xml.count('<plurals ')} plurals).")
        return 0
    sys.stdout.write(xml)
    return 0


if __name__ == "__main__":
    sys.exit(main())
