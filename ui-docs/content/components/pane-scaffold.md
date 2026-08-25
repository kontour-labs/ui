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

---

## Accessibility

The resize handle is a control, not a decoration. It reports
`contentDescription = "Resize panes"` and a `ProgressBarRangeInfo` over
`0.2..0.8`, with `setProgress` wired to the split — so the panes can be resized
from a screen reader without the drag gesture.

On a compact window there is one pane, and `onBack` is what returns from the
detail to the list. Wire it to the platform back handler as well as to any
visible button: a phone user who opened a detail and cannot get back to the list
is stuck, and the back gesture is the thing they will try first.

Give each pane a heading at its top. Two panes side by side with no headings are
one long run of content to anyone navigating by structure.

---

← [Adaptive](adaptive.md) · [All components](../components.md)
