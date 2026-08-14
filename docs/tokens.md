# Tokens

Everything a component is allowed to look like. Read through `Theme` inside a
composable:

```kotlin
Theme.colors.surface
Theme.typography.titleMedium
Theme.spacing.md
Theme.shapes.medium
Theme.elevation.low
Theme.motion.default
Theme.sizing.iconMedium
```

A component that hardcodes a value instead cannot be re-themed, will not respond
to the contrast setting, and will not honour reduced motion. There is no
exception to this — if a value you need is missing, add a token.

---

## Colour

Defined in [`theme/ColorScheme.kt`](../../../app/ui/src/commonMain/kotlin/io/kontour/ui/theme/ColorScheme.kt);
raw values in [`theme/Palette.kt`](../../../app/ui/src/commonMain/kotlin/io/kontour/ui/theme/Palette.kt).

Four built-in schemes: light, dark, and a high-contrast variant of each.

### Grounds

| Token | Light | Dark | For |
|---|---|---|---|
| `background` | `#FFFFFF` | `#121212` | The page |
| `surface` | `#FFFFFF` | `#221E29` | Cards, sheets, menus |
| `surfaceSunken` | `#F6F6F6` | `#1A1820` | Wells: input fills, code blocks, table stripes |
| `surfaceRaised` | `#FFFFFF` | `#2A2633` | Above `surface` — menus over cards |
| `surfaceInverse` | `#121212` | `#F4F1F8` | Toasts, tooltips |
| `onSurfaceInverse` | `#FFFFFF` | `#121212` | Content on `surfaceInverse` |

### Content

| Token | Light | Dark | For |
|---|---|---|---|
| `content` | `#121212` | `#F4F1F8` | Anything the user reads to understand the screen |
| `contentMuted` | `#545454` | `#A79FB0` | Captions, timestamps, secondary labels |
| `contentSubtle` | `#6B6B6B` | `#9A93A2` | Placeholders, tertiary hints. Still real text, still 4.5:1 |
| `contentDisabled` | `#A3A3A3` | `#5C5566` | Genuinely disabled controls only (WCAG-exempt) |

### Lines

| Token | Light | Dark | For |
|---|---|---|---|
| `outline` | `#E5E5E5` | `#3C3547` | Dividers and decorative rules |
| `outlineStrong` | `#8A8A8A` | `#7C7484` | The boundary of anything interactive |
| `outlineSubtle` | `#EFEFEF` | `#2C2735` | The faintest rule, for dense lists |

`outline` is too light to bound a control — it does not meet the 3:1 that
WCAG 1.4.11 asks of a UI component boundary. Inputs, checkboxes and switches use
`outlineStrong`. This is the single most commonly got-wrong pair.

### Actions

| Token | Light | Dark | For |
|---|---|---|---|
| `primary` / `onPrimary` | `#121212` / `#FFFFFF` | `#F4F1F8` / `#121212` | The solid call to action |
| `accent` / `onAccent` | `#6D28D9` / `#FFFFFF` | `#BB86FC` / `#1A1024` | Purple that carries text or a fill label |
| `accentContainer` / `onAccentContainer` | `#F3ECFE` / `#4C1D95` | `#2A1F3D` / `#D9BBFD` | Selected states, tinted chips |
| `brand` | `#BB86FC` | `#BB86FC` | Purple as **decoration only** |
| `focusRing` | `#6D28D9` | `#BB86FC` | The keyboard focus indicator |

**On `brand` versus `accent`.** `#BB86FC` is the Kontour purple, and on white it
reaches only 2.1:1 — it cannot carry text, a fill label, or a focus ring in light
mode. (The marketing site's `.button.primary:hover` does exactly this today and
fails.) So the palette splits the job: `brand` is the literal brand colour for
decoration, and `accent` is a purple dark enough to be read. `BrandIsDecorativeOnlyTest`
pins that contract so nobody quietly promotes `brand` to a text colour.

### Status

Each of `success`, `warning`, `danger` and `info` is a `StatusColors` with five
fields, matching how the web properties already use them:

| Field | For |
|---|---|
| `solid` / `onSolid` | Filled backgrounds — badges, solid buttons, progress fills |
| `container` / `onContainer` | Soft tints — banners, chips, callouts. `onContainer` doubles as the tone's standalone text colour |
| `border` | Hairline around `container`. Decorative — no contrast requirement |

| Tone | Light `solid` | Dark `solid` |
|---|---|---|
| `success` | `#2E7D32` | `#7BE08A` |
| `warning` | `#B45309` | `#FDBA74` |
| `danger` | `#B91C1C` | `#FCA5A5` |
| `info` | `#6D28D9` | `#C4B5FD` |

### Overlays

| Token | For |
|---|---|
| `scrim` | Dims content behind a modal |
| `overlayHover` / `overlayPressed` / `overlayDragged` | The tonal washes `KontourIndication` composites over a control |

---

## Type

Outfit, shipped as five static instances cut from the upstream variable font, so
weights render identically on every target. SIL OFL; licence at
[`app/ui/licenses/Outfit-OFL.txt`](../../../app/ui/licenses/Outfit-OFL.txt).

