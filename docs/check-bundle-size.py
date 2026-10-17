#!/usr/bin/env python3
"""What a reader downloads, with ceilings that only go down.

The site's load time had no budget, no gate and no test. `docs/building/testing.md`
says the suite is "counted, not timed", which is the right call for a component —
a recomposition count is the same number on a JVM and on a phone — and it left the
one thing a reader actually complains about unmeasured. Nothing anywhere asserted
on the size of the bundle.

This is the cheap half of that. `docs/measure-web.mjs` opens a browser and reports
what the load *costs*; this reports what it *weighs*, needs no browser, and runs in
under a second, so it can sit in CI on every change.

Sizes are gzip, because that is what crosses the wire — the host compresses text
and Wasm and leaves fonts alone, and the difference between the two numbers is a
factor of three. Cloudflare sends Brotli where the browser takes it, which is a
little smaller, so these are a ceiling on the real transfer rather than it. Raw sizes are printed alongside, never asserted.

Skia gets its own ceiling. `skiko.wasm` is the Compose renderer, it is the same
bytes whatever this library contains, and at roughly two thirds of the payload it
would swamp any total that mixed it with ours: a 10% growth in the application
binary is invisible inside a number Skia dominates. Splitting them is what makes
this a ratchet on our own code.

    python3 docs/check-bundle-size.py [--dist DIR] [--update]
    python3 docs/check-bundle-size.py --probes [--update]

`--probes` weighs the apps in `probes/` instead: one app each, so the figure is
what an app on the library ships rather than what the site does. See
`probes/README.md` for what each one answers.

`--update` rewrites the ceilings in this file to what was just measured. Use it
when a change is *meant* to move a number, in the same commit, so the diff shows
the cost.
"""

import argparse
import gzip
import pathlib
import re
import sys

