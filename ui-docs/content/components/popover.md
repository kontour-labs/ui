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
    Popover(visible = open, onDismissRequest = { open = false }, title = { +"Route 950" }) {
        Text(
            "Runs every 15 minutes until 11pm, then every 30 minutes overnight.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
    }
}
```

**`title` is a heading at the top of the panel**, `title = { +"Route 950" }`, in
`titleSmall` and announced as one. The content under it is any column.

**A popover is not a small dialog.** If the content is a decision that must be
made before anything else can happen, it is a [`Dialog`](dialog.md). If it is a
list of actions, it is a [`DropdownMenu`](dropdown-menu.md), which handles
keyboard traversal and the roles a menu owes a screen reader.

**`side` is honoured where the popover fits, and `alignment` is a preference.**
The popover opens on the side you asked for when all of it fits there. When it
does not, and all of it fits on the other side, it opens there instead. When it
fits whole on neither, it opens on your side and scrolls whatever does not fit,
unless your side has no usable room at all. `alignment` still gives way — a
popover aligned to the start of a button in the far corner slides until it is
on screen. Both are described for every anchored overlay under
[anchoring](../overlays.md#anchoring).

`side` has been turned round twice, from two phones. A trigger 130dp from the
bottom edge of a Pixel leaves about 90dp under it, a two-line popover is about
88dp, and the margin and the gap and the arrow want twenty more than there are:
it opened upward, for the sake of twenty pixels, and was made to stay on its
side and scroll while that side had a line's room. That went too far the other
way, and 0.5.3 found it from a date-time picker's time chip low on a phone: its
wheels opened below into a couple of rows' room, about a third of them, with
the whole screen free above. A part scrolled out of sight is a part not read,
so a popover that fits whole across its anchor opens there, and the Pixel's
two-line popover opens upward again.

`showArrow` draws the tie back to the anchor; turn it off when the popover is
wide enough that the arrow points at nothing in particular.

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

At [high contrast](../accessibility.md#what-high-actually-changes) a popover
with an arrow gains the border it otherwise leaves off, and the arrow is
outlined with it. Every border is `outlineSubtle`, which at this tier is the
near-ink `outline`, and is drawn thicker.

Under [reduced motion](../tokens.md#motion) it fades in where it will rest
instead of growing out of its anchor, and it arrives on a short tween rather
than a spring.
