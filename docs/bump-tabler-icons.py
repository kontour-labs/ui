#!/usr/bin/env python3
"""Keep the icons on the newest release of `io.kontour:tabler-icons-*`.

Asked for as *"whenever the library rebuilds/updates, it automatically checks
for a newer version of kontour's tabler icon library and updates to it"*. The
workflow `.github/workflows/icons.yml` runs this on every push to `main`, once a
day and by hand; when there is a newer release it puts the bump on a branch of
its own, opens a pull request, and runs CI on that branch, so a redrawn icon
shows as a golden diff and a removed one as a failed compile — on the pull
request, never on `main`.

    python3 docs/bump-tabler-icons.py --current
    python3 docs/bump-tabler-icons.py --latest [--metadata outline.xml filled.xml]
    python3 docs/bump-tabler-icons.py --is-newer 3.49.0
    python3 docs/bump-tabler-icons.py --write 3.49.0 [--integrity sha512-…]

`--latest` reads both packages' `maven-metadata.xml` from Maven Central, which
needs no credentials, and prints the newest stable version both styles have.

`--write` moves the three places a version lives:

* `gradle/libs.versions.toml`, `tabler-icons`;
* `docs/icondata.py`'s `INTEGRITY`, the docs' icon browser's pin on the
  `@tabler/icons` tarball of that version — from npm's own `dist.integrity`
  unless given, and named in the pull request so a person looks at it. A
  re-release of the same icons, `3.49.0.1`, is pinned as npm's `3.49.0`;
* the changelog, a line under `## Unreleased` → `### Changed`.
"""

import argparse
import json
import pathlib
import re
import sys
import urllib.request
import xml.etree.ElementTree as ElementTree

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOGUE = ROOT / "gradle/libs.versions.toml"
ICONDATA = ROOT / "docs/icondata.py"
CHANGELOG = ROOT / "ui-docs/content/changelog.md"

REPOSITORY = "https://repo1.maven.org/maven2"
ARTIFACTS = ("tabler-icons-outline", "tabler-icons-filled")

VERSION_LINE = re.compile(r'^(tabler-icons\s*=\s*")([^"]+)(")', re.M)
STABLE = re.compile(r"^\d+(\.\d+)*$")


def current() -> str:
    match = VERSION_LINE.search(CATALOGUE.read_text())
    if not match:
        raise SystemExit(f"no `tabler-icons` version in {CATALOGUE}")
    return match.group(2)


def key(version: str) -> tuple[int, ...]:
    return tuple(int(part) for part in version.split("."))


def metadata(artifact: str) -> str:
    url = f"{REPOSITORY}/io/kontour/{artifact}/maven-metadata.xml"
    with urllib.request.urlopen(url, timeout=60) as response:
        return response.read().decode()


def npm_version(version: str) -> str:
    """`3.49.0.1` → `3.49.0`: a re-release of the same icons is of the same Tabler release."""
    return ".".join(version.split(".")[:3])


def versions(xml: str) -> set[str]:
    root = ElementTree.fromstring(xml)
    return {v.text.strip() for v in root.iter("version") if v.text and STABLE.match(v.text.strip())}


def latest(sources: list[str]) -> str:
    """The newest stable version every style has: a release half-published is not one yet."""
    common = set.intersection(*(versions(xml) for xml in sources))
    if not common:
        raise SystemExit("no stable version is published for both icon styles")
    return max(common, key=key)


def npm_integrity(version: str) -> str:
    url = f"https://registry.npmjs.org/@tabler/icons/{version}"
    with urllib.request.urlopen(url, timeout=60) as response:
        integrity = json.load(response)["dist"]["integrity"]
    if not integrity.startswith("sha512-"):
        raise SystemExit(f"@tabler/icons {version} has no sha512 integrity on npm: {integrity}")
    return integrity


def write_catalogue(version: str) -> None:
    text = CATALOGUE.read_text()
    CATALOGUE.write_text(VERSION_LINE.sub(lambda m: f"{m.group(1)}{version}{m.group(3)}", text, count=1))


def write_integrity(version: str, integrity: str) -> None:
    text = ICONDATA.read_text()
    if f'"{version}":' in text:
        return
    match = re.search(r"^INTEGRITY = \{\n", text, re.M)
    if not match:
        raise SystemExit(f"no `INTEGRITY = {{` in {ICONDATA}")
    entry = f'    "{version}": "{integrity}",\n'
    ICONDATA.write_text(text[: match.end()] + entry + text[match.end():])


def write_changelog(old: str, new: str) -> None:
    text = CHANGELOG.read_text()
    start = text.find("\n## Unreleased")
    if start < 0:
        raise SystemExit(f"no `## Unreleased` in {CHANGELOG}")
    end = text.find("\n## ", start + 1)
    end = len(text) if end < 0 else end
    section = text[start:end]
    line = (
        f"- **The icons are Tabler {new}**, from {old}: `io.kontour:tabler-icons-outline` and "
        f"`-filled`, which the library's own icons are drawn from.\n"
    )
    if line in section:
        return
    changed = section.find("\n### Changed\n")
    if changed >= 0:
        # At the end of the Changed list: before the next heading in the section.
        after = changed + len("\n### Changed\n")
        following = section.find("\n### ", after)
        at = (start + following) if following >= 0 else end
        body = text[:at].rstrip("\n") + "\n" + line
        text = body + text[at:]
    else:
        # A Changed of its own, ahead of Added if there is one.
        added = section.find("\n### Added\n")
        at = (start + added) if added >= 0 else end
        text = text[:at].rstrip("\n") + "\n\n### Changed\n\n" + line + text[at:]
    CHANGELOG.write_text(text)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--current", action="store_true", help="print the pinned version")
    group.add_argument("--latest", action="store_true", help="print the newest published version")
    group.add_argument("--is-newer", metavar="VERSION", help="exit 0 if VERSION is newer than the pinned one")
    group.add_argument("--write", metavar="VERSION", help="move every pin to VERSION")
    parser.add_argument("--metadata", nargs=2, metavar=("OUTLINE", "FILLED"), type=pathlib.Path,
                        help="read maven-metadata.xml from these files rather than Maven Central")
    parser.add_argument("--integrity", help="the @tabler/icons tarball's integrity, rather than npm's")
    args = parser.parse_args()

    if args.current:
        print(current())
    elif args.latest:
        sources = [p.read_text() for p in args.metadata] if args.metadata else [metadata(a) for a in ARTIFACTS]
        print(latest(sources))
    elif args.is_newer:
        if not STABLE.match(args.is_newer):
            raise SystemExit(f"not a release version: {args.is_newer}")
        sys.exit(0 if key(args.is_newer) > key(current()) else 1)
    else:
        new = args.write
        if not STABLE.match(new):
            raise SystemExit(f"not a release version: {new}")
        old = current()
        if key(new) <= key(old):
            raise SystemExit(f"{new} is not newer than the pinned {old}")
        release = npm_version(new)
        integrity = args.integrity or npm_integrity(release)
        write_catalogue(new)
        write_integrity(release, integrity)
        write_changelog(old, new)
        print(f"tabler-icons {old} → {new}; @tabler/icons integrity {integrity}")


if __name__ == "__main__":
    main()
