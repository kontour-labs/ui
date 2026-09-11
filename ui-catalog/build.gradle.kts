import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        // A framework, so an Xcode project can link the gallery and run it on a
        // device. `:ui` publishes klibs for the same targets and does not need
        // one — this is a host, and a host has to be linkable.
        target.binaries.framework {
            baseName = "Catalog"
            isStatic = true
        }
    }

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

// ---------------------------------------------------------------------------
// One region for the tests
// ---------------------------------------------------------------------------
//
// `DateTimeFormats` resolves from the platform's locale now — that is the whole
// of the "dates are backwards in the US" fix — which makes every calendar and
// every clock in the suite a function of the machine it runs on. A golden
// recorded in one region is not a golden, and an assertion about "9 Jun" is a
// different assertion in Chicago.
//
// So the tests get a region of their own: the one the screenshots were recorded
// in, stated here rather than inherited. Nothing else pins it, and the library
// itself goes on following whatever the user's device says.
tasks.withType<Test>().configureEach {
    systemProperty("user.language", "en")
    systemProperty("user.country", "AU")
}

/**
 * Spread test classes across the machine's cores.
 *
 * Gradle runs every test in one forked JVM unless told otherwise, which for a
 * suite whose cost is rasterisation means one core busy and the rest idle. On a
 * standard GitHub runner that is four cores doing the work of one, and the bill
 * is wall-clock minutes.
 *
 * Capped at four rather than left at `availableProcessors`: each fork holds its
 * own Skia and renders full-window images, so the ceiling is memory rather than
 * CPU, and a bigger machine would start swapping before it started helping.
 * `maxHeapSize` is stated for the same reason — the default is small enough that
 * a fork rendering 1440x1400 can spend its time collecting garbage.
 *
 * Note this distributes **classes**. A single test class is a single fork
 * however long it runs, which is why `SiteRenderTest` is four classes.
 */
tasks.withType<Test>().configureEach {
    maxParallelForks = minOf(4, Runtime.getRuntime().availableProcessors())
    maxHeapSize = "1g"
}

// ---------------------------------------------------------------------------
// Two checks on the goldens themselves
// ---------------------------------------------------------------------------
//
// The screenshot suite is what most of the library's rendering claims rest on,
// and twice now it has been wrong about its own coverage: a class that recorded
// mismatches and never read them, and — as far as anything here could tell — a
// golden nobody draws any more. Both are failures of the mechanism rather than
// of any component, so both get a check.

val screenshotGoldens = layout.projectDirectory.dir("screenshots")

/**
 * Every test file that renders a golden also asserts on the result.
 *
 * `Screenshot.render` *records* a mismatch and carries on — deliberately, so one
 * run reports all four schemes rather than the first — and `assertAllMatched`,
 * called from an `@AfterTest`, is the only thing that turns the record into a
 * failure. A class that renders and never asserts is therefore green whatever it
 * draws. `CatalogScreenshotTest` was exactly that for the whole life of the
 * suite: twenty-one goldens, no assertion, and nothing to notice.
 *
 * The rule is per *file* rather than per class, because the `@AfterTest` often
 * sits on a base class or beside a companion in the same file, and chasing
 * inheritance would mean parsing Kotlin for a check whose whole value is that it
 * is cheap enough to always run.
 *
 * ### Comments are stripped first, and that is not a nicety
 *
 * `HostFontScaleTest` names `Screenshot.render` in its KDoc to explain why it
 * measures ink instead of goldening — a naive `grep` reports it as the tenth
 * offender, and an offender you have to explain away every run is a check people
 * learn to ignore.
 *
 * Same two expressions `docs/check-components.py` uses. Block comments are
 * blanked keeping their newlines and line comments deleted outright, which is
 * more than a filename-only report needs — it is what lets the next rule added
 * here report a line number that is the one in the file.
 */
val checkScreenshotAssertions = tasks.register("checkScreenshotAssertions") {
    group = "verification"
    description = "Fails if a test file renders a golden without asserting that it matched."

    val sources = layout.projectDirectory.dir("src/jvmTest/kotlin")
    inputs.dir(sources).withPropertyName("jvmTestSources")
    // Nothing is produced; declaring a report keeps the task cacheable rather
    // than always out of date, which matters because `check` depends on it.
    val report = layout.buildDirectory.file("reports/screenshot-assertions.txt")
    outputs.file(report)

    val sourceRoot = sources.asFile
    val reportFile = report.get().asFile

    doLast {
        val blockComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val lineComment = Regex("""(?<!:)//[^\n]*""")

        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .filter { file ->
                val code = lineComment.replace(
                    blockComment.replace(file.readText()) { m -> "\n".repeat(m.value.count { it == '\n' }) },
                    "",
                )
                "Screenshot.render" in code && "Screenshot.assertAllMatched" !in code
            }
            .map { it.name }
            .toList()

        reportFile.apply { parentFile.mkdirs() }.writeText(
            if (offenders.isEmpty()) "ok\n" else offenders.joinToString("\n", postfix = "\n")
        )

        if (offenders.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("${offenders.size} test file(s) render goldens without asserting they matched:")
                    offenders.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("`Screenshot.render` records a mismatch and carries on. Add:")
                    appendLine()
                    appendLine("    @AfterTest")
                    appendLine("    fun allGoldensMatched() = Screenshot.assertAllMatched()")
                    appendLine()
                    appendLine("Without it the class is green whatever it draws.")
                }
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkScreenshotAssertions)
}

