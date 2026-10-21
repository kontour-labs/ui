# Accessibility

The contract every component in `io.kontour.ui` meets, and — more importantly —
how each part of it is enforced rather than merely agreed.

An accessibility checklist that lives in a review guideline decays. Everything
below is either a test that fails, a modifier applied by default, or a token
that has no unsafe value to pick.

---

## Contrast

**Enforced**, not aspired to: every built-in scheme is checked on every
build, and one that slips fails it.

The test walks every foreground/background pairing a component can produce, in
all four built-in schemes, and asserts WCAG:

| | Standard tier | High tier |
|---|---|---|
| Body text | 4.5:1 (AA) | 7:1 (AAA) |
| Control boundaries, focus rings | 3:1 | 4.5:1 |
| Labels on solid fills | 4.5:1 | 7:1 |

Deliberately exempt: `contentDisabled`, `outline`, `outlineSubtle` and the
status `border` tones. WCAG 1.4.3 exempts disabled controls and 1.4.11 exempts
purely decorative rules; holding dividers to a ratio would force them so dark
they read as borders.

Also exempt, and specifically so: `brand`. It exists *because* it cannot pass in
light mode — see [tokens.md](tokens.md#actions). That exemption is
itself checked, so making `brand` readable without also saying it may now carry
text fails the build.

This is not theatre. Three values in the original palette looked fine and failed
by a tenth of a point against `surfaceSunken`; the test found them before a
single component existed. Contrast cannot be eyeballed.

**For colours the system does not control** — a route colour out of a GTFS feed,
a user-picked accent, an image's dominant tone — use `contentColourFor()`, which
picks whichever of two candidates reads better:

```kotlin
val labelColour = contentColourFor(routeColour)
```

### Disabled is quieter, everywhere on the control

A disabled control is exempt from contrast, and it is also never *louder* than
the same control enabled. The rule, and what every control in the library does:

- **Its handle is the page's colour.** A switch's thumb, a slider's or a range
  slider's thumbs and a knob's face are `surface`, a hole in the grey rather
  than a mark on it. A slider's thumb overhangs its track onto the page, so it is
  ringed in the grey, which is what draws it at all.
- **Its fills and tracks are `contentDisabled`**, and its quieter parts — a
  rating's empty marks, a knob's track — are fainter still.
- **Nothing on it stands out from the page more than `contentDisabled` does**,
  or, at the high tier, than `outline`, which draws the edges that tier keeps.
- **Nothing on it is coloured.** Disabled is the grey, not the control's own
  colours faded: a knob with a green-to-red scale is grey. A colour picker keeps
  its swatches, because the colours are what it shows.
- **A control in a disabled row is disabled too.** A `Checkbox`, `RadioButton`
  or `Switch` in a disabled `SelectionRow` or `ListItem` draws itself disabled,
  so `enabled = false` is said once, on the row.

Asked for of the switch, whose disabled thumb was near-black in light and white
in dark: *"the head of the switch when it's disabled is a very strong colour.
this might apply to other components too"*. It did, to a slider's thumb, a
knob, a stepper's value, a field's placeholder and a checkbox in a disabled row,
and every control in the library is checked against the rule on every build, in
all four schemes.

---

## Contrast tiers

`ContrastLevel.Standard` and `ContrastLevel.High`. `KontourTheme` defaults to
whatever the OS reports — `Settings.Secure.CONTRAST_LEVEL` on Android 34+,
`UIAccessibilityDarkerSystemColorsEnabled` on iOS, `prefers-contrast: more` on
web — and follows it live.

A `Medium` tier is deliberately absent rather than stubbed. Every tier we name
is a tier the contrast suite has to actually verify, so naming one we have not
authored would be a lie in an enum.

### What `High` actually changes

Five things, and for a long time it was only the first:

**The scheme.** `kontourColourScheme(dark, contrast)` returns a different
`ColourScheme` — AAA body contrast, a near-ink `outline`, pure white on pure
black in dark mode, and an `outlineSubtle` equal to `outline`: there is no faint
rule at this tier, because a menu's border in the fainter grey was 3.03:1.

**The pairs.** The tier used to darken every boundary toward `primary`, which is
right for a boundary and wrong for the other half of a pair: a switch's off
track was **1.85:1** from its on track, where the standard tier has 4.49 in
light and 3.58 in dark.
`ColourScheme.inactive` is the off or not-yet half — a switch off, a timeline's
stops not reached, the page dots that are not current, and at this tier every
track a fill runs along (`Slider`, the progress forms, `Gauge`, `Meter`,
`Knob`). It equals `outlineStrong` at the standard tier, so nothing there moved,
and a check holds every pair at this tier to 3:1 and every off mark to 4.5:1 on
its ground.

**The tokens.** `Sizing` is keyed on the tier, so every line in the system gets
thicker: `borderWidth`, `borderWidthStrong`, `dividerThickness` and
`focusRingWidth`. A component that reads one of those responds without being
touched — and every ring, tick and rule in the library is drawn from one: the
avatar group's ring, a gauge's and a knob's ticks, a slider's thumb ring, the
colour picker's cursor, a table's rules and an activity calendar's rings used to
be constants of their own, and stayed hairlines beside borders twice their
weight.

**The edges.** `contrastEdge()` gives a container a boundary it does not
otherwise have, and returns `null` at `Standard` so the call site reads as
"this container's border, or an edge if the user needs one". `Card` Elevated
and Filled, `Button` Tertiary and the ghosts, `ListItem`, `Tag`, `Dialog`, both
sheets, `Toolbar`, `SegmentedControl`'s track, `TextFieldVariant.Filled`,
`NavBar` in both styles, `NavRail`, `NavSearch`'s results and a
navigation slot's recess, someone else's `ChatBubble`, `TimePicker`'s field,
`WheelPicker`'s band, a `Popover` with its arrow (the arrow is outlined too),
the pull-to-refresh disc, a disabled FAB, an avatar with neither name nor
picture, and the activity calendar's legend all take one. The
segmented control's *thumb* does not — see below.

`selectionEdge()` is the same for a **selection**: an outline in the accent
round a selected tint, which at this tier is 1.25:1 from the page. The travelling
indicators of `NavBar`, `NavRail` and `TabBar`, the current `Pagination` page, a
selected `FilterChip`, a selected `ListItem` (and so a `CommandPalette`
highlight and a `TimelineList` row) and a selected `Table` row take it.

**Marks as well as colours.** Where a state was colour and nothing else, the
tier adds a mark, and only this tier:

| Component | At `High` |
|---|---|
| `Switch` | A check in the thumb when on, a dash when off |
| `Timeline`, `TimelineList`, `BranchTimeline` | Stops not yet reached are hollow; the halo round the current stop is a solid ring |
| `PageIndicator` | A `Worm` rests with the current dot widened, as `Pill` does |
| `Breadcrumbs` | Crumbs you can follow are underlined |
| `Stat` | A good or bad trend gets a tick or an alert before it |
| `Badge` | A ring of the page's colour separates it from what it sits on |
| `Slider`, `RangeSlider` | Ticks are cut into the track in the page's colour |

And nothing that is text fades: a coach mark's step count, a chat bubble's
time, a toast's stacked rims, a wheel's neighbouring rows (to no fainter than
5.7:1) and an idle scrollbar all go to full ink.

That edge part is the one a darker `outline` cannot do. An elevated `Card` is
`surfaceRaised` on the page with a shadow for an edge, and a shadow does not
change between tiers; at the high-contrast light tier those two grounds are both
pure white, so the card measures **1.00:1** against the page it sits on — against
the 3:1 WCAG 1.4.11 asks of a control's boundary.

### A selection indicator, and what identifies it

A segmented control's thumb and the track under it used to be `surface` on
`surfaceSunken`, **1.08:1 apart in every scheme this library shipped**, standard
and enhanced, light and dark. In light a shadow separated them; the dark
elevation scale draws its shadows *black*, so on a near-black track there was
nothing left to darken and the thumb disappeared.

The **thumb** is the distinct token now, not the ground. Both grounds went back
to `surfaceSunken` — the same well every code block, table and text field uses —
and `surfaceIndicator` lifts the thumb off it: **1.59:1** in dark and **1.94:1**
at the enhanced tier, both further apart than the separate-track arrangement
managed, and neither needing a border.

Light is the scheme that cannot. White is the top of the ramp and the well is
`#F6F6F6`, so the fill is 1.08:1 whatever token it reads, and a shadow is worth
0.09 of a ratio on a ground that pale — measured, a borderless light thumb comes
to 1.17:1.

**So light is carried by its shadow and its label, and measures 1.17:1.** That
does not reach the 3:1 WCAG asks of a control's boundary, and it is a choice
rather than an oversight: a 1dp `outlineStrong` hairline was built and measured
and reaches 3.60:1, and was rejected because on a near-white ground it is a hard
dark stroke around the selected segment — the apology for a fill not doing its
job that an earlier round already removed once.

So the selected state is carried more than once everywhere: the fill in dark,
the shadow in light, and in both the label moving from `contentMuted` to
`content` — the last of which survives a reader who cannot separate the greys at
all. It is the same trade iOS makes with its own segmented control, at a
measured 1.15:1, and it is a trade: someone who could find the old `#D0D0D0`
ground and cannot find this shadow has lost something real.

A selected **chip** is its tint and its label's colour at the standard tier; its
unselected border goes. At the enhanced tier the tint cannot carry it alone, so
it keeps an edge, in the accent — `selectionEdge()`. `contrastFailures` walks
`outlineStrong` against the fills as well as the grounds, which is what keeps
the unselected chip's boundary honest.

**How this is kept honest.** `LocalContrastLevel` was provided by the theme and
read by nothing in the repo for most of the project's life, and it went
unnoticed because the tier had only ever been screenshotted as a *palette* —
`theme-light-high-contrast` and its dark twin — and never as components. Ten
per-family goldens at high contrast were added, and then removed again when the
gallery's hand-written panels were replaced by its demos: those two palette
pictures are the only high-contrast goldens today, and they carry the five
components whose edge is a shadow, in a Contrast section written for the purpose.

Three checks cover what a picture cannot. One reads the tokens through
`KontourTheme`, so it covers the wiring rather than the table. One samples across
a card's edge, because the card is in the semantics tree either way. The third
compares each high-contrast scheme against the values it shipped with, colour by
colour — which matters because a golden ignores a channel that moves by eight or
less, and never draws the scrim, the overlay washes, the code colours,
`contentDisabled`, `outlineSubtle` or any status border at all.

### Colour, and a pattern beside it

Where two things of one kind are told apart by colour — two series in a chart,
a tentative meeting and a confirmed one — a `FillPattern` can say it again:
hatching, dots or a cross-hatch drawn over the colour in a wash of the
content's own ink, so it reads on any colour and in either scheme. A chart
draws one per series by itself at the high tier and wherever the reader has
asked for colour not to be relied on (`differentiateWithoutColour()`); an
`EventCalendar`'s event takes one as its `pattern`. `Modifier.fillPattern` is
the same for any surface of an app's own, after its background:

```kotlin
Box(Modifier.background(colours.container, shape).fillPattern(FillPattern.Diagonal, colours.onContainer.copy(alpha = 0.25f), shape))
```

The named patterns are what a chart's series take, in
`FillPatternDefaults.Series`' order. For others, `FillPattern.lines` runs lines
in one direction or two at a spacing and weight of your own,
`FillPattern.dots` sets the dots' spacing and size and can stagger their rows,
and `FillPattern.custom` repeats a tile you draw:

```kotlin
val open = FillPattern.lines(PatternDirection.Rising, spacing = 14.dp, weight = 2.dp)
val halftone = FillPattern.dots(spacing = 8.dp, staggered = true)
```

`PatternBrushes` is the cache of tiles behind both, for a canvas of an app's
own: one brush for each pattern, colour and size, laid in the canvas's space so
neighbouring shapes' hatching lines up. It keeps the last 32 it made, so a
colour that moves, as one does through a theme's fade, does not leave a tile
behind for every frame.

---

## Touch targets

**Enforced by** `Modifier.minimumTouchTarget()`, applied by every interactive
component in the system.

| Platform | Minimum | Source |
|---|---|---|
| Android | 48dp | Android accessibility guidance |
| iOS | 44pt | Apple HIG |
| Web | 44dp, narrowing to 24dp on mouse | browser may be on a tablet |
| Desktop | 24dp | WCAG 2.2 SC 2.5.8 |

The modifier reserves layout space and centres the visual content inside it. A
20dp checkbox stays 20dp on screen but occupies 48dp of layout. Reserving the
space — rather than only widening the hit rectangle — is what stops two adjacent
small controls from having overlapping, ambiguous touch areas.

It shrinks to the pointer minimum when the active input is a mouse, and grows
back the instant a finger touches the screen. See *Input modality* below.

---

## Focus

**Enforced by** `Modifier.focusRing()`, and by the fact that `focusRing` is a
palette token guaranteed to clear 3:1 against every ground.

The ring is drawn outside the component's bounds with a 2dp gap, so it never
eats into content or shifts layout when it appears.

It is shown **only when focus arrived from the keyboard**. A ring on every tap
makes a touch interface look broken; no ring at all makes the app unusable
without a mouse. Tracking input modality is what lets both be true.

Apply `focusRing` *before* any `clip` in a modifier chain, or the clip cuts it
off.

---

## Input modality

`LocalInputModality` tracks how the user is currently driving the interface —
`Touch`, `Mouse`, `Keyboard` or `Stylus` — updated at the theme root from
pointer and key events.

Platform is a poor proxy for this. A Chromebook is Android with a trackpad, an
iPad has a pointer, a phone browser is "web" but touch-first. So the system
tracks the last used input instead:

| | Touch | Mouse | Keyboard | Stylus |
|---|---|---|---|---|
| Minimum target | 44–48dp | 24dp | n/a | 44–48dp |
| Hover states | off | on | off | off |
| Mouse cursor | n/a | shown | n/a | n/a |
| Focus ring | hidden | hidden | **shown** | hidden |
| Tooltips | long-press | hover | on focus | long-press |
| Scrollbars | while scrolling | persistent | persistent | while scrolling |

Only *traversal* keys (Tab, arrows, Page Up/Down, Home/End) switch the modality
to `Keyboard`. Typing into a text field does not — the user is already looking
at the caret, and painting focus rings across the screen because someone typed a
letter is noise.

The default is `Touch`: assuming touch only costs a mouse user some padding,
whereas assuming mouse gives a touch user targets too small to hit.

### The mouse cursor

`Modifier.pointerCursor` puts a hand over anything that answers a click, a text
beam over an editable field, and it takes the component's own `enabled` so a
disabled control does not promise a click it will not answer. Every clickable
component in the library sets one; you only need it for a target of your own.

It is a separate modifier rather than part of `focusRing`, which would have been
tidier to write and wrong: a focus ring is drawn when the keyboard reaches an
element and a cursor is about what the mouse is over, so folding them together
would show a cursor only on things you had already tabbed to.

**There are four cursors and no more.** Compose Multiplatform's `PointerIcon`
offers `Default`, `Crosshair`, `Text` and `Hand` across the platforms this
library builds for. There is no resize cursor for a pane splitter and no grab
cursor for a scrollbar thumb, so those get a hand — the closest of the four to
"you can take hold of this".

---

## Reduced motion

`Theme.motion.reduceMotion` follows the OS — Android's transition animation
scale, iOS's `UIAccessibilityIsReduceMotionEnabled`, the web's
`prefers-reduced-motion` — live, because users turn it on precisely *because*
something on screen is making them uncomfortable and waiting for a relaunch is
no help.

It does not mean "no animation", and what it actually changes is listed with the
motion tokens in [`tokens.md`](tokens.md#motion). In short: things fade rather
than fly, programmatic scrolls and size changes arrive at once, and whatever
follows the reader's own finger still follows it.

**The spinner keeps turning.** Loading is information that something is still
happening (WCAG 2.2.2 exempts it for that reason), and a spinner that stopped
would say the opposite. What stops is its breathing: the arc holds one length.

A theme nested inside another — a screen forcing dark mode — keeps the reader's
setting rather than reading the platform's again, and a custom `Motion` passed
to `KontourTheme` follows it too.

---

## Dynamic type

All type sizes are in `sp` and scale with the OS text-size setting. Every style
trims half-leading so blocks stay vertically centred as they grow.

Components must lay out correctly at **200%**. In practice that means: no fixed
heights on anything containing text, no single-line assumptions on labels that
can wrap, and icons sized from `Theme.sizing` rather than tied to font size.

The gallery and this site both have a **Text size** control for exactly this
check, and it starts at **Auto** — the device's own setting, which is what an
accessibility slider on a phone moves.

> **If you provide your own `LocalDensity`, carry `fontScale` through.**
> `KontourTheme` never touches it, so an app that leaves the density alone
> inherits whatever the platform hands it and scales correctly. But
> `Density(density, fontScale)` *replaces* the font scale rather than adjusting
> it — so a host that builds one out of `LocalDensity.current.density` alone
> pins every device at 100%, silently, no matter what its accessibility settings
> say. Nothing on screen looks wrong; the setting simply stops arriving.

---

## Semantics

Every interactive component declares:

- a correct `Role` — `Button`, `Checkbox`, `Switch`, `RadioButton`, `Tab`, `DropdownList`
- a `stateDescription` where state is not implied by the role
- `contentDescription` on anything conveying meaning without text (mandatory
  parameter on `IconButton`, not an optional one)
- `liveRegion` on anything that announces itself — toasts, validation errors,
  relative times that tick

Decorative elements are marked so, rather than left for a screen reader to
describe. Reading order is set with `isTraversalGroup` and `traversalIndex`
where visual order and composition order disagree.

### In the browser

The roles above reach Android's and iOS's accessibility trees as written. **In a
browser, Compose 1.12 does not pass them on by itself**: its web layer maps a
`Role` to its ARIA role and then overrides it with `button` on any node that can
be clicked, and writes no `aria-selected` or `aria-checked`. Left to that, a
[`TabBar`](components/tab-bar.md)'s tabs, a
[`SegmentedControl`](components/segmented-control.md)'s segments, a
[`RadioGroup`](components/radio-group.md)'s options, a
[`Select`](components/select.md)'s menu, a [`Checkbox`](components/checkbox.md),
a [`Switch`](components/switch.md) and a
[`SelectionRow`](components/selection-row.md) are all announced as buttons, with
nothing to say which is chosen or on — *"toggleable rows show up as buttons in
the browser's accessibility tree, and so do tabs"*.

**So in a browser the library puts them back.** Each of those controls says
where it is and what it is, and whenever Compose writes an element of the tree,
the role it should have — `tab`, `checkbox`, `switch`, `radio` — and its
`aria-selected` or `aria-checked` are written over Compose's, mixed for a
checkbox that is neither. It finds the element by its box, which Compose writes
from the same bounds, so a control laid out exactly over another of the same
size can be given the other's name; none of the library's are. A control of an
app's own, on `toggleable` or `selectable`, does not get this. Compose 1.13 maps
roles and states itself, and this goes once the library is on it; 1.12.1, the
latest stable release, does not.

**Names reach the browser too**: a label, a `contentDescription`, a field's name.

---

## The per-component contract

Everything on this page is asserted, not aspired to. Seven rules run over
every component in the system on every build — a role, an accessible
name, a disabled state that is announced as well as enforced, a touch target,
and survival at 200% type in RTL.

It is written up, with the four real bugs it found on its first run, in
[`building/testing.md`](../../docs/building/testing.md#the-contract-suite).
