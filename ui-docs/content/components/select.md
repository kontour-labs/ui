# `Select`

![Select](../../../ui-catalog/screenshots/components/select-light.png)

**A select is a field, not a button.** It shares `FieldScaffold` with
`TextField` rather than resembling it by hand — same frame, same label, same
helper and error slot — because in a form it *is* one of the fields, and a
select styled as a button in a column of text inputs reads as a different kind
of thing.

<!--sample:SelectBasics-->
```kotlin
val modes = remember { listOf("Any", "Train", "Bus", "Ferry") }
var mode by remember { mutableStateOf<String?>("Any") }

Select(
    value = mode,
    options = modes,
    onValueChange = { mode = it },
    label = "Mode",
)
```

Its menu anchors to the field frame, using `Modifier.anchorBounds` and
`AnchoredDropdownMenu` rather than the parent-anchoring `DropdownMenu`: "the
parent layout" is the wrong node when the menu is declared in one slot of a row
and has to line up with the whole row. The menu matches the field's width.

**Reach for a [`RadioGroup`](radio-group.md) above a `Select`** when
there are three or four options and room to show them. A select hides its
options behind a tap, a cost worth paying only when showing them would crowd the
screen.

---

← [Text editing](text-editing.md) · [All components](../components.md)