# Ceilings in gzip bytes, with roughly 3% of headroom over the measured figure so
# that a compressor version does not fail the build. They only go down.
#
# Measured 2026-09-09, Kotlin 2.4.10 / Compose 1.12.0-rc01, `--update`:
#
# `fonts` rose from 290,000 for two cuts of JetBrains Mono, 76,704 raw bytes:
# the library already hardcoded `FontFamily.Monospace` in `Kbd` and the site did
# it six more times, so this closes a cross-platform inconsistency rather than
# adding a feature. `app` fell 50,000 in the same pass, from deleting 3,870
# lines of hand-written gallery panels — a ratchet is allowed to tighten, and
# this is the run where it did.
#
# Raised 2026-09-28 for 0.2.0, from CI's own measurement on `main` (57fec0e):
# `app` 2,826,535 against 2,110,000, which is about +780,000 (+38%) on what it
# measured on 09-09. The other four did not move, and are what `--update` would
# write for them. The site builds here only in CI, so these are that run's
# figures put through `--update`'s own rule, by hand.
#
# Not a dependency that arrived unasked. Navigation 3 and `navigationevent` came
# in on purpose, for back, and `NavDisplay` is 38 KB of 10.5 MB of unoptimised
# code. Read by function name in that code, the growth is this library's and
# this site's own:
#
#   io.kontour.ui.components   2.05 MB  display 0.56, selection 0.41, date and
#                                       time 0.36, lists 0.26: Gauge, Knob,
#                                       Meter, Table, the four timelines and
#                                       ActivityCalendar among them
#   io.kontour.ui.demo         0.79 MB  a demo on every one of 118 pages
#   io.kontour.ui.docs         0.77 MB  the site
#   io.kontour.ui.catalog      0.43 MB  the gallery's shell, and its Back, frames
#                                       and scroll-feel pages
#   overlay, nav, sheet        0.90 MB  back handling, NavScope, in-place menus
#   io.kontour.ui.contract     0.30 MB  the registry: specimens for pages
#                                       without a demo, which none now lack
#   io.kontour.ui.locale       0.08 MB  formats, plurals, pseudo-locales
#
# The registry is the one line here the site could shed: it is the fallback in
# `Site.kt`'s `Specimens` for a page with no hand-written demo, and every
# component page has one. That is a tightening worth having, and short of the
# 38% by a long way.
#
# Raised 2026-10-01 for 0.3.0, again from CI's own measurement on `main`
# (9b78bf3), where `app` was 2,928,692 against 2,920,000 and failed the deploy.
# 0.2.2 measured 2,845,409 (v0.2.2, 72452a9), so the release added 83,283 gzip
# bytes, +2.9% (365,826 raw, +3.3%): the 3% of headroom, spent by one release.
# The other four did not move by a byte, and `--update` would write what they
# already are; `total` is not one of its groups and keeps 6,810,000, now the
# tighter of the two at 99,862 to spare. Put through `--update`'s rule by hand,
# as for 0.2.0, because the site builds only in CI.
#
# No dependency moved. Read by source, as the site cannot be built here to read
# the binary by function name, the growth is 0.3.0's own features — net lines
# since 0.2.2:
#
#   nav        +973  NavBar's shapes, cell widths and draggable marker; the
#                    Large TopBar's collapse into the bar, subtitle and all
#   sheet      +528  pinned parts, and the sheet's header band and inset
#   text       +412  WheelSelect, new
#   selection  +353  RangeSlider's finger on each thumb; SelectionRow takes
#                    SettingRow's place
#   motion,
#   interaction +451 Android back relaying the system gesture at its speed;
#                    Modifier.pressable
#   lists,
#   datetime,
#   overlay    +558  DisclosureListItem, new; the wheel's multi-flick drive; the
#                    backdrop drawn from every open overlay
#   the site  +1,070 lines of pages, compiled into the binary: the changelog,
#                    the migrating guide, and the new components' pages
# Raised 2026-10-03 for 0.4.0, from a measurement here rather than CI's: no CI
# run had built the site since 0.3.0. The site still cannot be *bundled* here —
# webpack's tooling fetches from codeload.github.com, which this environment's
# network refuses — but the binary this counts as `app` is the compiler's, and
# `:ui-docs:compileProductionExecutableKotlinWasmJsOptimize` builds it without
# webpack. Checked against CI first: v0.3.0 built that way measures 2,928,794,
# and CI measured 2,928,692 (9b78bf3), 102 bytes apart.
#
# `app` is 3,184,951: +256,259 gzip on 0.3.0, +8.7%. No dependency or toolchain
# moved — Kotlin, Compose and Skia are 0.3.0's — so `skiko`, `fonts` and `other`
# are what they were, and `js`, the loader's glue, has its old headroom. Read by
# source, net lines since v0.3.0:
#
#   the gallery  +4,736  the Apps page: Transit and Messages, two small apps
#                        built from the library, compiled into the site
#   components   +1,999  IndexScroller and its alphabets, DateTimePicker, the
#                        circular step progress, chips as tags, and WheelSelect
#                        opening to the side with room
#   nav, sheet,
#   overlay,
#   motion       +1,265  HeroTopBar and the bars' fade under, pageBack, and the
#                        navigation suite's scene strategy
#   the site     +947    of which the icon browser is 34,009 gzip bytes of the
#                        total: its page, its search and the path parser that
#                        draws icons from data rather than from six thousand
#                        compiled properties
#
# `app` through `--update`'s rule by hand, and `total`, which is not one of its
# groups, by the same rule over 0.3.0's total (6,710,138) plus `app`'s growth.
#
# Raised 2026-10-06 for 0.4.2, from CI's own measurement on `main` after #32
# (7227c14), the first run to build the site since the release's work merged.
# `app` is 3,367,130: +182,179 gzip on 0.4.0's 3,184,951, +5.7%. Kotlin, Compose
# and Skia are 0.4.0's — the one new dependency, activity-compose, is Android's
# alone — so `skiko`, `js`, `fonts` and `other` measured inside their ceilings
# and `--update`'s rule leaves them where they were. What grew is the library
# and the pages that show it: drag and drop (sources, targets, drop lists,
# autoscroll, keyboard moves, drops from outside the app), FileField and the
# web's file picker, DropZone, the Board app in the gallery, SecretField,
# DateField, LoadingState and StateSwitcher, HeroImages, a select's "Other",
# and the changelog and guides that describe them.
#
# `app` and `total` through `--update`'s rule by hand, over CI's figures.
#
# Raised 2026-10-08 for 0.4.3, from a measurement here, built as for 0.4.0 with
# `:ui-docs:compileProductionExecutableKotlinWasmJsOptimize`: `app` is
# 3,555,536, +188,406 gzip on 0.4.2's 3,367,130, +5.6%, and over its ceiling by
# 85,536. Compose moved from 1.12.0-rc01 to 1.12.1; the `skiko.wasm` it unpacks
# measures 3,324,678, inside its ceiling, so `skiko` stays. Read by source, net
# lines since v0.4.2:
#
#   calendar   +5,553  EventCalendar, new: day, week, month and year views,
#                      drag to create, move and resize, menus, keyboard
#   lists      +768    the scrollbar that measures its rows, swipe rows that
#                      confirm and close when something else is used
#   date and
#   time       +657    FieldPicker, the time picker's Now, the range picker's
#                      edge arrows and dates' colours and annotations
#   overlay    +663    coachmark tours
#   sheet, nav +433    pinned parts; top bars that follow the finger
#   the site   +1,032  lines of pages: the changelog, the event calendar's page
#
# `app` through `--update`'s rule by hand. `total`, which cannot be measured
# here — the loader's `js` needs the webpack step this environment cannot
# fetch — by the same rule over 0.4.2's total (7,148,576, as 0.4.2 derived it)
# plus `app`'s growth. It holds even with `js`, `fonts` and `other` at their
# ceilings: 7,370,214.
#
# Raised 2026-10-16 for 0.4.5, from CI's own measurement on
# `claude/quirky-fermi-ak6fnd` (4ea762bb), the first run to build the site with
# 0.4.4's and 0.4.5's work together. `app` is 3,779,287: +223,751 gzip on
# 0.4.3's 3,555,536, +6.3%, and over its ceiling by 109,287; `total` is
# 7,563,532, over by 3,532. Kotlin, Compose and Skia are 0.4.3's, so `skiko`
# (3,324,678), `js` (103,304), `fonts` (352,932) and `other` (3,331) measured
# inside their ceilings and stay. Read by source, lines added and removed in
# `:ui` since v0.4.4:
#
#   components +3,947 −679   SplitListItem, swipe actions that grow the full
#                            swipe's action, the list DSL's `split`
#   overlay    +1,751 −156
#   nav        +1,281 −441
#   list, date
#   and time,
#   calendar,
#   text       +3,620 −585
#   sheet      +365   −81
#   the site   +1,712 −251   lines of pages: the patterns guide, the changelog
#                            and migrating, the public install pages
#
# plus +509 −63 in the gallery, the drag-and-drop demo among them.
#
# `app` and `total` through `--update`'s rule by hand, over CI's figures.

