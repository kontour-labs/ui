# Module kontour-ui

A Compose Multiplatform design system built directly on Foundation, with no
Material dependency. Android, iOS, desktop, JS and Wasm.

**This is the reference, not the documentation.** It lists every public symbol
and its signature, which is the half a generator can do well. What it cannot
carry is why one component exists rather than another, when to reach for
something else, and the defects that shaped a decision — those are in the
hand-written pages, and a component page there is worth reading before its entry
here:

- **Using it** — `docs/using/`: components by category, tokens, theming,
  accessibility, the `+` vocabulary, overlays and sheets.
- **Building it** — `docs/building/`: how to add a component, and what the
  guards check.

Everything below is derived from the KDoc on the sources.

# Package io.kontour.ui.theme

Tokens and `KontourTheme`. Colour, typography, spacing, shape, elevation, motion,
sizing and the strings the library draws — each a parameter, so a product
overrides one group and inherits the rest.

# Package io.kontour.ui.foundation

The primitives every component is built from: `Text`, `Icon`, `Surface`,
`Divider`, and the `ContentScope` vocabulary behind the `+` operator.

# Package io.kontour.ui.a11y

Contrast ratios, touch-target minimums, and the semantics helpers components use
to announce themselves. The contrast functions are public so an app can hold its
own scheme to the same standard the built-in ones are held to.

# Package io.kontour.ui.interaction

Indication and haptics — `kontourIndication`, and the feedback intents a
component performs rather than the specific vibration it asks for.

# Package io.kontour.ui.input

Input modality tracking and focus rings. A focus ring that appears on a mouse
click is noise; one that never appears on a keyboard is a bug.

# Package io.kontour.ui.components.action

Buttons, icon buttons, floating action buttons, button groups and toolbars.

# Package io.kontour.ui.components.selection

Controls that record a choice: checkboxes, radios, switches, chips, sliders,
steppers and ratings. Nearly all of them belong inside a `SelectionRow`.

# Package io.kontour.ui.components.text

Text fields and their machinery — input transformations, IME chaining, and
`Select`.

# Package io.kontour.ui.components.datetime

Date and time pickers, and the relative-time text that keeps itself current.

# Package io.kontour.ui.components.display

Things that show rather than take: cards, banners, stats, key-value lists,
carousels, accordions, progress and empty states.

# Package io.kontour.ui.components.list

Rows and what happens to them: list items, setting rows, swipe actions,
reordering, pull-to-refresh and scrollbars.

# Package io.kontour.ui.nav

Navigation surfaces — bar, rail and drawer — plus top bars and tab bars. The
suite scaffold picks the surface from the window size so one list of
destinations serves all three.

# Package io.kontour.ui.overlay

Everything drawn above the page: dialogs, menus, popovers, tooltips, toasts and
confirmations, all through a single `OverlayHost` so stacking order is decided
in one place.

# Package io.kontour.ui.sheet

Bottom sheets and side sheets, with detents.

# Package io.kontour.ui.adaptive

Window size classes, and the layouts that respond to them.

# Package io.kontour.ui.motion

Shared transitions and the reduced-motion contract.

# Package io.kontour.ui.platform

The small set of things that differ per platform — reduced-motion and
high-contrast preferences, and haptics.