| Role | Large | Medium | Small | Weight | Line height |
|---|---|---|---|---|---|
| `display` | 48 | 40 | 32 | 800 | 1.10–1.20 |
| `headline` | 28 | 24 | 20 | 700 / 600 | 1.25–1.35 |
| `title` | 18 | 16 | 14 | 600 | 1.40 |
| `body` | 17 | 15 | 13 | 400 | 1.60 / 1.50 |
| `label` | 16 | 14 | 12 | 600 | 1.20 |

Plus `monoLabel` — 13sp, weight 700, `+0.14em` tracking, meant to be set in
upper case. It is the eyebrow above a section heading (`.mono-label` on the
marketing site).

Sizes are in `sp` and scale with the OS text-size setting. Every style trims
half-leading at the top and bottom of a block, so visual bounds match layout
bounds and vertical centring behaves.

Which one to reach for:

- **display** — hero moments. At most one per screen.
- **headline** — screen and section titles.
- **title** — card headers, list headlines, dialog titles.
- **body** — everything the user actually reads.
- **label** — text inside a control: buttons, chips, tabs, form labels.

---

## Spacing

A 4dp grid, named to match `--space-*` in the admin site so values port across
without arithmetic.

| Token | | Typical use |
|---|---|---|
| `xxs` | 4dp | Between an icon and its label |
| `xs` | 8dp | Inside a chip or badge |
| `sm` | 12dp | Between related rows |
| `md` | 16dp | Default padding inside a card; screen gutter |
| `lg` | 24dp | Between groups |
| `xl` | 32dp | Between sections |
| `xxl` | 40dp | Around a screen's hero |

`Theme.spacing.of(n)` gives `n × 4dp` for the rare one-off. Repeated use of the
same `of(n)` is a sign that value wants a name.

---

## Shape

| Token | Radius | Used by |
|---|---|---|
| `extraSmall` | 4dp | Badges, tags, inline code |
| `small` | 8dp | Buttons, inputs, checkboxes |
| `medium` | 12dp | Cards, list groups, menus |
| `large` | 16dp | Dialogs, large cards |
| `extraLarge` | 24dp | Bottom sheets, hero panels |
| `pill` | 50% | Nav bars, chips, avatars, FABs |

Tighter than the marketing site on purpose. Controls stay near-square so they
read as mechanical; roundness is spent on navigation and sheets, where it does
work.

---

## Elevation

Shadows are described the way CSS describes them — colour, offset, blur, spread
— not as an Android elevation in dp, because a single dp number cannot express a
two-layer shadow and Android's elevation model does not exist on the other four
targets.

| Token | For |
|---|---|
| `flat` | Flush with the page. Most content |
| `low` | Cards and list groups at rest |
| `medium` | Nav bars, raised cards |
| `high` | Menus, popovers, tooltips |
| `overlay` | Dialogs and sheets |

Each level stacks two `ShadowSpec` layers: a tight, darker *contact* shadow that
gives the shape a crisp edge against a busy background, and a wide, soft
*ambient* layer that carries height. One layer alone gives you either a hard
edge or a vague smudge, never both.

Alphas roughly double in dark mode. A soft black shadow on a near-black ground is
invisible, which is why dark themes so often look flat.

Elevation is a **rank**, not a decoration: pick the level that matches where the
element sits in the overlay stack.

---

## Motion

| Token | | For |
|---|---|---|
| `instant` | 0ms | No transition |
| `fast` | 150ms | State changes within a control |
| `default` | 220ms | Most transitions |
| `slow` | 400ms | Large surfaces entering or leaving |
| `deliberate` | 600ms | Scroll reveals; anything meant to be noticed |

Easings: `standard` is `cubic-bezier(0.16, 1, 0.30, 1)` — the marketing site's
verbatim, so a transition in the app and the same transition on the web feel like
one product. Plus `enter`, `exit` and `emphasized` (which overshoots).

Springs are `SpringToken(dampingRatio, stiffness)`: `springSnappy` for toggles
and thumbs, `springDefault` for size and position, `springGentle` for sheets.

**Reduced motion** does not mean *no* animation. A cross-fade is not what causes
vestibular discomfort — large translation, parallax and spinning are. When
`Theme.motion.reduceMotion` is set, durations collapse toward `fast`, transition
presets swap movement for opacity, springs degrade to tweens so nothing
overshoots, and continuous looping motion stops entirely.

Use the helpers rather than reading durations directly — they already account
for the preference:

```kotlin
animateFloatAsState(target, Theme.motion.tweenDefault())
animateDpAsState(target, Theme.motion.springOrTween())
```

---

## Sizing

| Token | | |
|---|---|---|
| `minTouchTarget` | 48dp Android, 44dp iOS/web, 24dp desktop | See [accessibility.md](accessibility.md) |
| `iconSmall` / `iconMedium` / `iconLarge` | 16 / 20 / 24dp | Three sizes; anything else needs justifying |
| `controlHeight*` | 28 / 36 / 44 / 52 / 60dp | Shared by buttons, inputs and selects so mixed rows align |
| `borderWidth` / `borderWidthStrong` | 1 / 2dp | |
| `focusRingWidth` / `focusRingOffset` | 2 / 2dp | 2dp is the thinnest that stays visible at 200% zoom |
