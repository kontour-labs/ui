import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// ---------------------------------------------------------------------------
// A very small Kotlin signature reader
// ---------------------------------------------------------------------------
//
// Shared by `checkApiConventions` and `checkKdocSamples` at the bottom of this
// file. Both need the same thing — given a declaration, what does it take? —
// and both had grown their own answer, one line-based and one bracket-based.
// The line-based one could not see a declaration that was indented, and 130 of
// this module's 391 declarations are: every method on every builder scope,
// which is precisely where the naming conventions drift.
//
// A top-level `object` rather than a script function: a `doLast` lambda that
// referenced a script function would capture the script instance and break the
// configuration cache, which is on. A singleton is a static reference and does
// not. Verified rather than assumed, before any of this was written.
//
// This is not a parser. It reads far enough to answer "what is this called and
// what does it take", which is all either rule set asks.
object KotlinSignatures {

    /** One declared parameter, or one primary-constructor property. */
    data class Parameter(
        val name: String,
        val type: String,
        /** The default expression's source text, or `null` when there is none. */
        val default: String?,
    ) {
        val optional: Boolean get() = default != null
    }

    enum class Kind { Function, Class }

    data class Declaration(
        val kind: Kind,
        val name: String,
        /** The extension receiver, for `fun ListItemScope.leading(…)`. */
        val receiver: String?,
        /** The type this is declared inside, for `ButtonGroupScope.action`. */
        val enclosing: String?,
        val visibility: String,
        val isComposable: Boolean,
        val indent: Int,
        val line: Int,
        val parameters: List<Parameter>,
    ) {
        /** How to name this in a message — as close to how it is called as it gets. */
        val qualified: String
            get() = listOfNotNull(receiver ?: enclosing, name).joinToString(".")

        /** Public unless it says otherwise — the Kotlin default. */
        val isPublic: Boolean get() = visibility == "public"

        /**
         * A component, as opposed to a state holder or a plain function.
         *
         * The distinction is load-bearing for the conventions: `OverlayEntry`
         * takes `onDismiss` and is right to, because the host owns whether the
         * entry is showing and by the time the callback runs there is nothing
         * left to decline. A *component* is handed the state by its caller, so
         * it can only ask — `onDismissRequest`.
         */
        val isComponent: Boolean get() = kind == Kind.Function && isComposable
    }

