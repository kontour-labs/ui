plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The examples in `docs/using/`, as source the compiler reads.
//
// A documentation example that does not compile is worse than none: it is a
// confident wrong answer, and this project kept shipping them. Round 6 added a
// checker that parses KDoc samples for named arguments, which found real
// breakage but can only ever check names — it cannot know a type is wrong, an
// import is missing, or a slot is empty. This module is the version of that
// check with a compiler behind it.
//
// ### One target on purpose
//
// The samples are `commonMain` and use nothing platform-specific, so one target
// answers the only question being asked — does this code compile against the
// library's public API. Adding the other five would multiply the build time of
// every documentation edit by six to re-answer it, and `:ui` already gates every
// target on its own sources.
//
// It depends on `:ui` the way an app would, so an example that reaches for
// something `internal` fails here rather than being discovered by whoever copies
// it out of the docs.
//
// Nothing here ships. It is not published, and no other module depends on it.
kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":ui"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)

            // Same reasoning as `:ui-catalog` — the library ships no glyphs, so
            // an example that wants an icon has to bring one, exactly as a
            // consuming app would.
            implementation(libs.bundles.icons)
        }
    }
}

// Keeps `docs/using/` and this module's source in step.
//
// The samples are the source of truth; the fenced blocks in the markdown are
// copies of them, marked with `<!--sample:Name-->`. This fails when a copy has
// drifted, and prints what to run to fix it:
//
//     python3 docs/sync-samples.py --write
//
// Same shape as the screenshot harness — compare by default, rewrite behind an
// explicit flag — and for the same reason. A step that silently regenerates its
// own expectations checks nothing.
val checkDocSamples = tasks.register<Exec>("checkDocSamples") {
    group = "verification"
    description = "Fails if a fenced sample in docs/using/ has drifted from its source in :ui-samples."

    val root = rootProject.layout.projectDirectory
    inputs.dir(layout.projectDirectory.dir("src/commonMain/kotlin")).withPropertyName("samples")
    inputs.dir(root.dir("docs/using")).withPropertyName("pages")
    inputs.file(root.file("docs/sync-samples.py")).withPropertyName("script")
    outputs.file(layout.buildDirectory.file("reports/doc-samples.txt"))

    workingDir = root.asFile
    commandLine("python3", "docs/sync-samples.py")
}

tasks.named("check") { dependsOn(checkDocSamples) }
