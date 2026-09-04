# `ColourSwatchPicker`

<!--sample:ColourSwatchPickerBasics-->
```kotlin
var accent by remember { mutableStateOf(RouteColor.Red) }

ColourSwatchPicker(
    value = accent,
    options = RouteColor.entries,
    onValueChange = { accent = it },
    swatchColour = { it.colour },
    swatchLabel = { it.displayName },
)
```

A grid of swatches rather than a dropdown of colour names, because the choice
being made is visual: "which of these do I like" is answered by looking, and a
list that shows one colour at a time makes the user open it six times.

**The tick is drawn in whatever colour is legible on the swatch**, resolved
through `contentColourFor()`. A fixed white tick vanishes on pale yellow and a
fixed black one vanishes on navy, and a picker whose selection is invisible on
two of its own options has a bug in it.

`swatchLabel` is required, not optional. A colour with no name is unusable to
anyone who cannot see it — and to anyone who can, describing it over the phone.

Options whose `swatchColour` is `null` render as an outlined swatch with
`automaticIcon`, for a "match the system" entry.

---

## Accessibility

Every swatch carries `swatchLabel` as its content description and reports
`Role.RadioButton` inside a `selectableGroup()`. **A colour with no name is
unusable** to anyone who cannot see it — and to anyone who can, describing a
screenshot over the phone.

The tick is drawn in whatever colour is legible on the swatch, resolved through
`contentColourFor()`. A fixed white tick vanishes on pale yellow and a fixed black
one on navy, and a picker whose selection is invisible on two of its own options
has a bug in it.

`swatchColour` returning `null` renders the "match the system" entry as an
outlined swatch with an icon — still named, still one of the group.