    /** Index of the `)` closing the `(` at [start]. */
    fun balanced(text: String, start: Int): Int {
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return i
            }
        }
        return text.length
    }

    /**
     * The same text with every comment blanked to spaces.
     *
     * Length-preserving, because every index the callers compute is an offset
     * into the result and they read the original at those offsets.
     *
     * Not cosmetic. A parameter carrying its own KDoc is common here and prose
     * contains commas — "…accounted for them, or when this bar is not at the
     * top of the window" split `TopBar`'s parameter list *inside* a comment, so
     * `windowInsets` and everything after it was invisible, and every sample
     * naming one was reported as naming something that does not exist. Four of
     * twelve findings on the first run were this.
     */
    fun withoutComments(text: String): String {
        val out = StringBuilder(text.length)
        var block = 0
        var line = false
        var i = 0
        while (i < text.length) {
            val two = if (i + 1 < text.length) text.substring(i, i + 2) else ""
            when {
                !line && two == "/*" -> { block++; out.append("  "); i += 2 }
                block > 0 && two == "*/" -> { block--; out.append("  "); i += 2 }
                block == 0 && !line && two == "//" -> { line = true; out.append("  "); i += 2 }
                else -> {
                    val ch = text[i]
                    if (ch == '\n') line = false
                    out.append(if ((block > 0 || line) && ch != '\n') ' ' else ch)
                    i++
                }
            }
        }
        return out.toString()
    }

    /**
     * The same text with everything nested blanked, so only depth-zero
     * punctuation survives. Comments go first, then `->` is masked to two
     * spaces: counting `>` as a closing bracket drives the depth negative on
     * the first `() -> Unit` parameter and blanks the rest of the signature,
     * which reports every parameter but the first as missing and looks exactly
     * like a real finding.
     */
    fun topLevel(raw: String): String {
        val masked = withoutComments(raw).replace("->", "  ")
        val out = StringBuilder(masked.length)
        var depth = 0
        for (ch in masked) {
            when (ch) {
                '(', '[', '{', '<' -> depth++
                ')', ']', '}', '>' -> depth--
            }
            out.append(if (depth == 0) ch else ' ')
        }
        return out.toString()
    }

    /**
     * The parameters inside a declaration's brackets, in declared order.
     *
     * Order matters as much as the names: a call may satisfy a parameter
     * positionally, and a convention may require one to sit in a given place.
     */
    fun parameters(inner: String): List<Parameter> {
        val flat = topLevel(inner)
        val cuts = flat.indices.filter { flat[it] == ',' }
        val bounds = listOf(-1) + cuts + listOf(flat.length)
        val name = Regex("""([A-Za-z_]\w*)\s*$""")
        return bounds.zipWithNext().mapNotNull { (from, to) ->
            val chunk = inner.substring(from + 1, to)
            val flatChunk = topLevel(chunk)
            val colon = flatChunk.indexOf(':')
            if (colon < 0) return@mapNotNull null
            val declared = name.find(flatChunk.substring(0, colon))?.groupValues?.get(1)
                ?: return@mapNotNull null
            val equals = flatChunk.indexOf('=', colon)
            val end = if (equals >= 0) equals else chunk.length
            Parameter(
                name = declared,
                type = chunk.substring(colon + 1, end).trim(),
                default = if (equals >= 0) chunk.substring(equals + 1).trim() else null,
            )
        }
    }

    private val functionHeader = Regex(
        """^([ \t]*)(?:(public|internal|private)\s+)?""" +
            """(?:(?:override|suspend|inline|operator|infix|expect|actual|tailrec)\s+)*""" +
            """fun\s+(?:<[^>]*>\s*)?(?:([A-Za-z_][\w.]*)\.)?([A-Za-z_]\w*)\s*\(""",
        RegexOption.MULTILINE,
    )

    private val classHeader = Regex(
        """^([ \t]*)(?:(public|internal|private)\s+)?""" +
            """(?:(?:abstract|open|sealed|data|value|inner|expect|actual)\s+)*""" +
            """class\s+([A-Za-z_]\w*)\s*(?:<[^>]*>\s*)?(?:internal\s+)?(?:constructor\s*)?\(""",
        RegexOption.MULTILINE,
    )

    /**
     * Every function and every primary constructor in a file, at any indent.
     *
     * Comments are blanked before matching, so a `fun` written inside one — in
     * a KDoc sample, most often — is not read as a declaration.
     */
    private val typeHeader = Regex(
        """^([ \t]*)(?:(?:public|internal|private|abstract|open|sealed|data|value|inner|expect|actual|enum|annotation)\s+)*""" +
            """(?:class|interface|object)\s+([A-Za-z_]\w*)""",
        RegexOption.MULTILINE,
    )

    /** One declared type: what it is called, and what it is annotated with. */
    data class DeclaredType(
        val name: String,
        val visibility: String,
        val annotations: List<String>,
        val line: Int,
    ) {
        val isPublic: Boolean get() = visibility == "public"
    }

    /** Every class, interface and object in a file, at any indent. */
    fun types(text: String): List<DeclaredType> {
        val clean = withoutComments(text)
        return typeHeader.findAll(clean).map { match ->
            val head = clean.substring(0, match.range.first)
            val modifiers = match.value
            DeclaredType(
                name = match.groupValues[2],
                visibility = listOf("private", "internal")
                    .firstOrNull { " $it " in " $modifiers " } ?: "public",
                annotations = head.trimEnd('\n').split("\n").asReversed()
                    .takeWhile { it.trimStart().startsWith("@") }
                    .map { it.trim() },
                line = head.count { it == '\n' } + 1,
            )
        }.toList()
    }

    fun declarations(text: String): List<Declaration> {
        val clean = withoutComments(text)
        val found = mutableListOf<Declaration>()

        // Where each type starts and how far it is indented, so an indented
        // declaration can say which type it belongs to. `action` on its own is
        // not a name anyone can look up; `ButtonGroupScope.action` is.
        val types = typeHeader.findAll(clean)
            .map { Triple(it.range.first, it.groupValues[1].length, it.groupValues[2]) }
            .toList()

        fun add(kind: Kind, match: MatchResult, indent: String, visibility: String, receiver: String?, name: String) {
            val opening = clean.indexOf('(', match.range.last - 1)
            if (opening < 0) return
            // The innermost type that opened before this and is less indented.
            val enclosing = types.lastOrNull { (at, depth, _) ->
                at < match.range.first && depth < indent.length
            }?.third
            // Annotations sit on the lines directly above; walk back over the
            // run of them. `@Composable` is what separates a component from a
            // state holder, and several rules below turn on that.
            val annotations = clean.substring(0, match.range.first)
                .trimEnd('\n')
                .split("\n")
                .asReversed()
                .takeWhile { it.trimStart().startsWith("@") }
            found += Declaration(
                kind = kind,
                name = name,
                receiver = receiver,
                enclosing = enclosing,
                visibility = visibility.ifEmpty { "public" },
                isComposable = annotations.any { it.trimStart().startsWith("@Composable") },
                indent = indent.length,
                line = clean.substring(0, match.range.first).count { it == '\n' } + 1,
                parameters = parameters(clean.substring(opening + 1, balanced(clean, opening))),
            )
        }

        functionHeader.findAll(clean).forEach { m ->
            add(Kind.Function, m, m.groupValues[1], m.groupValues[2], m.groupValues[3].ifEmpty { null }, m.groupValues[4])
        }
        classHeader.findAll(clean).forEach { m ->
            add(Kind.Class, m, m.groupValues[1], m.groupValues[2], null, m.groupValues[3])
        }
        return found.sortedBy { it.line }
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.dokka)
    `maven-publish`
}

