# Installing

Getting Kontour UI into an app: which modules there are, what each needs, and
getting them to resolve on a laptop and in CI.

Everything is published privately to **GitHub Packages** from `kontour-labs/ui`.
That registry authenticates *every* request, including the ones that come back
404, so there is no anonymous read and no way to try it without a credential
first. Much of this page is about that credential.

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

- **Compose Multiplatform 1.12.0-rc01.** A release candidate, deliberately: the
  screenshot harness the test suite rests on needs
  `ImageComposeScene.calculateContentSize()`, which lands there. An app on a
  stable 1.11 hits binary incompatibility; use the same release candidate or
  newer.
- **Kotlin 2.4.10** or newer, the version the libraries are built with.
- **Android minSdk 29.**

`:ui-nav3` brings Navigation 3 1.1.1 with it
(`org.jetbrains.androidx.navigation3:navigation3-ui` and
`androidx.navigation3:navigation3-runtime`). Declaring them yourself is fine, at
that version or newer.

## The repository

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.github.com/kontour-labs/ui") {
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("KONTOUR_PACKAGES_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("KONTOUR_PACKAGES_TOKEN")).orNull
            }
            content { includeGroup("io.kontour") }
        }
    }
}
```

`google()` is for the AndroidX artifacts Compose and Navigation 3 depend on on
Android; most projects have it already.

**`content { includeGroup("io.kontour") }` is not tidiness.** Without it Gradle
will try this repository for every dependency in your build — several hundred
credentialed requests, all of which 404, and all of which fail outright for
anyone whose token is missing or wrong. Scoped, the only thing that can fail
here is the design system, which is what makes the error mean something.

## Adding the modules

One version for all three, in the version catalog:

```toml
# gradle/libs.versions.toml
[versions]
kontour = "0.4.2"

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
`implementation("io.kontour:ui:0.4.2")`, and so on.

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

**Icons are yours.** Components take an `ImageVector`, so the app chooses its
set. The documentation and the gallery use Tabler, as Kontour Labs builds it —
`io.kontour:tabler-icons-outline` and `-filled`, every icon of the release, with
`Tabler.Outline.*` under `io.kontour.icons.tabler`. The library draws its own
few system icons from it, so it comes with the library, from the repository
below: GitHub Packages serves everything Kontour Labs publishes from the one
URL. Any `ImageVector` works. The site's Icons page, in its top bar, searches
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
  `button`, with no checked or selected state, whatever role the library passes;
  [In the browser](accessibility.md#in-the-browser) has what that affects and
  when Compose fixes it.

  On an iPhone or iPad, every browser is WebKit, which opens the keyboard only
  for an input focused during the tap itself; Compose focuses its own a frame
  later. `KontourTheme` installs a primer for that: a tap on a text field, a
  combobox, a nav bar's search or an editable knob focuses a hidden input
  straight away, the keyboard opens for it, and Compose's input takes it over a
  moment later. Nothing is added off iOS. A text field of your own on
  `BasicTextField` does not get it.
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

## The token

A **classic** personal access token with the `read:packages` scope, and nothing
else. Create it at **Settings → Developer settings → Personal access tokens →
Tokens (classic)**.

> **Fine-grained tokens do not work.** They cannot read GitHub Packages at the
> time of writing, whatever permissions you give them. If you are looking at a
> permissions matrix that has no "Packages" row, that is the wrong kind of token.

The account that owns the token needs read access to `kontour-labs/ui` — the
packages inherit the repository's access, so someone who cannot see the repo
cannot see its packages either. One token reads all three modules.

### On a laptop

`~/.gradle/gradle.properties`, **not** the repository:

```properties
gpr.user=your-github-username
gpr.key=ghp_…
```

### In CI

Store the same pair as secrets in the *consuming* repository (or as organisation
secrets, if you are on a plan that offers them for private repositories) and put
them in the environment of any step that resolves dependencies:

```yaml
permissions:
  contents: read
  # No `packages: read` — see below for why it would not help.

steps:
  - name: Test
    env:
      KONTOUR_PACKAGES_ACTOR: ${{ secrets.KONTOUR_PACKAGES_ACTOR }}
      KONTOUR_PACKAGES_TOKEN: ${{ secrets.KONTOUR_PACKAGES_TOKEN }}
    run: ./gradlew test
```

---

## Why the workflow's own token will not do

This is the part that wastes an afternoon, so it is worth stating plainly.

**`secrets.GITHUB_TOKEN` cannot read these packages from another repository,
and no permissions block changes that.** A workflow's own token is scoped to the
repository it runs in: it reads *that* repository's packages and no others.

GitHub does have a per-package grant that would widen it — **Manage Actions
access**, where you name other repositories that may read the package. It only
exists for **container** packages (`ghcr.io`). A Maven package has no such
screen: its access is inherited from the repository that published it and cannot
be extended. If you go looking for that setting on a Maven package you will not
find it, because it is not there.

So a consumer in another repository needs a credential belonging to an
**account** rather than to a workflow. Options, in the order most people should
consider them:

| | |
|---|---|
| **Classic PAT in a secret** | What `kontour-labs/anyways` does. Least setup, definitely works. Tied to whoever created it, and needs rotating when it expires |
| **Machine-user PAT** | The same, on a dedicated bot account added to the org. No individual's credentials in CI, at the cost of a seat |
| **GitHub App token** | An org-owned app with `packages: read`, installed on both repositories, minting a short-lived token per run via `actions/create-github-app-token`. Cleanest and self-expiring; the most setup |

Publishing *to* the registry is different and simpler: the release workflow lives
in this repository, so its own `GITHUB_TOKEN` with `packages: write` is enough.
Only reading from elsewhere needs an account credential.

---

## When it fails

| What you see | What it is |
|---|---|
| `401 Unauthorized` on an `io.kontour` artifact | No credential, or one without `read:packages`. A fine-grained token looks exactly like this |
| `401` in CI while your laptop works | The workflow is still using `secrets.GITHUB_TOKEN`, or the secrets are not in that step's `env` |
| `404 Not Found` on a version | That version was never published. `Packages` on the repository lists what exists, per module |
| Hundreds of failing requests for unrelated dependencies | The `content { includeGroup(…) }` filter is missing |
| AndroidX artifacts will not resolve | `google()` is missing from the repositories |
| Resolution succeeds but the classes are missing on one platform | A variant did not publish. Each module should have seven per version: the module itself, and one each for `-android`, `-iosarm64`, `-iossimulatorarm64`, `-js`, `-jvm`, `-wasm-js` |
| `NoSuchMethodError` or a linkage error from inside `io.kontour` | The modules are on different versions, or Compose Multiplatform is older than 1.12.0-rc01 |
| "No KontourTheme found" or "No OverlayHost found" | The root wrappers under [Setting up `:ui`](#setting-up-ui) are missing, or the screen is composed outside them — a separate window, a dialog of the platform's own |

## Working without a token

A consumer that has this repository checked out beside it can substitute the
sources for the published artifacts and skip the registry entirely. That is what
`anyways` does under `-Pkontour.ui.local=true`; the pattern is a conditional
`includeBuild`, which swaps all three modules at once:

```kotlin
if (providers.gradleProperty("kontour.ui.local").orNull.toBoolean()) {
    includeBuild("../../ui")
}
```

Useful when changing the library and the app together, and a reasonable fallback
for someone who cannot get a token. It should stay off by default: the published
artifacts have to be what CI builds and what a fresh clone gets, or they are only
ever exercised by the release job.
