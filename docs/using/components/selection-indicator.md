# `SelectionIndicatorBox`

The travelling pill behind a selected destination — the thing that slides from
one nav item to the next rather than appearing on it.

*Also on this page: `Modifier.selectionIndicatorItem`.*

One indicator, drawn by the container, moved between children. That is the whole
design: an indicator drawn *by each item* cannot animate between two of them,
because the one leaving and the one arriving are different composables and
neither can see the other. The box owns it, each item marks itself with
`Modifier.selectionIndicatorItem(key)`, and the box interpolates between the
bounds it has been told about.

Used by `NavBar`, `NavRail`, `NavDrawer` and `TabBar`, which is why they agree
about how selection looks — see
[how selection is shown](nav-surfaces.md#how-selection-is-shown).

---

← [Foundation](foundation.md) · [All components](../components.md)
