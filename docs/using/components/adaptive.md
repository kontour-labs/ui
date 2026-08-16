# Adaptive layout and motion

| | |
|---|---|
| `WindowSizeClass` / `WindowSizeClassProvider` | Compact / medium / expanded / large, from a measured window |
| `WindowAdaptiveInfo` | Size class *and* input modality, together |
| `Scaffold` | Top bar, bottom bar, FAB, and the padding for the content |
| `ListDetailPaneScaffold` | Two panes when there is room, one at a time when there is not |
| `SupportingPaneScaffold` | Content with a helper pane, or a sheet when narrow |
| `AspectRatioBox` | Reserves a media slot before its content loads |
| `Motion.fadeThrough` / `sharedAxis` / `containerTransform` | Transition presets |
| `Modifier.revealOnScroll` | Fades content in the first time it appears |
| `Modifier.parallax` | Scroll-linked drift |
| `Modifier.shimmer` | The travelling highlight behind a skeleton |
| `GlassSurface` | Translucent panel for a bar over content |
| `Modifier.atmosphere` | The radial glow both websites use behind a hero |
| `Modifier.edgeVignette` | Soft falloff at the edges of a scrolling page |

---

## Layout

**`WindowAdaptiveInfo` bundles size with input modality** because the decisions
are rarely about one alone. A 900dp touchscreen held in the hands is not a 900dp
desktop window, and a resize handle is a very different proposition in each.

**`Scaffold` hands the content the whole area plus the padding to inset by**,
rather than squeezing it into the gap between the bars. That is what lets a list
scroll *under* a translucent bar.

The padding is the **larger** of the bar and the inset on each edge, never their
sum — a bar has already padded itself for the inset it sits under, and adding
both insets the content twice. `ScaffoldGeometryTest` renders a real scaffold and
measures it, because a double-inset looks like a slightly generous gap rather
than a bug.

**A pane scaffold decides layout, not state.** The caller keeps the selection,
which is what makes back work: on one pane it clears the selection, on two panes
there is nothing to go back from and it does not appear.

---

## Motion presets

The choice between them says what the *relationship* between two states is:

| | For | Says |
|---|---|---|
| `fadeThrough` | Unrelated content in the same place | "a different thing" |
| `sharedAxis` | A step forward or back in a sequence | "further along" |
| `containerTransform` | One thing becoming a bigger view of itself | "the same thing" |

Picking the wrong one is not cosmetic. A shared-axis slide between two unrelated
tabs implies an order that is not there; a fade between a list row and its detail
throws away the one cue connecting them.

Every preset collapses to a plain cross-fade under reduced motion, and so do
`revealOnScroll`, `parallax` and `shimmer` — movement is the thing the preference
exists to remove. The full list of what reduced motion changes is with the
[motion tokens](../tokens.md#motion).

---

## There is no portable backdrop blur

Compose's `Modifier.blur` blurs a layer's *own* content, not what is behind it,
and there is no common equivalent of CSS's `backdrop-filter` or iOS's
`UIVisualEffectView`. So `GlassSurface` draws a translucent tint and a hairline
edge — most of what reads as glass — and offers real frosting only via a
`backdrop` slot that composes the background **twice**. Fine for a static image;
not for the live map a floating bar actually sits over, which is why it is not
the default.

`Modifier.atmosphere` is opt-in and deliberately not baked into `Surface`. It is
a treatment for a hero or an onboarding screen; a gradient behind every list is
one nobody notices and everybody pays for.
