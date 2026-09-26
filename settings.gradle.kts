rootProject.name = "kontour-ui"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// The library. Product-agnostic, and published.
include(":ui")

// Platform haptics, with no Compose in it. `:ui` depends on this, not the other
// way round, which is why it is not one of the `ui-*` modules.
include(":haptics")

// Navigation 3 scene strategies over the library's pane scaffolds. Published
// beside it, and separate so that `:ui` takes no navigation dependency at all.
include(":ui-nav3")

// The gallery, and the living documentation. Every component in every state it
// has, and the source of the screenshot goldens — so it is also where most of
// the library's tests live.
include(":ui-catalog")

// The examples in `ui-docs/content/`, as source. The docs hold copies; this is what
// the compiler reads, so an example that no longer compiles fails the build
// rather than being found by whoever pastes it into their app.
include(":ui-samples")

// The documentation site: a page per component, deployed to GitHub Pages. It
// reads the prose from `docs/` and the specimens from `:ui-catalog`, so there
// is no third copy of either.
include(":ui-docs")

// Hosts that put the gallery on a screen. None of them ships; they exist so the
// library can be run and poked at on each platform it claims to support.
//
// There is a third, `showcase/ios/`, and it is deliberately not here: linking a
// framework needs Xcode, so the iOS host is an Xcode project over the `Catalog`
// framework `:ui-catalog` already builds. A `:showcase:ios` module would mean a
// second framework and a second answer to a question `:ui-catalog` has answered.
include(":showcase:desktop")
include(":showcase:android")
