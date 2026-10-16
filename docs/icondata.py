#!/usr/bin/env python3
"""Lay out the icon browser's data: every Tabler icon the library's build has,
with its category, its tags and its paths, in small files the site fetches as
it needs them.

The library's icons are Kotlin properties, `Tabler.Outline.Home`, and the docs
site cannot list them: naming all six thousand in the site's code would put all
six thousand in its Wasm binary, past what `check-bundle-size.py` allows by a
long way. So the browser draws them from data instead, the same paths
`kontour-labs/tabler` generates the properties from — the `@tabler/icons`
release of the version in `gradle/libs.versions.toml`, from npm, checked against
the hash pinned below — and fetches them a chunk at a time as rows come into
view.

    python3 docs/icondata.py --out ui-docs/build/icon-data [--tarball icons.tgz]
    python3 docs/icondata.py --check --out ui-docs/build/icon-data

`lay-out-site.py` calls it for the deployed site, into `icons/`. What it writes:

* `manifest.txt` — one line, the name of the directory beside it holding this
  data. The directory is named for its content, so it is cached for good and a
  new release or a new layout is a new directory.
* `<hash>/index.txt` — a header line, `tabler <TAB> version <TAB> outline count
  <TAB> filled count <TAB> chunk size`, then one line per icon in Tabler's name
  order: `name <TAB> category <TAB> tags, comma-separated <TAB> 1 if it has a
  filled form`.
* `<hash>/outline/NNN.txt`, `<hash>/filled/NNN.txt` — the icons in the same
  order, `CHUNK` to a file (the filled ones numbered among themselves), one line
  each: `name <TAB> path <TAB> path…`, a path being `<fill><stroke>;<alpha>;
  <evenOdd>;<d>` — the encoding of `kontour-labs/tabler`'s own fixture, with the
  same defaults, so the browser draws what the property draws.

Plain text, not JSON: the site reads it with `split`, where JSON would need a
parser in the size-capped binary.
"""

import argparse
import base64
import hashlib
import io
import json
import pathlib
import re
import shutil
import sys
import tarfile
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOGUE = ROOT / "gradle/libs.versions.toml"

# The npm release's `dist.integrity`, as `kontour-labs/tabler` pins it in its
# `gradle.properties`. A version without one here fails, by name: bumping the
# icons is a deliberate act, and so is trusting a new tarball.
#   curl -s https://registry.npmjs.org/@tabler/icons/<version> | jq -r .dist.integrity
INTEGRITY = {
    "3.49.0": "sha512-l+GnSkFETx0U26icxv1VUFnxxiTqXfCcVRiRAnyMwibupM2S2L/hSaZ9gmSgz01rkrsH/93NvLsxykqdanc6lw==",
    "3.48.0": "sha512-lQk06gVHNVBnJp0UOgtth54mcZBJM7rdYKsAxKOProj1bbSAx+/xiuOWfHlyvgdL3M+Y+I0HINrjHa3ygp8eXQ==",
}

# Icons to a chunk file: about 30 KB of paths, 8 KB as the host compresses it,
# and about a screenful of rows on a wide window.
CHUNK = 128

STYLES = {
    # (fills, strokes) by default, as each style's SVG root says.
    "outline": (False, True),
    "filled": (True, False),
}


def tabler_version() -> str:
    """The `@tabler/icons` release the pinned icons are drawn from.

    The catalogue pins `io.kontour:tabler-icons-*`, whose version is Tabler's —
    `3.49.0` — or Tabler's with a fourth part, `3.49.0.1`, for a re-release of
    the same icons by `kontour-labs/tabler`. npm knows only the first three.
    """
    match = re.search(r'^tabler-icons\s*=\s*"([^"]+)"', CATALOGUE.read_text(), re.M)
    if not match:
        raise SystemExit(f"no `tabler-icons` version in {CATALOGUE}")
    return npm_version(match.group(1))


def npm_version(version: str) -> str:
    """`3.49.0.1` → `3.49.0`: the Tabler release a `tabler-icons` version is of."""
    return ".".join(version.split(".")[:3])