// ---------------------------------------------------------------------------
// Publishing
// ---------------------------------------------------------------------------
//
// A release is `git tag v0.2.0` and nothing else. Three sources, in this order:
//
//  1. `-Pkontour.version=…`, for a deliberate publish that is not a tag.
//  2. The tag being built — and **only** a tag.
//  3. `0.1.0-SNAPSHOT`, for everything else.
//
// Rule 2 used to read `GITHUB_REF_NAME` without asking what kind of ref it was,
// which meant a branch build took the *branch name* as its version. This would
// have published `io.kontour:ui:claude/compose-multiplatform-components-lz1c97`
// — a coordinate with a slash in it — the first time anything ran `publish`
// outside a tag, while the comment above it claimed a branch build was a
// snapshot. `GITHUB_REF_TYPE` is the field that answers the question.
//
// The result is then checked, because the failure mode is silent: an unpublish
// on a package registry is a support ticket, and the check costs one regex.
//
// Everything is read through `providers`, never `System.getenv`. The
// configuration cache is on (`gradle.properties`), and a direct environment read
// at configuration time is invisible to it: the cached entry would be reused
// with last run's token baked in.
//
// **This publishes from Linux.** Kotlin 2.x cross-compiles Apple *klibs* — which
// is what a library consumer resolves — so a Linux publish uploads the complete
// set, verified by publishing locally and reading `native_targets=ios_arm64` out
// of the klib manifest. What still needs Xcode is *linking a framework*, and
// that happens in the consuming app, not here.
group = "io.kontour"
version = providers.gradleProperty("kontour.version")
    .orElse(
        providers.environmentVariable("GITHUB_REF_TYPE")
            .zip(providers.environmentVariable("GITHUB_REF_NAME")) { type, name ->
                if (type == "tag") name.removePrefix("v") else ""
            }
            .filter { it.isNotEmpty() }
    )
    .orElse("0.1.0-SNAPSHOT")
    .map { candidate ->
        require(Regex("""^\d+\.\d+\.\d+(?:-[0-9A-Za-z.]+)?$""").matches(candidate)) {
            "`$candidate` is not a publishable version. A release tag is `v1.2.3`, " +
                "optionally with a `-rc1`-style suffix; anything else has to come " +
                "through -Pkontour.version. This check exists because the alternative " +
                "is noticing after the upload."
        }
        candidate
    }
    .get()

/**
 * What this build would publish as.
 *
 *     ./gradlew :ui:coordinate
 *
 * The version comes from three sources with a precedence between them, and
 * without this there is no way to ask which one won short of running a publish
 * and reading the upload. That is how a branch build came to be publishable as
 * `io.kontour:ui:some/branch` unnoticed.
 */
tasks.register("coordinate") {
    group = "help"
    description = "Prints the group:artifact:version this build would publish as."
    // Captured at configuration time: a `doLast` that read `project` would hold
    // the project reference and break the configuration cache, which is on.
    val coordinate = "${project.group}:${project.name}:${project.version}"
    doLast { println(coordinate) }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/kontour-labs/ui")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }

    publications.withType<MavenPublication>().configureEach {
        pom {
            name = "Kontour UI"
            description = "A Compose Multiplatform design system built on Foundation, without Material."
            url = "https://github.com/kontour-labs/ui"
            licenses {
                license {
                    name = "Proprietary"
                    comments = "All rights reserved. The bundled Outfit fonts are SIL OFL 1.1 " +
                        "and redistributable in this artifact; see ui/licenses/Outfit-OFL.txt."
                }
            }
            scm {
                url = "https://github.com/kontour-labs/ui"
                connection = "scm:git:https://github.com/kontour-labs/ui.git"
            }
        }
    }
}

