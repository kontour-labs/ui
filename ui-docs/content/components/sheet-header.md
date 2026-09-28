# `SheetHeader`

The title row a sheet needs: a label, optional supporting text and an actions
slot. The [`DragHandle`](drag-handle.md) above it is the sheet's, not the
header's.

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

The title and its supporting line **start where the sheet's own content starts**.
They used to carry 8dp of their own on top of the header bar's, so the words sat
in from everything under them while the trailing controls — which never had the
extra — lined up correctly.

---

## Three sizes

`style` takes the same three a [`TopBar`](top-bar.md) does, and deliberately the
same names — a sheet that is a screen should not need a second vocabulary.

| `SheetHeaderStyle` | Shape | For |
|---|---|---|
| `Small` | One line, title leading | The default; a panel over something |
| `Centred` | One line, title centred — or `Small`, where it cannot be | A control on both sides, where a leading title reads off-balance |
| `Large` | A large title level with the controls; under them when something leads | A sheet that *is* the destination — a whole trip, a form |

`Centred` centres on the **header**, not on the room its controls left over.
Those are the same thing only when both sides are the same width, which is why a
weighted title in a row drifts left when the only control is a close button — the
same defect the top bar had, fixed once in the layout both share. The header's
own padding counts towards the centre too: it is wider at the start, where the
title lines up with the sheet's content, and it used to sit outside the bar and
move the middle 4dp towards the close button.

A title too wide to sit in the middle without being squeezed is laid out
**exactly as `Small` lays it out**, against the leading edge. It used to keep the
centring on whatever room was left, and with two actions at one end that read as
"not really centred" — so it is now centred on the header or not centred at all.

`Large` puts its title on the controls' line, level with them, where nothing leads
the header — it used to sit on a line of its own under them, which left a header
whose only control was its close button with a whole empty row above its title.
The title takes what the controls leave and wraps to a second line rather than
truncate. With a leading slot, it goes under the controls and gets the full
width, so a long title never truncates around a button. There is no collapse-on-scroll, which is
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

At high contrast the close button, a ghost, gains an outline in the colour of
its glyph, and the title and supporting line take the enhanced scheme's colours.
Nothing else about the header changes.

Under reduced motion the close button keeps its tonal wash and loses its
press-shrink. The default close hides the sheet by the sheet's own rule, which
under reduced motion is a fade where it rests.
