# `TextField`

![TextField](../../../ui-catalog/screenshots/components/textfield-light.png)

Label, placeholder, supporting text, error, and leading/trailing slots.

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
