import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The documentation site. One Wasm bundle, hash routing, a page per component —
// the prose from `docs/using/components/`, the live component from
// `componentRegistry`, and a link into the Dokka reference.
//
// Web only. There is no reason to build a website for iOS, and a second target
// here would be a second thing to keep compiling for nobody's benefit.
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":ui"))
            // For `componentRegistry` — the same list the contract suite runs
            // over and the renders are drawn from. A site with its own list of
            // specimens is a site that documents a library nobody shipped.
            implementation(project(":ui-catalog"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.bundles.icons)
        }
    }
}

// The prose, as Kotlin.
//
// Generated at build time rather than parsed at run time, so the bundle a reader
// downloads carries structured content instead of a markdown parser and a pile
// of strings — and a malformed page fails here rather than in their browser.
val generatedDocs = layout.buildDirectory.dir("generated/docPages")

val generateDocPages = tasks.register<Exec>("generateDocPages") {
    group = "documentation"
    description = "Turns docs/using/components/*.md into DocPages.kt"

    val pages = rootProject.layout.projectDirectory.dir("docs/using/components")
    val script = rootProject.layout.projectDirectory.file("docs/generate-doc-pages.py")
    inputs.dir(pages).withPropertyName("pages")
    inputs.file(script).withPropertyName("script")
    outputs.dir(generatedDocs).withPropertyName("generated")

    workingDir = rootProject.layout.projectDirectory.asFile
    commandLine("python3", script.asFile.path, generatedDocs.get().asFile.path)
}

kotlin.sourceSets.named("wasmJsMain") {
    kotlin.srcDir(generatedDocs)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateDocPages)
}

// `wasmJsBrowserDistribution` writes to `build/dist/wasmJs/productionExecutable`,
// which is what `.github/workflows/pages.yml` copies. That path is a convention
// of the Kotlin plugin rather than a promise, so the workflow asserts the files
// it expects are there rather than trusting the copy to have found them.
