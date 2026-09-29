# `Skeleton` / `SkeletonText` / `SkeletonListItem`

*Also on this page: `Modifier.shimmer`.*

<!--sample:SkeletonBasics-->
```kotlin
// Shaped like the content it stands in for, so nothing moves when the real
// thing arrives. A spinner in the same place would move everything.
Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
    Skeleton(Modifier.width(180.dp).height(20.dp))
    Skeleton(Modifier.width(120.dp).height(16.dp))
    // A shape no skeleton comes in — a map tile — clipped, with the same
    // sweep, so it loads in step with the lines above it.
    Box(Modifier.size(96.dp).clip(Theme.shapes.medium).shimmer())
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

## `Modifier.shimmer`

```
Box(Modifier.size(96.dp).clip(Theme.shapes.medium).shimmer())
```

The skeletons' own sweep, for a placeholder in a shape they do not come in: a
map tile, a chart, a photo cropped to a circle. Clip the box to the shape and it
fills with the same ground and the same band, moving at the same speed, so it
loads in step with the skeletons beside it rather than as a second effect.

It only paints, behind the node's content, and adds nothing for a screen reader
to read, so the loading region carries the announcement as it does for a
skeleton. On a container that is already `surfaceSunken`, pass a `colour` that
stands apart from it — the default would be a slab on a slab.

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

At high contrast a skeleton takes no edge. It stays the enhanced scheme's
`surfaceSunken`, faint against the page on purpose, since it stands in for
content rather than being any. In a dark scheme the shimmer's band is `outline`,
which the tier lightens, so the sweep shows more there.

Under reduced motion the shimmer stops and the block holds a flat tone; the
sweep's animation is not started at all. `Modifier.shimmer` does the same. In a
right-to-left layout the band runs from the right.
