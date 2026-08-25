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

← [Adaptive](adaptive.md) · [All components](../components.md)
