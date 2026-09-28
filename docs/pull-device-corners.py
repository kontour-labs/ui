#!/usr/bin/env python3
"""The per-device corner table, and where it comes from.

Android reports a window's rounded-corner **radius** from API 31 and reports
nothing at all about the corner's **shape**, at any API level worth counting. So
the library needs two things this script produces:

- the radii for devices the platform will not answer for — everything below API
  31, and any corner an OEM declined to declare;
- a device registry to key a curated smoothing table on, so a judgement about a
  corner's curve is attached to a *device generation* rather than to a
  manufacturer's whole catalogue.

### Three sources, and they answer different questions

**Google's `supported_devices.csv` says what exists.** 53,958 rows, 37,082
distinct codenames, current within days of a launch — every Android device that
may install from Play, as `(brand, marketing name, codename, model)`. No ROM
project can be this current: the round that added this one was prompted by
*"I'm on a Pixel 11 Pro XL, so you haven't catered for it"*, and `kodiak` will
not appear in a LineageOS tree for a year. It is the registry the smoothing
table is checked against, and the 59 Pixels in it are kept in the JSON so a
reader can see where a family list stops.

**LineageOS says what the radii are**, for the devices that declare them. Two
raw files and no GitHub API — which is just as well, since the API is blocked
from some sandboxes and `raw.githubusercontent.com` is not:

- `LineageOS/hudson`'s `updater/devices.json` — **one request** for every
  supported device: codename, OEM, marketing name.
- `LineageOS/lineage_wiki`'s `_data/devices/<codename>.yml` — one small file per
  device carrying `vendor`, `name`, `release`, `tree` and `screen`.

The device trees themselves are the disappointing half, and the number is
recorded here so nobody re-derives it hopefully: **about one device in sixteen**
declares `rounded_corner_radius` in an overlay, and those that do are mostly
2017-2019 hardware. That is not a failure — it is exactly the population the
platform cannot answer for, so the scraped radii and `RoundedCorner` cover
disjoint sets. Nothing anywhere declares a corner *shape*: zero trees set
`config_mainDisplayShape`, and the `@drawable/rounded_corner_top` vector that
would carry a real curve ships inside a compiled APK when it ships at all.

**AOSP says nothing this needs, and that was checked rather than assumed.**
`android.googlesource.com` is reachable and its `device/google/*` trees are
readable, but the Pixel trees carry `rounded_corner_content_padding` and not the
radius, and nothing is published for the Pixel 10 or 11 at all. It is not a
source.

### Three modes, because there are two artefacts

    python3 docs/pull-device-corners.py            # compare; non-zero on drift
    python3 docs/pull-device-corners.py --write    # regenerate the Kotlin
    python3 docs/pull-device-corners.py --pull     # re-fetch (needs network)

`docs/device-corners.json` is the checked-in intermediate and the only thing that
touches the network. The Kotlin is generated from it, and the default mode
compares the two — so CI gates the consistency with no token, no rate limit and
no flake, and a contributor with no network can still regenerate. It is the same
split `sync-samples.py` uses and for the same reason: a step that regenerates its
own expectations every run checks nothing.
"""

from __future__ import annotations

import csv
import io
import json
import math
import re
import sys
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

RAW = "https://raw.githubusercontent.com"
DEVICES = f"{RAW}/LineageOS/hudson/main/updater/devices.json"
WIKI = f"{RAW}/LineageOS/lineage_wiki/main/_data/devices"

# Google's own list of every device that may install from Play: 53,958 rows,
# 37,082 distinct codenames, and current within days of a launch. LineageOS is
# the source for *radii* and this is the source for **what exists** — the two
# answer different questions and the second one is why `kodiak` (Pixel 11 Pro XL)
# can be checked at all, since no ROM project will carry it for a year yet.
PLAY_DEVICES = "https://storage.googleapis.com/play_public/supported_devices.csv"

SMOOTHING = Path("ui/src/commonMain/kotlin/io/kontour/ui/platform/DeviceCurves.kt")

