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

// The library. Product-agnostic, and the only module that gets published.
include(":ui")

// The gallery, and the living documentation. Every component in every state it
// has, and the source of the screenshot goldens — so it is also where most of
// the library's tests live.
include(":ui-catalog")

// Hosts that put the gallery on a screen. None of them ships; they exist so the
// library can be run and poked at on each platform it claims to support.
include(":showcase:desktop")
include(":showcase:android")
