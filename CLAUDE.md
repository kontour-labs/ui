# Working on Kontour UI

A Compose Multiplatform design system on Compose **Foundation**, with no
Material, for Android, iOS, desktop and the web. Published as `io.kontour:ui`,
`io.kontour:ui-nav3` and `io.kontour:haptics` to `https://maven.kontour.io`.

This file is the working summary and the list of traps. The full reasons are
in these files, which win where this file disagrees with them:

| | |
|---|---|
| [`docs/building/contributing.md`](docs/building/contributing.md) | How a component is shaped: parameter order, names, tokens, state, accessibility, and its checklist |
| [`docs/building/testing.md`](docs/building/testing.md) | Every gate, what CI runs and when, the goldens, the clocks |
| [`docs/building/releasing.md`](docs/building/releasing.md) | Tags, `maven.kontour.io`, a failed release |
| [`docs/README.md`](docs/README.md) | The library's layers and the rules that follow from them |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Running the gallery and the everyday checks |

## The modules

| | |
|---|---|
| `ui/` | The library: theme, tokens, every component. `src/commonMain/kotlin/io/kontour/ui/` |
| `ui-nav3/` | Navigation 3 scene strategies over the pane scaffolds |
| `haptics/` | Platform haptics, no Compose in it |
| `ui-catalog/` | The gallery's demos, and most of the tests: the contract suite, the screenshot goldens, the sweeps |
| `ui-samples/` | The documentation's examples, compiled against `:ui` and copied into the pages |
| `ui-docs/` | The site (`docs.kontour.io/ui`), a Wasm app. Its pages are `ui-docs/content/*.md` |
| `showcase/` | Hosts for the gallery: desktop, Android, an iOS Xcode project |
| `docs/` | Python checks and generators, and the contributor docs under `docs/building/` |

## Conventions that bite

- **No Material**, ever, even transitively: `checkNoMaterial` fails the build.
- **British spelling in the API.** `Text(colour = …)`, `ButtonColours`,
  `Theme.colours`. A sample with `color =` fails `checkKdocSamples`.
- **Tokens, not literals**: `Theme.colours`, `Theme.spacing`, `Theme.typography`,
  `Theme.shapes`, `Theme.motion`. English a component says on its own goes in
  `Theme.strings`.
- **Parameter order** is checked by `:ui:checkApiConventions`: `modifier` first
  among the optionals, `enabled` straight after it, `interactionSource` after
  every non-slot. Its eighteen rules are listed in `contributing.md`.
- **Every public change goes into the API dump**: run `./gradlew :ui:updateKotlinAbi`
  (and `:ui-nav3:`, `:haptics:`) and read the diff. A break gets a **Breaking**
  line in `ui-docs/content/changelog.md`'s Unreleased section and a before and
  after in `ui-docs/content/migrating.md`.
- **Comments and docs say why**, in plain sentences, and the long ones carry
  history ("Raised 2026-10-08 for 0.4.3 …"). Match the voice of the file you're in.
- **Commit messages** are `Area: what changed, in words`, with a body that
  explains: `SwipeActions: past the reveal the full swipe's action grows and
  pushes the others aside`.

## Building here

The library builds and tests on the JVM. In a cloud sandbox:

- **There is no Android SDK.** Use the `jvm` tasks; Android compiles only in CI.
- **Maven Central may be unreachable.** What is already in `~/.gradle`
  resolves, so `--offline` works once the cache is warm. Never point Gradle at a
  mirror or change the repositories to get round it.
- **The web build may not work.** The Wasm and JS klibs come from Central, and
  without them neither `:ui-docs:wasmJsBrowserDistribution` nor
  `:ui-docs:compileProductionExecutableKotlinWasmJsOptimize` runs. That is why
  the bundle size needs the section below.
- **Don't edit the tree while a Gradle build runs against it.** Wait for it to
  finish, or stop it first.
- Kill nothing you didn't start, and avoid `git checkout --ours`/`--theirs`,
  `reset --hard` and the like on work you haven't committed.

## Before a push: what CI will fail on

The everyday run, about ten minutes:

```sh
./gradlew :ui:jvmTest :ui:checkNoMaterial :ui:checkApiConventions :ui:checkKdocSamples \
          :ui-catalog:jvmTest :ui-docs:jvmTest :ui-samples:compileKotlinJvm \
          :ui-samples:checkDocSamples :ui-nav3:jvmTest :haptics:jvmTest
python3 docs/check-links.py
python3 docs/check-components.py
python3 docs/check-settings.py
python3 docs/export-strings.py --check
python3 docs/sync-samples.py
```

Add `-Pkontour.tests.full=true` for the 27 slow classes when a change touches
how components lay out, move, take input or announce themselves. That is about
fifty minutes in CI, and it is the run that finds the problems.

### Screenshot goldens

- Re-record with `-Pkontour.screenshots.update=true` on the same task, then
  **look at every PNG that changed** before committing it.
- **The catalog's whole-screen goldens draw its text.**
  `ui-catalog/screenshots/catalog-large.png` and `catalog-compact.png` show the
  About page, so changing any words on it moves them, as well as the
  `AboutShowcase` goldens. Re-record by running all of `:ui-catalog:jvmTest`,
  not just the class you think is affected.
- CI uploads a `screenshot-diffs` artifact with the `-actual` and `-diff`
  images. A golden re-recorded here has matched CI's pixel for pixel, so you
  can check your new golden against its `-actual` image.

### The documentation's code

- **`checkKdocSamples` reads every fenced Kotlin block** in KDoc,
  `ui-docs/content/`, `docs/` and the READMEs, **"not this" counterexamples
  included**. A wrong example still has to name parameters that exist.
