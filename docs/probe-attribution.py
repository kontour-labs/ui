#!/usr/bin/env python3
"""What is in a probe's Wasm, by package: the answer to "is X in every app?".

`check-bundle-size.py --probes` says how much each probe weighs. This says what
the weight is. It reads the probe's binary from before Binaryen optimised it,
because that one keeps the name section, adds up the size of every function's
body under the package (or, with `--classes`, the class) its name begins with,
and prints the biggest.

    python3 docs/probe-attribution.py probes/theme [--classes] [--top N] [--match TEXT]

The byte counts are of unoptimised code, so they are larger than what ships and
do not add up to the probe's figure. Compare them with each other, and with the
same probe before and after a change: a package that is there at all in `theme`
is one every app on the library pays for.
"""

import argparse
import collections
import pathlib
import sys


def leb(data: bytes, at: int) -> tuple[int, int]:
    """An unsigned LEB128 at `at`: its value, and where the next thing starts."""
    value, shift = 0, 0
    while True:
        byte = data[at]
        at += 1
        value |= (byte & 0x7F) << shift
        if byte < 0x80:
            return value, at
        shift += 7


def sections(data: bytes):
    if data[:4] != b"\0asm":
        raise ValueError("not a Wasm binary")
    at = 8
    while at < len(data):
        kind = data[at]
        size, start = leb(data, at + 1)
        yield kind, data[start:start + size]
        at = start + size


def imported_functions(body: bytes) -> int:
    """How many functions are imports, which come first in the index space."""
    count, at = leb(body, 0)
    functions = 0
    for _ in range(count):
        for _ in range(2):  # module name, field name
            length, at = leb(body, at)
            at += length
        kind = body[at]
        at += 1
        if kind == 0:  # function: a type index
            _, at = leb(body, at)
            functions += 1
        elif kind == 1:  # table: a reference type and limits
            at += 1
            flags, at = leb(body, at)
            _, at = leb(body, at)
            if flags & 1:
                _, at = leb(body, at)
        elif kind == 2:  # memory: limits
            flags, at = leb(body, at)
            _, at = leb(body, at)
            if flags & 1:
                _, at = leb(body, at)
        elif kind == 3:  # global: a value type, which may be a reference to a type
            if body[at] in (0x63, 0x64):
                at += 1
                _, at = leb(body, at)
            else:
                at += 1
            at += 1  # mutability
        elif kind == 4:  # tag: an attribute and a type index
            at += 1
            _, at = leb(body, at)
        else:
            raise ValueError(f"an import of kind {kind}, which this does not read")
    return functions


def body_sizes(body: bytes) -> list[int]:
    count, at = leb(body, 0)
    sizes = []
    for _ in range(count):
        size, at = leb(body, at)
        sizes.append(size)
        at += size
    return sizes


def function_names(body: bytes) -> dict[int, str]:
    length, at = leb(body, 0)
    if body[at:at + length] != b"name":
        return {}
    at += length
    names = {}
    while at < len(body):
        kind = body[at]
        size, start = leb(body, at + 1)
        if kind == 1:
            count, cursor = leb(body, start)
            for _ in range(count):
                index, cursor = leb(body, cursor)
                length, cursor = leb(body, cursor)
                names[index] = body[cursor:cursor + length].decode("utf-8", "replace")
                cursor += length
        at = start + size
    return names


def owner(name: str, classes: bool) -> str:
    """`io.kontour.ui.theme.Strings.<init>` → `io.kontour.ui.theme`, or with
    `classes`, `io.kontour.ui.theme.Strings`: the dotted prefix up to the first
    capitalised part, and that part too for a class."""
    parts = []
    for part in name.split("."):
        if part[:1].isupper():
            if classes:
                parts.append(part)
            break
        if not part or not (part[0].isalpha() or part[0] == "_"):
            break
        parts.append(part)
    return ".".join(parts) or "(unnamed)"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("probe", help="a probe's directory, such as probes/theme")
    parser.add_argument("--classes", action="store_true", help="by class rather than by package")
    parser.add_argument("--top", type=int, default=30)
    parser.add_argument("--match", help="only names containing this")
    options = parser.parse_args()

    build = pathlib.Path(options.probe) / "build"
    binaries = sorted(build.glob("**/productionExecutable/kotlin/*.wasm"))
    if not binaries:
        print(f"no unoptimised binary under {build}: build the probe first (see probes/README.md)")
        return 2

    totals: collections.Counter[str] = collections.Counter()
    unnamed = 0
    for binary in binaries:
        data = binary.read_bytes()
        imports, sizes, names = 0, [], {}
        for kind, body in sections(data):
            if kind == 2:
                imports = imported_functions(body)
            elif kind == 10:
                sizes = body_sizes(body)
            elif kind == 0:
                names.update(function_names(body))
        if not names:
            print(f"{binary} has no function names, so nothing can be attributed")
            return 2
        for offset, size in enumerate(sizes):
            name = names.get(imports + offset)
            if name is None:
                unnamed += size
                continue
            if options.match and options.match not in name:
                continue
            totals[owner(name, options.classes)] += size

    whole = sum(totals.values())
    print(f"  {options.probe}: {whole:,} bytes of function bodies, unoptimised\n")
    for name, size in totals.most_common(options.top):
        print(f"  {size:10,}  {size / whole:6.1%}  {name}")
    if unnamed:
        print(f"\n  {unnamed:,} bytes in functions with no name")
    return 0


if __name__ == "__main__":
    sys.exit(main())
