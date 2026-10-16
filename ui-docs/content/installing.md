# Installing

Getting Kontour UI into an app: which modules there are, what each needs, and
what to set up once they resolve.

Everything is on **Maven Central**, from
[`kontour-labs/ui`](https://github.com/kontour-labs/ui), under the
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0). `mavenCentral()`
is all a build needs: no token, no repository of its own, nothing to set up in CI.

## The modules

Three artifacts, all in the group `io.kontour`, all released together under one
version number:

| Artifact | What it is | Add it when |
|---|---|---|
| `io.kontour:ui` | The design system: the theme, the tokens and every component | Always |
| `io.kontour:ui-nav3` | [Navigation 3](navigation3.md) scene strategies over the pane scaffolds, and page transitions that follow the back gesture | The app navigates with Navigation 3 |
| `io.kontour:haptics` | [Platform haptics](haptics.md), with no Compose in it | Never beside `:ui`, which brings it. On its own, for code that wants haptics without the design system |

**Keep them on the same version.** They are built together from one commit and
call each other's internals freely; `ui-nav3:0.3.0` over `ui:0.4.0` can compile
and then fail at run time with a missing method. A version catalog entry for all
three, below, is the simplest way to make that impossible.

Each is published for **Android** (minSdk 29), **iOS** (`iosArm64` and
`iosSimulatorArm64` — no Intel simulator), the **desktop JVM**, and the web as
**JS** and **Wasm**.

## Requirements

- **Compose Multiplatform 1.12.1** or newer. 1.12 is the floor, deliberately:
  the screenshot harness the test suite rests on needs
  `ImageComposeScene.calculateContentSize()`, which lands there. An app on 1.11
  hits binary incompatibility.
- **Kotlin 2.4.10** or newer, the version the libraries are built with.
- **Android minSdk 29.**

`:ui-nav3` brings Navigation 3 1.1.1 with it
(`org.jetbrains.androidx.navigation3:navigation3-ui` and
`androidx.navigation3:navigation3-runtime`). Declaring them yourself is fine, at
that version or newer.

## The repositories

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

`google()` is for the AndroidX artifacts Compose and Navigation 3 depend on on
Android; most projects have both already.

**From 0.4.5.** Releases up to 0.4.4 were published to GitHub Packages, which
needs a token even to read, and are not on Maven Central. An app on one of those
moves to 0.4.5 and drops the `maven.pkg.github.com` repository and its
credentials: [migrating](migrating.md#044-to-045) has the diff.

## Adding the modules

One version for all three, in the version catalog:

```toml
# gradle/libs.versions.toml
[versions]
kontour = "0.4.5"

[libraries]
kontour-ui = { module = "io.kontour:ui", version.ref = "kontour" }
kontour-ui-nav3 = { module = "io.kontour:ui-nav3", version.ref = "kontour" }
kontour-haptics = { module = "io.kontour:haptics", version.ref = "kontour" }
```

In the shared module's `commonMain`, since every module is multiplatform:

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kontour.ui)
            implementation(libs.kontour.ui.nav3)   // with Navigation 3
        }
    }
}
```

Without a catalog, the same thing is
`implementation("io.kontour:ui:0.4.5")`, and so on.

---

## Setting up `:ui`

Two wrappers at the root, once:

```kotlin
KontourTheme {
    OverlayHost {
        AppRoot()
    }
}
```

- **`KontourTheme`** provides the tokens every component reads, and follows the
  system's dark mode, contrast and reduced-motion settings live. See
  [theming](theming.md).
- **`OverlayHost`** is where dialogs, sheets, menus, popovers and toasts draw,
  and what takes the back gesture while one is open. See
  [overlays](overlays.md).

Both **throw** rather than fall back when they are missing — "No KontourTheme
found" and "No OverlayHost found" — because a component in the wrong palette, or
a dialog drawing nowhere, is a bug that looks like a missing feature.

Then read [patterns](patterns.md) once, before the first screen: how a
scrolling screen, a group of rows and an overlay are meant to be put together,
and the mistakes each is easy to make.

**Icons are yours.** Components take an `ImageVector`, so the app chooses its
set. The documentation and the gallery use Tabler, as Kontour Labs builds it —
`io.kontour:tabler-icons-outline` and `-filled`, every icon of the release, with
`Tabler.Outline.*` under `io.kontour.icons.tabler`. The library draws its own
few system icons from it, so it comes with the library, from Maven Central
like everything else. Any `ImageVector` works. The site's Icons page, in its top bar, searches
every one of them — by name, by what Tabler tags it with, and by the everyday
word for it — and copies the property and its import.

**Fonts are included.** The default type is Outfit, with JetBrains Mono for code
and figures, both bundled; there is nothing to add unless you reskin, which
[theming](theming.md) covers.

### Per platform

- **Android.** For the back gesture to animate — a sheet or dialog following
  the finger, a page sliding under it — set
  `android:enableOnBackInvokedCallback="true"` on the `<application>` on Android
  13 to 15; an app targeting 16 has it already. For right-to-left languages, set
  `android:supportsRtl="true"`. `android.permission.VIBRATE` arrives from
  `:haptics`, below.
- **iOS.** Nothing is required. List the app's languages in
  `CFBundleLocalizations` so the system offers them; see
  [localisation](localisation.md).
- **The web.** Nothing is required. For text in a script the bundled fonts do
  not cover, Compose fetches Noto from Google Fonts on its own, which a strict
  content security policy or an offline app will want to know;
  [localisation](localisation.md) has the preload helper. Two things in the
  browser are Compose's rather than the library's. A headless Chromium can log
  "Failed to execute 'put' on 'Cache'" while the bundled fonts load: that is
  Compose Resources' own font cache, which the library never touches. And under
  Compose 1.12 the browser's accessibility tree names every clickable node a
  `button`, with no checked or selected state, whatever role it was given; the
  library puts its own controls' roles back, and
  [In the browser](accessibility.md#in-the-browser) has how and what it does
  not reach.

  On an iPhone or iPad, every browser is WebKit, which opens the keyboard only
  for an input focused during the tap itself; Compose focuses its own while the
  finger is still down, a frame after it lands, which WebKit does not count. So
  as the finger lifts from a text field, a combobox, a nav bar's search or an
  editable knob, `KontourTheme` focuses Compose's input again, during the tap,
  and the keyboard opens for it. A field that focuses itself later — the box
  under a select's "Other", a combobox's editor — has a hidden input focused
  in its place for the moment between. Nothing is added off iOS.

  On a phone or a tablet, in any browser, **a focused text field's selection,
  its handles and its menu are the browser's own**: Copy, Paste, Look Up and
  the rest, where the reader expects them. Compose's input is laid over the
  field's text while it has the focus, in the same face at the same size, and
  takes the touches on it; Compose still draws the text, and the browser draws
  its caret and selection over it. A field whose text is drawn as something
  else — a phone number grouped as it is typed — keeps Compose's selection and
  menu, as the browser's letters would not be under Compose's. The faces are
  the library's Outfit and JetBrains Mono; a theme in a face of its own has the
  browser's sans-serif under it, close but not exact. A swipe that starts on a
  focused field's text selects rather than scrolls the page.

  Neither reaches a text field of your own on `BasicTextField`.
- **The desktop.** Nothing is required; see `:haptics` for a Mac's trackpad.

## Setting up `:ui-nav3`

Hand the strategies to `NavDisplay`, inside the `OverlayHost` the app already
has — the supporting pane becomes a sheet on a narrow window, and a sheet draws
in the host:

```kotlin
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    sceneStrategies = listOf(rememberListDetailSceneStrategy()),
    sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
    entryProvider = entryProvider { … },
)
```

`rememberPageTransitionStrategy()` is the page transition and the back gesture's
motion on every screen; the list-detail and supporting-pane strategies are for
the screens that use those layouts. [Navigation 3](navigation3.md) has the whole
of it. The Android manifest flag above is what makes the predictive back gesture
animate here too.

## Setting up `:haptics`

Nothing, beside `:ui`: the theme plays through it, and every component that
answers with a haptic already does.

On its own, make a player and play effects:

```kotlin
val haptics = Haptics(context, view)      // Android
val haptics = Haptics()                   // iOS, the desktop, the web

