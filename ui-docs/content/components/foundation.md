# Foundation

The primitives every other component is built from.

| | |
|---|---|
| [`Text`](text.md) | Resolves style and colour from the theme. `String` and `AnnotatedString` overloads |
| [`SelectableText`](selectable-text.md) | A region whose text the reader can select and copy. Nothing is selectable outside one |
| [`Icon`](icon.md) | Tinted to the surrounding content colour. Takes an `ImageVector` or `Painter` |
| [`Surface`](surface.md) | Background, shape, border, shadow — and sets `LocalContentColour` |
| [`HorizontalDivider` / `VerticalDivider`](divider.md) | Decorative rules |
| [`Scrim`](scrim.md) | Dims and blocks input behind a modal |
| [`SelectionIndicatorBox`](selection-indicator.md) | The travelling pill behind a selected destination |
| [`Modifier.clearFocusOnTap`](modifier-clear-focus-on-tap.md) | Puts the keyboard away when a press lands on nothing |

`Surface` setting `LocalContentColour` is what makes `Text` and `Icon` need no
colour argument in the common case: a surface says what it is, and its content
resolves against it. That chain is why a component can be dropped on a dark card
without every child being told.

`DragHandle` is the near miss — a primitive by shape, but it means one specific
thing, so it is filed with the sheets and explained under
[the detent model](sheets.md#draghandle-is-drawn-not-draggable).

---

## Icons

**Components take icons from the caller.** Anything a caller puts *in* a
component — a button's leading icon, a menu item's icon, an empty state's
illustration — is an `ImageVector` or `Painter` parameter, so the choice of icon
library stays an application decision.

The exception is `Theme.icons`, a `SystemIcons`: the handful of glyphs a
component draws on its own behalf, because they are structure rather than
content. A submenu with no chevron gives no sign it opens anything, and a menu
item with no tick cannot show which option is current. Making the caller supply
those means every component ships looking broken until someone remembers to
pass one in.

They are Tabler's by default and the app's to change. `KontourTheme(icons =
SystemIcons(close = …))` replaces the glyphs it is given and keeps Tabler's for
the rest, `ProvideTokens(icons = Theme.icons.copy(…))` does the same for part
of an app, and a component that draws one takes it as a parameter too. Until
0.5.0 `SystemIcons` was an object, and a select's chevron or a toast's close
could only be changed by forking the component.

It is not a data class, on purpose. A constructor argument is an override,
`null` unless given, and each glyph falls back to Tabler only when it is read,
so a glyph no component of the app draws is never reached and is stripped by
R8 and by the JS, Wasm and Native compilers rather than travelling with the
app. A data class would hold all of them in its default instance.

A few marks are drawn rather than taken from it, because they move: a
checkbox's tick draws itself in, a switch's tick turns into its dash, and a
password field's slash crosses its eye.

### Why Tabler

The app and catalog use **Tabler**, as Kontour Labs builds it
(`io.kontour:tabler-icons-outline` and `-filled`: every icon of the release,
generated from Tabler's own). Tabler draws every glyph on a uniform 24×24 grid,
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
| `Modifier.pointerCursor` | [The mouse cursor](../accessibility.md#the-mouse-cursor) |
| `LocalInputModality` | [Input modality](../accessibility.md#input-modality) |
| `kontourIndication` | [Interaction](../../../docs/building/contributing.md#interaction) |
| `Feedback` / `FeedbackIntent` | [Interaction](../../../docs/building/contributing.md#interaction) |
| `SelectionIndicatorBox` | [How selection is shown](nav-scope.md#how-selection-is-shown) |
| `OverlayHost` | [Overlays](../overlays.md) |
