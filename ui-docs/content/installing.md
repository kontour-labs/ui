# Installing

Getting Kontour UI into an app: which modules there are, what each needs, and
what to set up once they resolve.

Everything is published to Kontour's Maven repository, **`https://maven.kontour.io`**,
from [`kontour-labs/ui`](https://github.com/kontour-labs/ui), under the
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0). It is public:
no token, no credentials, nothing to set up in CI.

## The modules

Four artifacts, all in the group `io.kontour`, all released together under one
version number:

| Artifact | What it is | Add it when |
|---|---|---|
| `io.kontour:ui` | The design system: the theme, the tokens and every component | Always |
| `io.kontour:ui-nav3` | [Navigation 3](navigation3.md) scene strategies over the pane scaffolds, and page transitions that follow the back gesture | The app navigates with Navigation 3 |
| `io.kontour:ui-charts` | [Charts](components/charts.md): `Chart`, `SectorChart`, `Sparkline` and `ChartDataTable` | The app draws a chart. From 0.5.0 |
| `io.kontour:haptics` | [Platform haptics](haptics.md), with no Compose in it | Never beside `:ui`, which brings it. On its own, for code that wants haptics without the design system |

**Keep them on the same version.** They are built together from one commit and
call each other's internals freely; `ui-nav3:0.3.0` over `ui:0.4.0` can compile
and then fail at run time with a missing method. A version catalog entry for all
four, below, is the simplest way to make that impossible.

Each is published for **Android** (minSdk 29), **iOS** (`iosArm64` and
`iosSimulatorArm64` — no Intel simulator), the **desktop JVM**, and the web as
**JS** and **Wasm**.

## Requirements

- **Compose Multiplatform 1.12.1** or newer, or on Android alone, **Jetpack
  Compose 1.12.1**: see [In a Jetpack Compose app](#in-a-jetpack-compose-app).
  1.12 is the floor, deliberately:
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

`google()` and `mavenCentral()`, which nearly every project has already, and
Kontour's repository for `io.kontour`:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository { maven("https://maven.kontour.io") }
            filter { includeGroup("io.kontour") }
        }
    }
}
```

`google()` is for the AndroidX artifacts Compose and Navigation 3 depend on on
Android; most projects have both already.

**`exclusiveContent` is not tidiness.** It makes `maven.kontour.io` the only
place Gradle looks for `io.kontour` — the library and the icons it is drawn
with — and the only thing it asks that repository for. Listed plainly instead,
Gradle would try it for every dependency in the build first.

**From 0.4.5.** Releases up to 0.4.4 were published to GitHub Packages, which
needs a token even to read, and are not on `maven.kontour.io`. An app on one of those
moves to 0.4.5 and drops the `maven.pkg.github.com` repository and its
credentials: [migrating](migrating.md#044-to-045) has the diff.

## Adding the modules

One version for all four, in the version catalog:

```toml
# gradle/libs.versions.toml
[versions]
kontour = "0.5.4"

[libraries]
kontour-ui = { module = "io.kontour:ui", version.ref = "kontour" }
kontour-ui-nav3 = { module = "io.kontour:ui-nav3", version.ref = "kontour" }
kontour-ui-charts = { module = "io.kontour:ui-charts", version.ref = "kontour" }
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
            implementation(libs.kontour.ui.charts) // with charts
        }
    }
}
```

Without a catalog, the same thing is
`implementation("io.kontour:ui:0.5.4")`, and so on.

### In a Jetpack Compose app

An Android app that is not multiplatform uses the same artifacts, with no
Kotlin Multiplatform or Compose Multiplatform plugin: the Android Gradle plugin
and Kotlin's Compose compiler, as any Jetpack Compose app has.

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation("io.kontour:ui:0.5.4")
    implementation("io.kontour:tabler-icons-outline:3.51.0") // to draw Tabler's icons yourself
}
```

**It is the same library, not a port of it.** On Android, every
`org.jetbrains.compose` artifact the library is built against is an empty one
that depends on Jetpack Compose's own: `foundation-android` is a few kilobytes
with no classes, and pulls in `androidx.compose.foundation:foundation`. So on
Android the components run on Jetpack Compose itself, from the one set of
sources every platform is built from. The one exception is Compose resources,
a small library of JetBrains' that loads the bundled fonts.

