# `SelectionRow`

<!--sample:SelectionRowBasics-->
```kotlin
var notifyOnDelay by remember { mutableStateOf(false) }

SelectionRow(
    selected = notifyOnDelay,
    onSelectedChange = { notifyOnDelay = it },
    role = Role.Checkbox,
) {
    +"Notify me about delays"
    supporting { +"Only for favourited routes" }
    // The row owns the interaction; the control is here to show state.
    trailing { Checkbox(notifyOnDelay, onCheckedChange = null) }
}
```

**This is the form almost every checkbox, radio and switch should take.** The
nested control takes `onCheckedChange = null` — the row owns the interaction,
the control is there to show state.

It takes [`ListItem`](list-item.md)'s builder rather than one of its
own, because a selection row is a list row that happens to toggle. Which slot
the control goes in *is* its position — there is no `controlPosition`, and
`leading` suits a list of options being picked from where `trailing` suits a
settings list.

`role` is required rather than inferred, because the row cannot see which
control you nested in it.

### A row that opens a choice

<!--sample:SelectionRowOpensChoice-->
```kotlin
// A setting whose value is chosen somewhere else: pressed, it opens the
// choice, and it shows what is chosen now.
SelectionRow(onClick = { save() }) {
    +"Theme"
    leading { +Tabler.Outline.Moon }
    trailing { +"Match system" }
}
```

The other half of a settings screen. A setting whose value flips where it stands
is the row above, with the control in it. A setting whose value is chosen
somewhere else — a sheet, a menu, a screen of its own — is this one: pass
`onClick` instead of `selected`, and put the current value in `trailing`. It is a
`Role.Button`, and silent when pressed, as a [`ListItem`](list-item.md) is: what
it opens is the answer. `onClick = null` shows a value that cannot be changed
here.

**There used to be a `SettingRow` for this,** and two components for one settings
screen was one too many: it was never clear which a row with a switch in it
should be. It should be this one with `role = Role.Switch`. A row you press to
*open* something only wants a switch in it if what it opens is where the switch
is really set, and that is rare.

### In a group

`position` puts a row in a group of rows — a [`ListSection`](list-section.md), or
rows that should read as one card. Given one, the row is a list row: the sunken
ground a `ListItem` has, with the corners that meet a neighbour squared, so a
group can mix rows that toggle with rows that open a choice and with plain
`ListItem`s, and read as one thing. Without one it is a bare row with no ground,
for a form or a card that already has its own.

---

## Accessibility

This is the component that makes the "one target, not two" rule easy to follow:
the **row** carries `selectable` or `toggleable` with the role, and the control
inside takes `onCheckedChange = null` or `onClick = null`.

`role` decides what is announced and how selection behaves. Under
`Role.Checkbox` and `Role.Switch` it reports a `toggleableState` and pressing
flips it; under `Role.RadioButton` selection is one-way, because a radio is
turned on by pressing it and off by pressing another.

The label in the row is the accessible name. A row with only an icon has none.

In the browser, under Compose 1.12, the row is announced as a button whatever
its `role`, with no checked state — see
[In the browser](../accessibility.md#in-the-browser).

At high contrast the row gains no edge, because it has no ground or border of
its own to outline, and a press's tonal wash is stronger. The control in it
answers the tier as it does alone: a `Switch` adds a check or a dash to its
thumb.

`position` is visual only: it rounds the corners and announces nothing.
Grouping is expressed by [`ListSection`](list-section.md).

Under reduced motion the row is unchanged, since it never shrinks when pressed
at any setting; the control in it shows the row's press and answers the setting
as it does alone.
