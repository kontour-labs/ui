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
