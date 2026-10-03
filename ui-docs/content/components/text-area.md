# `TextArea`

Grows between the bounds in its `lineLimits`, three lines to eight by default,
then scrolls internally. Growing rather than scrolling from the first line is
what lets a two-line note stay visible while a long one stays contained.

An empty area shows its `placeholder` where the text will start: at the top, at
the start edge (the right, right to left), wrapped over the lines the area
starts with.

<!--sample:TextAreaBasics-->
```kotlin
val note = rememberTextFieldState()

// Grows between the two bounds and then scrolls, so the form neither starts
// enormous nor jumps a line every time the sentence wraps.
TextArea(
    state = note,
    label = "What went wrong?",
    placeholder = "The 950 didn't turn up",
    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 8),
)
```

---

## Accessibility

Everything on [`TextField`](text-field.md) applies — the label as the accessible
name, `errorMessage` as `error` semantics, `enabled = false` as a disabled node.

`lineLimits` is the accessibility-relevant setting: the field grows between its
two bounds and then scrolls, so it neither starts enormous nor moves the rest of the
form a line at a time as the user types. Content that moves under a magnifier is
content that has to be found again.

Put it last in an [`ImeChain`](ime-chain.md), or don't put it in one: a
multi-line field needs its return key for returns.

At high contrast the frame's border is drawn at the tier's thicker line, thicker
still while the field is focused or in error, and a `Filled` area, which has no
border at the standard tier, gains an `outline` one.

Under reduced motion the message under the field takes its room at once and
fades in. The growth between the two bounds is layout rather than animation — a
line arrives with the text that needs it — and the border and the ground change
over `fast` at every setting.
