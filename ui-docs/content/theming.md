# Theming

*Also on this page: `KontourTheme`.*

How to change what the system looks like without touching a component.

## The default has no product in it

Monochrome — ink, white and a grey ramp — plus **one blue** for the accent, and
the four conventional status hues. That is the whole palette, and it is
deliberate: a library that shipped somebody's brand would make every app using
it look like that somebody, and the app that owned the brand would be the only
one not fighting the defaults.

So the default scheme is not a design; it is a *starting point that offends
nobody*. `brand` resolves to the accent until you set one, which is the library
saying it has no opinion rather than pretending to have none.

**The worked example is Kontour's own.** `KontourBrandTheme` in the `anyways`
app overrides four tokens per tier — accent, brand, focus ring — and inherits
everything else. It is about a hundred lines, most of them the purple values,
and it is the shape to copy.

---

## Installing the theme

```kotlin
KontourTheme {
    AppRoot()
}
```

Once, at the root. Everything in `io.kontour.ui` reads its tokens from here and
throws outside it — a component silently rendering in the wrong palette is a
worse bug than one that refuses to render.

By default it resolves dark mode, contrast tier and reduced motion from the
operating system, and follows all three live. Which platform setting drives
which is in [`accessibility.md`](accessibility.md#contrast-tiers).

---

## Overriding

Every token group is a parameter, so you override one and inherit the rest.

**Force a mode for one screen:**

```kotlin
KontourTheme(darkTheme = true) { MapScreen() }
```

**Change the words the library puts on screen:**

```kotlin
KontourTheme(
    strings = Strings(
        dismiss = "Schließen",
        back = "Zurück",
        pullToRefresh = "Zum Aktualisieren ziehen",
    ),
) { AppRoot() }
```

`Strings` is a token group like the rest, and it holds every word the library
draws that you did not supply — 51 of them. Each component still takes its own
parameter, defaulted from here, so a one-off at a call site keeps working and an
app-wide change is one argument rather than a sweep through every call site.

One field per idea, not per parameter: `SheetHeader`, `SideSheet` and
`ModalBottomSheet` all say "Close" and all three read `strings.close`. It is not
a localisation system — no plurals, no locale lookup, no resource bundle — and
it is not trying to be. What it guarantees is that no English is welded into a
component, so an app can feed it from whatever it already uses.

**Let an in-app setting win over the OS:**

```kotlin
KontourTheme(
    darkTheme = when (prefs.theme) {
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
        ThemePreference.System -> isSystemInDarkTheme()
    },
    reduceMotion = prefs.reduceMotion ?: platformPrefersReducedMotion(),
) { AppRoot() }
```

**Change the accent:**

```kotlin
KontourTheme(
    colours = lightColourScheme(
        // `accent` is a `StatusColours`, not a `Color` — it is a whole tone, the
        // same shape as `success` and `danger`, because a component that takes a
        // tone has to be able to take this one.
        accent = StatusColours(
            solid = Color(0xFF0B6E99),
            onSolid = Color.White,
            container = Color(0xFFE3F2FA),
            onContainer = Color(0xFF083D55),
            border = Color(0xFFBEE0EF),
        ),
        focusRing = Color(0xFF0B6E99),
    ),
) { AppRoot() }
```

Keeping the whole tone together is what makes the five values consistent: pick
`solid` on its own and the first `ButtonVariant.Accent` you draw has a label
nobody can read on it.

`lightColourScheme()` and `darkColourScheme()` default every parameter, so this
keeps the rest of the palette intact.

**Change the typeface:**

```kotlin
KontourTheme(typography = kontourTypography(family = myBrandFamily)) { … }
```

The scale — sizes, weights, line heights, tracking — is preserved; only the
family changes.

Nested `KontourTheme` calls re-provide tokens but do not install a second
input-modality tracker, so overriding a theme mid-tree is cheap.

---

## Writing a whole theme

A theme is four values. Build them with the factory functions rather than the
constructors, so you inherit defaults for anything you do not care about:

```kotlin
object OceanTheme {
    fun colours(dark: Boolean) = if (dark) {
        darkColourScheme(
            accent = StatusColours(
                solid = Color(0xFF4FC3F7),
                onSolid = Color(0xFF002E3F),
                container = Color(0xFF0A2A38),
                onContainer = Color(0xFFB3E5FC),
                border = Color(0xFF17495E),
            ),
            brand = Color(0xFF4FC3F7),
            focusRing = Color(0xFF4FC3F7),
        )
    } else {
        lightColourScheme(
            accent = StatusColours(
                solid = Color(0xFF01579B),
                onSolid = Color(0xFFFFFFFF),
                container = Color(0xFFE1F5FE),
                onContainer = Color(0xFF01426A),
                border = Color(0xFFB6E2F7),
            ),
            brand = Color(0xFF4FC3F7),
            focusRing = Color(0xFF01579B),
        )
    }

    val shapes = Shapes(
        small = RoundedCornerShape(2.dp),
        medium = RoundedCornerShape(4.dp),
    )
}

@Composable
fun OceanApp(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    KontourTheme(
        darkTheme = dark,
        colours = OceanTheme.colours(dark),
        shapes = OceanTheme.shapes,
        content = content,
    )
}
```

**Verify its contrast.** The built-in schemes are covered by
`ColourSchemeContrastTest`; a new scheme is not, until you add it. Copy the test
and point it at your scheme — the whole value of that suite is that it runs on
palettes nobody has eyeballed yet.

Remember the `brand` / `accent` split when authoring: `brand` may be any brand
colour at all, including one that fails contrast, because it is only ever
decoration. `accent` must be readable. If your brand colour happens to be dark
enough, set both to it.

---

## Contrast tiers

`kontourColourScheme(dark, contrast)` picks between the four built-in schemes.
A custom theme that wants a high-contrast tier authors a second scheme and
selects on `ContrastLevel` the same way — and can read
`platformPrefersHighContrast()` to know which tier to build, the same function
`KontourTheme` uses for its own default.

If you do not author one, pass your single scheme regardless of tier — users who
asked for high contrast will get standard contrast, which is a downgrade you
should make knowingly rather than by omission.

---

## The generator, later

`ColourScheme` is a plain `@Immutable data class` built by *factory functions*
rather than only by its constructor. That shape is deliberate.

Today the factories are hand-authored:

```kotlin
fun lightColourScheme(…): ColourScheme                 // every token
fun darkColourScheme(…): ColourScheme                  // every token
fun highContrastLightColourScheme(accent, brand, focusRing): ColourScheme
fun highContrastDarkColourScheme(accent, brand, focusRing): ColourScheme
```

The high-contrast pair takes three parameters rather than every token, and that
is not an oversight. At AAA the grounds are pure white or pure black, the content
is its opposite, and the greys are the lightest values that still clear 7:1 —
none of that is a design choice, it is what the tier is *for*. The accent is the
only part a product owns.

Deriving a full palette from a single seed colour — for user-selectable accents,
or Android's wallpaper-derived colours — means adding one more factory:

```kotlin
fun generatedColorScheme(seed: Color, dark: Boolean, contrast: ContrastLevel): ColourScheme
```

and nothing else. No component changes, because no component knows where its
`ColourScheme` came from. That is the entire reason components are forbidden from
touching `Palette` directly — the ban is on *components* reading it, not on you:
`Palette` is public, so an app that wants to change one colour can keep the
other twenty-seven instead of starting from raw hex.

The generator is not built yet. When it is, it will need to satisfy the same
contrast suite as the static schemes — which is the hard part of that work, and
the reason it was deferred rather than rushed.
