#!/usr/bin/env python3
"""Writes the library's English as an Android `strings.xml`, for a translator.

    python3 docs/export-strings.py > values/kontour_strings.xml
    python3 docs/export-strings.py --check

Every field of `Strings` becomes a resource named `kontour_<field>`: a plain
word as a `<string>`, a phrase with a number in it as `<plurals>` so a
translation can give every form its language has. Placeholders are `%1$s`,
`%2$s`, in the order the field's own parameters list them, which is the order
`Strings.from` fills them in. Translate the file, drop it in an app's
`values-de/`, and `Strings.from` with the Android lookup `localisation.md`
gives reads it back.

The plain words are read out of `Strings.kt` itself. The phrases are lambdas,
so their English is written out below — and `--check` fails if a field has
been added to `Strings` without one, or one here names a field that is gone.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path
from xml.sax.saxutils import escape

STRINGS = Path("ui/src/commonMain/kotlin/io/kontour/ui/theme/Strings.kt")
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
    "timeRange": "%1$s – %2$s",
    "eventCount": {"one": "%1$s event", "other": "%1$s events"},
    "moreEvents": {"one": "%1$s more event", "other": "%1$s more events"},
    "eventMoved": "%1$s moved to %2$s",
    "eventDeleted": "%1$s deleted",
    "endDayOffset": {"one": "+%1$s", "other": "+%1$s"},
    "endDaysLater": {"one": "%1$s the next day", "other": "%1$s, %2$s days later"},
    "untilTime": "Until %1$s",
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

# Fields whose phrase lives under other keys above.
FIELD_KEYS = {
    "sentences": ["sentenceSeparator"],
    "colourFormatName": [k for k in PHRASES if k.startswith("colourFormatName_")],
    "colourChannel": [k for k in PHRASES if k.startswith("colourChannel_")],
    "colourChannelName": [k for k in PHRASES if k.startswith("colourChannelName_")],
    "stepOfCount": ["stepOfCount", "stepOfCountInProgress"],
    "activityOnDay": ["activityOnDay", "activityOnDayNone"],
}


def fields() -> list[tuple[str, str, str]]:
    """(name, type, default) for every field of `Strings`."""
    text = STRINGS.read_text()
    body = text[text.index("data class Strings("):text.index("companion object")]
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


def build() -> tuple[str, list[str]]:
    problems = []
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- Generated by docs/export-strings.py. -->", "<resources>"]
    covered = set()
    for name, kind, default in fields():
        if kind == "String":
            literal = re.fullmatch(r'"((?:[^"\\]|\\.)*)",', default.strip())
            if not literal:
                problems.append(f"{name}: its default is not a plain string literal")
                continue
            lines.append(f'    <string name="{PREFIX}{name}">{android_escape(literal.group(1))}</string>')
            continue
        keys = FIELD_KEYS.get(name, [name])
        for key in keys:
            if key not in PHRASES:
                problems.append(f"{name}: no English for key `{key}` in export-strings.py")
                continue
            covered.add(key)
    for key, value in PHRASES.items():
        if key not in covered:
            problems.append(f"`{key}` is exported but no field of Strings asks for it")
            continue
        if isinstance(value, dict):
            lines.append(f'    <plurals name="{PREFIX}{key}">')
            for quantity, form in value.items():
                lines.append(f'        <item quantity="{quantity}">{android_escape(form)}</item>')
            lines.append("    </plurals>")
        else:
            lines.append(f'    <string name="{PREFIX}{key}">{android_escape(value)}</string>')
    lines.append("</resources>")
    return "\n".join(lines) + "\n", problems


def main() -> int:
    xml, problems = build()
    if problems:
        print("export-strings: " + "; ".join(problems), file=sys.stderr)
        return 1
    if "--check" in sys.argv:
        print(f"export-strings: every field of Strings has its English ({xml.count('<string ')} strings, "
              f"{xml.count('<plurals ')} plurals).")
        return 0
    sys.stdout.write(xml)
    return 0


if __name__ == "__main__":
    sys.exit(main())