CEILINGS = {
    "skiko": 3_430_000,
    "app": 3_900_000,
    "js": 110_000,
    "fonts": 370_000,
    "other": 10_000,
    "total": 7_800_000,
}

# The whole *distribution*, in files — `dist.rglob("*")`, which is every file the
# build emits and not the subset a browser fetches to show the first frame. The
# docstring here used to say "first load", which was wrong in a way that
# mattered: fonts arrive lazily through Compose Resources, so the seven of them
# below are half this number and none of them is on the critical path. What the
# ceiling actually bounds is how many separate things a reader's browser could
# be asked for, which is still worth bounding — but a change that adds a lazily
# fetched resource is spending against a different budget than it looks.
MAX_FILES = 14

# The probes in `probes/`, in gzip bytes of each one's own `.wasm`, under the
# same rule as `CEILINGS`. Each is listed with the probe it is read against,
# because the difference is the answer (`probes/README.md`) and the absolute
# figure mostly Compose.
#
# A probe with no ceiling fails: the first run prints the figures, and a
# ceiling is set from them by `--probes --update` with a note here saying where
# they came from.
#
# Set 2026-10-16 for 0.5.0 from CI's first measurement, on
# `claude/trusting-dijkstra-w1a6rz` (a7500415): `empty` 570,451; `theme`
# 728,179, +157,728 on it, which is what `KontourTheme` costs every app;
# `button` 803,990, +75,811 on `theme`, which is one component and everything
# it uses. `icon` and `icons` were 684,759 and 692,540, about 159 gzip bytes an
# icon, measured without a `Text`, so `icon` came out *under* `theme` by the
# text stack it left out. Both now draw the same `Text` as `theme`, and their
# ceilings here are `theme`'s plus the icons, estimated, until a run measures
# them. Each through `--update`'s rule, to the thousand.
#
# What `theme` is made of, from `probe-attribution.py` on the same run, before
# Binaryen: Compose's runtime, node and collection code first, then
# `io.kontour.ui.theme` at 100,713, `kotlin.text.regex` at 74,655 and
# `kotlinx.datetime.format` at 46,396. The last two are reached from the theme's
# defaults rather than from anything drawn, and are where to look first.
#
# Measured again 2026-10-17 on CI (05644143), the ceilings unmoved: the theme's
# six `Regex` calls became loops, and `theme` fell from 728,602 to 710,069,
# 18,533 less, with `button`, `icon` and `icons` down with it. `kotlin.text.regex`
# is still 55,268 before Binaryen, so something outside `:ui` reaches it too.
PROBE_CEILINGS: dict[str, int] = {
    "empty": 588_000,
    "theme": 751_000,
    "button": 829_000,
    "icon": 753_000,
    "icons": 761_000,
}