# The `codenames = listOf(…)` blocks in the smoothing table, and the strings
# inside one. A deliberately dumb pair of regexes over a deliberately dumb shape:
# the alternative is teaching Python to read Kotlin, and this gate's whole value
# is being boring enough to trust. Anchored on `codenames =` so the manufacturer
# keys next door — which are not codenames and are not in Google's registry — are
# not swept up with them.
CODENAME_BLOCK = re.compile(r"codenames\s*=\s*listOf\((.*?)\)", re.S)
CODENAME = re.compile(r'"([a-z0-9_]{3,})"')

DATA = Path("docs/device-corners.json")
TABLE = Path(
    "ui/src/androidMain/kotlin/io/kontour/ui/platform/DeviceCornerTable.android.kt"
)

# Where a device tree hides its overlay, newest branch first. Tried in order and
# the first hit wins; a device with none is a device with no radius, which is the
# common case.
OVERLAYS = (
    "overlay/frameworks/base/core/res/res/values/dimens.xml",
    "overlay-lineage/frameworks/base/core/res/res/values/dimens.xml",
    "{codename}/overlay/frameworks/base/core/res/res/values/dimens.xml",
)

BRANCHES = ("lineage-22.2", "lineage-22.1", "lineage-21.0", "lineage-20.0")

DIMEN = re.compile(
    r'<dimen\s+name="(rounded_corner_radius(?:_top|_bottom)?)"\s*>\s*'
    r"([0-9.]+)\s*(px|dp|dip)\s*</dimen>"
)

# A radius outside this is a unit-conversion bug rather than a phone. Emitting
# one would be worse than emitting nothing: `atLeast` *raises* a corner, so a
# wrong radius pushes a sheet's corner past the bezel it is meant to sit inside.
MIN_DP, MAX_DP = 1, 64

THREADS = 24
TIMEOUT = 20


def fetch(url: str) -> str | None:
    try:
        with urllib.request.urlopen(url, timeout=TIMEOUT) as response:
            return response.read().decode("utf-8", "replace")
    except (urllib.error.URLError, urllib.error.HTTPError, OSError):
        return None


def play_registry(text: str) -> dict[str, tuple[str, str]]:
    """Codename to (brand, marketing name), from Google's CSV.

    UTF-16 with a BOM, four columns, and one row per *model* — so a codename
    appears many times and the first spelling of each is kept.
    """
    rows = csv.reader(io.StringIO(text, newline=""))
    out: dict[str, tuple[str, str]] = {}
    for index, row in enumerate(rows):
        if index == 0 or len(row) < 3:
            continue
        codename = row[2].strip()
        if codename and codename not in out:
            out[codename] = (row[0].strip(), row[1].strip())
    return out


def named_codenames() -> list[str]:
    """Every codename the smoothing table names, in file order."""
    if not SMOOTHING.exists():
        return []
    text = SMOOTHING.read_text()
    return [
        codename
        for block in CODENAME_BLOCK.findall(text)
        for codename in CODENAME.findall(block)
    ]


def yaml_ish(text: str) -> dict[str, str]:
    """The half-dozen scalar keys this needs, without a YAML dependency.

    The wiki's device files are flat enough for it: every key this reads is a
    top-level scalar or a one-line inline mapping, and anything it cannot parse
    comes back missing rather than wrong.
    """
    out: dict[str, str] = {}
    for line in text.splitlines():
        match = re.match(r"^([a-z_]+):\s*(.+?)\s*$", line)
        if match:
            out[match.group(1)] = match.group(2).strip("'\"")
    return out


def density_of(screen: str) -> float | None:
    """Pixels per dp, worked out from the wiki's resolution and diagonal.

    A `px` dimen is a number of real pixels and the library wants dp, so the
    conversion needs a density the tree does not state. This derives it from the
    physical screen — dpi from the pixel diagonal over the inch diagonal, then
    the nearest Android bucket — and it is an approximation, which is why
    anything it produces still has to land inside [MIN_DP, MAX_DP] to be kept.
    """
    resolution = re.search(r"resolution:\s*'?(\d+)\s*x\s*(\d+)", screen)
    size = re.search(r"size:\s*([0-9.]+)", screen)
    if not resolution or not size:
        return None
    width, height = int(resolution.group(1)), int(resolution.group(2))
    inches = float(size.group(1))
    if inches <= 0:
        return None
    dpi = math.hypot(width, height) / inches
    # The buckets Android actually ships, so a 403dpi panel is 2.5x rather than
    # 2.52x — a device is configured at a bucket, not at its true dpi.
    buckets = (1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0)
    return min(buckets, key=lambda bucket: abs(bucket - dpi / 160.0))


