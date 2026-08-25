# `Tooltip`

The name of a control the user is pointing at.

Most callers want [`Modifier.tooltip`](#modifiertooltip) rather than the
component — it attaches to the control, tracks hover and focus, and honours the
tracked [input modality](../overlays.md#input-modality) so a tooltip never
appears from a touch that was really a tap.

*Also on this page: `Modifier.tooltip`.*

## `Modifier.tooltip`

```
Modifier.tooltip("Save this trip")
```

A tooltip is **not** an accessible name. A screen reader reads the control's own
`contentDescription`, so an icon button still needs one — the tooltip is for
people who can see the icon and cannot name it.

---

← [Overlays](overlays.md) · [All components](../components.md)
