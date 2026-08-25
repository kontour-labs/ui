# `TextArea`

Grows between `minLines` and `maxLines`, then scrolls internally. Growing rather
than scrolling from the first line is what lets a two-line note stay visible
while a long one stays contained.

<!--sample:TextAreaBasics-->
```kotlin
val note = rememberTextFieldState()

// Grows between the two bounds and then scrolls, so the form neither starts
// enormous nor jumps a line every time the sentence wraps.
TextArea(
    state = note,
    label = "What went wrong?",
    placeholder = "The 950 didn't turn up",
    minLines = 3,
    maxLines = 8,
)
```

---

## Accessibility

Everything on [`TextField`](text-field.md) applies — the label as the accessible
name, `errorMessage` as `error` semantics, `enabled = false` as a disabled node.

`minLines`/`maxLines` is the accessibility-relevant pair: the field grows between
them and then scrolls, so it neither starts enormous nor moves the rest of the
form a line at a time as the user types. Content that moves under a magnifier is
content that has to be found again.

Put it last in an [`ImeChain`](ime-chain.md), or don't put it in one: a
multi-line field needs its return key for returns.

---

← [Text editing](text-editing.md) · [All components](../components.md)
