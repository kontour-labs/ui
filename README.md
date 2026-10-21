# Kontour UI

A Compose Multiplatform design system, built on **Foundation** — no Material.

Android, iOS, desktop and the web from one source set.
<!--counts-->175 public components across 134 pages<!--/counts-->, every one with
a demo you can operate, a compiled example and a generated parameter table.

```kotlin
KontourTheme {
    ListItem(onClick = { open(stop) }) {
        +stop.name
        supporting { +stop.detail }
        leading { +Tabler.Outline.Bus }
        trailing { Switch(saved, onCheckedChange = ::save) }
    }
}
```

### **[docs.kontour.io/ui](https://docs.kontour.io/ui/)**

Every component running, not pictured, with its parameters, its accessibility
and its demo. The same pages are in [`ui-docs/content/`](ui-docs/content), and
as Markdown for agents at
[`docs.kontour.io/ui/llms.txt`](https://docs.kontour.io/ui/llms.txt).

---

## Installing

From Kontour's Maven repository, which needs no token:

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

```toml
# gradle/libs.versions.toml
[versions]
kontour = "0.4.6"

[libraries]
kontour-ui = { module = "io.kontour:ui", version.ref = "kontour" }
# Navigation 3 scene strategies over the pane scaffolds, if the app uses it
kontour-ui-nav3 = { module = "io.kontour:ui-nav3", version.ref = "kontour" }
```

```kotlin
// build.gradle.kts, in the shared module
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kontour.ui)
        }
    }
}
```

Releases up to 0.4.4 were on GitHub Packages and needed a token; from 0.4.5
nothing does — [migrating](ui-docs/content/migrating.md#044-to-045) has the
change.

| Artifact | What it is |
|---|---|
| `io.kontour:ui` | The theme, the tokens and every component |
| `io.kontour:ui-nav3` | [Navigation 3](ui-docs/content/navigation3.md) scene strategies, and page transitions that follow the back gesture |
| `io.kontour:haptics` | [Platform haptics](ui-docs/content/haptics.md) with no Compose in it. `:ui` brings it; on its own for an app that wants only the effects |

Keep all three on the same version. Each is published for Android (minSdk 29),
iOS (`iosArm64`, `iosSimulatorArm64`), the desktop JVM, and the web as JS and
Wasm, built with **Kotlin 2.4.10** against **Compose Multiplatform 1.12.1**,
which is the floor: an app on 1.11 hits binary incompatibility.

## The first screen

Two wrappers at the root, once:

```kotlin
KontourTheme {
    OverlayHost {
        App()
    }
}
```

`KontourTheme` provides the tokens and follows the system's dark mode, contrast
and reduced-motion settings. `OverlayHost` is where dialogs, sheets, menus and
toasts draw. Both throw when missing, rather than drawing in the wrong palette
or nowhere. [Installing](ui-docs/content/installing.md) has the per-platform
setup: the Android manifest flag the back gesture needs, what the web does with
the keyboard, and the rest.

Then, in this order:

- [**Components**](ui-docs/content/components.md) — the map of all of them, by
  what they are for.
- [**Patterns**](ui-docs/content/patterns.md) — how the pieces are meant to go
  together, and the mistakes that are easy to make: a scrollbar inside its list,
  a sheet without a host, a hand-counted group of rows.
- [**Tokens**](ui-docs/content/tokens.md) and [**theming**](ui-docs/content/theming.md)
  — spacing, colour, type and motion, and reskinning them.
- [**The `+` vocabulary**](ui-docs/content/dsls.md) — how a component's content
  is written.
- [**Accessibility**](ui-docs/content/accessibility.md),
  [**localisation**](ui-docs/content/localisation.md),
  [**overlays**](ui-docs/content/overlays.md),
  [**drag and drop**](ui-docs/content/drag-and-drop.md).
- [**Changelog**](ui-docs/content/changelog.md) and
  [**migrating**](ui-docs/content/migrating.md) when you upgrade.

## Why no Material

Material is a design language with opinions about colour, shape, motion and
typography. Adopting it and then overriding it produces two type scales and two
elevation models fighting each other, and a component set you use a tenth of.

Foundation gives the primitives — layout, gestures, semantics, indication — with
no opinions attached. The build fails if Material arrives in the dependency
graph, transitively or otherwise, so it never ships with the library.

## Contributing

[`CONTRIBUTING.md`](CONTRIBUTING.md): running the gallery, the checks, and how a
release is cut. The conventions every component follows are in
[`docs/building/contributing.md`](docs/building/contributing.md).

## Licence

[Apache License 2.0](LICENSE). The fonts `:ui` bundles and the Unicode CLDR
plural rules it is generated from are under their own licences, listed in
[`NOTICE`](NOTICE).
