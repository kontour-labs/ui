# Theming

How to change what the system looks like without touching a component.

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

**Change one colour:**

```kotlin
val ocean = Color(0xFF0B6E99)
KontourTheme(
    colors = lightColorScheme(accent = ocean, focusRing = ocean),
) { AppRoot() }
```

`lightColorScheme()` and `darkColorScheme()` default every parameter, so this
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
    fun colors(dark: Boolean) = if (dark) {
        darkColorScheme(
            accent = StatusColors(
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
        lightColorScheme(
            accent = StatusColors(
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
        colors = OceanTheme.colors(dark),
        shapes = OceanTheme.shapes,
        content = content,
    )
}
```

**Verify its contrast.** The built-in schemes are covered by
`ColorSchemeContrastTest`; a new scheme is not, until you add it. Copy the test
and point it at your scheme — the whole value of that suite is that it runs on
palettes nobody has eyeballed yet.

Remember the `brand` / `accent` split when authoring: `brand` may be any brand
colour at all, including one that fails contrast, because it is only ever
decoration. `accent` must be readable. If your brand colour happens to be dark
enough, set both to it.

---

## Contrast tiers

`kontourColorScheme(dark, contrast)` picks between the four built-in schemes.
A custom theme that wants a high-contrast tier authors a second scheme and
selects on `ContrastLevel` the same way.

If you do not author one, pass your single scheme regardless of tier — users who
asked for high contrast will get standard contrast, which is a downgrade you
should make knowingly rather than by omission.

---

## The generator, later

`ColorScheme` is a plain `@Immutable data class` built by *factory functions*
rather than only by its constructor. That shape is deliberate.

Today the factories are hand-authored:

```kotlin
fun lightColorScheme(…): ColorScheme
fun darkColorScheme(…): ColorScheme
fun highContrastLightColorScheme(): ColorScheme
fun highContrastDarkColorScheme(): ColorScheme
```

Deriving a full palette from a single seed colour — for user-selectable accents,
or Android's wallpaper-derived colours — means adding one more factory:

```kotlin
fun generatedColorScheme(seed: Color, dark: Boolean, contrast: ContrastLevel): ColorScheme
```

and nothing else. No component changes, because no component knows where its
`ColorScheme` came from. That is the entire reason components are forbidden from
touching `Palette` directly.

The generator is not built yet. When it is, it will need to satisfy the same
contrast suite as the static schemes — which is the hard part of that work, and
the reason it was deferred rather than rushed.