/**
 * Names a golden that nothing renders any more.
 *
 * Two hundred and twenty-five committed PNGs and, until this, no way to tell
 * which of them anybody still draws. A golden whose render was deleted or
 * renamed is not merely clutter — it is a picture of a component as it was on
 * some past day, sitting in the directory a reviewer looks at to decide what the
 * library looks like today.
 *
 * ### Why the names come from the run rather than the source
 *
 * They are built at runtime: `button-primary` appears nowhere in this
 * repository, because the per-component pass composes the slug from the
 * registry. Re-deriving them by parsing Kotlin would mean reimplementing the
 * loops that generate them, and a reimplementation that drifts reports orphans
 * that are not orphans. So the suite reports what it actually rendered, and this
 * diffs that against what is committed.
 *
 * Four forks mean four manifests; `Screenshot` writes one per pid and this
 * unions them. Everything that could make that union *incomplete* — and so turn
 * a live golden into a false orphan — is caught by the status file `jvmTest`
 * writes at the end of a run:
 *
 * | run | status | this check |
 * |---|---|---|
 * | full suite | `complete` | reports orphans |
 * | `--tests X` | `filtered: …` | skips, and says so |
 * | a test failed | absent — `doLast` does not run on a failed `Test` | skips |
 * | up to date / from cache | restored with the rest of the outputs | reports against the last real run, which its inputs guarantee is current |
 *
 * That last row is why the manifests are declared as outputs of `jvmTest`: with
 * the goldens and the test sources both inputs, "up to date" means the recorded
 * run is still the truth, and Gradle restores or re-runs accordingly. Left
 * undeclared they would survive a `FROM-CACHE` run that never rendered anything
 * and describe whatever build happened to touch this directory last.
 *
 * Recording under `-Pkontour.screenshots.update=true` needs no special case: a
 * newly written golden goes through the same `render` call, so it is in the
 * manifest as well as on disk.
 */
val checkGoldenOrphans = tasks.register("checkGoldenOrphans") {
    group = "verification"
    description = "Names committed goldens that :ui-catalog:jvmTest no longer renders."

    val manifests = layout.buildDirectory.dir("screenshot-manifests").get().asFile
    val goldens = screenshotGoldens.asFile
    // A finalizer of a test task runs at most as often as the tests do, and the
    // work is a directory listing plus a handful of short text files. Up-to-date
    // checking would cost more than it saves, and its inputs are another task's
    // outputs written during the same build.
    outputs.upToDateWhen { false }

    doLast {
        val statusFile = File(manifests, "status")
        if (!statusFile.isFile) {
            logger.lifecycle("checkGoldenOrphans: skipped — :ui-catalog:jvmTest did not finish a run.")
            return@doLast
        }
        val status = statusFile.readText().trim()
        if (status != "complete") {
            logger.lifecycle("checkGoldenOrphans: skipped — $status")
            return@doLast
        }

        val rendered = manifests.listFiles().orEmpty()
            .filter { it.extension == "txt" }
            .flatMap { it.readLines() }
            .filter { it.isNotBlank() }
            .toSet()

        val committed = goldens.walkTopDown()
            .filter { it.isFile && it.extension == "png" }
            .map { it.relativeTo(goldens).path.removeSuffix(".png").replace(File.separatorChar, '/') }
            .toSortedSet()

        val orphans = committed - rendered
        if (orphans.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("${orphans.size} committed golden(s) are no longer rendered by any test:")
                    orphans.forEach { appendLine("  - screenshots/$it.png") }
                    appendLine()
                    appendLine("${rendered.size} name(s) were rendered by this run.")
                    appendLine("Delete the files above, or restore the render that used to draw them.")
                }
            )
        }
    }
}

tasks.named<Test>("jvmTest") {
    val manifests = layout.buildDirectory.dir("screenshot-manifests").get().asFile
    systemProperty("kontour.screenshots.manifestDir", manifests.path)
    // Declared so Gradle treats the manifests like any other result of this
    // task: restored on a cache hit, rebuilt when the goldens or the tests move,
    // never left behind to describe a run that no longer happened.
    outputs.dir(manifests).withPropertyName("screenshotManifests")

    doFirst {
        manifests.deleteRecursively()
        manifests.mkdirs()
    }

    doLast {
        // `this` is the task, supplied by the Kotlin DSL's `doLast { }` receiver
        // extension rather than captured from the configuration block.
        val filter = (this as Test).filter
        // `--tests` lands in `commandLineIncludePatterns`, which is on the
        // implementation rather than the `TestFilter` interface. Read
        // reflectively so a Gradle upgrade that moves it degrades to an honest
        // "I could not tell", printed by the check, rather than to a silent pass
        // — or, worse, to a confident orphan report built from a partial run.
        @Suppress("UNCHECKED_CAST")
        val commandLine = runCatching {
            filter.javaClass.getMethod("getCommandLineIncludePatterns").invoke(filter) as Set<String>
        }
        File(manifests, "status").writeText(
            when {
                commandLine.isFailure ->
                    "unknown: could not read the command-line filter from ${filter.javaClass.name}"
                commandLine.getOrThrow().isNotEmpty() ->
                    "filtered: --tests ${commandLine.getOrThrow().joinToString(" ")}"
                filter.includePatterns.isNotEmpty() ->
                    "filtered: includePatterns ${filter.includePatterns.joinToString(" ")}"
                filter.excludePatterns.isNotEmpty() ->
                    "filtered: excludePatterns ${filter.excludePatterns.joinToString(" ")}"
                else -> "complete"
            }
        )
    }

    finalizedBy(checkGoldenOrphans)
}
