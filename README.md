# Kontour UI

A Compose Multiplatform design system, built on **Foundation** — no Material.

Android, iOS, desktop and web from one source set. 138 components, a contract
suite every one of them passes, and screenshot goldens that compare rather than
overwrite.

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

**[Documentation](docs/README.md)** · [components](docs/using/components.md) ·
[theming](docs/using/theming.md) · [the `+` vocabulary](docs/using/dsls.md) ·
[contributing](docs/building/contributing.md)

---

## Installing

Published privately to GitHub Packages. Add the repository, authenticating with
a token that has `read:packages`:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.pkg.github.com/kontour-labs/ui") {
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                password = providers.gradleProperty("gpr.token").orNull
            }
        }
    }
}
```

```kotlin
// build.gradle.kts
implementation("io.kontour:ui:0.1.0")
```

### A note on the Compose version

This publishes against **Compose Multiplatform 1.12.0-rc01**, a release
candidate, and that is deliberate rather than an oversight:
`ImageComposeScene.calculateContentSize()` lands there, and the screenshot
harness the whole test suite rests on uses it.

A consumer on a stable 1.11 will hit binary incompatibility. Until 1.12 is
final, pin to the same RC or stay on a version of this library published before
the bump.

---

## Why no Material

Material is a design language with opinions about colour, shape, motion and
typography. Adopting it and then overriding it produces two type scales and two
elevation models fighting each other, and 400 KB of a component set you use a
tenth of.

Foundation gives the primitives — layout, gestures, semantics, indication — with
no opinions attached. `:ui:checkNoMaterial` walks the resolved dependency graph
and fails the build if Material arrives, transitively or otherwise, because the
risk was never someone typing the import.

## Running the gallery

Every component in every state, on whichever platform you want to poke at it:

```sh
./gradlew :showcase:desktop:run          # a JVM window
./gradlew :showcase:android:installDebug # a device or emulator
./gradlew :ui-catalog:jsBrowserRun       # a browser
```

The gallery is also where most of the tests live: the contract suite, the
screenshot goldens, and the specimen registry that drives the per-component
images in the docs.

## Building

```sh
./gradlew :ui:jvmTest :ui:checkNoMaterial :ui:checkApiConventions \
          :ui:checkKdocSamples :ui-catalog:jvmTest
python3 docs/check-links.py
```

Four guards run alongside the tests, and each exists because something drifted
past a review:

| | |
|---|---|
| `checkNoMaterial` | Material anywhere in the resolved graph |
| `checkApiConventions` | nine rules over every public declaration |
| `checkKdocSamples` | a sample naming a parameter that does not exist, or omitting a required one |
| Screenshot goldens | a render that moved |

[`docs/building/`](docs/building/) has the rest.
