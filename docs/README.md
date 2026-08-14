# Kontour UI

The design system in [`app/ui`](../../../app/ui). A custom component library
built directly on Compose Foundation, with no Material dependency.

## Why it exists

Material is a complete, opinionated design language. Adopting it means adopting
its colour roles, its type scale, its motion, its component shapes — and then
fighting all of them to look like Kontour. Commit `34862a9` decided not to.

What replaces it has to earn that decision: a component library good enough that
nobody misses `androidx.compose.material3`, themed so it can express what
`kontour.io` looks like today, and disciplined enough that the next person to add
a component does not have to invent the conventions themselves.

## The mental model

Four layers. Each one only knows about the layers below it.

```
  components/  nav/  overlay/  sheet/     what you call
  ─────────────────────────────────────
  foundation/                            Text, Icon, Surface, Divider
  ─────────────────────────────────────
  interaction/  input/  a11y/  motion/   how it responds and who can use it
  ─────────────────────────────────────
  theme/                                 what it looks like
```

Three rules follow from that shape, and they are the whole system:

**1. Components read tokens, never literals.** No component contains a `Color`,
a `Dp` radius, or a duration. It reads `Theme.colors.surface`,
`Theme.shapes.medium`, `Theme.motion.default`. That indirection is what makes a
theme swappable, a contrast tier possible, and reduced motion automatic.

**2. Components describe intent, not mechanism.** A button asks for
`FeedbackIntent.Confirm`, not a haptic constant. A sheet asks for
`Theme.motion.springGentle`, not a stiffness of 300. The platform-specific and
preference-specific translation happens once, in one place.

**3. Accessibility is structural, not a review checklist.** Contrast is asserted
by a test over every token pairing. Touch targets are enforced by a modifier
every interactive component applies. Focus rings appear based on tracked input
modality rather than a guess. None of these are things a contributor has to
remember — they are things the code does.

## What exists today

| | |
|---|---|
| [`tokens.md`](tokens.md) | Colour, type, spacing, shape, elevation, motion, sizing — the full reference |
| [`theming.md`](theming.md) | Building a theme; how a generated palette slots in later |
| [`accessibility.md`](accessibility.md) | The contract every component meets, and how it is enforced |
| [`components.md`](components.md) | The component inventory |
| [`overlays.md`](overlays.md) | Dialogs, menus, tooltips and toasts — the stack, the queue, and which to reach for |
| [`sheets.md`](sheets.md) | Bottom and side sheets, and the detent model the map screens need |
| [`contributing.md`](contributing.md) | The checklist for adding a component |

See the *Status* table below for what is built today.

## Status

| Phase | | Status |
|---|---|---|
| 0 | Module scaffolding, all six targets | done |
| 1 | Tokens and theme | done |
| 2 | Foundation primitives and mechanisms | done |
| 3 | Actions — buttons, FABs, links | done |
| 4 | Selection controls | core built |
| 5 | Date and time | core built |
| 6 | Text editing | core built |
| 7 | Display and content | core built |
| 8 | Collections | done — `DataTable`/`TreeList` deliberately skipped |
| 9 | Overlays | done, with `Select`, `Combobox`, `MultiSelect` and the text toolbar |
| 10 | Sheets | done |
| 11 | Navigation | done |
| 12 | Adaptive layout and motion | done |
| 13 | Contract suite, catalog, screenshot goldens, CI | done |

## Verifying

```sh
cd app
./gradlew :ui:jvmTest :ui:checkNoMaterial :ui-catalog:jvmTest
```

Three gates, all running on the JVM without an emulator or a simulator:

**The contract suite** asserts six rules over every entry in
`componentRegistry` — modifier reaches the outermost node, disabled blocks the
callback and says so, a role is declared, a visible label names the control, the
touch target meets the minimum, and it survives 200% type in RTL. Adding a
component means adding a line there; see
[`contributing.md`](contributing.md#registering-a-component).

**`checkNoMaterial`** walks the resolved runtime graph and fails if anything
pulled Material in. The whole system exists to not be Material, and one
transitive dependency would undo that quietly.

**Screenshot goldens** in `ui-catalog/screenshots/` are compared, not just
regenerated. A mismatch fails and writes the render plus a diff with every
changed pixel in magenta to `ui-catalog/build/screenshot-diffs/`. To accept an
intended change:

```sh
./gradlew :ui-catalog:jvmTest -Pkontour.screenshots.update=true
```

then **look at the result** before committing it. That step is the point — a
golden nobody looked at pins whatever was broken when it was recorded, and every
visual bug found so far was found by looking rather than by a test.

Per-target compilation is the fourth gate and runs in CI
([`.github/workflows/app.yml`](../../../.github/workflows/app.yml)):

```sh
./gradlew :ui:compileKotlinJs :ui:compileKotlinWasmJs \
          :ui:compileKotlinIosArm64 :ui:assemble
```

## The catalog

`Catalog()` in `:ui-catalog` is every component, in every state, running on all
five targets from one source. It carries the switches that are hardest to check
by eye and easiest to get wrong — dark, high contrast, text size to 200%,
right-to-left, reduced motion, and a forced input modality — so a component can
be seen under each without changing a system setting.

The modality switch matters more than it looks: focus rings, hover, scrollbar
visibility and tooltip triggers all branch on it, and on a desktop host you
would otherwise only ever see the pointer branch.

## Using it

```kotlin
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme

KontourTheme {
    // everything in io.kontour.ui works in here, and only in here
}
```

`KontourTheme` resolves dark mode, contrast tier and reduced motion from the
operating system by default, and follows them live. Components outside it throw
rather than falling back to a default palette — silently rendering in the wrong
theme is a worse failure than not rendering.
