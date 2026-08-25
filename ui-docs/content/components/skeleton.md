# `Skeleton` / `SkeletonText` / `SkeletonListItem`

<!--sample:SkeletonBasics-->
```kotlin
// Shaped like the content it stands in for, so nothing moves when the real
// thing arrives. A spinner in the same place would move everything.
Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
    Skeleton(Modifier.width(180.dp).height(20.dp))
    Skeleton(Modifier.width(120.dp).height(16.dp))
}
```
`Skeleton`, `SkeletonText` and `SkeletonListItem` — placeholders in the shape of
what is coming.

**They are hidden from the accessibility tree.** There is nothing to announce,
and a screen reader walking a dozen unlabelled boxes is noise — the container
carries the loading announcement instead. The shimmer stops under reduced
motion.

**Reach for a skeleton over a spinner** when you know the shape of what is
loading. A list of five rows that appears as five grey rows does not reflow when
it arrives.

---

← [Display and content](display.md) · [All components](../components.md)
