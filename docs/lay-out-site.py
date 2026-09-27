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

### The text mirror

The site is one Compose canvas. Nothing on it is text a browser can find, or that
an agent fetching the page can read: `curl` gets an HTML shell and a script tag.
So beside the application this writes every page as the Markdown it was compiled
from, `md/<path>.md`, plus `llms.txt` — an index of those, one line each — and
`llms-full.txt`, all of them in one file. Written from `ui-docs/content`, not
from the bundle, so `--text-only` can produce and check it without a Wasm build.

Links that stay among the pages are left relative, and resolve in the mirror as
they do on GitHub. A link that leaves `ui-docs/content` — to `docs/building/`,
to a source file — becomes a link to that file in the repository.

### Where it is served

`docs.kontour.io` is Kontour's documentation, and this library's is one part of
it, under `/ui/`. `--deploy DIR` assembles what is uploaded to Cloudflare Pages:
the site at `DIR/ui/`, and at the root the files Pages reads for the whole host —
`_headers`, `_redirects` — and a root `llms.txt` that points at this one. The
prefix is read off `--base-url`, so it is written once.

    python3 docs/lay-out-site.py --out site --api ui/build/dokka/html --deploy dist
    python3 docs/lay-out-site.py --text-only --out site      # the mirror alone
    python3 docs/lay-out-site.py --check --out site --deploy dist
"""

import argparse
import pathlib
import re
import shutil
import sys
from urllib.parse import urlparse

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from doctree import (  # noqa: E402
    CONTENT, FAMILY, GUIDES, INDEXES, content_pages, family_of, page_path,
)

# Where the site is read. Also where its prefix comes from: `/ui/` here is the
# directory the upload puts it in, so the two cannot disagree.
SITE_URL = "https://docs.kontour.io/ui/"

# Where a link that leaves the published pages goes. The same constant as
# `Route.kt`'s `Repository`, for the same reason: every reader is signed in to
# the organisation, for whom the private repository resolves.
REPOSITORY = "https://github.com/kontour-labs/ui"

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


# A Markdown link, as the pages write them: `[text](target)`.
LINK = re.compile(r"\]\(([^)\s]+)\)")

# What a paragraph cannot begin with and still be the page's opening sentence.
NOT_PROSE = ("#", "|", "```", ">", "- ", "* ", "![", "<!--", "---", "*Also on this page")


def title_of(text: str, path: pathlib.Path) -> str:
    title = re.search(r"^#\s+(.+)$", text, re.M)
    if not title:
        raise SystemExit(f"{path}: no title")
    return title.group(1).strip()


def summary_of(text: str) -> str:
    """The page's first sentence of prose, links reduced to their text.

    What `llms.txt` says a page is about. A component page opens on its sample,
    so this skips code, tables and the "Also on this page" line rather than
    taking the first line after the title.
    """
    paragraphs, current, fenced = [], [], False
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("```"):
            fenced = not fenced
            if current:
                paragraphs.append(current)
                current = []
            continue
        if fenced:
            continue
        if not stripped:
            if current:
                paragraphs.append(current)
                current = []
            continue
        current.append(stripped)
    if current:
        paragraphs.append(current)
    for paragraph in paragraphs:
        if paragraph[0].startswith(NOT_PROSE):
            continue
        prose = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", " ".join(paragraph))
        sentence = re.match(r"(.+?[.!?:])(?:\s|$)", prose)
        first = sentence.group(1) if sentence else prose
        return first[:-1] + "." if first.endswith(":") else first
    return ""


def mirrored(text: str, source: pathlib.Path) -> str:
    """A page's Markdown with every link that leaves the pages sent to the repository.

    Only a target that resolves to a real file is touched, and never inside a
    fenced block: `items[0](x)` in a Kotlin sample is a call, not a link.
    """
    base = source.parent

    def rewrite(match: re.Match) -> str:
        target = match.group(1)
        if re.match(r"[a-z]+:", target) or target.startswith("#"):
            return match.group(0)
        file, _, anchor = target.partition("#")
        resolved = (base / file).resolve()
        if not resolved.exists():
            return match.group(0)
        try:
            resolved.relative_to(CONTENT.resolve())
            return match.group(0)
        except ValueError:
            pass
        repo_path = resolved.relative_to(pathlib.Path.cwd().resolve()).as_posix()
        return f"]({REPOSITORY}/blob/main/{repo_path}{'#' + anchor if anchor else ''})"

    out, fenced = [], False
    for line in text.splitlines(keepends=True):
        if line.lstrip().startswith("```"):
            fenced = not fenced
        out.append(line if fenced else LINK.sub(rewrite, line))
    return "".join(out)


def reading_order() -> list[tuple[str, list[pathlib.Path]]]:
    """The pages in the order the site's sidebar gives them, grouped as it groups them."""
    pages = {page_path(path): path for path in content_pages()}
    placed: set[str] = set()
    sections: list[tuple[str, list[pathlib.Path]]] = []

    guides = [pages[g] for g in GUIDES if g in pages]
    placed.update(GUIDES)
    sections.append(("Guides", guides))

    claimed = family_of()
    for family in INDEXES:
        index = f"components/{family}"
        members = sorted(
            (stem for stem, (owner, _) in claimed.items() if owner == family),
            key=lambda stem: claimed[stem][1],
        )
        paths = [index] + [f"components/{stem}" for stem in members]
        paths = [p for p in paths if p in pages and p not in placed]
        placed.update(paths)
        sections.append((f"Components: {FAMILY[family]}", [pages[p] for p in paths]))

    rest = [pages[p] for p in sorted(pages) if p not in placed]
    if rest:
        sections.append(("Other", rest))
    return sections


