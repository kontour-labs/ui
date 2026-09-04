# `Switch`

![Switch, unchecked](../../../ui-catalog/screenshots/components/switch-light.png)
![Switch, checked](../../../ui-catalog/screenshots/components/switch-checked-light.png)

<!--sample:SwitchBasics-->
```kotlin
var liveAlerts by remember { mutableStateOf(true) }

Switch(checked = liveAlerts, onCheckedChange = { liveAlerts = it })
```

**Use a switch for a setting that takes effect immediately, and a `Checkbox` for
one that is part of a form and takes effect on submit.** A user who flips a
switch expects the thing to have happened; a user who ticks a box expects to
press Save.

The thumb stretches as it travels — wider mid-flight, round at rest — and keeps
its 2dp of clearance on both sides the whole way, growing into whichever side has
the room. At either end that is all behind it, so the stretch trails the way give
should.

**The track is filled in both states and the thumb never changes colour.** Only
the track behind it does, because a switch has one moving part and one thing that
changes behind it; recolouring the thumb as well makes the flip read as two
events. The off track used to be an unfilled, stroked capsule, on the reasoning
that a grey track sits too close in tone to the surfaces it is toggled on top of
to read as a distinct control. The reasoning was right and the conclusion was
not — the answer is not *no* fill but a fill dark enough. It is
`outlineStrong`, the token that exists to bound an interactive control at the 3:1
WCAG asks for, and `ColourSchemeContrastTest` holds it there against every ground
a switch can land on.

---

**Drag the thumb.** A switch is the most draggable-looking control there is, and
a drag that stops short of the middle springs back rather than toggling. Wherever
the finger lets go, that is where the spring starts from — there is one position
for the thumb, not a drag position and a separate resting animation that have to
agree. It works inside a `SelectionRow` too, where the row still owns the tap —
the row publishes its own toggle for the switch to drag against.

---

## Accessibility

`Role.Switch` with a `toggleableState`, which is what makes a screen reader say
"on" and "off" rather than "checked" and "unchecked". The difference is not
pedantry: a switch takes effect immediately, and a checkbox is a value that is
submitted later.

Put it in a [`SelectionRow`](selection-row.md) with `onCheckedChange = null` so
the row is the target — a bare switch has no name.

The thumb stretches while it moves and does not under reduced motion. Nothing
about the announcement changes.

---

← [Selection](selection.md) · [All components](../components.md)
