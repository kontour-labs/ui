# Accessibility

The contract every component in `io.kontour.ui` meets, and — more importantly —
how each part of it is enforced rather than merely agreed.

An accessibility checklist that lives in a review guideline decays. Everything
below is either a test that fails, a modifier applied by default, or a token
that has no unsafe value to pick.

---

## Contrast

**Enforced by** `ColorSchemeContrastTest`, which runs in `:ui:jvmTest`.

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
light mode — see [tokens.md](tokens.md#actions). `BrandIsDecorativeOnlyTest`
pins that, and will fail if someone changes `brand` to something readable
without also updating the contract.

This is not theatre. Three values in the original palette looked fine and failed
by a tenth of a point against `surfaceSunken`; the test found them before a
single component existed. Contrast cannot be eyeballed.

**For colours the system does not control** — a route colour out of a GTFS feed,
a user-picked accent, an image's dominant tone — use `contentColorFor()`, which
picks whichever of two candidates reads better:

```kotlin
val labelColor = contentColorFor(routeColor)
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
| Focus ring | hidden | hidden | **shown** | hidden |
| Tooltips | long-press | hover | on focus | long-press |
| Scrollbars | overlay, fading | persistent | persistent | overlay |

Only *traversal* keys (Tab, arrows, Page Up/Down, Home/End) switch the modality
to `Keyboard`. Typing into a text field does not — the user is already looking
at the caret, and painting focus rings across the screen because someone typed a
letter is noise.

The default is `Touch`: assuming touch only costs a mouse user some padding,
whereas assuming mouse gives a touch user targets too small to hit.

---

## Reduced motion

`Theme.motion.reduceMotion` follows the OS — Android's transition animation
scale, iOS's `UIAccessibilityIsReduceMotionEnabled`, the web's
`prefers-reduced-motion` — live, because users turn it on precisely *because*
something on screen is making them uncomfortable and waiting for a relaunch is
no help.

It does not mean "no animation". A cross-fade is not what triggers vestibular
discomfort; large translation, parallax and spinning are. When it is set:

- durations collapse toward `Theme.motion.fast`
- transition presets swap movement for opacity
- springs degrade to tweens, so nothing overshoots or bounces
- `KontourIndication` drops the press-shrink and keeps the tonal wash
- continuous looping motion — marquee, indeterminate spinners — stops

Use `Theme.motion.tweenDefault()`, `tweenSlow()` and `springOrTween()` rather
than reading durations directly; they already account for the preference.

---

## Dynamic type

All type sizes are in `sp` and scale with the OS text-size setting. Every style
trims half-leading so blocks stay vertically centred as they grow.

Components must lay out correctly at **200%**. In practice that means: no fixed
heights on anything containing text, no single-line assumptions on labels that
can wrap, and icons sized from `Theme.sizing` rather than tied to font size.

The catalog has a font-scale slider for exactly this check.

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

Every component in the system must:

1. take `modifier: Modifier = Modifier` as its first optional parameter;
2. accept an optional `interactionSource` and honour `enabled`;
3. declare a correct semantics `Role` and `stateDescription`;
4. meet the platform minimum touch target;
5. behave under RTL, 200% font scale, and reduced motion.

A shared test suite asserts 1–5 against a registry of every component, so a new
component that skips one fails CI rather than merging. See
[contributing.md](contributing.md).
