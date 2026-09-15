# `ColourPicker`

*Also on this page: `ColourFormat`, `ColourPickerMode`, `Hsv`, `Hsl`.*

Picks a colour — a saturation-and-value area, a hue track, and as much or as
little else as the screen wants.

<!--sample:ColourPickerBasics-->
```kotlin
var accent by remember { mutableStateOf(Color(0xFF1E88E5)) }

ColourPicker(colour = accent, onColourChange = { accent = it })
```

**Every part except the spectrum is optional**, because the same component is
asked for very different things: a theme editor wants the area, the hue, the
opacity and a hex field; a label picker wants eight swatches and nothing else.
Turning a part off removes it rather than disabling it.

| | Off by | Why you would |
|---|---|---|
| Swatches | `swatches = emptyList()` | Nothing worth suggesting |
| Opacity | `alphaSlider = false`, the default | Most colours are opaque |
| The field | `valueField = false` | Nobody here is going to type a hex |
| The notation switch | `onFormatChange = null`, the default | One notation is enough |
| The mode switch | `onModeChange = null`, the default | See below |

<!--sample:ColourPickerParts-->
```kotlin
// A label colour: the eight we offer, and nothing to invent a ninth with.
ColourPicker(
    colour = label,
    onColourChange = { label = it },
    mode = ColourPickerMode.Palette,
    valueField = false,
)
```

`ColourPickerMode.Spectrum` and `ColourPickerMode.Palette` are not a style
preference. A palette answers "which of ours" and a spectrum answers "which
colour", so an app that means the first should not offer the second — a brand
picker with a full spectrum in it invites an off-brand answer. Pass
`onModeChange` only when both are genuinely allowed.

---

## It keeps a hue, not a colour

The picker holds its own `Hsv` and converts on the way out, and that is not an
optimisation. **A colour that has reached pure black or pure white has no hue
left in it** — the three channels are equal and there is nothing to recover — so
a picker that re-derived one from `colour` every frame would watch its own hue
track jump to red as the value reached the bottom of the area, and stay there on
the way back up. Dragging to black and back is the first thing anyone does with a
colour area, and it is checked on every build.

`colour` is still the source of truth, and anything arriving from outside the
picker's own gestures is adopted.

---

## Typing, and the loop a bound field would otherwise be

`ColourFormat` is `Hex`, `Rgb`, `Hsv` or `Hsl`, and the field row changes with
it: one field for a hex, three channels for the rest, plus opacity when it is on.
Four notations rather than one, because the people who type into a colour picker
have already learned one somewhere else — a hex off a brand sheet, RGB out of a
design tool, HSL out of a stylesheet.

A field bound to a colour has to push the colour in and pull typing out, and the
naive version of that oscillates: you type `0f8`, the picker resolves it, the
canonical `#00FF88` comes back, and the field rewrites itself under your cursor
mid-word. So the field is only rewritten when the incoming text means a
**different colour** than what is already in it. `0f8` and `#00FF88` parse to the
same thing and nothing is touched; a colour picked on the area above parses to
something else, so the field follows it.

Out-of-range is not a value, and is not clamped: type `300` into a channel and
nothing happens until it is in range, because clamping would turn it into `255`
under your finger and take the second digit you meant to type with it.

---

## The maths is public

`Color.toHsv()`, `Color.toHsl()`, `Hsv.toColour()`, `Hsl.toColour()`,
`Color.toHex()` and `colourFromHex()` are ordinary functions in `foundation`,
usable without the picker.

```kotlin
val warmer = colour.toHsv().copy(hue = 30f).toColour()
val fromBrandSheet = colourFromHex("#3355AA") ?: fallback
```

`Hsv` and `Hsl` are not the same numbers for the same colour: HSL's `0.5`
lightness is the *full* hue where HSV's `1.0` value is, and the two disagree
about saturation throughout. `colourFromHex` takes `#RGB`, `#RGBA`, `#RRGGBB` and
`#RRGGBBAA` with or without the hash, and returns null rather than throwing or
falling back to black — it sits behind a field somebody is halfway through
typing into, and `#33` is not an error, it is a colour that is not finished.

Alpha goes **last** in a hex, which is not the order a `Color(0xFF3355AA)`
literal is written in. That literal is a packed ARGB integer; every design tool,
every stylesheet and every hex a person has ever typed puts alpha at the end.

---

## Accessibility

The hue and opacity tracks report `ProgressBarRangeInfo` and accept
`setProgress`, so a screen reader can adjust either one directly — they are
values, and a value is exactly what that action is for.

**The area cannot be, and says so rather than pretending.** Saturation and value
are two axes on one node, and there is no accessible action for a point. The
route to any colour without the gesture is the field and the swatches, which is
why `valueField` defaults to on: a picker with the field turned off and no
swatches has one input and it is a drag. Turn it off only where the colour is a
convenience rather than the point.

Every swatch carries its own hex as a content description, through
[`ColourSwatchPicker`](colour-swatch-picker.md), and reports
`Role.RadioButton` inside a selectable group.
