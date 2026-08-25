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

← [Text editing](text-editing.md) · [All components](../components.md)
