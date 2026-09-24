# `TextField`

Label, placeholder, supporting text, error, and leading/trailing slots.

<!--sample:TextFieldSlots-->
```kotlin
val stop = rememberTextFieldState()

// `label` is a `String?` rather than a slot on purpose: a floating label is
// chrome, it animates between two positions, and `FieldScaffold` reads it to
// give the control its accessible name — none of which a composable can do.
TextField(
    state = stop,
    label = "Where to?",
    placeholder = "Station, stop or address",
    supporting = "We'll remember your last five",
    leadingIcon = Tabler.Outline.MapPin,
    variant = TextFieldVariant.Filled,
)

// An error message rather than a red border alone: colour by itself fails
// WCAG 1.4.1, and the message is how a screen-reader user hears about it.
TextField(
    state = stop,
    label = "Where to?",
    errorMessage = "We don't know that stop".takeIf { stop.text.isEmpty() },
)
```

`label` really is a `String?` here, not a slot — a field's floating label is
*chrome*, not content. It animates between two positions, is read by
`FieldScaffold` to set the control's accessible name, and has nowhere to put a
composable. That is the exception the [`+` vocabulary](../dsls.md) does not
cover, and it is deliberate.

Two variants from `TextFieldVariant`: `Outlined` and `Filled`. `Filled` takes a
`contrastEdge()` at the high-contrast tier, since a filled field on a filled
surface is otherwise two tones with no boundary.

### The keyboard

**A field that takes focus brings itself into view whole** — label, frame and the
supporting or error line under it — and again whenever the keyboard's height
changes while it is focused. Foundation only brings the *caret* into view, which
left the bottom of the frame and its message behind the keyboard. `Select`,
`Combobox` and `MultiSelect` go through the same scaffold and do the same.

**Make room for the keyboard once.** With the content padded by the keyboard's
inset — `safeDrawing`, `imePadding()`, or a scaffold that uses either — the host
must not move the view for the keyboard as well, or the two add up to a
keyboard's height of blank space between the keyboard and the field:

- **iOS:** `ComposeUIViewController(configure = { onFocusBehavior = OnFocusBehavior.DoNothing })`.
  The default shifts the whole view up to keep the focused field above the
  keyboard.
- **Android:** `android:windowSoftInputMode="adjustResize"` on the activity, and
  `enableEdgeToEdge()`, so the window reports the keyboard as an inset instead of
  panning the window under it.

The catalog's hosts do both.

---

## Accessibility

Three things happen on the control's own node, and all three exist because
something was wrong before them.

**`label` becomes the field's `contentDescription`.** The visible label is a
sibling `Text` in the scaffold, drawn with `clearAndSetSemantics {}`, because
Compose has no `labelledBy` and nothing otherwise associates the two. Without it
a user hears "Origin", moves to the next node, and is in an unnamed edit box.

**`errorMessage` sets `error` semantics**, not just a red border. Colour alone
fails WCAG 1.4.1, so the message is how a screen-reader user learns there is a
problem — and error outranks focus visually, because an accent ring would hide
the thing being fixed.

**`enabled = false` marks the node disabled.** Foundation greys the field and
stops accepting input without marking it, so a screen reader went on offering
"double tap to edit" on a field that would not take a character.

Helper and error share one slot and animate in place, so a form does not jump a
line height every time validation flips.