def tarball_bytes(version: str, tarball: pathlib.Path | None) -> bytes:
    pinned = INTEGRITY.get(version)
    if pinned is None:
        raise SystemExit(
            f"no integrity pinned for @tabler/icons {version}: add it to INTEGRITY in {pathlib.Path(__file__).name}\n"
            f"  curl -s https://registry.npmjs.org/@tabler/icons/{version} | jq -r .dist.integrity"
        )
    if tarball is None:
        cache = ROOT / f"build/tabler/icons-{version}.tgz"
        if not cache.is_file():
            url = f"https://registry.npmjs.org/@tabler/icons/-/icons-{version}.tgz"
            print(f"  fetching {url}")
            cache.parent.mkdir(parents=True, exist_ok=True)
            with urllib.request.urlopen(url, timeout=120) as response:
                cache.write_bytes(response.read())
        tarball = cache
    data = tarball.read_bytes()
    algorithm, expected = pinned.split("-", 1)
    actual = base64.b64encode(hashlib.new(algorithm, data).digest()).decode()
    if actual != expected:
        raise SystemExit(f"{tarball} is not @tabler/icons {version}: its {algorithm} is {actual}, not {expected}")
    return data


def property_name(name: str) -> str:
    """`a-b-2` → `AB2`, as `kontour-labs/tabler` names the property."""
    prop = "".join(part[:1].upper() + part[1:] for part in name.split("-"))
    if not re.fullmatch(r"[A-Z][A-Za-z0-9]*", prop):
        raise SystemExit(f"Tabler icon `{name}` does not make a Kotlin identifier (got `{prop}`)")
    return prop


def paint(name: str, style: str, key: str, value: str) -> bool:
    if value == "currentColor":
        return True
    if value == "none":
        return False
    raise SystemExit(f"Tabler icon `{name}` ({style}): {key}=\"{value}\"")


def paths_of(name: str, nodes: list, style: str) -> list[str]:
    """One icon's paths, encoded, with the style's defaults applied as
    `kontour-labs/tabler`'s `Icons.fromNodes` applies them."""
    fills, strokes = STYLES[style]
    out = []
    for element, attributes in nodes:
        if element != "path":
            raise SystemExit(f"Tabler icon `{name}` ({style}): a `<{element}>`; only paths are supported")
        d, fill, stroke, alpha, even_odd = None, fills, strokes, "", False
        for key, value in attributes.items():
            text = str(value)
            if key == "d":
                d = text
            elif key == "fill":
                fill = paint(name, style, key, text)
            elif key == "stroke":
                stroke = paint(name, style, key, text)
            elif key == "opacity":
                if not 0 <= float(text) <= 1:
                    raise SystemExit(f"Tabler icon `{name}` ({style}): opacity=\"{text}\"")
                alpha = repr(float(text))
            elif key == "fill-rule":
                if text not in ("evenodd", "nonzero"):
                    raise SystemExit(f"Tabler icon `{name}` ({style}): fill-rule=\"{text}\"")
                even_odd = text == "evenodd"
            else:
                raise SystemExit(f"Tabler icon `{name}` ({style}): {key}=\"{text}\" is not understood")
        if not d or not d.strip():
            raise SystemExit(f"Tabler icon `{name}` ({style}): a path with no `d`")
        if "\t" in d or ";" in d or "\n" in d:
            raise SystemExit(f"Tabler icon `{name}` ({style}): a path the encoding cannot carry")
        # Neither filled nor stroked draws nothing.
        if not fill and not stroke:
            continue
        out.append(f"{int(fill)}{int(stroke)};{alpha};{int(even_odd)};{d}")
    if not out:
        raise SystemExit(f"Tabler icon `{name}` ({style}): no visible paths")
    return out


def tags_of(entry: dict, name: str) -> str:
    """Tabler's tags, as one comma-separated field: lower case, without the
    name's own words, which the search reads from the name anyway."""
    words = set(name.split("-"))
    seen = []
    for tag in entry.get("tags", []):
        # A few are numbers in the JSON — `a-b-2` has `2`.
        tag = re.sub(r"[,\t\n]+", " ", str(tag)).strip().lower()
        if tag and tag != name and tag not in words and tag not in seen:
            seen.append(tag)
    return ",".join(seen)


