# Testing the design system

Everything below runs on the JVM without an emulator or a simulator. That is the
reason `:ui` has a `jvm()` target it never ships to.

```sh
./gradlew :ui:jvmTest :ui:checkNoMaterial :ui:checkApiConventions \
          :ui:checkKdocSamples :ui-catalog:jvmTest \
          :ui-samples:compileKotlinJvm :ui-samples:checkDocSamples \
          :ui:dokkaGenerateHtml
python3 docs/check-links.py
python3 docs/check-components.py
```

| Gate | Asks |
|---|---|
| [The contract suite](#the-contract-suite) | Is every component operable, named and reachable? |
| [`checkNoMaterial`](#checknomaterial) | Did anything pull Material in? |
| [`checkApiConventions`](#checkapiconventions) | Do the signatures follow the house order? |
| [`EverythingRespondsTest`](#everythingrespondstest) | Does every control in the catalog do something? |
| [Screenshot goldens](#screenshot-goldens) | Did anything change how it looks? |
| [`:ui-samples`](#the-examples-compile) | Do the documentation's examples still compile? |
| [`checkDocSamples`](#the-examples-compile) | Is the copy on the page the code that was compiled? |
| [`check-links.py`](#the-documentation-is-checked-too) | Does every link in the repository resolve? |
| [`checkKdocSamples`](#the-examples-compile) | Does a KDoc snippet name a parameter that exists? |
| [`dokkaGenerateHtml`](#the-api-reference-is-a-gate) | Does every `[Link]` in the KDoc resolve? |
| [`check-components.py`](#every-component-has-a-page) | Does every public component have a page, an index entry, a demo and an example? |
| [`SiteRenderTest`](#the-site-is-drawn-and-looked-at) | Does every page of the site draw, at every window width? |

Per-target compilation runs only in CI
([`ci.yml`](../../.github/workflows/ci.yml)) because it is slow:

```sh
./gradlew :ui:compileKotlinJs :ui:compileKotlinWasmJs \
          :ui:compileKotlinIosArm64 :ui:assemble
```

A component that compiles on the JVM and not on Wasm is a component that ships
broken, and `commonMain` will not tell you which.

### What CI actually runs, and what it does not run twice

One workflow, six jobs. It was two workflows, which is how the API reference
came to be generated twice on every commit: `ci.yml` built it in `targets` and
uploaded it as an artifact, and `pages.yml` built it again because a workflow
cannot reach another workflow's artifacts. Dokka lives in the `site` job now —
the one that publishes it under `/api/`.

| Job | Runs on | For |
|---|---|---|
| `guard` | every event | Decides whether main has to re-prove what a pull request already did |
| `test` | unless `guard` settles it | The suite above, plus the Python checks |
| `targets` | unless `guard` settles it | Every target compiled |
| `site` | every event | The Wasm bundle and the API reference — the slowest thing here |
| `deploy` | not on a pull request | GitHub Pages |
| `publish` | a `v*` tag, or a manual version | GitHub Packages |

`deploy` needs only `site`, deliberately: a library regression should not take
the documentation offline, and a broken page should not block a release.

#### The guard

Merging a pull request produces a commit whose **tree** is byte-for-byte the
branch head's, whenever main has not moved underneath it — and that holds for a
merge commit, a squash and a rebase alike. So the work `test` and `targets`
would do on main is work already done, on the same bytes, minutes earlier.
`guard` compares the two tree SHAs through the API and asks whether that head
has a successful CI run; if both hold, main builds the site and deploys and does
nothing else.

This is what a merge queue gives you, arrived at from the other direction:
rather than testing the merge result before it lands, notice afterwards that the
result is identical to something already tested.

Every path out of it that cannot *prove* the trees match settles on "run
everything" — a direct push, a conflicted merge, main having moved, a missing
run, a failed API call, and the guard job itself failing. That last one is why
the two gated jobs read `!cancelled() && …` rather than a bare condition: a
failed dependency skips its dependants by default, which is the one direction
this must never fail in.

#### What is cached

| | Key | Why |
|---|---|---|
| `~/.konan` | the version catalogue | A compiler and a platform klib set per target, hundreds of megabytes, in no Gradle cache |
| `~/.gradle/{nodejs,yarn,binaryen}` | the version catalogue | Node is 210 MB and binaryen is 322 MB, fetched from outside GitHub; the measured restore rate for a cache entry in this repository is 360 MB/s |

Keyed on `gradle/libs.versions.toml` rather than on a lockfile because there is
no lockfile — `kotlin-js-store` is not committed, and which Node, which yarn and
which binaryen get fetched is decided by the Kotlin and Compose versions pinned
there.

`build/js/node_modules` is deliberately not cached. Gradle regenerates the
`package.json` files under `build/js` on every run, and a restored `node_modules`
disagreeing with a freshly generated manifest is a confusing failure in exchange
for a much smaller saving than the two above.

---

## The contract suite

`ComponentContractTest` runs seven assertions over every entry in
`componentRegistry` that is under contract — 32 of its 42 specimens. Every
component in the system must:

1. take `modifier: Modifier = Modifier` as its first optional parameter, and
   apply it to the outermost node;
2. accept an optional `interactionSource` and honour `enabled` — both halves:
   the callback does not fire, *and* the node reports itself disabled;
3. declare a correct semantics `Role` and `stateDescription`;
4. carry its visible label as its accessible name;
5. meet the platform minimum touch target;
6. behave under RTL, 200% font scale, and reduced motion.

A component absent from the registry is a component none of this applies to,
which is why adding one there is part of adding a component — see
[`contributing.md`](contributing.md#registering-a-component).

### One list, two consumers

The registry lives in `ui-catalog/src/commonTest/…/contract/`, and the same list
drives the [per-component renders](#screenshot-goldens). The other ten specimens
are there only to be drawn: `underContract = false` says so, and it is what lets
a `Kbd` — which has no role, no disabled state and no touch target — have a
picture without being asked to pass assertions about being operable.

One omission is therefore visible twice. A component nobody adds here is a
component nothing checks *and* nothing draws.

### What it found

The suite was written after twelve phases of components had already been
reviewed by eye, in every scheme, in a screenshot. It failed on its first run:

- **`ListItem` and `SettingRow` dropped their `clickable` when disabled.** The
  callback could not fire, so it looked right. But the node then had no role and
  no disabled flag: a disabled row announced as plain text, and there was no way
  to tell it was unavailable rather than broken.
- **`IconToggleButton` announced `Role.Switch` from a wrapper `Box`** around an
  `IconButton` that announced `Role.Button` — a switch containing a button, and
  the wrong role either way. `Switch` describes the sliding control; a star that
  calls itself one describes a widget that is not on screen.
- **A disabled `Slider` still exposed `setProgress`.** The pointer path returned
  early, so it could not be dragged — but assistive tech could still set its
  value, moving a control that looked inert.
- **No text field or select carried its label.** Compose has no `labelledBy`, so
  the label sat beside the control as an unrelated node: the user heard "Origin",
  moved on, and landed in an unnamed edit box.

---

## `checkNoMaterial`

Walks the resolved runtime graph and fails if anything pulled Material in. The
whole system exists to not be Material, and one transitive dependency would undo
that quietly.

## `checkApiConventions`

Reads every public composable's signature and enforces the parameter order in
[`contributing.md`](contributing.md#the-shape-of-a-component): `modifier` first
among the optionals, `enabled` directly after it, `interactionSource` after
everything that is not a slot — where a **builder** counts as a slot, because
`ListItemScope.() -> Unit` is the trailing lambda a caller writes their content
in even though it carries no `@Composable`.

It also refuses four parameter names outright, each with the reason:
`supportingText`, `headline`, `isEnabled`, `onDismiss`.

## `EverythingRespondsTest`

Presses every enabled control in every showcase and requires that something
observable changed — the semantics tree, or the toast a specimen with no state
of its own raises to say what was tapped.

`onClick = {}` looks alive while doing nothing: it still presses, still ripples,
still focuses. A control nobody can tell is dead has hidden two real defects in
this project already.

Whatever is **disabled** is exempt and nothing else is. If something turns up in
the failure message that genuinely should not respond, disable it — a control
that looks pressable and is not is the bug this exists to catch.

## Screenshot goldens

Two kinds, both in `ui-catalog/screenshots/`, both **compared** rather than
regenerated. A mismatch fails and writes the render plus a diff with every
changed pixel in magenta to `ui-catalog/build/screenshot-diffs/`.

- **Page goldens** render a whole showcase in each scheme. They prove a page
  still draws, and they are how a change to a shared token is caught everywhere
  at once.
- **Per-component renders**, under `screenshots/components/`, are one specimen
  on its own, light and dark, driven from the registry. These are what the
  [component pages](../../ui-docs/content/components.md) show.

A component whose defining state is not its resting one also declares a
`RenderState`, which adds `<slug>-<state>-{light,dark}.png` beside the resting
pair — `swipetodismiss-revealed`, `tab-selected`, `accordion-expanded`. Resting
keeps the bare slug, so adding one moves no existing link. Add one when the
resting picture would not let a reader tell the component from a plain row, and
not otherwise: a second image a reader could have predicted from the first is a
file to keep current for nothing.

Every one of these is **cropped to the pixels it drew**, plus a 12dp margin.
They all render into the same generous card at the same density — that is what
keeps a gallery of them comparable, since a `Button` really is smaller than a
`ListItem` — and the empty space is then trimmed off. Before that, `iconbutton`
was 1.1% button. A specimen may still ask for a taller card to *fit* in, and it
costs nothing if it over-asks, because whatever it does not use is cropped away
again.

To accept an intended change:

```sh
./gradlew :ui-catalog:jvmTest -Pkontour.screenshots.update=true
```

then **look at the result** before committing it. That step is the point — a
golden nobody looked at pins whatever was broken when it was recorded, and every
visual bug found in this project was found by looking rather than by a test.

Two things the harness does on purpose. It reports **every** mismatch from an
`@AfterTest` rather than throwing at the first, because a change that moves four
schemes would otherwise be found one run at a time. And each per-component
render is read back and required to differ from its own corner pixel: a
component that draws nothing produces a clean card, and the golden would then
*defend* the blank.

---

## Two clocks, and which one a test is on

`Scene` in `Gestures.kt` drives `ImageComposeScene` by hand: every call to
`frame()` advances a counter by 16ms and renders. Animations, gestures and
anything reading `withFrameNanos` run on **that** clock, and a frame count is
exactly the right unit for them.

`delay` does not. It resolves against the wall clock, and nothing ties the two
together — a frame costs about 45ms of real time on a throttled container and
rather less on a CI runner. So "render 95 frames" is 3.6 seconds on one machine
and under 1.5 on another, and a test that waits for a `delay(1500)` by rendering
95 frames is a test whose result depends on how fast PNG encoding is.

It has caught two things out.

`OverlayMotionScreenshotTest` hit it first and could fix it at the source: its
own scaffolding did the waiting, so the `delay` became
`repeat(n) { withFrameNanos { } }` and the golden went back on the frame clock.
Its KDoc has said so since.

`ToastStackTest.eachToastKeepsItsOwnClock` could not. The `delay(durationMillis)`
is inside `Toast` — it is what the test is *about* — so there was nothing to
convert. It rendered 95 frames and called it two seconds, which was true on the
machine it was written on and false on GitHub's runners, where it began failing
the day `:ui-catalog` grew enough other tests to change what it shared a runner
with. It had been wrong since it was written and passed by luck.

The answer for that shape is `Scene.renderUntil`: render frames until a
predicate holds, bounded by a deadline in **real** milliseconds. Wait for the
event, on a deadline measured in the units the component actually uses. And
where a test measures something *before* the wait, guard it — that test now
compares the two-toast stack against a pinned-toast-only stack first, so a
machine slow enough to lose the short toast during setup fails saying the test
proved nothing rather than passing on a coincidence.

**The rule:** frames for anything animated, `renderUntil` for anything that
times itself out.

## What the slot conversion cost, and what pays for it

`ListItem(label = "…")` could not produce a row without an accessible name.
`ListItem { leading { +icon } }` can. That is a real regression in what the type
system guarantees, and it was the known price of moving to slots.

`ComponentContractTest.everyControlAnnouncesSomething` is what pays for it: every
component in the registry that declares a role must announce a non-empty name.
The four bare selection controls — `Checkbox`, `RadioButton`, `Switch`,
`TriStateCheckbox` — opt out through `namedByContext`, because they genuinely
cannot name themselves and are designed to be labelled by the row they sit in.
An opt-out rather than an opt-in, so a component that *loses* its name fails
rather than passing quietly.

That is a stronger guarantee than the string parameter gave, because it tests the
rendered semantics rather than the signature.

### How the DSLs are tested

**"It renders the same thing."** Every component was converted to slots and
~150 call sites rewritten, and **not one screenshot golden moved**. That is not
an assertion anybody wrote; it is the existing goldens refusing to change.

**"It does the thing a golden cannot see."** `DslBehaviourTest` covers the
positions each row is handed (including the one-row and two-row cases a
three-item example never exercises) and whether a menu closes itself. Both guards
were verified by reverting: reading the lazy builder's running offset late
instead of capturing it makes every row `Middle`, and the test says so.

---

## How sheets are tested

Two suites, because sheets fail in two different ways.

`SheetAnchorTest` (in `:ui`) covers the arithmetic: detents resolving off-screen,
duplicates colliding, a peek clamped to its container, order preserved. Pure
functions, no composition.

`SheetGeometryTest` (in `:ui-catalog`) renders a real sheet and measures **both
what the state reports and where the sheet actually landed**. Both, because they
can disagree — and when they do, the state is the half that looks right. Both
bugs found while building sheets were of exactly that kind:

- The sheet was bottom-aligned *and* offset, double-counting, so a short sheet
  was drawn entirely below the screen while `offset` reported the correct number.
- The peek detent fell back silently, because the anchor was measured against a
  sheet top that had not been set yet.

Neither was visible to a pure test, and neither was obvious in the screenshot —
the first looked like "the sheet didn't open", the second like "the peek height
is a bit generous".

---

## How performance is measured

**Counted, not timed.** A stopwatch here measures this container: a software
rasteriser, no GPU, and a harness that PNG-encodes and re-decodes every frame.
A count of measures, layouts, draws or recompositions is CPU-bound Kotlin running
the identical code on a JVM and on a phone — the number taken here is the number
a phone sees. So counts are the gates, and the two timing instruments that exist
are diagnostics that print a number without failing a build.

| Instrument | Where | Counts |
|---|---|---|
| `PhaseCounts` + `Modifier.countPhases` | `ui/src/commonTest/…/PhaseCounts.kt` | measures, placements, draws |
| the `Counted` pattern | `OverlayRecompositionTest` | recompositions |
| `SheetState.anchorRebuilds` | `:ui`, production code | anchor rebuilds per frame |
| `BackdropCostDiagnostic` | `:ui-catalog` | *times* frames — diagnostic only |
| `FrameReadout` | `:ui-catalog`, on screen | real frames, on a real device |

`countPhases` has to be applied through a component's **real public content
slot**. A replica assembled by the test is the easiest way to write a performance
test that passes while the component it is named after stays slow.

Two counters have a control assertion in front of them, and should keep one: the
first draft of `SheetFramePressureTest` passed with every counter at zero on a
sheet that never moved.

### What the compiler already knew

```sh
./gradlew :ui:compileKotlinJvm -Pkontour.compose.reports
build/compose-reports/ui/*-composables.txt   # what is skippable, and why not
build/compose-reports/ui/*-classes.txt       # what is stable
```

Behind a property because it slows every Compose compilation. The first run of it
found 23 unstable parameters — 12 `kotlinx.datetime` and one `IntRange`, all of
them immutable types the compiler simply had no way to know about, because they
do not depend on Compose and so carry no annotation. `compose-stability.conf` at
the repository root names them and takes those 13 to zero.

That mattered more than it looks. Strong skipping is on, so an unstable parameter
is not *skipped never* — it is compared by **instance identity**. A caller
writing `LocalDate(year, month, 1)` inline hands a fresh instance every
recomposition and the whole calendar re-runs for the same day. Compared by
`equals`, it skips.

The ten that remain are all `key: Any`, and they are right as they are: a key
*should* be compared by identity.

### On a device

The frame readout is the only instrument that sees a GPU, and only Android can
run it — there is no iOS runner in this repository, only the framework an Xcode
project would link.

```sh
./gradlew :showcase:android:installRelease   # release, not debug
```

Judging performance from a debug build is judging the wrong thing.

---

## The documentation is checked too

```sh
python3 docs/check-links.py
```

Resolves every relative link and `#anchor` in **every markdown file in the
repository**, module READMEs included. A dead link in a document about how to
use something is the same class of defect as a dead callback in the catalog, and
just as checkable.

It used to walk `docs/` alone, which is how `ui/README.md` came to spend the
whole extraction pointing at a directory that no longer existed — and one level
outside the repository at that. Nothing failed, because nothing looked.

### The examples compile

```sh
./gradlew :ui-samples:compileKotlinJvm :ui-samples:checkDocSamples
```

The examples on the pages under `ui-docs/content/` are not written in the
documentation. They live in `ui-samples/`, which depends on `:ui` the way an app
would — so an example that reaches for something `internal`, names a parameter
that has been renamed, or calls a function that no longer exists fails the build
rather than being discovered by whoever pastes it into their app. Writing the
first batch found three such: a `Carousel` scroll method, a `NavRail` parameter
and a `TopBar` one, all of them plausible and none of them real.

A page claims a sample with a comment before the fence:

```
<!--sample:ButtonBasics-->
```

`checkDocSamples` then requires the fenced block to be that function's body,
verbatim. To update the pages after editing a sample:

```sh
python3 docs/sync-samples.py --write
```

Compare by default, rewrite behind a flag — the same shape as the screenshot
goldens, and for the same reason.

**A fence with no marker is left alone**, deliberately. Plenty of blocks in
these pages are not examples: a signature under discussion, a fragment with an
ellipsis, a shell command. Requiring every one to compile would mean inventing a
context for each, and the useful examples would drown in the scaffolding.

`:ui:checkKdocSamples` still covers the snippets inside KDoc, which the compiler
cannot see. It parses rather than compiles, so it only catches a wrong or
missing argument name — the weaker check, kept for the place the strong one
cannot reach.

### The API reference is a gate

```sh
./gradlew :ui:dokkaGenerateHtml     # ui/build/dokka/html
```

The API reference, from the KDoc that is already there. It is not published
anywhere yet, and generating it in CI regardless is what stops it rotting before
it is: Dokka fails on a malformed `@param` or a `[Link]` to a symbol that no
longer exists, which is a class of KDoc defect nothing else here sees. The run
uploads the site as a build artifact.

It earns its place by being **complete** in the one way the hand-written pages
are not — every public symbol appears, whether or not anybody remembered to
write it up. The two do different jobs: Dokka knows every signature and no
reasons; `ui-docs/content/` carries the comparisons, the "reach for this instead" and
the bug histories. `ui/Module.md` is the module and package overview, and it
says so on the reference's own landing page.

`failOnWarning` is on, so this is a **gate and not a report**. It is in the list
at the top of this page for a reason: it used to run in CI only, and six broken
`[Link]`s accumulated before anyone saw them — `[Fab]` for a component called
`FloatingActionButton`, `[Select]` for a symbol in another package, and
`[WindowInsets.sheetEdges]` for an extension that is actually on the companion.
Every one of them was written by somebody who ran the whole local list and got a
clean pass.

A link to a symbol in another package needs the path, and the display form keeps
the page readable: `[Select][io.kontour.ui.components.text.Select]`.

### The site is drawn, and looked at

```sh
./gradlew :ui-docs:jvmTest        # ui-docs/build/site-shots/<class>/index.html
```

Every page of the documentation site, at 390, 700, 1024 and 1440 — one width
inside each `WindowWidthClass` bucket — with a contact sheet per width.

`:ui-docs` was a `wasmJs`-only module until Round 16, which is not a small
detail: it had no test source set that could run on a laptop, so there was
nowhere to put a test even if somebody had wanted one. The site shipped with a
landing page that threw on any window narrower than 600dp, a `ProseWidth` that
had never once applied, and half its pages showing nothing at all — and every
gate in this file was green throughout.

Two things are asserted, and they are the two that are never intentional:

- **it renders** — an exception from any page at any width fails, which is the
  class of defect that shipped;
- **it drew something** — a page whose content area is one flat colour is a page
  with no content. The check starts below the top bar and to the right of the
  index, because chrome draws on every page and satisfied the naive version.

**Not goldens.** A component's appearance should not change quietly; a
documentation page changes every time somebody improves a sentence, and 122
pages times four widths of churning PNGs is a review tax paid in rubber stamps
within a fortnight.

The contact sheets are the point, and going through them is a person's job. The
complaint that started Round 16 was "most components don't have live previews,
and the ones that do aren't interactive" — which is true, was true, and is not
a sentence any assertion in that file could have written. CI uploads them as an
artifact on every run for the same reason.

### Every component has a page

```sh
python3 docs/check-components.py
```

Ten rules.

1. Every component in `componentRegistry` has a page whose title names it.
2. No symbol is the subject of two pages.
3. Every component page is linked from a category index.
4. Every page has an interactive demo, and every demo has a page.
5. **Every public `@Composable` in `:ui` is claimed by some page.**
6. Every page shows an example that compiles.
7. Every page says what is particular about its accessibility.
8. Every page title names a declaration that exists in `:ui`.
9. The README's component count is the tree's component count.
10. Every name an accessibility section mentions exists in `:ui`.

The fifth is the one that matters, and it exists because the first four were all
green while a fifth of the library was undocumented. Rules 1–4 chain
`registry → pages → indexes → demos`, and every link in that chain is a *list* —
so nineteen overlays and sheets, five foundation primitives and six adaptive
components, none of which anybody had added to the registry, were invisible to
every gate in the repository at once. Round 16 found them by hand.

Rule 5 is anchored to the compiled surface instead. The public composables in
`:ui` are the source of truth and the pages have to cover them, so adding a
component and forgetting to document it now fails the build. A symbol is
"claimed" by being in a page's title or in its `*Also on this page: …*` line, and
the search runs over all of `ui-docs/content` rather than only `components/` —
`KontourTheme` is a theme rather than a component, and `theming.md` is where
anyone would look for it.

The three ways to satisfy it are the three honest answers: give the component a
page, name it on the page of the thing it accompanies, or make it `internal`
because callers were never meant to reach it.

Rules 4, 6 and 7 are **ratchets**. Not every page had a demo the day rule 4
arrived, and a list of exempted names is how a defect becomes permanent — so
what is recorded is a ceiling that only ever goes down. You cannot exempt your
own page, only make the total worse.

The three ceilings say quite different things, and the numbers are the point:

| | Ceiling | Reads as |
|---|---|---|
| `MAX_WITHOUT_DEMO` | 1 | `DateTimeFormats` is a set of patterns; there is nothing to press |
| `MAX_WITHOUT_SAMPLE` | 0 | reached — every page shows compiled code |
| `MAX_WITHOUT_ACCESSIBILITY` | 0 | reached, in the round the ceiling arrived at 102 |

The accessibility ceiling is worth a note, because it is the one that moved
furthest. Writing it down as 102 was not an excuse to leave it there: the
sections were then written from what each component's source actually does —
which role it reports, what it announces, what its live region says — and doing
it that way is what turned up two real defects. `TimeField` drew its label as a
sibling `Text`, so the control's announced name was its own value; and
`MultiSelect` reported `Role.RadioButton` per option, telling every screen-reader
user that choosing a second mode of transport would clear the first.

Rules 6 and 7 are what "every page is the same shape" is enforced by, and the
shape is only half enforced — the other half is generated. `:ui-docs` renders a
`## API` section on every page from
[`generateApiTables`](#the-parameter-tables-are-generated), so a page that never
grows another paragraph still carries a working demo, a compiled example and a
complete parameter table.

Rules 9 and 10 are the anti-fiction pair. The README said "138 components" for
four rounds and matched nothing measurable — not the registry (49), not the pages
(103) — so the number now lives between `<!--counts-->` markers and fails the
build when it stops being true. Rule 10 does the same job for the accessibility
sections, which are dense with parameter names by design: a name renamed in
`:ui` while the prose describing it stayed put is exactly how those pages would
rot fastest.

Rule 8 is the cheapest of the ten and the only one pointing this direction.
Rules 1-4 ask whether everything in the library has a page; this asks whether the
page is about anything, which catches a component renamed in `:ui` whose page
kept the old spelling. Nothing here could detect that before.

### The parameter tables are generated

```sh
./gradlew :ui-docs:generateApiTables
```

Every component page on the site carries a full parameter table — name, type and
default — and none of them is written by hand. `KotlinSignatures` reads `:ui`'s
own source and emits `ApiTables.kt` into the site's generated sources.

That parser lives in `buildSrc` rather than in a build script because three tasks
in two modules use it: `checkApiConventions` and `checkKdocSamples` in `:ui`, and
this. One reader means the table on the page cannot disagree with the rules the
conventions check enforces, and a renamed parameter changes the page on the next
build rather than on the day somebody notices.

Before this the tree contained no parameter table at all — not one, on any page.
That is not an oversight anybody made: 103 hand-written tables are 103 things to
keep in step with a library that changes every round, and the first stale one
makes all of them suspect.
