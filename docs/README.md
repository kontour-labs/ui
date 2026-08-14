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
| 8 | Collections | not started |
| 9 | Overlays | done, with `Select`, `Combobox`, `MultiSelect` and the text toolbar |
| 10 | Sheets | not started |
| 11 | Navigation | not started |
| 12 | Adaptive layout and motion | not started |
| 13 | Catalog, screenshot goldens, CI | not started |

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
