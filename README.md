# Kontour UI

A Compose Multiplatform design system, built on **Foundation** — no Material.

Android, iOS, desktop and web from one source set.
<!--counts-->138 public components across 103 pages<!--/counts-->, every one with
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

### **[kontour-labs.github.io/ui](https://kontour-labs.github.io/ui/)**

The documentation site: every component running, not pictured. Its source is
[`ui-docs/content/`](ui-docs/content), which reads on GitHub too —
[components](ui-docs/content/components.md) ·
[installing](ui-docs/content/installing.md) ·
[tokens](ui-docs/content/tokens.md) ·
[theming](ui-docs/content/theming.md) ·
[accessibility](ui-docs/content/accessibility.md) ·
[the `+` vocabulary](ui-docs/content/dsls.md)

Adding to the library instead? [`docs/`](docs/README.md) is the other half:
[contributing](docs/building/contributing.md) ·
[testing](docs/building/testing.md)

---

## Installing

Published privately to GitHub Packages, so it needs a **classic** personal
access token with `read:packages` — the fine-grained kind cannot read packages
at the time of writing.

```kotlin
// settings.gradle.kts
maven("https://maven.pkg.github.com/kontour-labs/ui") {
    credentials { /* gpr.user / gpr.key */ }
    content { includeGroup("io.kontour") }
}

// build.gradle.kts
implementation("io.kontour:ui:0.1.0")
```

[`installing.md`](ui-docs/content/installing.md) has the whole of it: why
`content { includeGroup }` is not tidiness but the difference between a working
build and every dependency failing first, why a workflow in another repository
cannot use its own `GITHUB_TOKEN` at any permission level, where the token goes,
and what each failure mode looks like.

**One thing worth knowing before you start.** This publishes against Compose
Multiplatform **1.12.0-rc01**, a release candidate, deliberately:
`ImageComposeScene.calculateContentSize()` lands there and the screenshot
harness the whole test suite rests on uses it. A consumer on a stable 1.11 will
hit binary incompatibility — pin to the same RC, or stay on a version published
before the bump.

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
          :ui:checkKdocSamples :ui-catalog:jvmTest :ui-docs:jvmTest \
          :ui-samples:compileKotlinJvm :ui-samples:checkDocSamples \
          :ui:dokkaGenerateHtml
python3 docs/check-links.py
python3 docs/check-components.py
```

Twelve gates, all on the JVM, no emulator and no simulator. Each exists because
something drifted past a review — what each asks and what each has caught is in
[`docs/building/testing.md`](docs/building/testing.md).

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

It publishes from Linux. Kotlin cross-compiles the Apple **klibs**, which is
what a consumer resolves; what needs Xcode is linking a *framework*, and that
happens in the consuming app.