PROBE_BASELINES = {
    "empty": None,
    "theme": "empty",
    "button": "theme",
    "icon": "theme",
    "icons": "icon",
}

COMPRESSIBLE = {".html", ".css", ".js", ".mjs", ".json", ".wasm", ".svg"}


def classify(path: pathlib.Path, skiko_sizes: set[int]) -> str:
    """Which ceiling this file counts against.

    Both `.wasm` files reach the distribution under a content hash, so neither can
    be recognised by name. Skia is identified by matching the raw byte count of the
    `skiko.wasm` the build unpacked — an exact-size match on a file the same build
    produced, rather than "the bigger one", which would silently swap the two the
    day the application binary overtakes the renderer.
    """
    if path.suffix == ".wasm":
        return "skiko" if path.stat().st_size in skiko_sizes else "app"
    if path.suffix in {".ttf", ".otf", ".woff2"}:
        return "fonts"
    if path.suffix in {".js", ".mjs"}:
        return "js"
    return "other"


def transfer_size(path: pathlib.Path) -> int:
    raw = path.read_bytes()
    if path.suffix not in COMPRESSIBLE:
        return len(raw)
    return len(gzip.compress(raw, 9))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dist", default="ui-docs/build/dist/wasmJs/productionExecutable")
    parser.add_argument("--update", action="store_true")
    parser.add_argument("--probes", action="store_true")
    options = parser.parse_args()

    if options.probes:
        return check_probes(options.update)

    dist = pathlib.Path(options.dist)
    if not dist.is_dir():
        print(f"no distribution at {dist} — run :ui-docs:wasmJsBrowserDistribution first")
        return 2

    skiko_sizes = {p.stat().st_size for p in pathlib.Path(".").glob("*/build/compose/**/skiko.wasm")}
    if not skiko_sizes:
        print("could not find the unpacked skiko.wasm, so the renderer cannot be told")
        print("apart from the application binary. Build the distribution first.")
        return 2

    files = sorted((p for p in dist.rglob("*") if p.is_file()), key=lambda p: -p.stat().st_size)
    groups: dict[str, int] = {name: 0 for name in CEILINGS if name != "total"}
    for path in files:
        groups[classify(path, skiko_sizes)] += transfer_size(path)
    groups["total"] = sum(groups.values())

    print(f"  {dist}   {len(files)} files\n")
    print("        gzip         raw   file")
    for path in files:
        print(f"  {transfer_size(path):10,}  {path.stat().st_size:10,}   {path.relative_to(dist)}")

    if options.update:
        source = pathlib.Path(__file__).read_text()
        for name, measured in groups.items():
            ceiling = int(measured * 1.03 / 10_000 + 1) * 10_000
            source = re.sub(
                rf'^(    "{name}": )[\d_]+,$',
                lambda m, c=ceiling: f"{m.group(1)}{c:_},",
                source,
                flags=re.M,
            )
        pathlib.Path(__file__).write_text(source)
        print("\n  ceilings updated")
        return 0

    print()
    over = []
    for name, ceiling in CEILINGS.items():
        measured = groups[name]
        room = ceiling - measured
        flag = "OVER" if room < 0 else f"{room:,} to spare"
        print(f"  {name:>6}  {measured:10,}  of {ceiling:10,}   {flag}")
        if room < 0:
            over.append((name, measured, ceiling))

    if len(files) > MAX_FILES:
        print(f"\n  {len(files)} files in the distribution, ceiling is {MAX_FILES}.")
        over.append(("files", len(files), MAX_FILES))

    if over:
        print("\n  The bundle grew past a ceiling:\n")
        for name, measured, ceiling in over:
            print(f"    {name}: {measured:,} against {ceiling:,} — over by {measured - ceiling:,}")
        print(
            "\n  If the growth is the point of the change, re-run with --update in the\n"
            "  same commit so the diff carries the cost. If it is not, this is a\n"
            "  dependency or a resource that arrived without anyone asking for it."
        )
        return 1

    print("\n  Under every ceiling.")
    return 0


