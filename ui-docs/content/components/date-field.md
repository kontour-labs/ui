# `DateField`

*Also on this page: `DateTimeField`, `FieldPicker`.*

The tappable field that opens a [`DatePicker`](date-picker.md), as
[`TimeField`](time-field.md) is for a time — same frame, same label, the same
field a form is made of.

<!--sample:DateFieldBasics-->
```kotlin
var date by remember { mutableStateOf<LocalDate?>(null) }
var picking by remember { mutableStateOf(false) }

// One tap target that opens the picker, as `TimeField` is for a time —
// not a text field with a calendar button beside it.
DateField(value = date, onClick = { picking = true }, label = "Post date")

// A sheet on a phone, a dialog on anything wider — as every field's picker opens.
FieldPicker(visible = picking, onDismissRequest = { picking = false }, title = "Post date") {
    DatePicker(
        value = date,
        // A date is picked at one tap, so the picker goes with it.
        onValueChange = { date = it; picking = false },
        // Marks today, and gives the header its way back to it.
        today = remember { DatePickerDefaults.currentDate() },
    )
}
```

**A line under it, as a text field has.** `supporting` says what the date is
for or what it has to be, and `errorMessage` takes its place in the error colour,
with the field's edge in it and said with the field — *"Choose a date after
today"*. `DateTimeField` and [`TimeField`](time-field.md) take the same two.

**One target, not two.** A read-only text field with a calendar button beside
it is two controls that open the same thing, and the larger of them does
nothing when pressed. The field is the button.

**It does not open the picker itself.** `onClick` is where the caller opens a
`DatePicker`, so the picker never has to know how it is presented and the form
decides what a press does.

**`FieldPicker` is where a field's picker opens**, for all three fields: a
[modal sheet](modal-bottom-sheet.md) on a compact window, under 600dp wide, with
the `title` in its header and a close button; a [dialog](dialog.md) on anything
wider, with the `title` as its heading and Done. A form with a date and a time
on it opens both the same way. The picker sets the value as it is used, so
there is no Cancel; a date, picked at one tap, can close the picker itself, as
above.

**`value` is a `LocalDate?`**, as the picker's is: `null` is no date yet, and
the field shows `placeholder` — `Theme.strings.select` by default — in the muted
colour a text field's placeholder takes, so an empty field is not mistaken for
one with a date in it. `style` is how the date is written: `DateStyle.Medium`
by default, `Full` with the weekday.

**`DateTimeField`** is the same field for a
[`DateTimePicker`](date-time-picker.md): a `LocalDateTime`, written as the
locale writes a date and a time together.

---

## Accessibility

The label is the field's accessible name and the written date its
`stateDescription`, as on a [`TimeField`](time-field.md): "Post date, 14 Mar
2026, button". The visible label is cleared, since Compose has no `labelledBy`
and a label drawn above a control is otherwise an unrelated node. With no date
the placeholder is the state — "Post date, Select, button" — so an empty field
says what to do with it rather than a name with nothing after it.

It is a button, not an editable field: typing a date is how a form gets
"31/02". Pressing it opens the picker the caller chose.

At high contrast the field gains an edge round its sunken fill and its focus
ring is drawn thicker. Nothing in it moves, so reduced motion changes nothing
here.
