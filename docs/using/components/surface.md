# `Surface`

Background, shape, border and shadow in one — and the thing that sets
`LocalContentColor` for everything inside it.

That last part is the point. A surface says what it is, and
[`Text`](text.md) and [`Icon`](icon.md) resolve against it, so a component
dropped onto a dark card is legible without any of its children being told where
they are. Setting a background with `Modifier.background` instead skips that
step, and is how content ends up dark-on-dark.

`shadow` comes from `Theme.elevation` rather than a `Dp`, because elevation is a
role — the same nominal height reads differently on a light scheme and a dark
one, where a shadow has almost nothing to fall on and a lighter surface does the
work instead.

---

← [Foundation](foundation.md) · [All components](../components.md)
