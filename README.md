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

Published privately to GitHub Packages, so it needs a token that can read
packages — a **classic** personal access token with `read:packages`. The
fine-grained kind cannot read packages at the time of writing.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.pkg.github.com/kontour-labs/ui") {
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
            // Not tidiness. GitHub Packages authenticates *every* request,
            // including the ones that come back 404, so an unscoped repository
            // sends a credentialed request for every dependency in your build
            // and anyone without a token watches all of them fail before Gradle
            // reaches Maven Central.
            content { includeGroup("io.kontour") }
        }
    }
}
```

```kotlin
// build.gradle.kts
implementation("io.kontour:ui:0.1.0")
```

`gpr.user` and `gpr.key` go in `~/.gradle/gradle.properties`, not in the
repository. In GitHub Actions, `GITHUB_ACTOR` and `GITHUB_TOKEN` are already
there — but a workflow in *another* repository also needs `permissions:
packages: read`, and the package has to grant that repository access under
**Packages → ui → Manage Actions access**. Without that the failure is a 401
that reads exactly like a bad token.

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

## Releasing

A release is a tag, and nothing else:

```sh
git tag v0.2.0 && git push origin v0.2.0
```

CI runs the checks, then publishes `io.kontour:ui:0.2.0` to GitHub Packages. The
version comes from the tag, so the artifact cannot disagree with it.

To cut one without tagging — from a branch, or to re-publish — run the **CI**
workflow manually and give it a version. That is the only other way to get a
publishable version out of the build: on a branch with no version supplied, the
version is `0.1.0-SNAPSHOT` and the publish job does not run.

```sh
./gradlew :ui:coordinate    # what this checkout would publish as
```

It publishes from Linux. Kotlin cross-compiles the Apple **klibs**, which is what
a consumer resolves; what needs Xcode is linking a *framework*, and that happens
in the consuming app.
