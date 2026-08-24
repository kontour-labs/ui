# `SplitButton`

![SplitButton](../../../ui-catalog/screenshots/components/splitbutton-expanded-light.png)

<!--sample:SplitButtonBasics-->
```kotlin
var open by remember { mutableStateOf(false) }

SplitButton(
    onClick = { save() },
    expanded = open,
    onExpandedChange = { open = it },
    menuContentDescription = "Other save options",
    menu = {
        item("Save and close", onClick = { saveAndClose() })
        item("Save a copy", onClick = { saveCopy() })
    },
) {
    +"Save"
}
```

The left half runs the **default** action immediately; the right half opens the
rest. That division is the whole component, and it is what separates it from a
`Button` that opens a menu: here the common case costs one tap and never shows a
list.

The two halves sit flush with a hairline between them and only the outside
corners round — the same `ButtonGroupPosition.shape` a
[`ButtonGroup`](button-group.md) uses, because it is the same idea: separate targets
that read as one control. The pair owns the touch target between them for the
reason `ButtonGroup` does, so the reserved slack does not land in the seam and
turn a 1dp join into a 9dp gap.

**Reach for a plain `Button` and a `DropdownMenu`** when there is no default. A
split button whose main half also opens the menu is a wide chevron. And reach for
[`ButtonGroup`](button-group.md) when the alternatives are *equal* — three ways of
doing a thing, none of them the usual one — since a split button claims one of
them is the answer.

`menuContentDescription` is required and separate from the label: the chevron
half has no text of its own, and a screen reader that reads "Save, Save" for the
two halves has described neither.

---

← [Actions](actions.md) · [All components](../components.md)
