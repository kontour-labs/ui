#!/usr/bin/env python3
"""Check the iOS host's Xcode project against the repository around it.

`showcase/ios/` is the one thing in this repository that nothing can build here.
Kotlin cross-compiles Apple klibs on Linux, and *linking* a framework needs
Xcode — so the compile gate reaches `:ui-catalog:compileKotlinIosSimulatorArm64`
and stops, and the project file itself has never been opened by the thing that
reads it.

A `project.pbxproj` fails in fiddly ways when it is written by hand: a file
reference that points nowhere, a source file in the group and not in the compile
phase, a build setting whose value drifted away from the Gradle build it names.
None of those is visible until Xcode opens the project, which is a person with a
Mac, which is the slowest feedback loop in the repository.

So the structural half is checked here instead, and the structural half is most
of it:

  1.  the pbxproj parses as the old-style property list Xcode reads
  2.  every object reference resolves, and no object is referenced by nothing
  3.  there is exactly one application target, with the four phases it needs
  4.  the Gradle phase runs **before** the compile phase — the framework has to
      exist before Swift is asked to link it
  5.  every file reference exists on disk
  6.  every Swift file on disk is in the compile phase (the trap: adding a
      second file and forgetting to register it, which fails as a missing symbol)
  7.  `Info.plist` is referenced as `INFOPLIST_FILE` and is **not** in the
      resources phase, where it would be copied into the bundle twice
  8.  `FRAMEWORK_SEARCH_PATHS` points at where the Kotlin Gradle plugin actually
      writes the framework, which is
      `<module>/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`
  9.  `OTHER_LDFLAGS` links the framework whose `baseName` `:ui-catalog`
      declares — the two are written in different languages in different files
      and a rename in one is silent in the other
  10. `ENABLE_USER_SCRIPT_SANDBOXING` is off, because the Gradle phase writes
      outside the sandbox and the failure names a file rather than the cause
  11. the shell phase's Gradle task is one the module has, and is reached by a
      `cd` that lands on the repository root
  12. the shared scheme exists and names the target by its real identifier

What is left unchecked is what only Xcode can answer: whether the app launches,
whether a static Kotlin framework drags in every system framework it needs, and
whether `:ui`'s seven bundled fonts reach the app bundle through Compose
resources. Nothing in this repository has ever exercised the iOS resource path.
Those are written down in `docs/building/testing.md` rather than pretended about.
"""

import plistlib
import re
import subprocess
import sys
import xml.dom.minidom
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
HOST = ROOT / "showcase" / "ios"
PROJECT = HOST / "KontourUI.xcodeproj" / "project.pbxproj"
SCHEME = HOST / "KontourUI.xcodeproj" / "xcshareddata" / "xcschemes" / "KontourUI.xcscheme"
CATALOG_BUILD = ROOT / "ui-catalog" / "build.gradle.kts"

failures: list[str] = []


def fail(message: str) -> None:
    failures.append(message)


# ---------------------------------------------------------------------------
# 1. The parser
# ---------------------------------------------------------------------------
#
# Xcode writes pbxproj in the old NeXTSTEP plist syntax, which `plistlib` does
# not read — it handles the XML and binary forms only. It is a small grammar:
# dictionaries, arrays, bare words, quoted strings, and two comment forms. Small
# enough to parse here, and parsing it is the check.

BARE = re.compile(r"[A-Za-z0-9_$./\-]+")
OBJECT_ID = re.compile(r"[0-9A-F]{24}")


class PlistSyntaxError(Exception):
    pass


