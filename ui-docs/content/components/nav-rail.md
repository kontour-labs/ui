# `NavRail`

*Also on this page: `NavRailDefaults`.*

The primary navigation on a window with room beside the content, **down the
leading edge**. Collapsed it is a column of icons; expanded it is a sidebar, with
labels, section titles and groups that open in place. They are one component at
two widths.

<!--sample:NavRailExpanding-->
```kotlin
var here by remember { mutableStateOf("plan") }
var railOpen by remember { mutableStateOf(false) }

// A column of icons that grows into a sidebar. The icons stay where they
// are; the labels are revealed beside them.
NavRail(expanded = railOpen, onExpandedChange = { railOpen = it }) {
    item(here == "plan", { here = "plan" }, key = "plan", icon = Tabler.Outline.Map) { +"Plan" }
    item(here == "saved", { here = "saved" }, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
}
```

Pass `onExpandedChange` for the toggle; leave it null for a rail fixed at whatever
`expanded` says — `NavRail(expanded = true)` is a permanent sidebar.

**The icons do not move as the rail grows.** A destination is an icon beside its
label at every width, in a glyph box that is always the same size, so the growing
rail reveals the labels rather than rearranging around them. Everything that
changes — labels fading in, section titles taking the place of the hairlines that
stood for them, a group becoming a disclosure — changes when the *animated* width
passes halfway, not when the flag flips.

**Expanded, it reads at a drawer's density.** A row is its icon and `sm` above
and below — 48dp, the touch target — rows are `xxs` apart, and a label sits `md`
from its icon. The glyph box is as wide as a collapsed rail needs, so the icons
stay put, but only an icon tall, so a sidebar is not a column of widely spaced
buttons.

### Sections and groups

<!--sample:NavRailTree-->
```kotlin
var here by remember { mutableStateOf("home") }
var routesOpen by remember { mutableStateOf(true) }

// A sidebar — a rail fixed at its widest — with a section and a group. The
// group opens in place here; in a collapsed rail it opens a menu instead.
NavRail(expanded = true) {
    item(here == "home", { here = "home" }, key = "home", icon = Tabler.Outline.Home) { +"Home" }
    section("Saved") {
        item(here == "stops", { here = "stops" }, key = "stops", icon = Tabler.Outline.Star) { +"Stops" }
        group(
            expanded = routesOpen,
            onExpandedChange = { routesOpen = it },
            key = "routes",
            icon = Tabler.Outline.Route,
            header = { +"Routes" },
        ) {
            item(here == "950", { here = "950" }, key = "950", icon = null) { +"950" }
            item(here == "998", { here = "998" }, key = "998", icon = null) { +"998" }
        }
    }
    divider()
    item(here == "alerts", { here = "alerts" }, key = "alerts", icon = Tabler.Outline.Bell) { +"Alerts" }
}
```

Wide, a `section` is a title over its destinations and a `group` is a row that
opens and closes its destinations below it, indented by
`NavRailDefaults.NestIndent`. Narrow, a section is a hairline of the same height,
so nothing below it moves, and a group is an icon that opens a menu of its
destinations beside the rail.

**The rail opens showing the page you are on.** `revealSelected` scrolls the
selected destination into view as the rail first appears, snapped rather than
animated, for a list longer than the rail is tall — which then scrolls rather than
squashing its rows.

`header` and `footer` are columns at the rows' leading edge, and
[`LocalNavExpansion`](nav-search.md) tells what is in them how wide the rail
currently is.

---

## Accessibility

Destinations are `Role.Tab` inside a `selectableGroup()`. The toggle is a
`Role.Button` with its own label and a `stateDescription` of expanded or
collapsed. A group is a disclosure — a `Role.Button` that says whether it is open —
or, narrow, a button that opens a menu. A section title is a heading.

At [high contrast](../accessibility.md#what-high-actually-changes) the rail takes
an edge against the page beside it, and the marker on the current destination is
outlined in the accent as well as tinted. The hairlines that stand in for section
titles thicken with the tier.

Under reduced motion the rail takes its new width at once and its labels fade
in, and the marker fades up on the new destination rather than travelling to it.
A group's children take their room at once and fade in, and its chevron turns
without overshooting.