haptics.play(HapticEffect.Selection(fine = true))
```

- **Android.** The module's manifest declares `android.permission.VIBRATE`,
  which merges into the app: a normal permission, granted at install and never
  prompted for. Removing it (`tools:node="remove"`) leaves the system's feedback
  constants, which need none.
- **iOS.** A static framework may need `-framework CoreHaptics` in the app's
  linker flags.
- **The desktop.** A Mac's Force Touch trackpad, on Java 22 or later. Run with
  `--enable-native-access=ALL-UNNAMED` to keep Java from warning about the
  native call; the jar ships the ProGuard rule a minified build needs.
- **The web.** The Vibration API, where a browser has one — Chrome and Firefox
  on Android.

[Haptics](haptics.md) has the effects, and what each platform can play.

---

## When it fails

| What you see | What it is |
|---|---|
| `Could not find io.kontour:ui:0.4.4` | Versions up to 0.4.4 are only on GitHub Packages. Move to 0.4.5 or newer |
| `Could not find` a version that is on the [releases](https://github.com/kontour-labs/ui/releases) page | It was tagged in the last half hour: Maven Central takes about that long to serve a new release |
| `401 Unauthorized` from `maven.pkg.github.com` | The old GitHub Packages repository is still in `settings.gradle.kts`. Remove it |
| AndroidX artifacts will not resolve | `google()` is missing from the repositories |
| Resolution succeeds but the classes are missing on one platform | A variant did not publish. Each module should have seven per version: the module itself, and one each for `-android`, `-iosarm64`, `-iossimulatorarm64`, `-js`, `-jvm`, `-wasm-js` |
| `NoSuchMethodError` or a linkage error from inside `io.kontour` | The modules are on different versions, or Compose Multiplatform is older than 1.12.1 |
| "No KontourTheme found" or "No OverlayHost found" | The root wrappers under [Setting up `:ui`](#setting-up-ui) are missing, or the screen is composed outside them — a separate window, a dialog of the platform's own |

## Working from source

An app that has this repository checked out beside it can substitute the
sources for the published artifacts, to change the library and the app
together. The pattern is a conditional `includeBuild`, which swaps all three
modules at once:

```kotlin
if (providers.gradleProperty("kontour.ui.local").orNull.toBoolean()) {
    includeBuild("../../ui")
}
```

It should stay off by default: the published artifacts have to be what CI builds
and what a fresh clone gets, or they are only ever exercised by the release job.
