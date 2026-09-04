# `Chip`, `FilterChip`, `InputChip`

*Also on this page: `ChipGroup`.*

Chips are for things that come in *sets*. A single chip on a screen is usually a
small button wearing the wrong clothes.

| | |
|---|---|
| `Chip` | Presses like a button. `onClick` |
| `FilterChip` | On or off, and shows which. `selected` + `onClick` |
| `InputChip` | Something the user entered, with a remove button. `onRemove` + `removeLabel` |

<!--sample:FilterChipGroup-->
```kotlin
var active by remember { mutableStateOf(setOf(Mode.Fastest)) }

ChipGroup {
    Mode.entries.forEach { mode ->
        FilterChip(
            selected = mode in active,
            onClick = {
                active = if (mode in active) active - mode else active + mode
            },
            selectedIcon = Tabler.Outline.Check,
        ) {
            +mode.displayName
        }
    }
}
```

A selected `FilterChip` fills with the accent container and takes the accent for
its label, dropping the outline it wears unselected. That is the whole of the
difference.

**The tick is opt-in**, through `selectedIcon`. Pass one and it expands in and
shoves the label across, which is what makes a filter bar feel responsive when
you rattle through several. It is not a default because the library ships no
glyphs at all — the icon set is yours — so there is no tick here to reach for.

Pass one when it matters which chips are on. Without it selection is carried by
colour alone, which is legible but is a single channel, and a filter bar is
exactly the place someone scans rather than reads.

`InputChip`'s `removeLabel` is **required** and announces the remove button —
"Remove Perth Station", not "Remove". It used to default to `"Remove $label"`;
with the label in a slot there is no string to interpolate, and a bare "Remove"
in a row of five chips tells a screen-reader user nothing about which one goes.
The remove button is a separate target with its own description, so a screen
reader offers "Perth Station" and "Remove Perth Station" as distinct actions.

`ChipGroup` wraps onto new lines rather than scrolling horizontally — a
scrolling row hides options off the edge of the screen.

---

## Accessibility

Three components with three roles, and the role is the difference: `Chip` is
`Role.Button`, `FilterChip` is `Role.Checkbox` — because a filter is on or off —
and `InputChip`'s remove affordance is a separate button carrying `removeLabel`
as both its `onClickLabel` and its icon description.

That last one matters: an input chip with no remove label is a chip a user can
add and never take off.

The selected tick is decorative and cleared; the state is announced by the role,
not by the icon.