def pull() -> dict:
    listing = fetch(DEVICES)
    if listing is None:
        sys.exit(
            "--pull needs raw.githubusercontent.com and could not reach it. The "
            f"checked-in {DATA} is what the table is built from; --write and the "
            "default compare mode both work offline."
        )
    devices = json.loads(listing)

    def one(entry: dict) -> tuple[str, dict] | None:
        codename = entry["model"]
        text = fetch(f"{WIKI}/{codename}.yml")
        if text is None:
            return None
        wiki = yaml_ish(text)
        record = {
            "name": entry.get("name") or wiki.get("name", ""),
            "vendor": (wiki.get("vendor_short") or entry.get("oem", "")).lower(),
            "released": wiki.get("release", "")[:4],
            "tree": wiki.get("tree", ""),
            "screen": wiki.get("screen", ""),
        }
        return codename, record

    with ThreadPoolExecutor(THREADS) as pool:
        found = [row for row in pool.map(one, devices) if row is not None]
    registry = dict(found)

    def radii(item: tuple[str, dict]) -> tuple[str, dict[str, float]] | None:
        codename, record = item
        tree = record["tree"]
        if not tree:
            return None
        for branch in BRANCHES:
            for path in OVERLAYS:
                url = f"{RAW}/LineageOS/{tree}/{branch}/{path.format(codename=codename)}"
                text = fetch(url)
                if not text or "rounded_corner_radius" not in text:
                    continue
                found: dict[str, float] = {}
                density = density_of(record["screen"]) or 0.0
                for name, value, unit in DIMEN.findall(text):
                    raw = float(value)
                    if unit == "px":
                        if density <= 0:
                            continue
                        raw /= density
                    dp = round(raw)
                    if MIN_DP <= dp <= MAX_DP:
                        found[name] = dp
                if found:
                    return codename, found
        return None

    with ThreadPoolExecutor(THREADS) as pool:
        measured = [row for row in pool.map(radii, registry.items()) if row is not None]

    for codename, found in measured:
        top = found.get("rounded_corner_radius_top") or found.get("rounded_corner_radius")
        bottom = (
            found.get("rounded_corner_radius_bottom")
            or found.get("rounded_corner_radius")
            or top
        )
        if top:
            registry[codename]["top"] = int(top)
            registry[codename]["bottom"] = int(bottom or top)

    # Google's registry, for what exists rather than for what has a radius.
    # UTF-16 with a BOM, which `fetch` cannot know about — every other source
    # here is UTF-8. Read as bytes and decoded once, rather than teaching the
    # shared helper about one file's encoding.
    play = {}
    try:
        with urllib.request.urlopen(PLAY_DEVICES, timeout=TIMEOUT) as response:
            play = play_registry(response.read().decode("utf-16", "replace"))
    except (urllib.error.URLError, urllib.error.HTTPError, OSError):
        pass
    named = named_codenames()
    missing = [codename for codename in named if codename not in play]

    # Every Pixel, kept in full. It is a hundred rows against 37,082, it is the
    # family the smoothing table has the most to say about, and it is what makes
    # "the list stopped at the Pixel 9" visible to a reader of the JSON rather
    # than to a reader of a phone.
    pixels = {
        codename: name
        for codename, (brand, name) in sorted(play.items())
        if brand == "Google" and name.startswith("Pixel")
    }

    print(
        f"{len(devices)} devices listed, {len(registry)} with a wiki entry, "
        f"{len(measured)} declaring a corner radius; Google lists {len(play)} "
        f"codenames, {len(pixels)} of them Pixels"
    )
    if missing:
        print(f"  not in Google's registry: {', '.join(missing)}")
    return {
        "source": "LineageOS/hudson and LineageOS/lineage_wiki for radii; "
        "Google's play_public supported_devices.csv for the registry",
        "devices": dict(sorted(registry.items())),
        "pixels": pixels,
        # The codenames the smoothing table names, each one seen in Google's own
        # registry at the moment of the pull. The offline gate checks the table
        # against this, so adding a codename means running `--pull` and having it
        # blessed rather than spelling it hopefully.
        "verified_codenames": sorted(set(named) - set(missing)),
    }


