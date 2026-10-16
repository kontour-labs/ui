# Contributing

How the library is built, checked and released. Using it is the
[README](README.md) and [docs.kontour.io/ui](https://docs.kontour.io/ui/).

| | |
|---|---|
| [`docs/building/contributing.md`](docs/building/contributing.md) | The conventions every component follows: parameter order, names, tokens, state, accessibility, and what a pull request needs |
| [`docs/building/testing.md`](docs/building/testing.md) | Every check, what it asks and what it has caught; what CI runs and when |
| [`docs/building/releasing.md`](docs/building/releasing.md) | Tagging a release, Maven Central, and the secrets it needs |
| [`docs/building/hosting.md`](docs/building/hosting.md) | The documentation site on Cloudflare Pages |
| [`docs/README.md`](docs/README.md) | The library's layers and the rules that follow from them |

## Running the gallery

Every component in every state, on whichever platform you want to poke at it:

```sh
./gradlew :showcase:desktop:run          # a JVM window
./gradlew :showcase:android:installDebug # a device or emulator
./gradlew :ui-catalog:jsBrowserRun       # a browser
open showcase/ios/KontourUI.xcodeproj    # an iPhone or a simulator, from a Mac
```

The iOS one is an Xcode project rather than a Gradle task, because linking a
framework needs Xcode. It builds `:ui-catalog`'s `Catalog.framework` from a Run
Script phase, so there is nothing to run first — and its Run action is set to
**Release**, because `embedAndSignAppleFrameworkForXcode` picks the Kotlin
framework from the configuration and a Debug one is built without optimisation.

The first build is slow for the same reason, and on a cold machine it also pulls
a Kotlin/Native toolchain. To watch that somewhere it is visible rather than
inside Xcode's log, build the framework from a terminal first:

```sh
./gradlew :ui-catalog:linkReleaseFrameworkIosSimulatorArm64
```

The gallery is also where most of the tests live: the contract suite, the
screenshot goldens, and the specimen registry that drives the per-component
images in the docs.

## Checking

```sh
./gradlew :ui:jvmTest :ui:checkNoMaterial :ui:checkApiConventions \
          :ui:checkKdocSamples :ui-catalog:jvmTest :ui-docs:jvmTest \
          :ui-samples:compileKotlinJvm :ui-samples:checkDocSamples \
          :ui-nav3:jvmTest :ui-nav3:checkNoMaterial \
          :haptics:jvmTest :haptics:checkNoCompose \
          :ui:compileIosMainKotlinMetadata \
          :ui-catalog:compileIosMainKotlinMetadata \
          :ui-nav3:compileIosMainKotlinMetadata \
          :haptics:compileIosMainKotlinMetadata \
          :ui:dokkaGenerateHtml :ui-nav3:dokkaGenerateHtml :haptics:dokkaGenerateHtml
python3 docs/check-links.py
python3 docs/check-components.py
```

`-Pkontour.tests.full=true` adds the catalog's slow classes — the sweeps over
every component, width and setting — and `-Pkontour.screenshots.update=true`
re-records the goldens a change meant to move.

No emulator and no simulator. Everything runs on the JVM except the
`compileIosMainKotlinMetadata` tasks, which type-check the iOS source sets — UIKit
cinterop included — from a Linux host, which is as far as this repository can
follow a platform actual. Each gate exists because something drifted past a
review; [`testing.md`](docs/building/testing.md) has what each asks and what
each has caught.

## Releasing

Turn the changelog's **Unreleased** into the version and its date, give every
break a section in the migrating guide, and push a tag:

```sh
git tag v0.4.5 && git push origin v0.4.5
```

CI runs what has not passed for that tree, publishes all three modules to Maven
Central, and deploys the documentation labelled with the version.
[`releasing.md`](docs/building/releasing.md) has the rest: the version rule,
what to do when a release fails, and setting up Central's secrets.
