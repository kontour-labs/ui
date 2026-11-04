# `Modifier.marquee`

<!--sample:MarqueeBasics-->
```kotlin
Text(
    text = stop.name,
    maxLines = 1,
    modifier = Modifier.marquee(),
)
```

For the label that is *usually* short and occasionally is not — a stop name, a
route headsign, a now-playing line. Safe to apply unconditionally: it measures
first and animates only when the content is wider than the space, so the common
case costs a comparison. Pair it with `maxLines = 1`; without that the text wraps
instead of overflowing, there is nothing to scroll, and the modifier does nothing
forever.

**It is off under reduced motion, and that is not a degradation.** Everything
else in this library softens — a spring becomes a tween, a slide becomes a fade.
This one stops entirely, because it is the one animation here that never ends,
and perpetual motion at the edge of vision is the specific thing that preference
exists to stop. The text truncates instead, which is what it would have done
without the modifier at all.

`iterations` defaults to three passes. `Int.MAX_VALUE` gives a ticker that never
stops — right for a live status line, wrong for a list, where a dozen rows all
scrolling at once is a screen nobody can read.

## Back and forth

<!--sample:MarqueeBackAndForth-->
```kotlin
Text(
    text = stop.name,
    maxLines = 1,
    modifier = Modifier.marquee(travel = MarqueeTravel.BackAndForth),
)
```

With `travel = MarqueeTravel.BackAndForth` the text does not loop. It waits at
its start, glides until its end reaches the box's end edge, waits, and glides
back. It goes only as far as the text overflows, with no gap and no second
copy.

Use it where seeing the start and the end matters and a wrap-around would split
the text: a now-playing line, or a long stop name. A loop brings the start back
in a gap behind the end, so for a moment the box shows the end of one copy
beside the start of the next — "Stand E Elizabeth Quay" — which reads as a stop
that does not exist. A loop suits a ticker, where the text is a stream.

A pass is there and back, so `iterations` counts round trips, and it always
settles at its start.

## Dragging it

<!--sample:MarqueeDraggable-->
```kotlin
Text(
    text = stop.name,
    maxLines = 1,
    modifier = Modifier.marquee(draggable = true),
)
```

With `draggable = true`, a finger or a mouse can pull the text sideways and
read it at its own pace, instead of waiting for the next pass. Holding it stops
it. Let go and it coasts, then, after the pause, carries on from where it was
left and finishes the pass it was in. With no passes left it goes back the
shorter way to its start, so it never settles part-way through the text.

Back and forth, it pulls only as far as either end of the text, and does not
wrap round. Let go, it finishes the way it was going: on its way out, out to
the end and home again; on its way home, home.

It is off by default because, when the text is too wide, it takes the sideways
drag within the label from anything around it that also swipes sideways, such
as a pager or a row's swipe actions. Text that fits takes nothing.

It wraps foundation's `basicMarquee` rather than replacing it. That already
measures, already stops when the content fits, already handles right-to-left.
What it does not have is this library's pace or any knowledge of `reduceMotion`,
and those are the two things worth owning. A draggable marquee, and one that
goes back and forth, are the exceptions: pulling the text needs the offset,
which `basicMarquee` keeps to itself, and it only ever loops, so those scroll
on their own.

---

## Accessibility

**Off entirely under reduced motion**, looping or back and forth, and that is
not a degradation. Everything else in the library softens — a spring becomes a
tween, a slide becomes a fade — and this one stops, because it is the only
animation here that never ends.
Perpetual motion at the edge of vision is the specific thing the preference
exists to stop, and there is no gentler version of "forever". The text truncates
instead, which is what it would have done without the modifier.

A draggable marquee still drags under reduced motion, because motion the user
makes is not motion done to them, and it never sets off by itself. Until it is
first pulled, the text is truncated just the same.

Scrolling changes nothing about what is announced: the full text is in the node
either way, so a screen reader gets all of it without waiting.

`iterations = Int.MAX_VALUE` is for a single live status line. A dozen rows all
scrolling at once is a screen nobody can read, with or without a preference set.

Nothing about it changes at high contrast. The modifier moves what it is on and
draws nothing of its own — no colour, no line, no fade at the edges — so the text
keeps whatever colour its component gave it.