// ---------------------------------------------------------------------------
// The API reference
// ---------------------------------------------------------------------------
//
//     ./gradlew :ui:dokkaGenerateHtml     # build/dokka/html
//
// Generated from the KDoc that is already there, and it earns its place by being
// *complete* in the one way the hand-written pages are not: every public symbol
// appears, whether or not anybody remembered to write it up.
//
// The two do different jobs and neither replaces the other. Dokka knows every
// signature and no reasons; `docs/using/` carries the comparisons, the "reach
// for this instead", and the bug histories — the things that are true of a
// component but not visible in it.
//
// It is not published anywhere yet. Generating it in CI is what stops it rotting
// before it is: Dokka fails on a malformed `@param` or a `[Link]` to something
// that no longer exists, which is a class of KDoc defect nothing else here sees.
dokka {
    // A warning here is a broken cross-reference in the KDoc — a `[Link]` to a
    // symbol that was renamed or deleted — and without this Dokka prints it and
    // succeeds, which makes the CI step an artifact upload rather than a gate.
    // Verified by pointing a link at a symbol that does not exist.
    dokkaPublications.configureEach {
        failOnWarning = true
    }

    // Not "Kontour UI". The module name becomes a *directory* in the output,
    // slugified — a display name with a space in it produced `-kontour -u-i/`
    // and every link on the index carried an unencoded space. This matches the
    // root project and the repository, and stays a legal path segment.
    moduleName = "kontour-ui"
    // The same value the artifact gets, so a page and a jar cannot disagree
    // about which release they are.
    moduleVersion = version.toString()

    dokkaSourceSets.configureEach {
        // The module and package overviews. Without this every package index is
        // a bare list of symbols with no statement of what the package is for,
        // and the generated site has no way back to the pages that explain the
        // reasoning — which is the half of the documentation Dokka cannot
        // produce and should not pretend to replace.
        includes.from("Module.md")

        // The library is common code first; the JDK is an implementation detail
        // of one of its five targets. Linking to it makes `String` on a page
        // about a Compose Multiplatform component point at java.lang, and the
        // lookup needs the network at build time — which then fails on any
        // runner that cannot reach docs.oracle.com and prints six identical
        // warnings while succeeding anyway.
        enableJdkDocumentationLink = false

        sourceLink {
            localDirectory = layout.projectDirectory.dir("src").asFile
            remoteUrl("https://github.com/kontour-labs/ui/tree/main/ui/src")
            remoteLineSuffix = "#L"
        }
    }
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
            //
            // `api`, not `implementation`: these types appear in the public API of
            // nearly every component — Modifier, Composable, Color, ImageVector —
            // so a consumer cannot call into `:ui` without them on its own compile
            // classpath.
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)

            // Also `api`: the date/time components take LocalDate and LocalTime.
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)

            // `implementation`: only the handful of structural glyphs in
            // `foundation/SystemIcons.kt` are used from here, and none of them
            // appear in a public signature. Everything a *caller* draws is still
            // passed in as an ImageVector, so an app is free to use another set.
            // Unused vectors are separate top-level declarations and get
            // stripped by R8 and by the JS/Wasm DCE.
            implementation(libs.bundles.icons)
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

