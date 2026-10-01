# `Modifier.pressable`

**Something of your own, pressed the way the library's buttons are.** A tile, a
card, a row you drew yourself: one modifier gives it the whole of what pressing
anything in the library is, so it sits among the library's components without
being the one that feels different. Asked for as *"a way to easily make custom
components clickable using the style of our ui library? allow for things like
long press too"*.

<!--sample:PressableBasics-->
```kotlin
val stop = stops.first()
// First in the chain: what follows is what it presses, and a clip before
// it would cut off its focus ring.
Column(
    Modifier
        .pressable(
            onClick = { openStop(stop.name) },
            onLongClick = { pin(stop.name) },
            onLongClickLabel = "Pin",
        )
        .clip(Theme.shapes.container)
        .background(Theme.colours.surfaceSunken)
        .padding(Theme.spacing.md),
) {
    Text(stop.name, style = Theme.typography.titleSmall)
    Text("${stop.routes} routes", style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted)
}
```

A `clickable` gets you a press and nothing else. This gets you:

| | What | The same as |
|---|---|---|
| **The press** | The tonal wash in `shape`, the slight shrink, and the floor under a quick tap that makes a tap seen at all | Every button |
| **Hover** | A wash under a mouse, and nothing left behind by a finger | Every button |
| **The keyboard** | The focus ring, round `shape`, and thicker at high contrast | `Modifier.focusRing` — see [Focus](../accessibility.md#focus) |
| **The pointer** | A hand over it on a desktop or the web | [`Modifier.pointerCursor`](modifier-pointer-cursor.md) |
| **The target** | At least 48dp to a finger however small it is drawn | `Modifier.minimumTouchTarget` |
| **The long press** | `onLongClick`, felt as the library's long press | The haptics table in [Theming](../theming.md) |
| **What is said** | A button, and the long press named as an action of its own | — |

**Put it first**, before the `clip` and the ground. What comes after it is what
it presses: the shrink takes the ground with the content, and the wash is drawn
over both in `shape`. A `clip` before it cuts off the focus ring, which is drawn
just outside.

**Pass the shape it is clipped to.** The wash and the ring follow `shape`; left
at the default they are the library's container corners, and a tile with other
corners gets a wash that squares them off.

**A plain press makes no haptic**, as the library's buttons make none. A long
press does, once: the library's own, with the platform's turned off, since both
would be a double buzz.

**Large things should not shrink.** `pressScale = 1f` keeps the wash and drops
the shrink — right for a card or a whole row, where the surface moving reads as
a flinch rather than a press.

`onDoubleClick` is there too. A double tap delays every single tap by the time
it waits for the second, so give one only where the double tap is worth that.

---

## Accessibility

It is a button to a screen reader unless you pass another `role`, or `null`
where the content already says what it is. `onClickLabel` names what a press
does where the content does not; `onLongClickLabel` names the long press, which
is otherwise announced as a bare "long press" action. A long press is always
reachable from the accessibility actions, so it can never be the only way to
something.

Disabled, it is announced as disabled, takes no presses, and shows the arrow
rather than the hand. Draw the disabled look yourself — the modifier does not
know what your content is.

At high contrast the focus ring thickens with every other line, and the press
wash is the scheme's own overlay, which the high-contrast schemes already
strengthen. The ground and the edge are yours: a quiet ground at high contrast
wants the edge the library's own tiles get —
`contrastEdge()?.let { Modifier.border(it, shape) } ?: Modifier`, which is null
at the standard tier and leaves it untouched.

Under [reduced motion](../tokens.md#motion) the shrink is dropped and only the
wash remains: the tile still answers the press, it just does not move.