def mirror_header(path: str, base_url: str) -> str:
    return (
        f"<!-- Kontour UI documentation, {path}. On the site: {base_url}#/{path}\n"
        f"     Source: {CONTENT.as_posix()}/{path}.md -->\n\n"
    )


def llms_index(sections, base_url: str) -> str:
    lines = [
        "# Kontour UI",
        "",
        "> A Compose Multiplatform design system: components, tokens and theming for",
        "> Android, iOS, desktop and the web. Every page of its documentation, as Markdown.",
        "",
        "This site is private. Fetch it with the Cloudflare Access service token in two",
        "headers — `CF-Access-Client-Id` and `CF-Access-Client-Secret` — conventionally",
        "from the environment variables `KONTOUR_DOCS_CLIENT_ID` and",
        "`KONTOUR_DOCS_CLIENT_SECRET`. Each link below is the page's Markdown; the same",
        f"page on the site is `{base_url}#/<path>`, and the API reference is at",
        f"{base_url}api/. Everything in one file: {base_url}llms-full.txt",
        "",
        "If you are an agent working in an app built on this library, the Agents guide",
        "below says what to read in what order.",
    ]
    for heading, paths in sections:
        if not paths:
            continue
        lines += ["", f"## {heading}", ""]
        for path in paths:
            text = path.read_text()
            key = page_path(path)
            about = summary_of(text)
            entry = f"- [{title_of(text, path)}]({base_url}md/{key}.md)"
            lines.append(f"{entry}: {about}" if about else entry)
    return "\n".join(lines) + "\n"


def write_mirror(site: pathlib.Path, base_url: str) -> int:
    """`md/`, `llms.txt` and `llms-full.txt`, into [site]. Returns the page count."""
    sections = reading_order()
    full = [llms_index(sections, base_url).split("\n## ")[0].rstrip() + "\n"]
    count = 0
    for _, paths in sections:
        for path in paths:
            key = page_path(path)
            body = mirrored(path.read_text(), path)
            target = site / "md" / f"{key}.md"
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(mirror_header(key, base_url) + body)
            full.append(f"\n\n---\n\n{mirror_header(key, base_url)}{body.rstrip()}\n")
            count += 1
    (site / "llms.txt").write_text(llms_index(sections, base_url))
    (site / "llms-full.txt").write_text("".join(full))
    return count