class OldStylePlist:
    def __init__(self, text: str) -> None:
        self.src = text
        self.i = 0

    def error(self, message: str) -> None:
        line = self.src.count("\n", 0, self.i) + 1
        raise PlistSyntaxError(f"line {line}: {message}")

    def skip(self) -> None:
        while self.i < len(self.src):
            if self.src[self.i] in " \t\r\n":
                self.i += 1
            elif self.src.startswith("//", self.i):
                nl = self.src.find("\n", self.i)
                self.i = len(self.src) if nl < 0 else nl
            elif self.src.startswith("/*", self.i):
                end = self.src.find("*/", self.i + 2)
                if end < 0:
                    self.error("unterminated /* comment")
                self.i = end + 2
            else:
                return

    def value(self):
        self.skip()
        if self.i >= len(self.src):
            self.error("a value was expected and the file ended")
        c = self.src[self.i]
        if c == "{":
            return self.dictionary()
        if c == "(":
            return self.array()
        if c == '"':
            return self.quoted()
        match = BARE.match(self.src, self.i)
        if not match:
            self.error(f"unexpected character {c!r}")
        self.i = match.end()
        return match.group(0)

    def dictionary(self) -> dict:
        self.i += 1
        out: dict = {}
        while True:
            self.skip()
            if self.i < len(self.src) and self.src[self.i] == "}":
                self.i += 1
                return out
            key = self.value()
            if not isinstance(key, str):
                self.error("a dictionary key has to be a string")
            self.skip()
            if self.i >= len(self.src) or self.src[self.i] != "=":
                self.error(f"expected '=' after the key {key!r}")
            self.i += 1
            out[key] = self.value()
            self.skip()
            if self.i >= len(self.src) or self.src[self.i] != ";":
                self.error(f"expected ';' after the value of {key!r}")
            self.i += 1

    def array(self) -> list:
        self.i += 1
        out: list = []
        while True:
            self.skip()
            if self.i < len(self.src) and self.src[self.i] == ")":
                self.i += 1
                return out
            out.append(self.value())
            self.skip()
            if self.i < len(self.src) and self.src[self.i] == ",":
                self.i += 1
            elif self.i < len(self.src) and self.src[self.i] == ")":
                self.i += 1
                return out
            else:
                self.error("expected ',' or ')' inside an array")

    def quoted(self) -> str:
        self.i += 1
        out: list[str] = []
        while self.i < len(self.src):
            if self.src[self.i] == "\\":
                out.append(self.src[self.i : self.i + 2])
                self.i += 2
            elif self.src[self.i] == '"':
                self.i += 1
                return "".join(out)
            else:
                out.append(self.src[self.i])
                self.i += 1
        self.error("unterminated quoted string")
        return ""


if not PROJECT.exists():
    print(f"check-xcode-host: {PROJECT.relative_to(ROOT)} does not exist")
    sys.exit(1)

parser = OldStylePlist(PROJECT.read_text())
try:
    root = parser.value()
    parser.skip()
    if parser.i != len(parser.src):
        parser.error("content after the root dictionary")
except PlistSyntaxError as problem:
    print(f"check-xcode-host: {PROJECT.relative_to(ROOT)} is not a valid property list")
    print(f"  {problem}")
    print("  Xcode will refuse to open the project with a message no less vague than this one.")
    sys.exit(1)

objects: dict = root.get("objects", {})
if not isinstance(objects, dict) or not objects:
    print("check-xcode-host: the project has no `objects` dictionary")
    sys.exit(1)


def of(isa: str) -> dict:
    return {oid: o for oid, o in objects.items() if o.get("isa") == isa}


# ---------------------------------------------------------------------------
# 2. Every reference resolves, and nothing is orphaned
# ---------------------------------------------------------------------------

referenced: set[str] = {root.get("rootObject", "")}


