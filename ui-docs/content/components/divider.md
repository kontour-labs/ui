# `HorizontalDivider` / `VerticalDivider`

Decorative rules. Both carry an empty semantics node — nothing to announce —
because a line is not something a screen reader should read out.

<!--sample:DividerBasics-->
```kotlin
Column {
    Text("Departures")
    HorizontalDivider()
    Text("Alerts")
}

Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
    Text("Platform 2")
    VerticalDivider(Modifier.height(16.dp))
    Text("Joondalup line")
}
```

A divider is the weakest way to group things and usually the wrong one. Space
separates without drawing anything, and a [`ListSection`](list-section.md) or a
[`Card`](card.md) says *these belong together* rather than *these are apart*.
Reach for a rule where the alternative would be an unreasonable amount of space —
between the halves of a toolbar, under a bar that must read as attached.

---

## Accessibility

Both dividers carry `Modifier.semantics { }`, which clears them from the
accessibility tree entirely. A rule is presentational, and a screen reader
stopping on one is noise.

If a divider is doing something a reader needs to know about — separating two
*groups* rather than two rows — the grouping is what should be expressed, with a
heading or a `ListSection`, and the rule is still just a rule.

At high contrast a divider is drawn at `dividerThickness`, which goes from 1dp to
2dp, in the enhanced scheme's `outline`, which is near-ink. A `thickness` or
`colour` passed explicitly stays as given.

Nothing about a divider moves, so reduced motion changes nothing.
