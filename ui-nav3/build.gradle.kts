import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.dokka)
    `maven-publish`
}

// Navigation 3 scene strategies that lay out with `:ui`'s pane scaffolds.
//
// A module of its own so that `:ui` takes no navigation dependency at all: the
// scaffolds work for anyone, and this is the part that turns a back stack into
// one. The ready-made adaptive strategy for Navigation 3 is Material's, and
// `checkNoMaterial` below is what keeps it out — which is the whole reason this
// module exists.
kontourPublishing(
    displayName = "Kontour UI for Navigation 3",
    summary = "Navigation 3 scene strategies built on Kontour UI's pane scaffolds, without Material.",
)

dokka {
    // The same gate `:ui` sets: a warning is a broken `[Link]` in the KDoc.
    dokkaPublications.configureEach {
        failOnWarning = true
    }
    moduleName = "kontour-ui-nav3"
    moduleVersion = version.toString()

    // Documented from common code. This module has no platform code of its own,
    // and Dokka's Android source set would need `:ui` *compiled* for Android —
    // an SDK — to say nothing the common one does not.
    dokkaSourceSets.matching { it.name.startsWith("android") }.configureEach {
        suppress.set(true)
    }

    dokkaSourceSets.configureEach {
        includes.from("Module.md")
        enableJdkDocumentationLink = false
        sourceLink {
            localDirectory = layout.projectDirectory.dir("src").asFile
            remoteUrl("https://github.com/kontour-labs/ui/tree/main/ui-nav3/src")
            remoteLineSuffix = "#L"
        }
    }
}

kotlin {
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

    // The same six targets as `:ui`. A strategy that could not follow the library
    // onto one of them would make that target the one without navigation.
    jvm()

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
        namespace = "io.kontour.ui.nav3"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // All `api`: `SceneStrategy`, `NavEntry` and `PaneScaffoldDefaults`
            // appear in this module's public signatures, so a caller cannot use
            // it without them on its own compile classpath.
            api(project(":ui"))
            api(libs.navigation3.ui)
            api(libs.navigation3.runtime)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.compose.uiTest)
        }

        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

checkNoMaterial()

// The web test tasks, off for the reason `:ui`'s build gives at length: a
// library has no executable for them to run in, and the check that insists on
// one is switched off with them.
tasks.matching {
    it.name.startsWith("checkComposeUiTestConfiguration") ||
        it.name in setOf("jsTest", "jsBrowserTest", "wasmJsTest", "wasmJsBrowserTest")
}.configureEach { enabled = false }

// The region the rest of the suite runs in, and the same cap on a slow test.
tasks.withType<Test>().configureEach {
    systemProperty("user.language", "en")
    systemProperty("user.country", "AU")
    systemProperty("kotlinx.coroutines.test.default_timeout", "5m")
}
