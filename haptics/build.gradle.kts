import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.dokka)
    `maven-publish`
}

// Haptics for every platform `:ui` ships on, and nothing else.
//
// A module of its own, and one with no Compose in it, because *playing* a haptic
// has nothing to do with drawing: the Android vibrator, UIKit's feedback
// generators, Core Haptics, the browser's Vibration API and a Mac's trackpad are
// all reachable from plain Kotlin. `:ui` depends on this and keeps the policy —
// which interaction gets which effect, the levels, the rate floor — so an app
// that only wants the effects can have them without a design system attached.
//
// It exists instead of a third-party library because the one that came closest
// played everything through the raw vibrator, where Android's touch-feedback
// setting cannot reach it, and swapped Apple's own tuned feedback for
// approximations. This one reaches for the platform's predefined effects first.
kontourPublishing(
    displayName = "Kontour Haptics",
    summary = "Platform haptics for Kotlin Multiplatform — Android, iOS, the web and Mac trackpads — with no Compose dependency.",
    licenceNote = "All rights reserved.",
)

dokka {
    // The same gate `:ui` sets: a warning is a broken `[Link]` in the KDoc.
    dokkaPublications.configureEach {
        failOnWarning = true
    }
    moduleName = "kontour-haptics"
    moduleVersion = version.toString()

    dokkaSourceSets.configureEach {
        includes.from("Module.md")
        enableJdkDocumentationLink = false
        sourceLink {
            localDirectory = layout.projectDirectory.dir("src").asFile
            remoteUrl("https://github.com/kontour-labs/ui/tree/main/haptics/src")
            remoteLineSuffix = "#L"
        }
    }
}

kotlin {
    // The public API, checked in under `api/`. See `:ui` for why.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    // Main compilations only, for the reason `:ui` gives.
    targets.configureEach {
        compilations.configureEach {
            if (name == "main") {
                compileTaskProvider.configure {
                    compilerOptions.allWarningsAsErrors.set(true)
                }
            }
        }
    }

    // The same six targets as `:ui`, so the library can never have a platform
    // this cannot follow it onto.
    //
    // The JVM one is Java 11 bytecode where `:ui`'s is the toolchain's: used on
    // its own this should load on any desktop JVM, and it does — the one piece
    // that needs Java 22, the Mac trackpad, is reached by name and only when
    // the running JVM has it. See `DesktopHaptics`.
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    iosArm64()
    iosSimulatorArm64()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "io.kontour.haptics"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

checkNoCompose()

// The web test tasks, off for the reason `:ui`'s build gives at length: a
// library has no executable for them to run in.
tasks.matching {
    it.name in setOf("jsTest", "jsBrowserTest", "wasmJsTest", "wasmJsBrowserTest")
}.configureEach { enabled = false }

tasks.withType<Test>().configureEach {
    systemProperty("user.language", "en")
    systemProperty("user.country", "AU")
}