Gradle takes the newer of two versions, so an app on an older Compose BOM has
Compose raised to 1.12.1 with the library, as it would for any library built on
it. Kotlin, `minSdk` and the rest are as under [Requirements](#requirements).
Checked in November 2026 with an app on the Android Gradle plugin 9.3 and
Kotlin 2.4.10, on a Pixel: the theme, the fonts, a sheet and the icons.

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
few system icons from it, so it comes with the library, from the same
repository. Any `ImageVector` works. The site's Icons page, in its top bar, searches
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
  for a tap that lands on an input, as that tap's own default action. Compose
  draws its fields on the canvas, and its own input is there only once a field
  has the focus, so a first tap on a field lands on the canvas and gets no
  keyboard. So `KontourTheme` keeps a transparent input over each text field
  while nothing is being typed in it: the tap lands on that, the keyboard
  opens, and the focus goes straight on to Compose's input with the keyboard
  still up. The touches go on to Compose too, so the caret goes where the
  finger was and a drag that starts on a field still scrolls; the field's own
  buttons are left out of it. A field that focuses itself a moment later — a
  combobox's editor, a nav bar's search, the box under a select's "Other" —
  or that a finger drags, as an editable knob, has a hidden input focused for
  it as the finger lifts instead, which holds the keyboard until Compose's own
  input takes it. Nothing is added off iOS.

  On a phone or a tablet, in any browser, **a focused text field's selection,
  its handles and its menu are the browser's own**: Copy, Paste, Look Up and
  the rest, where the reader expects them. Compose's input is laid over the
  field's text while it has the focus, in the same face at the same size, and
  takes the touches on it; Compose still draws the text and the caret, and the
  browser draws its selection over it. A field whose text is drawn as something
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

## Older browsers

Kotlin/Wasm needs a browser with WebAssembly garbage collection: Safari 18.2,
Chrome and Edge 119, Firefox 120, or newer. Anything older shows the loading
page and goes no further. That includes every iPhone and iPad below iOS 18.2,
which is every iPhone 8 and X, and a Mac that cannot update past Monterey.

**An app that has to reach them builds in compatibility mode**, which
Compose Multiplatform has had since 1.9: the app is compiled for both `js` and
`wasmJs`, and the page loads the Wasm build where the browser can run it and
the JavaScript build where it cannot.

```kotlin
// The web app's build.gradle.kts
kotlin {
    js {
        browser()
        binaries.executable()
    }
    wasmJs {
        browser()
        binaries.executable()
    }
}
```

```sh
./gradlew :webApp:composeCompatibilityBrowserDistribution
```

The page to deploy is in `webApp/build/dist/composeWebCompatibility/productionExecutable`.
Each browser downloads only the build it runs.

Every library the app uses has to be published for `js` as well as `wasmJs`.
Kontour's modules are, and will stay so for exactly this reason: an app on a
library without them cannot use compatibility mode at all. Kept, too, for 0.5.4,
after dropping it was weighed in November 2026: it costs the library a second
compile, and without it a Kontour app had no way to open on an older iPhone.

**The JavaScript build is the slower of the two**, noticeably so for an
interface drawn on a canvas, and it runs on the devices that are already the
slowest. It is there so those readers get the app rather than a blank page,
not so they get it at the same speed.

---

## The page's script

**An app's page needs one script**, in its `<head>` before the app's own script,
for two things Compose's web build does not do for itself:

```html
<script>
    (function () {
        // Skia's drawing context, one for each canvas, for the life of the page.
        var contexts = new Map(), kept = new Set(), lastCanvas = null;
        var getContext = HTMLCanvasElement.prototype.getContext;
        HTMLCanvasElement.prototype.getContext = function (type) {
            if (type === "webgl2" || type === "webgl") lastCanvas = this;
            return getContext.apply(this, arguments);
        };
        function viaJs(imports) {
            if (!imports || typeof imports !== "object") return imports;
            var out = {}, changed = false;
            Object.keys(imports).forEach(function (name) {
                var module = imports[name];
                if (name.indexOf("skiko") < 0 || !module || typeof module !== "object") { out[name] = module; return; }
                var wrapped = {};
                Object.keys(module).forEach(function (key) {
                    var f = module[key];
                    wrapped[key] = typeof f === "function" ? function () { return f.apply(null, arguments); } : f;
                });
                var make = module.org_jetbrains_skia_DirectContext__1nMakeGL;
                var finalize = module.org_jetbrains_skia_impl_Managed__invokeFinalizer;
                if (typeof make === "function" && typeof finalize === "function") {
                    wrapped.org_jetbrains_skia_DirectContext__1nMakeGL = function () {
                        var canvas = lastCanvas, context = canvas && contexts.get(canvas);
                        if (context) return context;
                        context = make.apply(null, arguments);
                        if (canvas && context) { contexts.set(canvas, context); kept.add(context); }
                        return context;
                    };
                    wrapped.org_jetbrains_skia_impl_Managed__invokeFinalizer = function (finalizer, pointer) {
                        if (kept.has(pointer)) return;
                        return finalize.apply(null, arguments);
                    };
                }
                out[name] = wrapped;
                changed = true;
            });
            return changed ? out : imports;
        }
        var instantiate = WebAssembly.instantiate;
        WebAssembly.instantiate = function (source, imports) { return instantiate.call(WebAssembly, source, viaJs(imports)); };
        var streaming = WebAssembly.instantiateStreaming;
        if (streaming) WebAssembly.instantiateStreaming = function (source, imports) { return streaming.call(WebAssembly, source, viaJs(imports)); };
    })();
</script>
```

**Safari on iOS 27.2 needs it to draw at all.** There, a Kotlin/Wasm app's calls
into Skia, which Compose draws with, start arriving with their floating-point
arguments wrong after a few hundred of them: a shadow's blur fails with
`RuntimeException: Can't wrap nullptr`, text measures to nothing, and the
screen's controls pile up in its top left corner. Found in November 2026 on an
iPhone, and only there. Chrome, Firefox and Safari on a Mac do not do it. The
calls go wrong only when they pass straight from the app's WebAssembly module
into Skia's, so the script puts a plain JavaScript call in between.

**Every browser resizes faster with it.** Compose web makes a new Skia context
each time the window changes size and leaves the old one to the garbage
collector, and with it everything Skia had built: its compiled GPU programs, its
glyph atlas, its cached paths. In Chrome on a Mac, a step of a window being
dragged wider cost 15–21ms, and Skia's memory went from 18MB to 54MB over a
hundred of them. The script keeps one context for each canvas instead: 0–8ms a
step, and the memory stays where it was.

It runs in every browser, rather than only the ones that need it, so there is
nothing to detect. The library cannot do either for itself: by the time any
Kotlin runs, the module is already linked. This site's own page has it, and each
part can come out once Compose or Safari no longer needs it.

**Test on an iPhone over HTTPS.** Safari on iOS 27 runs a page served over plain
HTTP, a development server on the local network included, without its
JavaScript and WebAssembly compiler, which makes it some thirty times slower.
Timings from a `http://` page say nothing about the app.

---

## The size of an app

The library is large, and an app ships only the part of it that it uses.

**On the web and on iOS, nothing needs turning on.** A production Kotlin/Wasm
or Kotlin/JS build is compiled as a whole program: the compiler starts from
`main`, keeps only what it can reach, and Binaryen then optimises what is left.
Kotlin/Native links an iOS app the same way. A component the app never calls,
and a Tabler icon it never names, is not in the binary at all. Each icon is its
own declaration, built the first time it is drawn, for exactly this reason.

**On Android, R8 does the same**, with `isMinifyEnabled = true` in the release
build type. The library needs no keep rules of its own there.

**On the desktop, nothing is shrunk unless you ask.** A JVM app ships every
class of every dependency, which for the icons alone is some six thousand.
Compose Desktop shrinks with ProGuard in its release build:

```kotlin
// build.gradle.kts
compose.desktop.application {
    buildTypes.release.proguard {
        isEnabled = true
        configurationFiles.from("proguard-rules.pro")
    }
}
```

The library needs one rule there, for the class `:haptics` loads by name on a
Mac (see [Haptics](haptics.md)). Its jar carries the rule in
`META-INF/proguard/kontour-haptics.pro`; a shrinker that does not read rules
out of dependencies needs it copied into the app's own file. Without it the
app still runs, and the trackpad silently stops tapping:

```
# proguard-rules.pro
-keep class io.kontour.haptics.MacTrackpadFfm { <init>(); }
-dontwarn java.lang.foreign.**
```

**What every app pays for anyway** is whatever `KontourTheme` itself refers
to: the type scale's fonts, the default English of every component, and the
locale data for formatting numbers and dates. The fonts are about 350 KB and
ship with the app on every platform but the web, where they are fetched as
they are first drawn. `probes/` in the repository weighs this, an app at a
time.

---

## When it fails

| What you see | What it is |
|---|---|
| `Could not find io.kontour:ui:0.4.4` | Versions up to 0.4.4 are only on GitHub Packages. Move to 0.4.5 or newer |
| `Could not find io.kontour:…` for a version that exists | `maven.kontour.io` is missing from the repositories, or is not the one `exclusiveContent` names for `io.kontour` |
| `401 Unauthorized` from `maven.pkg.github.com` | The old GitHub Packages repository is still in `settings.gradle.kts`. Remove it |
| AndroidX artifacts will not resolve | `google()` is missing from the repositories |
| Resolution succeeds but the classes are missing on one platform | A variant did not publish. Each module should have seven per version: the module itself, and one each for `-android`, `-iosarm64`, `-iossimulatorarm64`, `-js`, `-jvm`, `-wasm-js` |
| `NoSuchMethodError` or a linkage error from inside `io.kontour` | The modules are on different versions, or Compose Multiplatform is older than 1.12.1 |
| "No KontourTheme found" or "No OverlayHost found" | The root wrappers under [Setting up `:ui`](#setting-up-ui) are missing, or the screen is composed outside them — a separate window, a dialog of the platform's own |

## Working from source

An app that has this repository checked out beside it can substitute the
sources for the published artifacts, to change the library and the app
together. The pattern is a conditional `includeBuild`, which swaps all four
modules at once:

```kotlin
// settings.gradle.kts
if (providers.gradleProperty("kontour.ui.local").orNull.toBoolean()) {
    includeBuild("../ui")
}
```

It should stay off by default: the published artifacts have to be what CI builds
and what a fresh clone gets, or they are only ever exercised by the release job.
