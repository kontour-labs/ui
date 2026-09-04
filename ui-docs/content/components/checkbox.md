# `Checkbox`

<!--sample:CheckboxBasics-->
```kotlin
var notify by remember { mutableStateOf(false) }

Checkbox(checked = notify, onCheckedChange = { notify = it })
```

The tick is drawn on a `Canvas` and *strokes itself on* along its path rather
than fading in, with the box springing up to meet it. Two frames of personality
on a control people tap dozens of times a session.

`onCheckedChange` is nullable, and passing `null` makes the checkbox **inert but
still stateful** — the enclosing row owns the click and the checkbox is there to
show state. It is not the same as `enabled = false`, which says the choice is
unavailable.

Inert still means it announces `Role.Checkbox` and its tick. That matters inside
a [`SettingRow`](setting-row.md), which is `clickable` rather than
`toggleable` and so publishes no checked state of its own; without the control
saying it, the row announces as a button with a name and no on or off.
`InertControlPublishesStateTest` covers all three controls.

**The box answers the press, not the release.** The tick starts being drawn under
the finger and starts being rubbed out under a press on a ticked box — a third of
the way, so a press-and-slide-off still comes back. `RadioButton` does the same
with its dot. A switch's thumb has stretched like this since it was written; these
two sat still until the value committed, so the same tap read as responsive on one
control and dead on the others. Inside a `SelectionRow` they read the row's press,
which is what `LocalRowInteractionSource` was always for.

---

## Accessibility

`Role.Checkbox` with a `toggleableState`, so a screen reader announces checked,
unchecked or — for [`TriStateCheckbox`](tri-state-checkbox.md) — indeterminate.

A bare checkbox has no name. Put it in a [`SelectionRow`](selection-row.md) with
`onCheckedChange = null`, so the row carries the click, the role and the label
and there is one target rather than two.

The touch target is the minimum regardless of the box's drawn size, which is why
a checkbox in a dense list still meets it.
