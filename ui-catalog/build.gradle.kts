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

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            // Skiko, so the screenshot harness has a real canvas to render into.
            implementation(compose.desktop.currentOs)
        }
    }
}

// Screenshot goldens are written here and committed. `:ui-catalog:jvmTest`
// regenerates them; review the diff rather than accepting it — a golden nobody
// looked at pins whatever was broken when it was recorded.
tasks.withType<Test>().configureEach {
    systemProperty("kontour.screenshots.dir", layout.projectDirectory.dir("screenshots").asFile.path)
}
