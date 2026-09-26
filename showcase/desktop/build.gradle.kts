import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":ui-catalog"))
            implementation(compose.desktop.currentOs)
        }
    }
}

// Desktop is not a shipping target. This window exists so the component library
// can be run, poked at and screenshotted without an emulator attached.
compose.desktop {
    application {
        mainClass = "io.kontour.ui.catalog.desktop.MainKt"

        // A Mac's trackpad haptics are one call into AppKit through Java's
        // foreign-function API, which warns on first use unless the app says
        // native access is intended. Skiko's own native library is the other
        // caller the flag quiets.
        jvmArgs += "--enable-native-access=ALL-UNNAMED"

        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "Kontour UI Catalog"
            packageVersion = "1.0.0"
        }
    }
}