def packed(data: dict) -> str:
    """`codename|top|bottom` records, `;`-separated, sorted.

    A string rather than a `mapOf`, because a map of this many entries is that
    many objects built in a static initialiser on a class nothing may ever touch:
    on API 31 and up with a cooperative device, the platform answers and this is
    never parsed at all.
    """
    rows = [
        f"{codename}|{record['top']}|{record['bottom']}"
        for codename, record in sorted(data["devices"].items())
        if "top" in record
    ]
    return ";".join(rows)


def render(data: dict) -> str:
    table = packed(data)
    rows = table.count(";") + 1 if table else 0
    return f'''package io.kontour.ui.platform

// GENERATED by docs/pull-device-corners.py — do not edit by hand.
//
//   python3 docs/pull-device-corners.py --pull    # refresh docs/device-corners.json
//   python3 docs/pull-device-corners.py --write   # regenerate this file
//
// {rows} devices, from LineageOS device trees. Every one of them is a device the
// platform cannot be asked: `RoundedCorner` arrived in API 31 and these overlays
// are overwhelmingly older hardware, so this table and the platform cover
// disjoint sets rather than disagreeing about the same one.

/** `codename|topDp|bottomDp` records, `;`-separated and sorted by codename. */
internal const val DeviceRadiusTable: String =
    "{table}"
'''


def main() -> int:
    mode = sys.argv[1] if len(sys.argv) > 1 else ""

    if mode == "--pull":
        DATA.write_text(json.dumps(pull(), indent=2, sort_keys=True) + "\n")
        print(f"wrote {DATA}; now run --write")
        return 0

    if not DATA.exists():
        print(f"{DATA} is missing — run --pull first", file=sys.stderr)
        return 1
    data = json.loads(DATA.read_text())
    wanted = render(data)

    # The checks that stop a bad pull from reaching the library. A ratchet on the
    # table's size, and a range on every radius: `atLeast` raises a corner, so a
    # wrong number pushes a shape *past* the bezel it is meant to sit inside.
    table = packed(data)
    seen: set[str] = set()
    for row in filter(None, table.split(";")):
        codename, top, bottom = row.split("|")
        if codename in seen:
            print(f"{codename} appears twice in the table", file=sys.stderr)
            return 1
        seen.add(codename)
        for value in (int(top), int(bottom)):
            if not MIN_DP <= value <= MAX_DP:
                print(f"{codename}: {value}dp is not a corner radius", file=sys.stderr)
                return 1

    # The smoothing table names devices; every one of them has to be a device.
    #
    # This is the gate the round after the first one earnt. The table listed
    # current families and defaulted everything else, which was wrong within a
    # fortnight — reported as *"I'm on a Pixel 11 Pro XL, so you haven't catered
    # for it"* — and nothing could have caught it, because a codename that
    # matches nothing looks exactly like a codename that matches something.
    verified = set(data.get("verified_codenames", []))
    unknown = [name for name in named_codenames() if name not in verified]
    if unknown:
        print(
            f"{SMOOTHING} names {len(unknown)} codename(s) that no pull has seen "
            f"in Google's device registry: {', '.join(unknown)} — run "
            "`python3 docs/pull-device-corners.py --pull` to check them against it",
            file=sys.stderr,
        )
        return 1

    if mode == "--write":
        TABLE.write_text(wanted)
        print(f"wrote {TABLE}: {len(seen)} devices, {len(table)} bytes packed")
        return 0

    if not TABLE.exists() or TABLE.read_text() != wanted:
        print(
            f"{TABLE} does not match {DATA} — run "
            "`python3 docs/pull-device-corners.py --write`",
            file=sys.stderr,
        )
        return 1
    print(
        f"device corner table: {len(seen)} devices, {len(table)} bytes packed, "
        f"in step; {len(verified)} smoothing codenames verified against Google's "
        f"registry of {len(data.get('pixels', {}))} Pixels"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