- A block under `<!--sample:Name-->` is a copy of the public function `Name` in
  `ui-samples/`. Edit the source, then `python3 docs/sync-samples.py --write`.
- `dokkaGenerateHtml` fails on a `[Link]` in KDoc that doesn't resolve.

### Timeouts

The catalog's JVM tests run under a five-minute `runTest` cap
(`kotlinx.coroutines.test.default_timeout` in `ui-catalog/build.gradle.kts`).
**`UncompletedCoroutinesError: After waiting for 5m` is a cost problem, not a
flake.** Run the test here, `jstack` the `Gradle Test Executor` while it runs,
and find what got expensive: in 0.4.5 it was `ActivityCalendar` publishing 369
custom actions, each of which `SemanticsActionSweepTest` followed by thirty
frames of the whole page. Don't raise the cap, and never skip or disable a
test.

## The bundle size

**CI's site job fails when the Wasm bundle grows past a ceiling**, and it fails
on most releases because the library grows. The ceilings are in
[`docs/check-bundle-size.py`](docs/check-bundle-size.py), in gzip bytes, with
the history of every raise above them:

| | What it is | Moves when |
|---|---|---|
| `app` | The compiled library, gallery and site | Almost every release. The one to watch |
| `skiko` | Skia's renderer | Compose or Skiko changes version |
| `js` | The loader | Kotlin or webpack changes |
| `fonts` | The bundled fonts | A font is added |
| `other` | Everything else | Rarely |
| `total` | The sum | With `app` |

There is also `MAX_FILES` (14) for the number of files in the distribution.

### Check it before CI does

`app` grows by about **15–25 gzip bytes for every net line** added to
`ui/src/commonMain`, `ui-catalog/src/commonMain`, `ui-docs/src/commonMain` and
`ui-docs/content`. That was 0.4.0 at 23, 0.4.3 at 18 and 0.4.5 at 16; use 25 to
be safe. So before pushing a branch that adds a lot:

```sh
# Net lines since the ceilings last moved
last=$(git log -1 --format=%h -G'^    "app": ' -- docs/check-bundle-size.py)
git diff --shortstat "$last"..HEAD -- ui/src/commonMain ui-catalog/src/commonMain \
    ui-docs/src/commonMain ui-docs/content
```

Multiply the net lines by 25 and compare that with `app`'s headroom: its
ceiling minus the last measured figure, which is in the newest note above
`CEILINGS`. After 0.4.5 that was 3,900,000 − 3,779,287 = 120,713, which is
about 4,800 lines.

**Where the toolchain works** (a developer machine, or a sandbox that can reach
Central), measure it directly:

```sh
./gradlew :ui-docs:wasmJsBrowserDistribution
python3 docs/check-bundle-size.py
```

**Where it doesn't**, CI measures it. The *Build the site* job takes about nine
minutes and runs beside *Test*, which takes about fifty, so read its *Check the
bundle size* step first instead of waiting for the whole run.

### Raising a ceiling

Only when the growth is the point of the work, and only from a real
measurement: CI's, or `check-bundle-size.py` run on a full distribution.

1. Set each ceiling that is over by `--update`'s rule,
   `int(measured * 1.03 / 10_000 + 1) * 10_000`. Leave the others, especially
   `skiko`, `js`, `fonts` and `other`, unless they are over too. With a full
   distribution, `python3 docs/check-bundle-size.py --update` does it; from CI's
   log, do it by hand for `app` and `total`.
2. Above `CEILINGS`, add a dated note in the same shape as the last one. Give
   the release, where the figure came from (CI and its commit, or here), each
   measured figure against its old ceiling and the growth on the last
   measurement, which groups stayed inside, and where the growth came from by
   source area in lines (`git diff --shortstat` per directory).
3. Commit it by itself (`Bundle size: raise app and total for 0.4.6`), in the
   branch that grew.

**A jump that the source lines don't explain is a bug, not a raise.** For
example, `skiko` or `js` moving without a version bump, or `app` jumping on a
small change. Something arrived that nobody asked for (a dependency, a font, a
resource), and the job is to find it.

## CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml), on GitHub's runners.

- **It runs on pushes to `main`, on `v*` tags, and when started by hand**, and
  never on a pull request or a push to another branch. To check a branch:
  Actions → CI → Run workflow, on the branch, with **full** ticked. A run
  started by hand skips nothing.
- Jobs: *Compile every target* (about 8 min), *Build the site* (about 9),
  *Test* (about 15, or 50 with full), then *Deploy* and *Publish*. Each gate
  leaves a `passed-<gate>-<fingerprint>` marker, and a push skips the gates
  whose inputs already passed.
- A failed *Test* writes *Say what failed* to the run summary, and uploads
  `screenshot-diffs` when a golden moved.
- **`Read timed out` fetching from `maven.kontour.io`** is the bucket's
  Cloudflare cache, not the code. A `.klib` that isn't cached at the edge comes
  from R2 at tens of KB/s. `curl -sSI` the file and look at `cf-cache-status`:
  it should be `HIT`. `releasing.md`'s *Its cache* step has the rule that fixes
  it.
- **A new file at the top of the repository counts for every gate** until it
  is placed in the `own` list in `ci.yml`'s `changes` job. A file Gradle never
  reads, like this one, also goes in the deploy step's list of paths that don't
  rebuild the site.

## Releasing

Change the changelog's `## Unreleased` to the version and its date, give every
break a section in `migrating.md`, check the API dumps, and push a `vX.Y.Z` tag.
CI publishes to `maven.kontour.io` (an R2 bucket that refuses a version it
already has) and deploys the docs for that version. `releasing.md` has the
rest. The icons, `io.kontour:tabler-icons-*`, come from
`kontour-labs/tabler` in the same bucket. `docs/bump-tabler-icons.py` moves
the pin.
