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

## The space round it

**In a sheet, the header has the sheet's inset at the sides of what it draws and
below it, and the handle's band above.** `BottomSheetDefaults.ContentPadding`,
16dp, from the sheet's side to the title, from the icon's edge to the sheet's
other side, and from the header's row to the bottom of a sheet collapsed to it.
Above, the drag handle's 24dp band is the header's top padding, so it adds none
of its own there: the row starts at the band's foot, with the handle in the middle
of the band over it.

It measures to what is drawn rather than to what is laid out. A close button is a
48dp target round a 24dp icon, and the 12dp of nothing above the icon used to sit
under the handle's band and the header's padding: 44dp above the title, 16 beside
it. The header now gives that slack back, reaching up into the band where nothing
is drawn — and the button keeps all 48dp of its target, since the header is on top
of the band.

Outside a sheet with a handle it has the inset above as well as below.

**On a display whose corners are rounder than the sheet's, it moves in along
the corner.** An edge sheet's top corners are the display's own, about 55pt on
an iPhone and 51dp on a newer Pixel, against the sheet's 34dp, and at 16dp in
the title's corner was 5dp from the curve. So the header, the handle's band
above it and the sheet's `header` move in by what the rounder corner cuts
into its diagonal, 1 − 1/√2 of the difference: 5dp at 51, 6dp at 55, nothing at
the sheet's own 34 or the floating sheet's 28. Each side follows its own corner,
and the band grows by the deeper of the two. It is worked out once for a sheet
rather than on every frame, from the roundest corners it will have, so a floating
sheet that becomes an edge sheet as it rises keeps the same room all the way
instead of moving its detents under the finger. Your own content below the header
keeps `ContentPadding`.

## Three sizes

`style` takes the same three a [`TopBar`](top-bar.md) does, and deliberately the
same names — a sheet that is a screen should not need a second vocabulary.

| `SheetHeaderStyle` | Shape | For |
|---|---|---|
| `Small` | One line, title leading | The default; a panel over something |
| `Centred` | One line, title centred — or `Small`, where it cannot be | A control on both sides, where a leading title reads off-balance |
| `Large` | Two lines: controls, then a large title | A sheet that *is* the destination — a whole trip, a form |

`Centred` centres on the **header**, not on the room its controls left over.
Those are the same thing only when both sides are the same width, which is why a
weighted title in a row drifts left when the only control is a close button — the
same defect the top bar had, fixed once in the layout both share. The header's
own padding counts towards the centre too: it is wider at the start, where the
title lines up with the sheet's content, and it used to sit outside the bar and
move the middle 4dp towards the close button.

**Only the title has to fit for it to be centred.** A supporting line under it is
fitted to the width the title is centred in, and ends in an ellipsis if it is
longer — the two used to be judged together, so a short title with a long
subtitle was pushed to the leading edge by the subtitle. A title that is itself
too wide to sit in the middle without being squeezed is laid out **exactly as
`Small` lays it out**, against the leading edge, with its supporting line in
full. It used to keep the centring on whatever room was left, and with two
actions at one end that read as "not really centred" — so it is centred on the
header or not centred at all.

`Large` gives the title the full width, on a line of its own under the controls,
so it is the one arrangement where a long title never truncates around a button.
It shared the close button's line for a while when nothing led the header, and
was put back. There is no collapse-on-scroll, which is
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
