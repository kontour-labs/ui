# Collections

Rows, and the things that happen to them.

| | For | Instead of |
|---|---|---|
| [`ListItem`](list-item.md) | One row of anything | — |
| [`ExpandingListItem`](expanding-list-item.md) | A row that unfolds more rows | `Accordion`, when what opens is *content* |
| [`SettingRow`](setting-row.md) | The settings shape: icon, label, value | `SelectionRow`, when the row *is* the toggle |
| [`ListSection`](list-section.md) | A titled group of rows | — |
| [`SwipeActions`](swipe-actions.md) | Actions behind a sideways drag | A menu, which you owe anyway |
| [`SwipeToDismiss`](swipe-actions.md) | Removing a row by dragging it away | — |
| [`ReorderableItem`](reorderable-item.md) | Drag to reorder, live | — |
| [`PullToRefresh`](pull-to-refresh.md) | Pull at the top to reload | A toolbar action, which you owe anyway |
| [`LoadMore`](load-more.md) | The paging row at the end | — |
| [`Modifier.fadingEdges`](modifier-fading-edges.md) | Fading content at a scrollable edge | — |
| [`Scrollbar`](scrollbar.md) | Where you are in a long list | — |
| [`DragHandle`](drag-handle.md) | The grab bar at the top of a sheet | — |

---

## Gestures are shortcuts, never routes
Swiping, pulling and dragging are invisible, have no keyboard or pointer
equivalent, and are unreachable for anyone who cannot make a sustained drag. Each
of the three above carries its actions a second way and the caller still owes a
third:

- `SwipeActions` puts every action on the row as a **custom accessibility
  action**, so a screen reader can reach it. That covers assistive tech, not a
  sighted mouse user — put the same actions in a menu.
- `ReorderableItem` exposes **move up** and **move down** the same way, since a
  drag is not a gesture a screen reader can perform and reordering with no
  alternative makes a whole feature unreachable.
- `PullToRefresh` needs a refresh action in the toolbar as well.
