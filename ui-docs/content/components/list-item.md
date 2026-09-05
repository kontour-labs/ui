# `ListItem`

*Also on this page: `ListGroup`.*

<!--sample:ListItemBasics-->
```kotlin
LazyColumn {
    itemsIndexed(stops) { index, stop ->
        ListItem(
            onClick = { openStop(stop.name) },
            // Rounds the outside corners of the group and leaves the seams
            // square, so a run of rows reads as one block.
            position = ListItemPosition.of(index, stops.size),
        ) {
            leading { +Tabler.Outline.Bus }
            +stop.name
            supporting { +"${stop.routes} routes" }
            trailing { +Tabler.Outline.ChevronRight }
        }
    }
}
```

The builder is `ListItemScope`, and the bare `+` is the row's headline — see
[`dsls.md`](../dsls.md) for the whole vocabulary. `overline`, `supporting`,
`leading` and `trailing` are the other slots.

**A group of rows is one object, not a stack of cards.** Only the outside corners
of a group round; the ones facing a neighbour get a hairline. `ListItemPosition`
carries that, and `of(index, count)` gets the one-item case right — which is the
case a three-item example in a catalog never exercises and every settings screen
with a single row hits immediately.

**Rows default to a *sunken* ground.** In this scheme `surface` and `background`
are the same white, so a row drawn on `surface` is invisible on a page and a
group of them reads as loose text.

`role` defaults to `Role.Button` and is a parameter, because a row can be a
button, a radio option or a plain block of text depending on what it is for.

> A disabled `ListItem` used to drop its `clickable` entirely. The callback could
> not fire, so it looked right — but the node then had no role and no disabled
> flag, and a disabled row announced as plain text with no way to tell it was
> unavailable rather than broken. It keeps the modifier and passes
> `enabled = false` now.

---

## Accessibility

`role` is the parameter that decides what this announces, and it is worth
choosing rather than accepting: `Role.Button` for a row that navigates,
`Role.RadioButton` for one of a set, `Role.Checkbox` for one that toggles.

The row carries the click and the role, and any control inside it takes
`onClick = null` — otherwise there are two targets for one thing, and the smaller
one is the control. That is the rule that runs through
[`SelectionRow`](selection-row.md), [`SettingRow`](setting-row.md) and
[`RadioGroup`](radio-group.md).

`label`, `supporting` and `overline` merge into one announcement in reading
order. `leading` and `trailing` icons take `null` unless they carry meaning the
text does not.
