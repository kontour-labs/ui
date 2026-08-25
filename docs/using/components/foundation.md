# Foundation

The five primitives every other component is built from.

| | |
|---|---|
| [`Text`](text.md) | Resolves style and colour from the theme. `String` and `AnnotatedString` overloads |
| [`Icon`](icon.md) | Tinted to the surrounding content colour. Takes an `ImageVector` or `Painter` |
| [`Surface`](surface.md) | Background, shape, border, shadow — and sets `LocalContentColor` |
| [`HorizontalDivider` / `VerticalDivider`](divider.md) | Decorative rules |
| [`Scrim`](scrim.md) | Dims and blocks input behind a modal |

`Surface` setting `LocalContentColor` is what makes `Text` and `Icon` need no
colour argument in the common case: a surface says what it is, and its content
resolves against it. That chain is why a component can be dropped on a dark card
without every child being told.

`DragHandle` lives here too, in
[`sheets.md`](../sheets.md#draghandle-is-drawn-not-draggable).

![DragHandle](../../../ui-catalog/screenshots/components/draghandle-light.png)

---

## Icons

**Components take icons from the caller.** Anything a caller puts *in* a
component — a button's leading icon, a menu item's icon, an empty state's
illustration — is an `ImageVector` or `Painter` parameter, so the choice of icon
library stays an application decision.

The exception is `foundation/SystemIcons.kt`: the handful of glyphs a component
draws on its own behalf, because they are structure rather than content. A
submenu with no chevron gives no sign it opens anything, and a menu item with no
tick cannot show which option is current. Making the caller supply those means
every component ships looking broken until someone remembers to pass one in.

`SystemIcons` is **public**, because it is shipped rather than hidden:
`SheetHeader.closeIcon` defaults to `SystemIcons.Close`, and a default a caller
can read but cannot write is worse than no default. The type is `ImageVector`,
from a dependency the library already exposes, so nothing about the icon set
leaks into the API with it.

Each glyph is a separate top-level declaration, so the ones `:ui` never touches
are stripped by R8 and by the JS/Wasm dead-code eliminator rather than
travelling with the artifact.

### Why Tabler

The app and catalog use **Tabler** (`icons-tabler-outline-cmp`,
`icons-tabler-filled-cmp`). Tabler draws every glyph on a uniform 24×24 grid,
which matters more than it sounds: FontAwesome uses a 512-tall grid of varying
width while declaring every glyph as square, so its icons both distort and
occupy visibly different widths within the same slot.

`IconMetricsDiagnostic` asserts Tabler's uniformity so a future version cannot
regress it silently.

`Icon` still corrects for a non-square viewport, so a set that does have one
renders undistorted. With Tabler that correction is a no-op.

---

## Mechanisms

Not components, but the layer everything above depends on. Each has its own
page:

| | |
|---|---|
| `Modifier.minimumTouchTarget` | [Touch targets](../accessibility.md#touch-targets) |
| `Modifier.focusRing` | [Focus](../accessibility.md#focus) |
| `LocalInputModality` | [Input modality](../accessibility.md#input-modality) |
| `kontourIndication` | [Interaction](../../building/contributing.md#interaction) |
| `Feedback` / `FeedbackIntent` | [Interaction](../../building/contributing.md#interaction) |
| `SelectionIndicatorBox` | [How selection is shown](nav-surfaces.md#how-selection-is-shown) |
| `OverlayHost` | [Overlays](../overlays.md) |
