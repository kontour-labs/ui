# `TextField`

![TextField](../../../ui-catalog/screenshots/components/textfield-light.png)

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

---

← [Text editing](text-editing.md) · [All components](../components.md)
