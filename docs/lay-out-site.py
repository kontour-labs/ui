#!/usr/bin/env python3
"""Arrange the built distribution and the API reference into what gets deployed.

This was thirty lines of shell inside `.github/workflows/ci.yml`, which meant the
arrangement a reader actually gets — where the reference sits, which files are
dropped, what the browser is told to fetch early — existed only on a CI runner and
could not be produced, inspected or measured anywhere else.

That matters more than tidiness. `docs/measure-web.mjs` opens a browser on the
site, and pointing it at the raw distribution measures a page that nobody is
served: no preloads, so a different waterfall, so different numbers. A layout step
that can only run in CI is a layout step nothing can test.

    python3 docs/lay-out-site.py --out site [--api ui/build/dokka/html]

### Preloads

Everything the bundle fetches after boot is generated here from what the build
actually produced, never hand-written. Two reasons, both learned:

* the filenames are a convention of the Kotlin plugin rather than a promise —
  they carry content hashes, and a hardcoded tag once named a file that only
  exists in an intermediate build directory;
* a preload that misses is **invisible**. The browser fetches it, gets nothing or
  gets it in the wrong mode, and the page still works — just slower — so nothing
  short of a check tells anybody.

Every tag is `as="fetch"`, which is not a guess: Wasm arrives through the module
loader's `fetch`, and Compose Resources reads fonts with `window.fetch` too. The
request destination for both is the empty string, and **a preload only satisfies a
request whose destination it matches**. The fonts were `as="font"` for two rounds,
which cannot match a `fetch()` however correct it looks, and a browser measurement
showed both preloaded faces being downloaded a second time.

`crossorigin` is required even same-origin: without it the preload is made in
no-cors mode, `fetch()` defaults to cors, and the two do not match either.
"""

import argparse
import pathlib
import re
import shutil
import sys

FONT_SUFFIXES = {".ttf", ".otf", ".woff2"}

# Body text and headings — the two weights on screen when the site first draws.
# See `preload_tags` for why this is two and not all five.
FIRST_SCREEN_FONTS = ("outfit_regular", "outfit_semibold")

PRELOAD = re.compile(r'<link\s+rel="preload"[^>]*>')
HREF = re.compile(r'href="([^"]*)"')
AS = re.compile(r'\bas="([^"]*)"')


def preload_tags(site: pathlib.Path) -> list[str]:
    """One tag per asset the bundle fetches for itself, in load order.

    Wasm first because it is two thirds of the payload and nothing can start on it
    until `ui-docs.js` has been fetched, parsed and run — one round trip and a
    parse serialised in front of the biggest thing on the page.

    Then the two faces that draw the first screen, and only those. Measured, at
    Chrome's Fast 4G preset, two runs each, time until the application draws:

        as shipped, two faces as="font"      5843 / 5934 ms   12 requests
        two faces as="fetch"                 5850 / 5888 ms   10 requests
        all five faces as="fetch"            6029 / 6113 ms   10 requests

    Correcting the `as` is free and removes both duplicate downloads. Preloading
    all five costs about 190ms of first frame, every time, because 276 KB of fonts
    then competes with 5.4 MB of Wasm on a link that has a ceiling — and the three
    extra faces are not on screen when it is paid.

    So the *set* is a judgement about the first screen and cannot be read off the
    build. The filenames still are: each is checked to exist, so a rename fails
    here rather than shipping a preload that quietly misses. If the first screen
    ever changes weight, `docs/measure-web.mjs` shows the new face arriving after
    the first frame instead of before it.
    """
    tags = []
    for wasm in sorted(p for p in site.glob("*.wasm")):
        tags.append(
            f'    <link rel="preload" href="{wasm.name}" '
            f'as="fetch" type="application/wasm" crossorigin>'
        )
    if not tags:
        raise SystemExit("no .wasm in the distribution — the bundle layout changed")

    fonts = {p.stem: p for p in site.rglob("*") if p.suffix in FONT_SUFFIXES}
    missing = [name for name in FIRST_SCREEN_FONTS if name not in fonts]
    if missing:
        raise SystemExit(
            f"the first screen's faces are not in the distribution: {', '.join(missing)}. "
            "Either they were renamed, in which case update FIRST_SCREEN_FONTS, or the "
            "resources stopped being bundled."
        )
    for name in FIRST_SCREEN_FONTS:
        font = fonts[name]
        href = font.relative_to(site).as_posix()
        tags.append(
            f'    <link rel="preload" href="{href}" '
            f'as="fetch" type="font/{font.suffix.lstrip(".")}" crossorigin>'
        )
    later = sorted(set(fonts) - set(FIRST_SCREEN_FONTS))
    if later:
        print(f"  not preloaded, fetched when first used: {', '.join(later)}")
    return tags


def check(site: pathlib.Path) -> None:
    """Every preload names a real file and is fetched the way the app fetches it."""
    index = (site / "index.html").read_text()
    problems = []
    for tag in PRELOAD.findall(index):
        href = HREF.search(tag)
        kind = AS.search(tag)
        if not href:
            problems.append(f"a preload with no href: {tag}")
            continue
        if not (site / href.group(1)).is_file():
            problems.append(f"preloads {href.group(1)}, which the build did not produce")
        if not kind or kind.group(1) != "fetch":
            problems.append(
                f"{href.group(1)} is preloaded as=\"{kind.group(1) if kind else ''}\". "
                "Everything this bundle loads goes through fetch(), whose request "
                'destination is "", and a preload only satisfies a request whose '
                "destination it matches — so this one will be downloaded twice."
            )
    if problems:
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        raise SystemExit(1)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dist", default="ui-docs/build/dist/wasmJs/productionExecutable")
    parser.add_argument("--api", default=None, help="Dokka HTML, mounted at /api")
    parser.add_argument("--out", default="site")
    options = parser.parse_args()

    dist = pathlib.Path(options.dist)
    if not dist.is_dir():
        raise SystemExit(f"no distribution at {dist} — run :ui-docs:wasmJsBrowserDistribution")

    site = pathlib.Path(options.out)
    if site.exists():
        shutil.rmtree(site)
    shutil.copytree(dist, site)

    # The Wasm compiler emits a source map beside the binary whatever webpack is
    # told, and a recursive copy takes everything. 281 KB of it, fetched by nobody.
    for stale in site.rglob("*.map"):
        stale.unlink()

    if options.api:
        api = pathlib.Path(options.api)
        if not api.is_dir():
            raise SystemExit(f"no API reference at {api} — run :ui:dokkaGenerateHtml")
        shutil.copytree(api, site / "api")
        if not (site / "api/index.html").is_file():
            raise SystemExit("the API reference has no index.html")

    # Pages runs the whole tree through Jekyll unless told not to, and Jekyll drops
    # files and directories beginning with an underscore — which is most of what a
    # webpack bundle emits.
    (site / ".nojekyll").touch()

    index_path = site / "index.html"
    if not index_path.is_file():
        raise SystemExit("the distribution has no index.html")

    index = index_path.read_text()
    if "</head>" not in index:
        raise SystemExit("index.html has no </head> to inject the preloads before")

    tags = preload_tags(site)
    index = index.replace("</head>", "\n".join(tags) + "\n</head>", 1)
    index_path.write_text(index)

    check(site)

    print(f"  {site}")
    for path in sorted(p for p in site.iterdir() if p.is_file()):
        print(f"  {path.stat().st_size:10,}  {path.name}")
    print(f"\n  {len(tags)} preloads, all as=\"fetch\", all naming files that exist")
    return 0


if __name__ == "__main__":
    sys.exit(main())
