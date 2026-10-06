# `Select`

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

**Outlined by default, and it need not be.** `variant = TextFieldVariant.Plain`
draws the value and its chevron with no box round them — for a select at the end
of a row, or in a sentence, where the box was more frame than field. Focus and
an error bring its edge back. The same goes for every select: `MultiSelect`,
`Combobox` and `WheelSelect` take the same `variant`.

**An answer of their own.** Give it `onOtherTextChange` and the menu ends with
"Other". Picked, a box opens inside the field's outline, under the value, and takes
the keyboard; what is typed comes back through `onOtherTextChange`, and
`otherText` holds it. Picking one of the options instead closes the box and calls
`onOtherTextChange(null)`, so the answer is always `otherText ?: value` and
neither callback clears the other's state.

<!--sample:SelectWithOther-->
```kotlin
val reasons = remember { listOf("Running late", "Cancelled", "Too full") }
var reason by remember { mutableStateOf<String?>(null) }
var otherReason by remember { mutableStateOf<String?>(null) }

// "Other" ends the menu; picked, a box opens in the field to type in.
// The answer is `otherReason ?: reason`.
Select(
    value = reason,
    options = reasons,
    onValueChange = { reason = it },
    label = "Reason",
    otherText = otherReason,
    onOtherTextChange = { otherReason = it },
)
```

The box is part of the field rather than a field under it, so the question and
its answer read as one; it is still a text box of its own to a screen reader,
named for both — "Reason, Other" — and not part of the dropdown's node. The word
is `Theme.strings.selectOther`, or `otherLabel`, and the hint in the empty box
`selectOtherHint`. `MultiSelect` takes the same three parameters, with "Other"
ticked alongside the options.

**Reach for a [`RadioGroup`](radio-group.md) above a `Select`** when
there are three or four options and room to show them. A select hides its
options behind a tap, a cost worth paying only when showing them would crowd the
screen.

---

## Accessibility

Looks like a [`TextField`](text-field.md) and carries the same label-as-name,
`error` and disabled semantics from the same scaffold.

The menu reports **every option as a radio button, chosen or not**, in one
group: "one of these is on" is what a select actually conveys, and a group lets a
screen reader say which of how many an option is. A menu of buttons all
announcing "button" tells a screen reader user nothing about which is in force,
or that the others are choices at all.

In a browser under Compose 1.12 each option is announced as a button, with no
state — see [In the browser](../accessibility.md#in-the-browser).

The closed field announces the selected option, so `optionLabel` is what gets
spoken. The default `toString()` is right only for a `List<String>`.

At high contrast the field's frame thickens with the tier, and a `Filled` or
`Plain` field gains the edge it otherwise lacks. The menu is a
[`DropdownMenu`](dropdown-menu.md)'s, so its border goes from the faint
`outlineSubtle` to the near-ink `outline`.

Under reduced motion the menu fades in where it opens instead of growing out of
the field, and the chevron turns without overshooting. The field never shrinks
when pressed, at any setting.