// ---------------------------------------------------------------------------
// API conventions
// ---------------------------------------------------------------------------
//
// The rules a reader can rely on when they have never seen a component before.
// They were established by hand in one sweep, and this is what stops them
// drifting apart again one component at a time — the same job `checkNoMaterial`
// does for the dependency graph.
//
// Run on its own with:
//
//     ./gradlew :ui:checkApiConventions
//
val checkApiConventions = tasks.register("checkApiConventions") {
    group = "verification"
    description = "Fails if a public component breaks the library's API conventions."

    val sources = layout.projectDirectory.dir("src/commonMain/kotlin")
    inputs.dir(sources).withPropertyName("commonMain")
    // Nothing is produced; this makes the task cacheable rather than always out
    // of date, which matters because `check` depends on it.
    outputs.file(layout.buildDirectory.file("reports/api-conventions.txt"))

    val report = layout.buildDirectory.file("reports/api-conventions.txt")

    doLast {
        // Words that mean one thing each. A second name for the same idea is how
        // an API stops feeling like one library.
        val bannedNames = mapOf(
            "supportingText" to "use `supporting` — it pairs with `label`, which carries no suffix either",
            "headline" to "use `label` — the short name of a control",
            "isEnabled" to "`enabled` is the component's own state; name a predicate for what it decides",
        )

        // Banned on components only. A state holder that *owns* the visibility
        // is telling rather than asking, and `OverlayEntry.onDismiss` is right
        // for exactly that reason — it runs after the host has already removed
        // the entry, so there is nothing left to decline. This rule was invisible
        // until the gate learned to read classes, and the first thing it found
        // was that deliberate exception, which is why it is stated this way
        // rather than suppressed at the site.
        val bannedOnComponents = mapOf(
            "onDismiss" to "components take `onDismissRequest` — the caller owns `visible` and is being asked, not told",
        )

        val problems = mutableListOf<String>()

        // Every `internal` type the module declares. A public default that names
        // one is a default the caller cannot write: it renders in the API
        // reference as `closeIcon: ImageVector = SystemIcons.Close` and there is
        // no `SystemIcons` to reach for.
        val internalTypes = sources.asFileTree.matching { include("**/*.kt") }
            .flatMap { file -> KotlinSignatures.types(file.readText()) }
            .filter { it.visibility == "internal" }
            .map { it.name }
            .toSet()

        sources.asFileTree.matching { include("**/*.kt") }.forEach { file ->
            val text = file.readText()
            val rel = file.relativeTo(sources.asFile).path

            // No user-visible English welded into a default.
            //
            // Forty-nine of these existed, and the only way to change one was to
            // pass it at every call site — which for a library shared across
            // projects means an app ships English it never chose and cannot find.
            // They live in `Theme.strings` now, and a default reads from there.
            //
            // Two of the forty-nine were missed by the survey that found the
            // rest, because it looked for `name: String = "…"` and they were
            // spelled `String? = "Cancel"` and `val copy: String = "Copy"` in a
            // data class. This rule does not have that problem, which is the
            // argument for having it rather than trusting the sweep.
            //
            // `Theme.strings.x` and a non-literal expression both pass. So does
            // `Strings.kt` itself, which is where the words are supposed to be.
            if (!rel.endsWith("theme/Strings.kt")) {
                KotlinSignatures.declarations(text)
                    .filter { it.isPublic }
                    .forEach { declaration ->
                        declaration.parameters
                            .filter { Regex("""^String\??$""").matches(it.type.trim()) }
                            .forEach { parameter ->
                                val default = parameter.default?.trim().orEmpty()
                                if (Regex("""^"[^"]*[A-Za-z]{2}[^"]*"$""").matches(default)) {
                                    problems += "$rel:${declaration.line} :: " +
                                        "${declaration.qualified}: `${parameter.name}` defaults to " +
                                        "the literal $default — put the word in `Theme.strings` " +
                                        "and default from there"
                                }
                            }
                    }
            }

            // Every public scope carries both markers.
            //
            // `@LayoutScopeMarker` is a `@DslMarker`, and it is the one doing
            // work: without it, an inner scope's block can implicitly call an
            // *outer* scope's members, so `supporting { … }` written inside a
            // `leading { … }` silently attaches to the row rather than failing.
            // Seven of twelve scopes were missing it, and the nesting ones —
            // `MenuScope.submenu`, `NavDrawerScope.section` — needed it most.
            //
            // `@Stable` is the mirror image: it was on five, missing from seven,
            // and true of all of them (no scope has public mutable state). It
            // claims nothing false and it means the pair can be one rule rather
            // than two with exceptions.
            KotlinSignatures.types(text)
                .filter { it.isPublic && it.name.endsWith("Scope") }
                .forEach { type ->
                    listOf("@LayoutScopeMarker", "@Stable")
                        .filterNot { it in type.annotations }
                        .forEach { missing ->
                            problems += "$rel:${type.line} :: ${type.name}: a scope must " +
                                "carry `$missing`"
                        }
                }

            // Every public declaration, at any indent, one line or many.
            //
            // It used to be top-level multi-line functions only — 153 of this
            // module's 391 declarations. The 130 indented ones it could not see
            // are every method on every builder scope, and those are exactly
            // where the naming drifts: `MenuScope.item`, `ButtonGroupScope.action`,
            // `KeyValueScope.row` and `NavDrawerScope.destination` are four names
            // for the same idea, and the gate reported no problems throughout.
            KotlinSignatures.declarations(text)
                .filter { it.isPublic && it.parameters.isNotEmpty() }
                .forEach { declaration ->
                    val params = declaration.parameters
                    val names = params.map { it.name }
                    val where = "$rel:${declaration.line} :: ${declaration.qualified}"

                    bannedNames.forEach { (banned, why) ->
                        if (banned in names) problems += "$where: parameter `$banned` — $why"
                    }
                    if (declaration.isComponent) {
                        bannedOnComponents.forEach { (banned, why) ->
                            if (banned in names) problems += "$where: parameter `$banned` — $why"
                        }
                    }

                    // `modifier` is the first optional parameter, so everything
                    // before it is required and a caller can always reach it the
                    // same way.
                    val modifierIndex = names.indexOf("modifier")
                    if (modifierIndex >= 0) {
                        val firstDefaulted = params.indexOfFirst { it.optional }
                        if (firstDefaulted != modifierIndex) {
                            problems += "$where: `modifier` must be the first optional parameter " +
                                "(found ${names[firstDefaulted]} before it)"
                        }

                        // `enabled` is the second optional parameter, always.
                        // A rule with an exception is a rule nobody can apply
                        // without looking it up — an earlier draft let `enabled`
                        // follow an optional `onClick` instead, and it misfired
                        // on the first component with two callbacks.
                        val enabledIndex = names.indexOf("enabled")
                        if (enabledIndex >= 0 && enabledIndex != modifierIndex + 1) {
                            problems += "$where: `enabled` must come directly after " +
                                "`modifier` (found it after ${names[enabledIndex - 1]})"
                        }
                    }

                    // Everything between `modifier` and the trailing slots is
                    // defaulted.
                    //
                    // The older rule only checked where the *first* default sat,
                    // so a required parameter further down slipped through: a
                    // caller then has to supply something from the middle of the
                    // optional run, and every default before it has to be named
                    // at the call site to reach it.
                    //
                    // Slots are exempt because they come last and are usually
                    // required — that is the convention, not a violation of it.
                    if (modifierIndex >= 0) {
                        val slot = Regex("""(?:->|Scope\.\(\))""")
                        val firstTrailingSlot = params.indices.reversed()
                            .takeWhile { slot.containsMatchIn(params[it].type) }
                            .minOrNull() ?: params.size
                        params.withIndex()
                            .filter { (i, p) ->
                                i > modifierIndex && i < firstTrailingSlot && !p.optional
                            }
                            .forEach { (_, parameter) ->
                                problems += "$where: `${parameter.name}` comes after " +
                                    "`modifier` and has no default — everything between " +
                                    "`modifier` and the trailing slots is optional"
                            }
                    }

                    // A callback named `onXChange` is half of a pair, so the
                    // other half has to be there.
                    //
                    // This is what `onValuesChange`, `onTimeChange` and
                    // `onPageChange` all failed: each named a change to
                    // something the component did not take. It catches the
                    // shape rather than the four names, so the next one is
                    // caught too.
                    if (declaration.isComponent) {
                        Regex("""^on([A-Z]\w*)Change$""").let { paired ->
                            names.mapNotNull { paired.find(it) }.forEach { match ->
                                val noun = match.groupValues[1]
                                val subject = noun.replaceFirstChar { it.lowercase() }
                                // `is<X>` counts. A predicate is the hoisted
                                // state in function form — `CalendarMonth` takes
                                // `isSelected: (LocalDate) -> Boolean` because
                                // which dates are selected is the caller's to
                                // decide, and that is a pair like any other.
                                if (subject !in names && "is$noun" !in names) {
                                    problems += "$where: `${match.value}` has no `$subject` " +
                                        "beside it — a callback named for a change is half " +
                                        "of a pair, and a notification should not borrow " +
                                        "the shape"
                                }
                            }
                        }

                        // A caller-owned overlay says `visible`, like `Dialog`.
                        // `expanded` is for something that grows in place and
                        // reports through `onExpandedChange` — an accordion, a
                        // drawer group — and the two were mixed.
                        if ("expanded" in names && "onDismissRequest" in names) {
                            problems += "$where: takes `expanded` with `onDismissRequest` " +
                                "— an overlay the caller owns is `visible`; `expanded` " +
                                "pairs with `onExpandedChange`"
                        }
                    }

                    // No `internal` type in a public default expression.
                    params.forEach { parameter ->
                        val leading = Regex("""^([A-Z]\w*)\b""")
                            .find(parameter.default?.trim().orEmpty())
                            ?.groupValues?.get(1)
                        if (leading != null && leading in internalTypes) {
                            problems += "$where: `${parameter.name}` defaults to " +
                                "`${parameter.default?.trim()}`, and `$leading` is internal " +
                                "— a caller cannot write this default"
                        }
                    }

                    // No fully-qualified `androidx.*` in a signature.
                    //
                    // It compiles and it reads as noise, but the reason it
                    // matters is downstream: this is what the generated API
                    // reference prints, so one unimported type makes a column
                    // of a table three times as wide as the rest.
                    params.filter { "androidx." in it.type }.forEach { parameter ->
                        problems += "$where: `${parameter.name}` is typed " +
                            "`${parameter.type.trim()}` — import it"
                    }

                    // `interactionSource` goes last, after everything except the
                    // slots — a caller overrides it rarely and reads past it often.
                    // Only when it is an override. `Modifier.focusRing` takes one
                    // as its subject, and a required parameter is not something a
                    // caller reads past.
                    //
                    // A *builder* counts as a slot too. `ListItemScope.() -> Unit`
                    // carries no `@Composable` — it collects composable content
                    // rather than being it — but it is the trailing lambda a
                    // caller writes their content in, so it belongs in the same
                    // place and under the same name.
                    val builderType = Regex("[A-Za-z]+Scope\\.\\(\\)\\s*->")
                    val interactionIndex = names.indexOfFirst {
                        it == "interactionSource"
                    }.takeIf { it >= 0 && params[it].optional } ?: -1
                    if (interactionIndex >= 0) {
                        val trailing = params.drop(interactionIndex + 1)
                            .filterNot {
                                "Composable" in it.type || builderType.containsMatchIn(it.type)
                            }
                        if (trailing.isNotEmpty()) {
                            problems += "$where: `interactionSource` must come after every " +
                                "non-slot parameter (found ${trailing.joinToString { it.name }} after it)"
                        }
                    }
                }
        }

        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                if (problems.isEmpty()) "No API convention problems.\n"
                else problems.joinToString("\n", postfix = "\n")
            )
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("API conventions broken in ${problems.size} place(s):")
                    problems.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("The conventions are in docs/building/contributing.md.")
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// KDoc samples
// ---------------------------------------------------------------------------
//
// There are ~130 Kotlin blocks in this module's KDoc and nothing ever compiled
// one. They are the first thing anyone reads about a component, and Round 3's
// slots conversion rewrote every call site in the library while leaving the
// samples in the comments above them — so a good number documented an API that
// had not existed for months. A sample that does not compile is worse than no
// sample, because it gets copied.
//
// This is not a compiler, and it deliberately answers two questions — the ones
// that actually went wrong:
//
//     for every `Component(name = …)` in a sample, is `name` a parameter of
//     `Component`?
//     and is every parameter `Component` does not default supplied, by name,
//     by position, or as the trailing lambda?
//
// The first found 29 defects across 13 samples on its first run. The second
// found six more that had survived it: a call whose arguments are all real
// parameters but which is missing the content slot entirely, which is exactly
// the shape a pre-slots sample takes — `MenuItem("Copy", onClick = ::copy)`
// names nothing wrong, it just no longer passes the content.
//
// Receivers, types and the local variables a fragment refers to are out of
// scope; checking those needs a real frontend.
//
// So is a third question that looks adjacent and is not answerable here: does
// every capitalised call resolve to something that exists? 27 names in the
// current samples resolve to nothing this module declares, and while a dozen
// are Compose (`Box`, `LazyColumn`, `Color`), the rest are deliberate
// placeholders standing in for the caller's own composables — `Logo`,
// `StopRow`, `AppRoot`. Telling those apart from a component that has been
// deleted needs an allowlist, and an allowlist is where a stale name would be
// added to make the build pass. The compiler answers it for free once samples
// live in a source set (Round 7 stage 5b); until then it goes unanswered
// rather than answered badly.
//
//     ./gradlew :ui:checkKdocSamples
//
val checkKdocSamples = tasks.register("checkKdocSamples") {
    group = "verification"
    description = "Fails if a KDoc sample names a parameter that does not exist, or omits a required one."

    val sources = layout.projectDirectory.dir("src/commonMain/kotlin")
    inputs.dir(sources).withPropertyName("commonMain")
    outputs.file(layout.buildDirectory.file("reports/kdoc-samples.txt"))

    val report = layout.buildDirectory.file("reports/kdoc-samples.txt")

    doLast {
        // The reader at the top of this file, under short local names.
        val balanced = KotlinSignatures::balanced
        val topLevel = KotlinSignatures::topLevel

        val kdoc = Regex("""/\*\*([\s\S]*?)\*/""")
        val sample = Regex("""```(?:kotlin)?\n([\s\S]*?)```""")
        val call = Regex("""\b([A-Z]\w*)\s*\(""")
        val namedArgument = Regex("""(?:\A|,)\s*(\w+)\s*=(?!=)""")

        val files = sources.asFileTree.matching { include("**/*.kt") }.files.sortedBy { it.path }

        // Every declared function, mapped to its parameter list.
        //
        // A name can be declared more than once — `Text` takes a `String` and an
        // `AnnotatedString`, `Icon` an `ImageVector` and a `Painter`. Both
        // overloads are kept, and a sample is judged against whichever it
        // satisfies: an argument only counts as wrong if *no* overload has it.
        val known = mutableMapOf<String, MutableList<List<KotlinSignatures.Parameter>>>()
        files.forEach { file ->
            KotlinSignatures.declarations(file.readText()).forEach { declaration ->
                known.getOrPut(declaration.name) { mutableListOf() } += declaration.parameters
            }
        }

        val problems = mutableListOf<String>()
        var samples = 0

        files.forEach { file ->
            val text = file.readText()
            val rel = file.relativeTo(sources.asFile).path
            kdoc.findAll(text).forEach { doc ->
                val first = text.substring(0, doc.range.first).count { it == '\n' } + 1

                // A line inside a KDoc block that does not start with `*` is
                // still part of the comment, but nothing trims its indentation,
                // so it renders with whatever leading whitespace it happens to
                // have. Round 3's slots conversion rewrote one-line calls into
                // multi-line lambdas and left 24 of these behind across eight
                // files, each one a sample that renders as a staircase.
                doc.groupValues[1].lines().drop(1).forEachIndexed { offset, line ->
                    if (line.isNotBlank() && !line.trimStart().startsWith("*")) {
                        problems += "$rel:${first + offset + 1}: KDoc line without its " +
                            "`*` prefix — `${line.trim().take(48)}`"
                    }
                }

                val body = doc.groupValues[1].lines()
                    .joinToString("\n") { it.replace(Regex("""^\s*\* ?"""), "") }
                sample.findAll(body).forEach { block ->
                    samples++
                    val code = block.groupValues[1]
                    val line = first + body.substring(0, block.range.first).count { it == '\n' }
                    call.findAll(code).forEach { site ->
                        val name = site.groupValues[1]
                        val overloads = known[name] ?: return@forEach
                        val opening = site.range.last
                        val closing = balanced(code, opening)
                        val inner = code.substring(opening + 1, closing)
                        val flat = topLevel(inner)

                        val named = namedArgument.findAll(flat).map { it.groupValues[1] }.toSet()
                        // Arguments before the first named one are positional.
                        // Kotlin forbids a positional argument after a named
                        // one, so counting depth-zero commas up to the first
                        // `name =` is the whole rule.
                        val firstNamed = namedArgument.find(flat)?.range?.first ?: flat.length
                        val leading = flat.substring(0, firstNamed)
                        val positional = when {
                            leading.isBlank() -> 0
                            else -> leading.count { it == ',' } + 1
                        }
                        // `Component(…) { … }` supplies the trailing slot, and
                        // that slot is a required parameter on most of these.
                        val trailingLambda = code.drop(closing + 1).trimStart().startsWith("{")

                        // Which of an overload's required parameters this call
                        // leaves unsupplied. One function rather than two,
                        // because the first version had an accept test and a
                        // report that disagreed: the test knew a trailing
                        // lambda fills the last slot, the report did not, so a
                        // sample missing only its `header` was told it was also
                        // missing the `content` sitting right below it.
                        fun unsatisfied(parameters: List<KotlinSignatures.Parameter>): List<String> {
                            val supplied = parameters.size - (if (trailingLambda) 1 else 0)
                            return parameters.withIndex()
                                .filter { (index, parameter) ->
                                    !parameter.optional &&
                                        index >= positional &&
                                        parameter.name !in named &&
                                        !(trailingLambda && index >= supplied)
                                }
                                .map { it.value.name }
                        }

                        fun satisfies(parameters: List<KotlinSignatures.Parameter>): Boolean =
                            named.all { given -> parameters.any { it.name == given } } &&
                                unsatisfied(parameters).isEmpty()

                        if (overloads.any(::satisfies)) return@forEach

                        // Nothing accepted it. Report the more specific of the
                        // two failures rather than both — a wrong name is the
                        // one someone can act on immediately.
                        val declared = overloads.flatten().map { it.name }.toSet()
                        val unknown = named.filter { it !in declared }
                        if (unknown.isNotEmpty()) {
                            unknown.forEach {
                                problems += "$rel:$line: `$name` has no parameter `$it`"
                            }
                        } else {
                            // Judged against whichever overload the call came
                            // closest to satisfying — reporting the other one's
                            // parameters would send the reader somewhere else.
                            val missing = unsatisfied(
                                overloads.minByOrNull { unsatisfied(it).size }.orEmpty()
                            )
                            if (missing.isNotEmpty()) {
                                problems += "$rel:$line: `$name` is missing required " +
                                    "${if (missing.size == 1) "argument" else "arguments"} " +
                                    missing.joinToString(", ") { "`$it`" }
                            }
                        }
                    }
                }
            }
        }

        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                if (problems.isEmpty()) "Every named argument in $samples KDoc samples resolves.\n"
                else problems.distinct().joinToString("\n", postfix = "\n")
            )
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("${problems.distinct().size} KDoc sample argument(s) that do not exist:")
                    problems.distinct().forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("A sample is the first thing anyone reads about a component, and it gets copied.")
                }
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkApiConventions, checkKdocSamples)
}
