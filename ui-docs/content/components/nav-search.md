# `NavSearch`

*Also on this page: `NavExpandingSlot`, `NavExpansion`, `LocalNavExpansion`, `NavSearchState`.*

A search sized to the navigation surface it is in: a pill that opens a field over
the screen in a bar or a narrow rail, and the field itself once there is room to
type.

<!--sample:NavSearchBasics-->
```kotlin
val search = rememberNavSearchState()

// A pill in a bar or a narrow rail that opens a field over the screen, and a
// field in place once there is room for one.
NavSearch(
    state = search,
    searchIcon = Tabler.Outline.Search,
    onSearch = { nearby() },
) {
    Text("Recent: Perth Underground")
}
```

**Tapping the pill opens an overlay, not a taller bar.** A bar clears
`WindowInsets.bottomEdges`, whose own documentation says why: a navigation bar
holds no text field and stays where it is while the user types. So the pill stays
put and the field goes into the `OverlayHost`, which brings the scrim, back and
Escape, and trapped focus with it. `NavExpandPlacement` says where it lands:
`AboveKeyboard`, near the thumb that opened it, or `Top`, where a browser puts one.

The pill is a recess in the surface it sits on — `surfaceSunken` in a bar and a
rail alike, since both have a ground now — and raised `surface` outside one.

### The same shape without a search in it

<!--sample:NavExpandingSlotBasics-->
```kotlin
var open by remember { mutableStateOf(false) }

// The same pill-and-panel with something other than a search in it.
NavExpandingSlot(
    expanded = open,
    onExpandedChange = { open = it },
    expandedContent = { Text("Filters go here") },
) {
    +Tabler.Outline.Filter
    +"Filters"
}
```

`NavExpandingSlot` is the pill-and-panel under `NavSearch`, and it knows nothing
about searching: a filter, an account switcher or a "where to?" prompt is the same
small thing that needs the whole screen once it is in use. Its content is a row in
the control's own style, so `+icon` and `+"Filters"` are all it needs.

### Slot content that knows how much room it has

`LocalNavExpansion` tells whatever is in a bar's slot, or a rail's `header` or
`footer`, how wide the surface around it is: `expanded` for whether there is room
for a word, `progress` for interpolating across a rail's animation, `onSurface`
for whether there is a ground behind it. It is what makes `NavSearch` a pill in a
bar and a field in a sidebar with no parameter at the call site.

---

## Accessibility

The pill is a `Role.Button` — pressing it opens something — and the field in the
panel takes focus when it opens, so the keyboard is up without a second tap.
