# `NavScope`

*Also on this page: `NavLabelScope`, `NavColours`, `NavDefaults`.*

The destinations of a navigation surface, written once. A [`NavBar`](nav-bar.md),
a [`NavRail`](nav-rail.md), a [`ModalNavRail`](modal-nav-rail.md) and a
[`NavigationSuiteScaffold`](navigation-suite-scaffold.md) all take the same
content, so moving between them at a breakpoint is not a second copy of it.

<!--sample:NavScopeBasics-->
```kotlin
var here by remember { mutableStateOf("home") }

// Written once, drawn by whichever surface it is in. Each destination says
// whether it is selected, has a key of its own, and a label a screen reader
// hears wherever only its icon is drawn.
NavBar {
    item(here == "home", { here = "home" }, key = "home", icon = Tabler.Outline.Home) { +"Home" }
    item(here == "saved", { here = "saved" }, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
    item(
        selected = here == "alerts",
        onClick = { here = "alerts" },
        key = "alerts",
        icon = Tabler.Outline.Bell,
        badge = { Badge(count = 3) },
    ) {
        +"Alerts"
    }
}
```

Four calls, and what each draws depends on the surface it is in:

| | a bar | a collapsed rail | an expanded or modal rail |
|---|---|---|---|
| `item` | a circle | an icon | a row |
| `section` | nothing: its items are inline | a hairline | a title over its items |
| `group` | a circle that opens a menu of its children | an icon that opens a menu beside the rail | a row that opens and closes its children below it |
| `divider` | nothing | a hairline | a hairline |

**It emits, the way a menu does.** Each call draws where it is written, so the
destinations can come from state, a loop or `stringResource` like any other
content, and a destination shown by a condition is simply not called when it is
not.

**Selection is per destination.** Each `item` says whether it is where the app is,
so a destination that comes and goes does not shift which one is selected — which
an index counted across the list did.

**Every destination has a `key`**, the identity the selection marker travels
between, and no two in one surface may share one: a duplicate throws. It is a
value of yours — a route, an enum — and never the label, which a translation
changes under the marker.

**Every destination has a label**, written `+"Stops"`, and one is required. It is
drawn where there is room and announced where there is not, which is why it is a
`String` rather than content: see [announcements](../dsls.md). `icon` is optional —
`null` draws the label's initial wherever the label is hidden, and nothing where it
shows, which is what a long text index like this site's wants.

**A group carries the marker while its selected destination is hidden** — in a
collapsed run, or in a menu that is not open — so the surface always shows where
you are. Its `expanded` is hoisted: an app opens the group holding the current page,
which is nearly always right and not something the component can know.

`badge` is a slot, `{ Badge(count = 3) }` or `{ Badge() }` for a dot, drawn on the
icon's corner and announced as part of the destination.

### How selection is shown

A single marker **travels** to the current destination — a circle in a bar, a pill
around the row in a rail. The movement carries the meaning, so the tint on the icon
and label is a *second* cue rather than the only one: selection by colour alone
fails WCAG 1.4.1, and two independent cues serve two different people. Every
surface, the tab bar and the segmented control use the one mechanism,
`SelectionIndicatorBox`, so they cannot drift apart. Under reduced motion the
marker snaps and cross-fades rather than travelling.

The selected destination's icon grows a little as the marker arrives. A badge is
placed against the glyph, not the touch target, so it sits on the icon it
annotates.

### Colours

`NavColours` is a surface's `container`, an unselected destination's `content`
(muted, as secondary text is), the `indicator` behind the selected one, that
destination's `selectedContent`, and `contentDisabled`. `NavDefaults.colours()`
works `selectedContent` out from `indicator`, so a marker of your own keeps what
is on it readable.

---

## Accessibility

Destinations are `Role.Tab` inside a `selectableGroup()`, so a screen reader
announces "2 of 4" rather than four unrelated buttons. A label hidden by the
surface is the destination's description; `contentDescription` replaces it where a
word does not say where the destination goes.

A group is a `Role.Button`: as a disclosure it says whether it is expanded, and in
a bar or a narrow rail it opens a menu. It is not a destination and does not claim
to be one. A disclosure taps the haptics as an `Accordion` does; a destination and
a menu opening are silent.

At high contrast the marker behind the selected destination gains an edge in the
accent round its tint, in a bar and in a rail, and a rail's section hairlines and
dividers are drawn thicker.

Under reduced motion the marker does not travel to a new destination: it goes
at once and fades up where it lands. The selected icon's small grow and a
group's chevron settle without overshoot, and a group's menu fades in place. A
disclosure group's children take their room at once and fade in.
