# Testing the design system

Five gates, all running on the JVM without an emulator or a simulator. That is
the reason `:ui` has a `jvm()` target it never ships to.

```sh
cd app
./gradlew :ui:jvmTest :ui:checkNoMaterial :ui:checkApiConventions \
          :ui-catalog:jvmTest
```

| Gate | Asks |
|---|---|
| [The contract suite](#the-contract-suite) | Is every component operable, named and reachable? |
| [`checkNoMaterial`](#checknomaterial) | Did anything pull Material in? |
| [`checkApiConventions`](#checkapiconventions) | Do the signatures follow the house order? |
| [`EverythingRespondsTest`](#everythingrespondstest) | Does every control in the catalog do something? |
| [Screenshot goldens](#screenshot-goldens) | Did anything change how it looks? |

Per-target compilation is the sixth, and runs only in CI
([`ci.yml`](../../.github/workflows/ci.yml)) because it is slow:

```sh
./gradlew :ui:compileKotlinJs :ui:compileKotlinWasmJs \
          :ui:compileKotlinIosArm64 :ui:assemble
```

A component that compiles on the JVM and not on Wasm is a component that ships
broken, and `commonMain` will not tell you which.

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
  [component pages](../using/components.md) show.

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

The examples on the pages under `docs/using/` are not written in the
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