def probe_wasm(name: str) -> list[pathlib.Path]:
    """The probe's own optimised binary: what Binaryen wrote, and only that."""
    build = pathlib.Path("probes") / name / "build"
    return sorted(build.glob("**/productionExecutable/optimized/*.wasm"))


def check_probes(update: bool) -> int:
    sizes: dict[str, tuple[int, int]] = {}
    missing = []
    for name in PROBE_BASELINES:
        files = probe_wasm(name)
        if not files:
            missing.append(name)
            continue
        sizes[name] = (sum(transfer_size(f) for f in files), sum(f.stat().st_size for f in files))
    if missing:
        print("no optimised binary for " + ", ".join(missing) + ". Build them first:\n")
        for name in missing:
            print(f"  ./gradlew :probes:{name}:compileProductionExecutableKotlinWasmJsOptimize")
        return 2

    if update:
        source = pathlib.Path(__file__).read_text()
        body = "".join(
            f'    "{name}": {int(gz * 1.03 / 1_000 + 1) * 1_000:_},\n' for name, (gz, _) in sizes.items()
        )
        source = re.sub(
            r"^(PROBE_CEILINGS: dict\[str, int\] = \{\n)(?:    .*\n)*(\})",
            lambda m: m.group(1) + body + m.group(2),
            source,
            count=1,
            flags=re.M,
        )
        pathlib.Path(__file__).write_text(source)
        print("  probe ceilings updated")
        return 0

    print("              gzip         raw     against   difference   ceiling")
    over = []
    for name, (gz, raw) in sizes.items():
        baseline = PROBE_BASELINES[name]
        difference = f"{gz - sizes[baseline][0]:+12,}" if baseline else " " * 12
        ceiling = PROBE_CEILINGS.get(name)
        if ceiling is None:
            verdict = "none set"
            over.append((name, gz, None))
        elif gz > ceiling:
            verdict = f"OVER by {gz - ceiling:,}"
            over.append((name, gz, ceiling))
        else:
            verdict = f"{ceiling - gz:,} to spare"
        print(f"  {name:>8}  {gz:10,}  {raw:10,}   {baseline or '':>9} {difference}   {verdict}")

    if "icons" in sizes and "icon" in sizes:
        each = (sizes["icons"][0] - sizes["icon"][0]) / 49
        print(f"\n  One more icon: about {each:,.0f} gzip bytes.")

    if over:
        print()
        for name, measured, ceiling in over:
            if ceiling is None:
                print(f"    {name}: {measured:,}, and no ceiling — set one with --probes --update")
            else:
                print(f"    {name}: {measured:,} against {ceiling:,}")
        print(
            "\n  A probe grew past its ceiling, or has none. If the growth is the point of\n"
            "  the change, raise it with --probes --update and a note above\n"
            "  PROBE_CEILINGS. If not, something every app pays for arrived."
        )
        return 1

    print("\n  Every probe under its ceiling.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