def build(version: str, data: bytes) -> dict[str, str]:
    """Every file, by its path inside the hashed directory."""
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as tar:
        def read(path: str):
            return json.load(tar.extractfile(f"package/{path}"))
        meta = read("icons.json")
        nodes = {style: read(f"tabler-nodes-{style}.json") for style in STYLES}

    names = sorted(nodes["outline"])
    if sorted(meta) != names:
        missing = set(meta) ^ set(names)
        raise SystemExit(f"icons.json and the outline paths disagree on {len(missing)} icons, e.g. {sorted(missing)[:5]}")
    orphans = sorted(set(nodes["filled"]) - set(names))
    if orphans:
        raise SystemExit(f"filled icons with no outline: {orphans[:5]}")
    for name in names:
        property_name(name)

    filled = [name for name in names if name in nodes["filled"]]
    files = {}
    lines = [f"tabler\t{version}\t{len(names)}\t{len(filled)}\t{CHUNK}"]
    for name in names:
        entry = meta[name]
        category = re.sub(r"[\t\n]+", " ", str(entry.get("category") or "")).strip()
        lines.append(f"{name}\t{category}\t{tags_of(entry, name)}\t{int(name in nodes['filled'])}")
    files["index.txt"] = "\n".join(lines) + "\n"

    for style, order in (("outline", names), ("filled", filled)):
        for start in range(0, len(order), CHUNK):
            chunk = order[start:start + CHUNK]
            body = "".join(
                name + "\t" + "\t".join(paths_of(name, nodes[style][name], style)) + "\n" for name in chunk
            )
            files[f"{style}/{start // CHUNK:03d}.txt"] = body
    return files


def digest(files: dict[str, str]) -> str:
    h = hashlib.sha256()
    for path in sorted(files):
        h.update(path.encode() + b"\0" + files[path].encode() + b"\0")
    return h.hexdigest()[:12]


def write(out: pathlib.Path, tarball: pathlib.Path | None = None) -> str:
    """Writes the data under [out]; returns the hashed directory's name."""
    version = tabler_version()
    files = build(version, tarball_bytes(version, tarball))
    name = digest(files)
    if out.exists():
        shutil.rmtree(out)
    for path, text in files.items():
        target = out / name / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
    (out / "manifest.txt").write_text(name + "\n")
    problems = check(out)
    if problems:
        raise SystemExit("\n".join(problems))
    return name


def check(out: pathlib.Path) -> list[str]:
    """What is wrong with the data under [out], if anything."""
    manifest = out / "manifest.txt"
    if not manifest.is_file():
        return [f"no {manifest}"]
    name = manifest.read_text().strip()
    root = out / name
    index = root / "index.txt"
    if not index.is_file():
        return [f"{manifest} names {name}, which has no index.txt"]
    header, *rows = index.read_text().splitlines()
    fields = header.split("\t")
    if len(fields) != 5 or fields[0] != "tabler":
        return [f"{index}: the header is not `tabler, version, outline, filled, chunk`: {header!r}"]
    _, version, outline, filled, chunk = fields
    outline, filled, chunk = int(outline), int(filled), int(chunk)
    problems = []
    if version != tabler_version():
        problems.append(f"{index} is Tabler {version}, and the library is built with {tabler_version()}")
    if len(rows) != outline:
        problems.append(f"{index} lists {len(rows)} icons and says {outline}")
    names = [row.split("\t", 1)[0] for row in rows]
    if names != sorted(names):
        problems.append(f"{index} is not in name order")
    has_filled = [row.split("\t")[0] for row in rows if row.endswith("\t1")]
    if len(has_filled) != filled:
        problems.append(f"{index} marks {len(has_filled)} icons filled and says {filled}")
    for style, order in (("outline", names), ("filled", has_filled)):
        for start in range(0, len(order), chunk):
            path = root / style / f"{start // chunk:03d}.txt"
            if not path.is_file():
                problems.append(f"no {path}")
                continue
            got = [line.split("\t", 1)[0] for line in path.read_text().splitlines()]
            if got != order[start:start + chunk]:
                problems.append(f"{path} does not hold icons {start}–{start + chunk - 1} of the {style} order")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="ui-docs/build/icon-data")
    parser.add_argument("--tarball", default=None, help="the @tabler/icons release, rather than fetching it")
    parser.add_argument("--check", action="store_true", help="check what is already laid out; write nothing")
    options = parser.parse_args()
    out = pathlib.Path(options.out)
    if options.check:
        problems = check(out)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        if problems:
            return 1
        print(f"  {out} checks out")
        return 0
    name = write(out, pathlib.Path(options.tarball) if options.tarball else None)
    files = list((out / name).rglob("*.txt"))
    size = sum(f.stat().st_size for f in files)
    print(f"  {len(files)} files, {size:,} bytes, in {out / name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
