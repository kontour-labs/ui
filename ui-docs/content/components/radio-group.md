# `RadioGroup`

![RadioGroup](../../../ui-catalog/screenshots/components/radiogroup-light.png)

<!--sample:RadioGroupBasics-->
```kotlin
var mode by remember { mutableStateOf(Mode.Fastest) }

RadioGroup(
    options = Mode.entries,
    selected = mode,
    onSelectedChange = { mode = it },
    label = { it.displayName },
    supporting = { it.explanation },
)
```

**Use `RadioGroup` rather than loose buttons.** Owning the selection there is
what lets the group apply `selectableGroup()`, which is what makes a screen
reader announce "option 2 of 5". It also makes the invalid states — two
selected, or none — unrepresentable.

It is generic in the option type, so the caller keeps their own enum or data
class and supplies `label` rather than mapping to strings and back.

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

---

← [Selection](selection.md) · [All components](../components.md)
