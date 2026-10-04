# `ListItem`

*Also on this page: `ListGroup`.*

<!--sample:ListItemBasics-->
```kotlin
LazyColumn {
    // A section puts a list item's gap between the rows and hands each its
    // position, which rounds the outside corners of the group and leaves
    // the seams square, so a run of rows reads as one block.
    listSection(stops, key = { it.name }) { stop, position ->
        ListItem(
            onClick = { openStop(stop.name) },
            position = position,
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
of a group round; the ones facing a neighbour get a hairline. `GroupPosition`
carries that, and `of(index, count)` gets the one-item case right — which is the
case a three-item example in a catalog never exercises and every settings screen
with a single row hits immediately.

`ListGroup` counts the rows for you, so there is no position to pass. Its
builder is composable: a row's text can be read from the theme where the row is
written, `item("Departs", supporting = Theme.formats.time(departure))`.

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
[`SelectionRow`](selection-row.md) and [`RadioGroup`](radio-group.md).

`label`, `supporting` and `overline` merge into one announcement in reading
order. `leading` and `trailing` icons take `null` unless they carry meaning the
text does not.

At high contrast every row takes an edge in the scheme's `outline`, because its
sunken ground is too close to the page to say where a row ends, and a selected
row also takes an accent outline round its tint. The focus ring is thicker and a
press's wash stronger.

Reduced motion changes nothing here, because nothing moves: a row never shrinks
when pressed, at any setting, and the tonal wash is the whole of its feedback.