def check_mirror(site: pathlib.Path, base_url: str) -> list[str]:
    """Every page mirrored, listed once, and nothing listed that is not there."""
    problems = []
    keys = {page_path(path) for path in content_pages()}
    mirror = site / "md"
    have = {p.relative_to(mirror).with_suffix("").as_posix() for p in mirror.rglob("*.md")} if mirror.is_dir() else set()
    for key in sorted(keys - have):
        problems.append(f"no mirror of {key}: md/{key}.md is missing")
    for key in sorted(have - keys):
        problems.append(f"md/{key}.md mirrors no page — a page was deleted and its mirror was not")
    index = site / "llms.txt"
    if not index.is_file():
        return problems + ["no llms.txt"]
    listed = re.findall(re.escape(base_url) + r"md/([^)\s]+)\.md\)", index.read_text())
    for key in sorted(keys):
        if listed.count(key) != 1:
            problems.append(f"llms.txt lists {key} {listed.count(key)} times")
    for key in sorted(set(listed) - keys):
        problems.append(f"llms.txt lists {key}, which is not a page")
    full = site / "llms-full.txt"
    if not full.is_file():
        problems.append("no llms-full.txt")
    else:
        text = full.read_text()
        for key in sorted(keys):
            if mirror_header(key, base_url) not in text:
                problems.append(f"llms-full.txt is missing {key}")
    return problems


def headers_file(site: pathlib.Path, prefix: str) -> str:
    """Cloudflare Pages' `_headers`, for the whole host.

    No search engine indexes a private site, and nothing here tries to be found.

    The long cache is only for the two Wasm binaries, and only by exact path. They
    are the only files the build names by their content — `ui-docs.js`, the fonts
    and `index.html` keep their names from build to build, and a year's cache on
    any of those would serve last month's site. So the rule is generated from
    what the build produced, as the preloads are, and a `.wasm` that stops
    carrying a hash stops getting one. `private`, because the content is behind
    Access and no cache between the edge and the reader should keep a copy.

    No `Cross-Origin-Opener-Policy`/`Cross-Origin-Embedder-Policy`: they exist to
    unlock `SharedArrayBuffer`, which a single-threaded Wasm app never touches,
    and `require-corp` would only break a cross-origin asset someone adds later.
    """
    lines = [
        "# Written by docs/lay-out-site.py. Cloudflare Pages reads it for the whole host.",
        "/*",
        "  X-Robots-Tag: noindex",
    ]
    for wasm in sorted(site.glob("*.wasm")):
        if re.fullmatch(r"[0-9a-f]{8,}", wasm.stem):
            lines += [f"/{prefix}/{wasm.name}", "  Cache-Control: private, max-age=31536000, immutable"]
        else:
            print(f"  {wasm.name} carries no content hash, so it is not cached long")
    # Plain text, not `text/markdown`: a browser shows the one and offers to
    # download the other, and "Open as text" is for reading and find-in-page.
    for text in ("md/*", "llms.txt", "llms-full.txt"):
        lines += [f"/{prefix}/{text}", "  Content-Type: text/plain; charset=utf-8"]
    return "\n".join(lines) + "\n"


def write_deploy(site: pathlib.Path, deploy: pathlib.Path, prefix: str, base_url: str) -> None:
    """The upload: the site under its prefix, and the host's own files at the root.

    Cloudflare Pages' Direct Upload replaces the *whole* project with each
    deployment. While this library is the only thing on `docs.kontour.io` that is
    what is wanted; when a second library's documentation arrives, the root files
    here and the upload have to be assembled from both, or each deploy will
    delete the other's.
    """
    if deploy.exists():
        shutil.rmtree(deploy)
    shutil.copytree(site, deploy / prefix)
    (deploy / "_headers").write_text(headers_file(site, prefix))
    # 302, not 301: the root is this library's for now, and a browser keeps a
    # permanent redirect long after the root becomes an index of several.
    (deploy / "_redirects").write_text(f"/ /{prefix}/ 302\n")
    (deploy / "llms.txt").write_text(
        "# Kontour documentation\n\n"
        "> The documentation for Kontour's libraries. Each has its own index.\n\n"
        "Private: send the Cloudflare Access service token's `CF-Access-Client-Id` and\n"
        "`CF-Access-Client-Secret` headers with every request.\n\n"
        "## Libraries\n\n"
        f"- [Kontour UI]({base_url}llms.txt): the Compose Multiplatform design system.\n"
    )


