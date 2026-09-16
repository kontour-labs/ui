# `SegmentedControl`

<!--sample:SegmentedControlBasics-->
```kotlin
var selected by remember { mutableStateOf(0) }

SegmentedControl(
    options = listOf("Bus", "Train", "Ferry"),
    selected = selected,
    onSelectedChange = { selected = it },
)
```

Two to four short options the user switches between often. Beyond four, or with
long labels, use a [`RadioGroup`](radio-group.md) or a
[`Select`](select.md); segments get too narrow to read and too
narrow to hit.

It takes `List<String>` rather than a slot on purpose — a segment that could
hold arbitrary content is a segment that can be made too wide to fit beside
three others, and the cap at four short labels is the component's whole
premise.

### When the labels do not fit

The track is divided evenly between the segments, so a long label on a narrow
control is *expected* to run out of room. **When one does, the options stop
sharing a row and stack into full-width rows instead** — one per option, every
label drawn whole.

Note "short" is relative to the *control's* width, not to the label. Four
segments in 244dp is 58dp each — the track is the control less 6dp of padding a
side, which an earlier version of this page forgot — and "Keyboard" exceeds that
at the default type scale, so a control that narrow stacks. A reader who has
turned text size up makes every segment tighter without making the control wider,
which is why this is a text-size question as much as a layout one: at 200% both
`Standard` and `Keyboard` are about twice their segment's width.

Wrapping to a second line is not the answer and was tried: `Keyboard`, `Standard`
and `200` are single unbreakable words, so a second line gets nothing.

Stacked, the control drops its horizontal drag and keeps its taps. The drag
quantises a position along one axis into equal buckets, and stacked there is no
such axis — a vertical drag would also be competing with the page for its own
direction. Tapping an option is the whole interaction at a size where this fires.

**The control also grows taller with the type.** Its height used to be pinned at
`max(controlHeightMedium, minTouchTarget)` whatever the text size, and a 14sp
label with a 1.20 line height passes that box at around 2.1×, at which point the
glyphs were clipped top and bottom with no ellipsis to mark it. It is a minimum
now rather than a fixed height.

If your labels stack at 100%, that is still the signal to reach for a
`RadioGroup` or a `Select` — stacking keeps the words readable, but four rows of
one option each is a radio group wearing a segmented control's clothes.

The indicator is a single surface that **slides** between positions rather than
each segment fading its own background — that is what makes it read as one
physical thing with a moving part. It shares
`foundation/SelectionIndicator.kt` with the four navigation surfaces, so they
cannot drift apart.

**Not to be confused with `TabBar`.** A segmented control switches a *value*;
tabs switch which *view* of one screen you are looking at. Announced
`Role.RadioButton` per segment, where a tab announces `Role.Tab`.

---

**Drag across the segments** and the thumb comes with you, ticking at each
boundary. It is *carried*, not chasing: between boundaries the thumb leans out of
its segment toward where your finger is and stretches as it leans, on the same
`SliderDefaults.DetentPull` the sliders use. It used to spring from one segment
to the next once the value had already changed, which meant the only part of the
gesture you could see was the part that was over. The gesture is on the track
rather than on each segment: a drag from "Depart" to "Arrive" leaves the segment
it began in, and a per-segment handler loses the pointer at the boundary. Taps
still belong to the segment under them.

The drag is *owned*: from the first pixel the finger travels, the control takes
the whole gesture and a scrolling page above it cannot take it back. Without
that, whether the thumb moved at all depended on the angle — the control and the
list it sits in are both waiting for their own touch slop, and more than 45° off
the track the list reached its threshold first and the thumb never moved. The
price is that you cannot scroll a page by dragging on a segmented control. A
press that never travels is left alone, so tapping one is unchanged.

---

## Accessibility

A `selectableGroup()` of `Role.RadioButton` segments, so it announces "2 of 3"
and "selected" — which is what it is, and what a row of buttons would not be.

That is the difference from [`ButtonGroup`](button-group.md), whose items are
actions, and from [`TabBar`](tab-bar.md), whose items are `Role.Tab` and change
what is on screen below them.

Beyond four or five options the segments become unreadable slivers before they
become inaccessible — but they become both. A [`Select`](select.md) is the answer
for a long set.
