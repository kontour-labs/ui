# `Popover`

Arbitrary content attached to a control. It points at the thing it is about and
leaves the rest of the screen alone.

<!--sample:PopoverBasics-->
```kotlin
var open by remember { mutableStateOf(false) }

// The anchor is whatever the popover is declared beside, so both live in
// one `Box` — the popover positions itself against its sibling.
Box {
    IconButton(
        icon = Tabler.Outline.InfoCircle,
        contentDescription = "About this route",
        onClick = { open = !open },
    )
    Popover(visible = open, onDismissRequest = { open = false }) {
        Text("Route 950", style = Theme.typography.titleSmall)
        Text(
            "Runs every 15 minutes until 11pm, then every 30 minutes overnight.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
    }
}
```

**A popover is not a small dialog.** If the content is a decision that must be
made before anything else can happen, it is a [`Dialog`](dialog.md). If it is a
list of actions, it is a [`DropdownMenu`](dropdown-menu.md), which handles
keyboard traversal and the roles a menu owes a screen reader.

`side` and `alignment` are a preference rather than an instruction: the popover
flips to the other side of its anchor when there is not room, which is the
behaviour [anchoring](../overlays.md#anchoring) describes for everything in the
overlay host. `showArrow` draws the tie back to the anchor; turn it off when the
popover is wide enough that the arrow points at nothing in particular.

---

## Accessibility

Escape closes it, handled with `onPreviewKeyEvent` so it fires before the
content sees the key.

A popover is *not* announced when it opens: it is content attached to a control,
not an alert, and the reader reaches it through traversal like anything else in
the overlay stack. If what you have is urgent enough to interrupt, that is a
[`Toast`](toast.md) with a live region, or a [`Dialog`](dialog.md).

The scrim defaults to `ScrimStyle.Transparent`, which blocks input without
dimming — and still carries the labelled dismiss action, so tapping away has an
equivalent for someone who cannot.
