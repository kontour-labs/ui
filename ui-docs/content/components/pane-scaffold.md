# `ListDetailPaneScaffold` / `SupportingPaneScaffold`

*Also on this page: `PaneFocus`.*

Two panes where there is room for two, and one at a time where there is not.

<!--sample:PaneScaffoldBasics-->
```kotlin
var focus by remember { mutableStateOf(PaneFocus.List) }
var selected by remember { mutableStateOf<String?>(null) }

// One pane on a phone and two on a tablet, from the same call. `onBack` is
// what closes the detail on a phone, where there is nowhere else to go.
ListDetailPaneScaffold(
    focus = focus,
    onBack = { focus = PaneFocus.List },
    list = {
        Column {
            stops.forEach { stop ->
                Text(
                    stop.name,
                    modifier = Modifier.fillMaxWidth().padding(Theme.spacing.md),
                )
            }
        }
    },
    detail = { Text(selected ?: "Pick a stop") },
)
```

`ListDetailPaneScaffold` takes a `PaneFocus` rather than a boolean, and `onBack`
rather than owning the back stack — so it composes with whatever the app already
uses for navigation instead of being a second one. On a narrow window the focus
decides which pane is on screen; on a wide one both are, and the focus only
decides which is highlighted.

`resizable` puts a drag handle on the divider. Worth it where the two panes carry
comparable amounts of content, and not where the list is a fixed set of six
entries.

`SupportingPaneScaffold` is the same idea with a different emphasis: a main pane
that is the point and a supporting one that qualifies it — filters beside
results, a legend beside a map. Where a list-detail pair swaps focus, this one
tucks the supporting pane away.

**The supporting pane slides in and out from the trailing edge**, and the main
pane widens to meet it rather than jumping. Its content is laid out at its full
width and clipped while it moves, so it slides rather than squeezing, and
`supporting` goes on being called until it has gone. On a compact window it is a
sheet that rises and falls the same way.

**No pane starts again when the layout changes.** Each is the same composable in
every arrangement, moved rather than rebuilt, so a list keeps its scroll position
when the window crosses the two-pane breakpoint and the main pane keeps whatever
it holds while the supporting pane opens and closes. Both used to reset.

**On one pane the list keeps its saved state while the detail is open.** It
leaves the composition while the detail has the screen — moved content is kept
only while it is somewhere — so the scaffold saves what it holds in
`rememberSaveable` as it goes and hands it back when it returns: back from a
stop finds the list where it was scrolled to, and so does narrowing to one pane
with a stop open. A plain `remember` in the list starts again, as it does
anywhere a composable leaves and comes back; hoist it above the scaffold if it
must outlast the detail. The detail is not kept this way, because it is usually
a different stop each time it opens.

---

## Accessibility

The resize handle is a control, not a decoration. It reports
`contentDescription = "Resize panes"` and a `ProgressBarRangeInfo` of the first
pane's share of the width, over the range the drag can reach — as close to either
end as leaves that pane its 280dp minimum — with `setProgress` putting the seam
at the share asked for. So the panes can be resized from a screen reader without
the drag gesture, and a request for 0.6 is 0.6 of the window, whatever its width.

On a compact window there is one pane, and `onBack` is what returns from the
detail to the list. The scaffold calls it for back itself — the system gesture,
iOS's edge swipe, Escape — and the detail follows a gesture while it runs, so
the list is uncovered under the hand. Call it from any visible back button too:
a phone user who opened a detail and cannot get back to the list is stuck.

Give each pane a heading at its top. Two panes side by side with no headings are
one long run of content to anyone navigating by structure.

At high contrast the rule between two panes takes the tier's thicker
`dividerThickness`, and so does the resize handle's line, which is three times
that under a pointer. On a compact window the supporting pane is a modal sheet,
which takes the tier's edge.

Under reduced motion the supporting pane takes its width at once and fades in,
rather than sliding in while the main pane narrows to meet it, and it goes at once
when it closes. On a compact window the list and the detail cross-fade rather
than sliding, a back gesture seeks that fade instead of moving the page, and the
supporting pane's sheet fades where it rests.
