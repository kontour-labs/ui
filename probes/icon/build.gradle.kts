import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The theme and one Tabler icon. Against `theme`, it is `Icon` and one vector;
// if it were thousands, the icons would not be stripped. See
// `probes/README.md`: this is measured, never shipped.
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(project(":ui"))
            implementation(libs.tabler.icons.outline)
        }
    }
}

// The site's Binaryen, for the reason `ui-docs/build.gradle.kts` gives, so that
// a probe and the site are optimised by the same passes.
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec>().version.set("133")
}
