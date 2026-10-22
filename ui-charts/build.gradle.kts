import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
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

// Charts: one cartesian `Chart` of marks, a `SectorChart` for parts of a whole,
// and a `Sparkline`, drawn from `:ui`'s theme.
//
// A module of its own so that an app without a chart carries none of it. Its
// rules are `:ui`'s, and are checked from there: `:ui:checkApiConventions` and
// `:ui:checkKdocSamples` read this module's sources as well as their own (see
// `libraryKotlinSources` in `ui/build.gradle.kts`), so CI's existing tasks cover
// it and nothing here duplicates them.
kontourPublishing(
    displayName = "Kontour UI Charts",
    summary = "Charts for Kontour UI: lines, areas, bars, points and sectors, themed, accessible and localised.",
)

dokka {
    // The same gate `:ui` sets: a warning is a broken `[Link]` in the KDoc.
    dokkaPublications.configureEach {
        failOnWarning = true
    }
    moduleName = "kontour-ui-charts"
    moduleVersion = kontourDocsVersion()

    // Documented from common code, for the reason `:ui-nav3` gives: there is no
    // platform code here, and the Android source set would need an SDK.
    dokkaSourceSets.matching { it.name.startsWith("android") }.configureEach {
        suppress.set(true)
    }

    dokkaSourceSets.configureEach {
        includes.from("Module.md")
        enableJdkDocumentationLink = false
        sourceLink {
            localDirectory = layout.projectDirectory.dir("src").asFile
            remoteUrl("https://github.com/kontour-labs/ui/tree/main/ui-charts/src")
            remoteLineSuffix = "#L"
        }
    }
}

kotlin {
    // The public API, checked in under `api/`. See `:ui` for why.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        filters {
            exclude {
                byNames.add("**.ComposableSingletons**")
            }
        }
    }

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

    // The same six targets as `:ui`, and Java 11 bytecode for the reason
    // `:ui-nav3` gives.
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
            freeCompilerArgs.add("-Xjdk-release=11")
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
        namespace = "io.kontour.ui.charts"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`: `Theme`, `LocaleFormats`, `ContentScope` and `LocalDate`
            // are in this module's signatures.
            api(project(":ui"))
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
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

// The web test tasks, off for the reason `:ui`'s build gives at length.
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
