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

### Code

`code = true` is for a snippet, a config file or a public key pasted in. The
text and the placeholder are set in the theme's mono face at the area's own
size, so columns line up and a 0 is not an O. The keyboard is asked for no
autocorrect and no capitalisation, since a corrected `val` or a capitalised key
is a different one. [`TextField`](text-field.md) takes the same flag.

<!--sample:CodeTextArea-->
```kotlin
val config = rememberTextFieldState("route = 960\nretry = 0")
val stop = rememberTextFieldState()

// The mono face, so columns line up and a 0 is not an O, and a keyboard
// that neither corrects nor capitalises: either would change what was meant.
TextArea(state = config, label = "Feed override", code = true)
TextField(state = stop, label = "Stop code", placeholder = "per-10352", code = true)
```

The face is [`Theme.typography.mono`](../theming.md)'s, which is JetBrains Mono
unless the theme says otherwise. Nothing else changes: the label, the frame and
the messages stay in the body face, and the area still wraps. A line of code
that must not wrap belongs in a horizontally scrolling block, not a field.

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
