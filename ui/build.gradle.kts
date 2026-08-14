import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // The JVM target is not a shipping platform. It exists so the component
    // library can be compiled, unit-tested and screenshot-tested without an
    // emulator or a simulator in the loop.
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
        namespace = "io.kontour.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Compose Foundation and nothing above it. Material is deliberately
            // absent — see the `checkNoMaterial` task at the bottom of this file.
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)

            // Date/time components need a calendar; nothing else leaks in.
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.uiTest)
        }

        jvmTest.dependencies {
            // Skiko + the desktop runtime, so `runComposeUiTest` has a real
            // canvas to render into when the suite runs on the JVM.
            implementation(compose.desktop.currentOs)
        }

        androidMain.dependencies {
            implementation(libs.compose.uiTooling)
        }

        webMain.dependencies {
            // DOM access for `prefers-reduced-motion` / `prefers-contrast`.
            // Supports both the js and wasmJs targets from one source set.
            implementation(libs.kotlinx.browser)
        }
    }
}

compose.resources {
    // The default package is derived from the root project name, which would put
    // the design system's font accessors under `anyways.ui.…`. Pin it instead, so
    // `:ui` stays nameable as a standalone library.
    packageOfResClass = "io.kontour.ui.generated.resources"
    publicResClass = false
}

// ---------------------------------------------------------------------------
// Material containment
//
// Commit 34862a9 removed Material from this project on purpose. The risk is not
// that someone types `import androidx.compose.material3` — that is easy to spot
// in review — but that a convenience library pulls it back in transitively and
// nobody notices until the app is 400 KB heavier and two type scales deep.
//
// This walks the fully resolved JVM runtime graph and fails the build if any
// Material module appears anywhere in it. One target is enough: `:ui` has no
// per-platform dependencies, so anything that reaches Android or iOS reaches
// the JVM classpath too.
// ---------------------------------------------------------------------------
val forbiddenGroups = setOf(
    "androidx.compose.material",
    "androidx.compose.material3",
    "org.jetbrains.compose.material",
    "org.jetbrains.compose.material3",
)

val jvmRuntimeGraph: Provider<ResolvedComponentResult> =
    configurations.named("jvmRuntimeClasspath")
        .flatMap { it.incoming.resolutionResult.rootComponent }

val checkNoMaterial = tasks.register("checkNoMaterial") {
    group = "verification"
    description = "Fails if a Material dependency reaches the :ui classpath."

    val root = jvmRuntimeGraph
    val forbidden = forbiddenGroups
    inputs.property("forbiddenGroups", forbidden)

    doLast {
        val visited = mutableSetOf<String>()
        val offenders = sortedSetOf<String>()

        fun walk(component: ResolvedComponentResult) {
            if (!visited.add(component.id.displayName)) return
            component.moduleVersion?.let { id ->
                if (id.group in forbidden) {
                    offenders += "${id.group}:${id.name}:${id.version}"
                }
            }
            component.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .forEach { walk(it.selected) }
        }
        walk(root.get())

        if (offenders.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Material reached the :ui classpath:")
                    offenders.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("The design system is built on Compose Foundation only.")
                    appendLine("Find which dependency pulls this in with:")
                    appendLine("  ./gradlew :ui:dependencies --configuration jvmRuntimeClasspath")
                }
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkNoMaterial)
}
