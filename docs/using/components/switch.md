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
its 3dp of clearance on both sides the whole way, growing into whichever side has
the room. At either end that is all behind it, so the stretch trails the way give
should. The off track is bordered and unfilled rather than grey-filled: a grey
track sits too close in tone to the surfaces it is toggled on top of to read as a
distinct control.

---

**Drag the thumb.** A switch is the most draggable-looking control there is, and
a drag that stops short of the middle springs back rather than toggling. Wherever
the finger lets go, that is where the spring starts from — there is one position
for the thumb, not a drag position and a separate resting animation that have to
agree. It works inside a `SelectionRow` too, where the row still owns the tap —
the row publishes its own toggle for the switch to drag against.

---

← [Selection](selection.md) · [All components](../components.md)