def check_deploy(deploy: pathlib.Path, prefix: str, base_url: str) -> list[str]:
    problems = []
    if not (deploy / prefix / "index.html").is_file():
        problems.append(f"no site at {deploy / prefix}")
    headers = deploy / "_headers"
    if not headers.is_file():
        problems.append("no _headers")
    else:
        rule = None
        for n, line in enumerate(headers.read_text().splitlines(), 1):
            if not line.strip() or line.startswith("#"):
                continue
            if line.startswith("/"):
                rule = line.strip()
                if "*" not in rule and not (deploy / rule.lstrip("/")).is_file():
                    problems.append(f"_headers:{n} names {rule}, which is not in the upload")
                continue
            if rule is None or not re.fullmatch(r"  [A-Za-z-]+: \S.*", line):
                problems.append(f"_headers:{n} is neither a path nor a header under one: {line!r}")
    redirects = deploy / "_redirects"
    if not redirects.is_file():
        problems.append("no _redirects")
    else:
        for n, line in enumerate(redirects.read_text().splitlines(), 1):
            if line.strip() and not line.startswith("#") and not re.fullmatch(r"/\S* /\S+ (301|302|307|308)", line):
                problems.append(f"_redirects:{n} is not `from to status`: {line!r}")
    root = deploy / "llms.txt"
    if not root.is_file() or f"{base_url}llms.txt" not in root.read_text():
        problems.append(f"the root llms.txt does not point at {base_url}llms.txt")
    return problems


def report(problems: list[str]) -> None:
    if problems:
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        raise SystemExit(1)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dist", default="ui-docs/build/dist/wasmJs/productionExecutable")
    parser.add_argument("--api", default=None, help="Dokka HTML, mounted at /api")
    parser.add_argument("--out", default="site")
    parser.add_argument("--deploy", default=None, help="also assemble the upload here, the site under its prefix")
    parser.add_argument("--base-url", default=SITE_URL, help="where the site is read; its path is the prefix")
    parser.add_argument("--text-only", action="store_true", help="write and check the text mirror alone")
    parser.add_argument("--check", action="store_true", help="check what is already laid out; write nothing")
    options = parser.parse_args()

    base_url = options.base_url.rstrip("/") + "/"
    prefix = urlparse(base_url).path.strip("/")
    site = pathlib.Path(options.out)
    deploy = pathlib.Path(options.deploy) if options.deploy else None

    if options.check:
        problems = check_mirror(site, base_url)
        if not options.text_only:
            try:
                check(site)
            except SystemExit:
                problems.append("the preloads above do not check out")
        if deploy:
            problems += check_deploy(deploy, prefix, base_url)
        report(problems)
        print(f"  {site}{' and ' + str(deploy) if deploy else ''} check out")
        return 0

    if options.text_only:
        if site.exists():
            shutil.rmtree(site)
        site.mkdir(parents=True)
        count = write_mirror(site, base_url)
        report(check_mirror(site, base_url))
        print(f"  {count} pages mirrored into {site}/md, indexed in {site}/llms.txt")
        return 0

    dist = pathlib.Path(options.dist)
    if not dist.is_dir():
        raise SystemExit(f"no distribution at {dist} — run :ui-docs:wasmJsBrowserDistribution")

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

    count = write_mirror(site, base_url)
    report(check_mirror(site, base_url))

    if deploy:
        write_deploy(site, deploy, prefix, base_url)
        report(check_deploy(deploy, prefix, base_url))

    print(f"  {site}")
    for path in sorted(p for p in site.iterdir() if p.is_file()):
        print(f"  {path.stat().st_size:10,}  {path.name}")
    print(f"\n  {len(tags)} preloads, all as=\"fetch\", all naming files that exist")
    print(f"  {count} pages mirrored as Markdown under md/, indexed in llms.txt")
    if deploy:
        print(f"  the upload is {deploy}, the site at {deploy}/{prefix}/ for {base_url}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
