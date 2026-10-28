# `Tooltip`

*Also on this page: `Modifier.tooltip`.*

The name of a control the user is pointing at.

<!--sample:TooltipBasics-->
```kotlin
// The modifier is what a caller nearly always wants: it tracks hover and
// focus itself and honours the input modality, so it never appears from a
// touch that was really a tap.
IconButton(
    icon = Tabler.Outline.Bookmark,
    contentDescription = "Save this trip",
    onClick = { save() },
    modifier = Modifier.tooltip("Save this trip"),
)

// The component, for a tooltip whose visibility you own — a coach mark on
// first run, or one shown from a keyboard shortcut.
var open by remember { mutableStateOf(false) }
Box {
    Button(onClick = { open = !open }, variant = ButtonVariant.Secondary) { +"Recentre" }
    Tooltip(visible = open) { +"Bring the map back to you" }
}
```

Most callers want [`Modifier.tooltip`](#modifiertooltip) rather than the
component — it attaches to the control, tracks hover and focus, and honours the
tracked [input modality](../overlays.md#input-modality) so a tooltip never
appears from a touch that was really a tap.

## `Modifier.tooltip`

```
Modifier.tooltip("Save this trip")
```

Or with the bubble's slots, the same as the component's: words with `+`, and an
icon or a painter beside them, set at the bubble's own size and ink.

```kotlin
Modifier.tooltip {
    +Tabler.Outline.CurrentLocation
    +"Bring the map back to you"
}
```

A tooltip is **not** an accessible name. A screen reader reads the control's own
`contentDescription`, so an icon button still needs one — the tooltip is for
people who can see the icon and cannot name it.

## A card to read off

`style = TooltipStyle.Card` draws the bubble as a small card instead: the
raised surface a menu is drawn on, edged and shadowed as one, standing a little
off what it is about with no arrow. It is for a few lines with figures in them,
which the inverse bubble's small, tight type does not suit. A chart's tooltip
is one.

```kotlin
Box {
    Meter(value = charge)
    Tooltip(visible = showingCharge, side = OverlaySide.End, style = TooltipStyle.Card) {
        +"81% charged, full by 6:40"
    }
}
```

**A tooltip opens where the whole of it fits.** `side` is where it goes when
it fits there. When it does not, it opens on the other side, and then beside
the control, the roomier side first, rather than squeezing its lines into the
room that is left. It scrolls only when there is no side of the control with
room for all of it, which at a phone's size and large type can still happen. A
card asks more of a side before it opens there: the width of a card, so its
rows do not break their names over two lines.

---

## Accessibility

**A tooltip is not an accessible name.** A screen reader reads the control's own
`contentDescription`, so an icon button still needs one — the tooltip is for
people who can see the icon and cannot name it.

`Modifier.tooltip` tracks hover and keyboard focus, and honours the tracked input
modality, so it never appears from a touch that was really a tap. That is also
what makes it appear on keyboard focus, which a hover-only tooltip does not.

The coach-mark form merges its title and body into one description
(`"$title. $text"`) so it is announced as a sentence rather than as two
unrelated nodes, and its dismiss button carries a real label.

At high contrast the bubble is the enhanced scheme's inverse surface and ink,
which already stand apart from the page, so it takes no edge. A card keeps
its edge and shadow at every tier, as a menu does. A coach mark's
dismiss button is a ghost button and gains the edge those take at this tier.

Under reduced motion a tooltip and a coach mark fade in where they will sit
rather than scaling up into place, and arrive without overshoot.
