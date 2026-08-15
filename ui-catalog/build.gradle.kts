import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    iosArm64()
    iosSimulatorArm64()

    // The catalog builds its own web bundles rather than riding along inside
    // `:webApp`, so shipping the app never ships the gallery.
    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    android {
        namespace = "io.kontour.ui.catalog"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":ui"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)

            // Icons live here, not in `:ui`. The design system takes an
            // ImageVector and ships no glyphs of its own, so the icon set stays
            // an application choice — the catalog just happens to make the same
            // one the app does.
            implementation(libs.bundles.icons)
        }

        // The contract suite lives here, not in `:ui`. It is a list of specimens
        // and seven assertions over them, and the *same* list drives the
        // per-component renders in `jvmTest` — which cannot see a test source
        // set in another module. One list, in the module that owns the gallery.
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.uiTest)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            // Skiko, so the screenshot harness has a real canvas to render into.
            implementation(compose.desktop.currentOs)
            // For geometry tests that need to find a node the component owns
            // internally — a nav bar's destinations are not reachable with an
            // `onGloballyPositioned` from outside, but they are findable by label.
            implementation(libs.compose.uiTest)
        }
    }
}

// Screenshot goldens live in `screenshots/` and are committed. `:ui-catalog:jvmTest`
// *compares* against them and fails on a mismatch, writing the render and a
// highlighted diff to `build/screenshot-diffs/`.
//
// To accept a change:
//
//     ./gradlew :ui-catalog:jvmTest -Pkontour.screenshots.update=true
//
// then look at the result before committing it. That step is the whole point —
// a golden nobody looked at pins whatever was broken when it was recorded.
tasks.withType<Test>().configureEach {
    systemProperty("kontour.screenshots.dir", layout.projectDirectory.dir("screenshots").asFile.path)
    systemProperty("kontour.screenshots.diffDir", layout.buildDirectory.dir("screenshot-diffs").get().asFile.path)
    systemProperty("kontour.screenshots.update", providers.gradleProperty("kontour.screenshots.update").getOrElse("false"))
    // Goldens are inputs now, so a change to one re-runs the comparison rather
    // than being skipped as up-to-date.
    inputs.dir(layout.projectDirectory.dir("screenshots")).withPropertyName("screenshotGoldens")
}
