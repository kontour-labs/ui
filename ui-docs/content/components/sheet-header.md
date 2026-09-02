# `SheetHeader`

The title row a sheet needs: a label, optional supporting text, an actions slot,
and the drag handle above them.

<!--sample:SheetHeaderBasics-->
```kotlin
SheetHeader(
    actions = {
        IconButton(
            icon = Tabler.Outline.Star,
            contentDescription = "Add to favourites",
            onClick = { save() },
        )
    },
) {
    +"Perth Underground"
    supporting { +"Platform 2 · Joondalup line" }
}
```

It takes a `ListItemScope`, the same shape [`ListItem`](list-item.md) and
[`TopBar`](top-bar.md) take, so `+"Title"` and `supporting { … }` mean the same
thing in all three. That is the point of the slot vocabulary — see
[`../dsls.md`](../dsls.md).

`Modifier.sheetPeekAnchor()` on the header is what tells a `peek` detent how tall
to be: the sheet measures the anchored node rather than taking a number, so a
two-line title peeks taller than a one-line one without anybody computing it.

---

## Three sizes

`style` takes the same three a [`TopBar`](top-bar.md) does, and deliberately the
same names — a sheet that is a screen should not need a second vocabulary.

| `SheetHeaderStyle` | Shape | For |
|---|---|---|
| `Small` | One line, title leading | The default; a panel over something |
| `Centred` | One line, title centred | A control on both sides, where a leading title reads off-balance |
| `Large` | Two lines: controls, then a large title | A sheet that *is* the destination — a whole trip, a form |

`Large` gives the title the full width, so it is the one arrangement where a long
title never truncates around a button. There is no collapse-on-scroll, which is
the one thing a large top bar does and a sheet cannot: the sheet already answers
that drag by moving between its detents, and two things responding to one gesture
is a header fighting its own sheet.

---

## Accessibility

The title is marked `heading()`, so a screen reader can jump to it — which is
what makes a long sheet navigable rather than a single run of text.

The close button carries `closeLabel`, and `onClose` defaults to closing the
enclosing sheet, so the affordance is there without the caller wiring it.
**`onClose = null` is how you say there is no close button** — for a sheet that
genuinely cannot be closed from inside, which should be paired with
`dismissible = false` on the sheet itself so the two agree.

Actions in the `actions` slot are ordinary buttons and need their own
`contentDescription` when they are icons.

---

← [Sheets](sheets.md) · [All components](../components.md)