def walk(value, path: str) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            walk(child, f"{path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            walk(child, f"{path}[{index}]")
    elif isinstance(value, str) and OBJECT_ID.fullmatch(value):
        referenced.add(value)
        if value not in objects:
            fail(f"{path} points at {value}, which is not an object in this project")


walk(objects, "objects")
if root.get("rootObject") not in objects:
    fail(f"rootObject {root.get('rootObject')!r} is not an object in this project")

for orphan in sorted(set(objects) - referenced):
    fail(
        f"{orphan} ({objects[orphan].get('isa')}) is referenced by nothing — "
        "Xcode ignores it, so whatever it was meant to do is not happening"
    )

# ---------------------------------------------------------------------------
# 3–4. One application target, its phases, and their order
# ---------------------------------------------------------------------------

projects = of("PBXProject")
if len(projects) != 1:
    fail(f"expected exactly one PBXProject, found {len(projects)}")

apps = {
    oid: t
    for oid, t in of("PBXNativeTarget").items()
    if t.get("productType") == "com.apple.product-type.application"
}
if len(apps) != 1:
    fail(f"expected exactly one application target, found {len(apps)}")

target_id, target = next(iter(apps.items())) if apps else ("", {})
phases = [objects.get(p, {}) for p in target.get("buildPhases", [])]
kinds = [p.get("isa") for p in phases]

for needed in ("PBXSourcesBuildPhase", "PBXFrameworksBuildPhase", "PBXResourcesBuildPhase"):
    if needed not in kinds:
        fail(f"the {target.get('name')!r} target has no {needed}")

if "PBXShellScriptBuildPhase" not in kinds:
    fail(
        "the target has no PBXShellScriptBuildPhase, so nothing builds the Kotlin "
        "framework and the Swift will not link"
    )
elif kinds.index("PBXShellScriptBuildPhase") > kinds.index("PBXSourcesBuildPhase"):
    fail(
        "the Gradle shell phase runs after the compile phase. The framework has to "
        "exist before Swift is asked to link it; on a clean checkout this fails and "
        "on a dirty one it links whatever was there last time"
    )

script_phases = [p for p in phases if p.get("isa") == "PBXShellScriptBuildPhase"]
for phase in script_phases:
    if str(phase.get("alwaysOutOfDate", "0")) != "1":
        fail(
            f"the shell phase {phase.get('name')!r} is not `alwaysOutOfDate`, and it "
            "declares no outputs — Xcode skips a phase in that state rather than "
            "running it, so a change to the library would not reach the app"
        )

# ---------------------------------------------------------------------------
# 5–6. Files on disk, and files in the compile phase
# ---------------------------------------------------------------------------


def group_path(oid: str, seen: frozenset[str] = frozenset()) -> Path | None:
    """Where a file reference sits, walking the group chain back to the project."""
    for gid, group in of("PBXGroup").items():
        if oid in group.get("children", []):
            if gid in seen:
                return None
            parent = group_path(gid, seen | {gid})
            if parent is None:
                return None
            return parent / group["path"] if "path" in group else parent
    return HOST


references = of("PBXFileReference")
on_disk: dict[str, Path] = {}
for oid, reference in references.items():
    if reference.get("sourceTree") != "<group>":
        continue
    base = group_path(oid)
    if base is None:
        fail(f"the group containing {reference.get('path')} is a cycle")
        continue
    path = base / reference["path"]
    on_disk[oid] = path
    if not path.exists():
        fail(
            f"the project references {path.relative_to(ROOT)}, which does not exist — "
            "Xcode shows this as a red filename and fails the build"
        )

sources = next((p for p in phases if p.get("isa") == "PBXSourcesBuildPhase"), {})
compiled_refs = {
    objects[b]["fileRef"] for b in sources.get("files", []) if b in objects
}
compiled = {on_disk[r] for r in compiled_refs if r in on_disk}

for swift in sorted(HOST.rglob("*.swift")):
    if swift not in compiled:
        fail(
            f"{swift.relative_to(ROOT)} is not in the compile phase. A Swift file "
            "Xcode does not compile is one whose symbols go missing at link time, "
            "which reads as a problem with the Kotlin framework and is not"
        )

# ---------------------------------------------------------------------------
# 7–10. Build settings
# ---------------------------------------------------------------------------

framework_base = None
match = re.search(r"baseName\s*=\s*\"([^\"]+)\"", CATALOG_BUILD.read_text())
if match:
    framework_base = match.group(1)
else:
    fail(
        f"no `baseName = \"...\"` in {CATALOG_BUILD.relative_to(ROOT)}, so this cannot "
        "check that the project links the framework the build produces"
    )

configuration_lists = of("XCConfigurationList")
target_configs = [
    objects[c]
    for c in configuration_lists.get(target.get("buildConfigurationList"), {}).get(
        "buildConfigurations", []
    )
    if c in objects
]
if not target_configs:
    fail("the application target has no build configurations")

resources = next((p for p in phases if p.get("isa") == "PBXResourcesBuildPhase"), {})
bundled_refs = {
    objects[b]["fileRef"] for b in resources.get("files", []) if b in objects
}

for configuration in target_configs:
    name = configuration.get("name", "?")
    settings = configuration.get("buildSettings", {})

    plist_setting = settings.get("INFOPLIST_FILE")
    if not plist_setting:
        fail(f"the {name} configuration sets no INFOPLIST_FILE")
    elif not (HOST / plist_setting).exists():
        fail(f"the {name} configuration's INFOPLIST_FILE, {plist_setting}, does not exist")
    else:
        for oid, path in on_disk.items():
            if path == HOST / plist_setting and oid in bundled_refs:
                fail(
                    f"{plist_setting} is both INFOPLIST_FILE and in the resources "
                    "phase, so it is copied into the bundle twice and the second "
                    "copy is the unprocessed one"
                )

    search = settings.get("FRAMEWORK_SEARCH_PATHS", [])
    search = [search] if isinstance(search, str) else search
    expected = "/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)"
    if not any(expected in entry for entry in search):
        fail(
            f"the {name} configuration's FRAMEWORK_SEARCH_PATHS does not contain a "
            f"path ending {expected}, which is where the Kotlin Gradle plugin writes "
            "the framework — `XcodeEnvironment.frameworkSearchDir` is CONFIGURATION "
            "then SDK_NAME, and neither is optional"
        )

    flags = settings.get("OTHER_LDFLAGS", [])
    flags = [flags] if isinstance(flags, str) else flags
    if framework_base and framework_base not in flags:
        fail(
            f"the {name} configuration's OTHER_LDFLAGS does not link {framework_base}, "
            f"which is the `baseName` {CATALOG_BUILD.relative_to(ROOT)} gives the "
            "framework. A static framework is not linked unless the app says so"
        )

    if settings.get("ENABLE_USER_SCRIPT_SANDBOXING", "NO") != "NO":
        fail(
            f"the {name} configuration leaves ENABLE_USER_SCRIPT_SANDBOXING on. The "
            "Gradle phase writes outside the sandbox, and the denial names a file "
            "rather than the setting"
        )

project_configurations: list[dict] = []
if projects:
    project_object = next(iter(projects.values()))
    project_configurations = [
        objects[c]
        for c in configuration_lists.get(project_object.get("buildConfigurationList"), {}).get(
            "buildConfigurations", []
        )
        if c in objects
    ]
project_settings = [c.get("buildSettings", {}) for c in project_configurations]
for settings, configuration in zip(project_settings, project_configurations):
    if settings.get("ENABLE_USER_SCRIPT_SANDBOXING", "NO") != "NO":
        fail(
            f"the project-level {configuration.get('name', '?')} configuration leaves "
            "ENABLE_USER_SCRIPT_SANDBOXING on, and the target does not override it"
        )

# ---------------------------------------------------------------------------
# 11. The Gradle task the shell phase names
# ---------------------------------------------------------------------------

for phase in script_phases:
    script = phase.get("shellScript", "")
    script = script.encode().decode("unicode_escape")
    task = re.search(r"\./gradlew[^\n]*?(:[\w\-]+:[\w]+)", script)
    if not task:
        fail(f"the shell phase {phase.get('name')!r} runs no `./gradlew <task>`")
        continue
    module, _, name = task.group(1).rpartition(":")
    module_dir = ROOT / module.strip(":")
    if not (module_dir / "build.gradle.kts").exists():
        fail(f"the shell phase runs {task.group(1)}, and {module} is not a module here")
    if "embedAndSign" not in name:
        fail(
            f"the shell phase runs {task.group(1)} rather than an "
            "`embedAndSignAppleFrameworkForXcode` task. That task is the one that "
            "reads SDK_NAME, CONFIGURATION and ARCHS out of Xcode's environment; a "
            "plain `linkDebugFramework…` builds one architecture and copies nothing"
        )
    if "cd " in script:
        hop = re.search(r'cd\s+"?\$SRCROOT/([^"\n]*)"?', script)
        if hop:
            landed = (HOST / hop.group(1)).resolve()
            if landed != ROOT:
                fail(
                    f"the shell phase's `cd $SRCROOT/{hop.group(1)}` lands on "
                    f"{landed}, and `./gradlew` is at {ROOT}"
                )

# ---------------------------------------------------------------------------
# 12. The shared scheme
# ---------------------------------------------------------------------------

if not SCHEME.exists():
    fail(
        f"{SCHEME.relative_to(ROOT)} does not exist. Without a shared scheme "
        "`xcodebuild -scheme` has nothing to name, and Xcode invents a private one "
        "per checkout that nobody else has"
    )
else:
    try:
        scheme = xml.dom.minidom.parse(str(SCHEME))
    except Exception as problem:  # noqa: BLE001 — any parse failure is the finding
        fail(f"{SCHEME.relative_to(ROOT)} is not valid XML: {problem}")
    else:
        blueprints = {
            node.getAttribute("BlueprintIdentifier")
            for node in scheme.getElementsByTagName("BuildableReference")
        }
        if not blueprints:
            fail("the scheme names no BuildableReference")
        for blueprint in sorted(blueprints):
            if blueprint != target_id:
                fail(
                    f"the scheme's BlueprintIdentifier {blueprint} is not the "
                    f"application target's identifier ({target_id}) — Xcode opens "
                    "this as a scheme with a missing target"
                )

# ---------------------------------------------------------------------------
# Also: the Info.plist has to be a property list
# ---------------------------------------------------------------------------

for oid, path in on_disk.items():
    if path.name == "Info.plist" and path.exists():
        try:
            plistlib.loads(path.read_bytes())
        except Exception as problem:  # noqa: BLE001
            fail(f"{path.relative_to(ROOT)} is not a valid plist: {problem}")

# ---------------------------------------------------------------------------

if failures:
    print(f"check-xcode-host: {len(failures)} problem(s) in showcase/ios")
    for problem in failures:
        print(f"  · {problem}")
    sys.exit(1)

swift_files = len(list(HOST.rglob("*.swift")))
print(
    f"check-xcode-host: {len(objects)} objects, {swift_files} Swift file(s) compiled, "
    f"links {framework_base}.framework — all accounted for."
)
