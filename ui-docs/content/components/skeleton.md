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

## Accessibility

Every skeleton clears its own semantics. A screen reader is told nothing about
them at all, which is correct — they stand in for content that does not exist
yet, and announcing "loading, loading, loading" seven times is not information.

The consequence is that a skeleton alone is silent. Where the wait is long
enough to notice, put a live region beside it, or use a
[`Spinner`](spinner.md) with a description.

Being shaped like the content is the accessibility feature: nothing moves when
the real thing arrives, so a magnified or slowly-read page does not shift under
the reader.
