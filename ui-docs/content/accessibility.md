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

Three things, and for a long time it was only the first:

**The scheme.** `kontourColourScheme(dark, contrast)` returns a different
`ColourScheme` — AAA body contrast, a near-ink `outline`, pure white on pure
black in dark mode.

**The tokens.** `Sizing` is keyed on the tier, so every line in the system gets
thicker: `borderWidth`, `borderWidthStrong`, `dividerThickness`,
`focusRingWidth` and `selectionIndicator`. A component that reads one of those
responds without being touched, which is most of them.

**The edges.** `contrastEdge()` gives a container a boundary it does not
otherwise have, and returns `null` at `Standard` so the call site reads as
"this container's border, or an edge if the user needs one". `Card` Elevated
and Filled, `Button` Tertiary and the ghosts, `ListItem`, `Tag`, `Dialog`, both
sheets, `Toolbar`, `SegmentedControl`'s track and `TextFieldVariant.Filled` all
take one. Its *thumb* does not — see below.
`NavBar` was listed here and does not call it — it sits on its own surface with
its own boundary, and the entry was wrong rather than the component.

That third part is the one a darker `outline` cannot do. An elevated `Card` is
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

It is a distinct ground now. `surfaceTrack` is tuned for exactly this and
nothing else, so the thumb reads **1.54:1** in light and 1.53 in dark without
the well — every code block, table and text field on this site — having to
darken with it. In dark the track goes to black and the thumb rises to
`surfaceRaised`, since a track cannot go below black but a thumb can come up.

**This does not reach the 3:1 WCAG asks of a boundary, and that is a choice
rather than an oversight.** No two greys in one ramp reach it; a white thumb
needs a mid-grey track, which stops being a ground and becomes a dark bar. An
earlier version drew a hard grey line around the selected segment to get there,
and it read as an apology for a fill that was not doing its job.

So the selected state is carried three ways instead of one: the fill, the shadow
under the thumb in light, and the label moving from `contentMuted` to `content`
— the last of which survives a reader who cannot separate the greys at all. It
is the same trade iOS makes, and it is a trade: someone who could find the old
border and cannot find this fill has lost something real.

A selected **chip** still takes `outlineStrong`, because a tint 1.29:1 from the
page genuinely cannot carry it alone. `contrastFailures` walks `outlineStrong`
against the fills as well as the grounds, which is what keeps that promise
honest.

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
| Scrollbars | not drawn | persistent | persistent | not drawn |

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
motion tokens in [`tokens.md`](tokens.md#motion).

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

---

## The per-component contract

Everything on this page is asserted, not aspired to. Seven rules run over
every component in the system on every build — a role, an accessible
name, a disabled state that is announced as well as enforced, a touch target,
and survival at 200% type in RTL.

It is written up, with the four real bugs it found on its first run, in
[`building/testing.md`](../../docs/building/testing.md#the-contract-suite).
