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
"Other", or `otherLabel`: the app's own words for it. Picked, a box opens inside the field's outline, under the value, and takes
the keyboard; what is typed comes back through `onOtherTextChange`, and
`otherText` holds it. Picking one of the options instead closes the box and calls
`onOtherTextChange(null)`, so the answer is always `otherText ?: value` and
neither callback clears the other's state.

<!--sample:SelectWithOther-->
```kotlin
val reasons = remember { listOf("Running late", "Cancelled", "Too full") }
var reason by remember { mutableStateOf<String?>(null) }
var otherReason by remember { mutableStateOf<String?>(null) }

// "Something else" ends the menu; picked, a box opens in the field to
// type in. The answer is `otherReason ?: reason`.
Select(
    value = reason,
    options = reasons,
    onValueChange = { reason = it },
    label = "Reason",
    otherText = otherReason,
    onOtherTextChange = { otherReason = it },
    otherLabel = "Something else",
    otherPlaceholder = "What happened?",
)
```

The box is a filled [`TextField`](text-field.md), a field's height, set into the
outline by the same 8dp at either side and below, with corners concentric with
the outline's; inside a `Filled` select its well is the page's colour, so it
still stands out. It is part of the field rather than a field under it, so the
question and its answer read as one; it is still a text box of its own to a screen reader,
named for both — "Reason, Other" — and not part of the dropdown's node. The word
is `Theme.strings.selectOther` unless `otherLabel` says another, and the hint in
the empty box `selectOtherHint` unless `otherPlaceholder` does. `MultiSelect`
takes the same parameters, with "Other" ticked alongside the options.

**More than words.** An option that is a person, a colour or a country reads
better drawn than named. The trailing `optionContent` draws each option in the
menu, and the value in the closed field, in place of `optionLabel` and
`optionIcon`; `valueContent` draws the value alone, for a field that shows less
than a row does — here the face and the name, without the home stop, so the
field stands as tall as the fields beside it.

<!--sample:SelectOptionContent-->
```kotlin
var rider by remember { mutableStateOf<Rider?>(household.first()) }

// A face, a name and a home stop in the menu; the face and the name in the
// field, so it stands as tall as the fields beside it. The name is still
// what a screen reader says for either, whatever the slot draws.
Select(
    value = rider,
    options = household,
    onValueChange = { rider = it },
    label = "Booked for",
    optionLabel = { it.name },
    valueContent = { person ->
        Avatar(name = person.name, size = AvatarSize.XSmall)
        +person.name
    },
) { person ->
    Avatar(name = person.name, size = AvatarSize.Small)
    Column {
        Text(person.name)
        Text(person.home, style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted)
    }
}
```

Both are given the option in a row-shaped scope, as a button's label is: `+"…"`
is a line of the field's text, ellipsised, and `+icon` an icon at the field's
size, in the field's colour — its disabled grey when it is disabled. Something
taller than a line makes the field taller to fit it, and a field never clips
what it draws. The placeholder, and an "Other", stay words.

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

That holds where `optionContent` or `valueContent` draws the option: an option
row and the field are each read as the option's label, and what the slot drew is
not read as well. A second line, an avatar's initials and a swatch's colour are
drawn and not said, so a drawn select sounds exactly as a select of words does —
and anything that should be heard belongs in `optionLabel`.

Open, a `Combobox`'s field is a text box, and a press on it goes to the text. Its
chevron is a button that closes the list, named as the menu's own close is, and
the field offers a collapse action while the list is open, so a screen reader can
close it from the field. Opened from the keyboard, the field keeps the focus for
typing; the Down arrow moves into the list.

At high contrast the field's frame thickens with the tier, and a `Filled` or
`Plain` field gains the edge it otherwise lacks. The menu is a
[`DropdownMenu`](dropdown-menu.md)'s, so its border goes from the faint
`outlineSubtle` to the near-ink `outline`.

Under reduced motion the menu fades in where it opens instead of growing out of
the field, and the chevron turns without overshooting. The field never shrinks
when pressed, at any setting.
