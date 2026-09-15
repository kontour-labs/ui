# `RadioGroup`

<!--sample:RadioGroupBasics-->
```kotlin
var mode by remember { mutableStateOf(Mode.Fastest) }

RadioGroup(
    options = Mode.entries,
    selected = mode,
    onSelectedChange = { mode = it },
) { option ->
    +option.displayName
    leading { +option.icon }
    supporting { +option.explanation }
}
```

**Use `RadioGroup` rather than loose buttons.** Owning the selection there is
what lets the group apply `selectableGroup()`, which is what makes a screen
reader announce "option 2 of 5". It also makes the invalid states — two
selected, or none — unrepresentable.

It is generic in the option type, so the caller keeps their own enum or data
class rather than mapping to strings and back, and fills each row through the
same `ListItemScope` vocabulary every other row in the library uses — `+` for
the label, `leading {}` for an icon, `supporting {}` for the line underneath.

That slot replaced a `label: (T) -> String` with a `supporting: ((T) -> String?)?`
beside it, which was two thirds of the scope reimplemented as parameters and
could not hold the third: **an option could not carry an icon at all.** The
`trailing` slot is not yours — the group fills it with the radio button after
your block runs, because that button is what makes the row an option rather
than a list row.

**Reach for a `RadioGroup` above a [`Select`](select.md)** when
there are three or four options and room to show them. A select hides its
options behind a tap, a cost worth paying only when showing them would crowd the
screen. Above roughly a dozen, use `Combobox` so the user can type rather than
scroll.

---

## Accessibility

Owning the selection at the group rather than at each button is what lets it
apply `selectableGroup()`, which is what makes a screen reader announce the
position within the set — "2 of 5". It also makes the invalid states, two
selected or none, unrepresentable.

Each row is a [`SelectionRow`](selection-row.md) with `role = Role.RadioButton`
and the button inside taking `onClick = null`, so the whole row is the target and
there is exactly one of them.

`supporting` is part of the option's announcement. Put the consequence of the
choice there rather than in a footnote below the group.
