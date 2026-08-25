# `Text`

*Also on this page: `ProvideTextStyle`.*

The typographic primitive. Resolves its style and its colour from the theme
rather than taking them, so a paragraph inside a `Card` on a dark scheme needs
no arguments at all.

<!--sample:TextBasics-->
```kotlin
Text("Perth Underground", style = Theme.typography.titleMedium)

Text(
    "Platform 2 · Joondalup line",
    style = Theme.typography.bodySmall,
    color = Theme.colors.contentMuted,
)

// The `AnnotatedString` overload is why there are two: a route number in
// the accent colour inside a sentence, without a second component and
// without breaking the line box.
Text(
    buildAnnotatedString {
        append("The ")
        withStyle(SpanStyle(color = Theme.colors.accent.solid)) { append("950") }
        append(" leaves in 4 minutes.")
    },
)
```

Two overloads, `String` and `AnnotatedString`. The second is what carries spans —
a route number in the accent colour inside a sentence — without a second
component or a second style.

Colour comes from `LocalContentColor`, which [`Surface`](surface.md) sets. That
chain is the reason a component can be dropped on a dark card and stay legible
without every child being told where it is.

---

## Accessibility

`Text` is the accessible content of nearly everything above it, so most of what
matters here is what it is *inside*: a component that draws its label with
`Text` and puts the name on the control is the pattern, and drawing a label
beside an unnamed control is the bug — Compose has no `labelledBy`, so a label
near a field is an unrelated node however close it is on screen.

Links inside an `AnnotatedString` use `LinkAnnotation`, so the platform gets real
link semantics — focusable, announced as a link, activated by the keyboard —
rather than a tap handler that works out which range was hit.

Never size type in `sp` computed from a `Dp`. `Theme.typography` scales with the
user's text setting; a literal does not, and 200% is the setting the
[accessibility page](../accessibility.md) promises to survive.

---

← [Foundation](foundation.md) · [All components](../components.md)
