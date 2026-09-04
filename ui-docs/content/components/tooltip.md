# `Tooltip`

*Also on this page: `Modifier.tooltip`.*

The name of a control the user is pointing at.

<!--sample:TooltipBasics-->
```kotlin
// The modifier is what a caller nearly always wants: it tracks hover and
// focus itself and honours the input modality, so it never appears from a
// touch that was really a tap.
IconButton(
    icon = Tabler.Outline.Bookmark,
    contentDescription = "Save this trip",
    onClick = { save() },
    modifier = Modifier.tooltip("Save this trip"),
)

// The component, for a tooltip whose visibility you own — a coach mark on
// first run, or one shown from a keyboard shortcut.
var open by remember { mutableStateOf(false) }
Box {
    Button(onClick = { open = !open }, variant = ButtonVariant.Secondary) { +"Recentre" }
    Tooltip(visible = open) { +"Bring the map back to you" }
}
```

Most callers want [`Modifier.tooltip`](#modifiertooltip) rather than the
component — it attaches to the control, tracks hover and focus, and honours the
tracked [input modality](../overlays.md#input-modality) so a tooltip never
appears from a touch that was really a tap.

## `Modifier.tooltip`

```
Modifier.tooltip("Save this trip")
```

A tooltip is **not** an accessible name. A screen reader reads the control's own
`contentDescription`, so an icon button still needs one — the tooltip is for
people who can see the icon and cannot name it.

---

## Accessibility

**A tooltip is not an accessible name.** A screen reader reads the control's own
`contentDescription`, so an icon button still needs one — the tooltip is for
people who can see the icon and cannot name it.

`Modifier.tooltip` tracks hover and keyboard focus, and honours the tracked input
modality, so it never appears from a touch that was really a tap. That is also
what makes it appear on keyboard focus, which a hover-only tooltip does not.

The coach-mark form merges its title and body into one description
(`"$title. $text"`) so it is announced as a sentence rather than as two
unrelated nodes, and its dismiss button carries a real label.
