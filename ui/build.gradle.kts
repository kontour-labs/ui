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
            "onDismiss" to "components take `onDismissRequest` — the caller owns `visible` and is being asked, not told",
        )

        val problems = mutableListOf<String>()

        sources.asFileTree.matching { include("**/*.kt") }.forEach { file ->
            val text = file.readText()
            val rel = file.relativeTo(sources.asFile).path

            // Public top-level functions and their parameter lists.
            // Multi-line parameter lists only: `fun foo(` … newline … `)`. A
            // one-line signature has nothing to get out of order, and matching
            // it here lets the block run on to the *next* function's closing
            // paren and report the wrong name.
            Regex("""^fun\s+(?:<[^>]*>\s+)?(?:[A-Za-z_][A-Za-z0-9_]*\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\(\n([\s\S]*?)^\)""", RegexOption.MULTILINE)
                .findAll(text)
                .forEach { match ->
                    val name = match.groupValues[1]
                    val body = match.groupValues[2]
                    val params = Regex("""^ {4}([a-z][A-Za-z0-9_]*)\s*:\s*(.*)$""", RegexOption.MULTILINE)
                        .findAll(body)
                        .map { it.groupValues[1] to it.groupValues[2] }
                        .toList()
                    if (params.isEmpty()) return@forEach
                    val names = params.map { it.first }
                    val where = "$rel :: $name"

                    bannedNames.forEach { (banned, why) ->
                        if (banned in names) problems += "$where: parameter `$banned` — $why"
                    }

                    // `modifier` is the first optional parameter, so everything
                    // before it is required and a caller can always reach it the
                    // same way.
                    val modifierIndex = names.indexOf("modifier")
                    if (modifierIndex >= 0) {
                        val firstDefaulted = params.indexOfFirst { "=" in it.second }
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
                    }.takeIf { it >= 0 && "=" in params[it].second } ?: -1
                    if (interactionIndex >= 0) {
                        val trailing = params.drop(interactionIndex + 1)
                            .filterNot {
                                "Composable" in it.second || builderType.containsMatchIn(it.second)
                            }
                        if (trailing.isNotEmpty()) {
                            problems += "$where: `interactionSource` must come after every " +
                                "non-slot parameter (found ${trailing.joinToString { it.first }} after it)"
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
                    appendLine("The conventions are in docs/app/design-system/building/contributing.md.")
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

        // Blank out everything nested so only depth-zero commas survive. `->`
        // is masked to two spaces first: counting `>` as a closing bracket
        // drives the depth negative on the first `() -> Unit` parameter and
        // blanks the rest of the signature, which reports every parameter but
        // the first as missing and looks exactly like a real finding. Spaces
        // rather than a sentinel because the replacement has to preserve
        // length — every index below is an offset into this string.
        // Comments first, blanked to spaces. A parameter carrying its own KDoc
        // is common here, and prose contains commas — "…accounted for them, or
        // when this bar is not at the top of the window." split `TopBar`'s
        // signature *inside* the comment, so `windowInsets` and everything
        // after it was never seen as a parameter and every sample naming one
        // was reported as naming something that does not exist. Four of the
        // twelve first findings were this.
        //
        // Spaces rather than deletion because every index below is an offset
        // into this string.
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
                    block == 0 && two == "//" -> { line = true; out.append("  "); i += 2 }
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

        fun topLevel(raw: String): String {
            val inner = withoutComments(raw)
            val masked = inner.replace("->", "  ")
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

        val declaration =
            Regex("""^(?:public |internal )?(?:@\w+\s+)*fun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)\s*\(""", RegexOption.MULTILINE)
        val parameter = Regex("""(?:\A|,)\s*(?:@\w+\s*)*(?:vararg\s+)?(\w+)\s*:""")

        // A declared parameter is its name paired with whether the caller may
        // omit it. A `Pair` rather than a data class purely because a local
        // class inside this `doLast` lambda crashes the script compiler.
        //
        // The order matters as much as the names: a sample may satisfy a
        // parameter positionally, and that can only be judged against the
        // position it was declared in.
        //
        // Split the list on its depth-zero commas, keeping each chunk so the
        // `=` that marks a default is still visible.
        fun parameters(inner: String): List<Pair<String, Boolean>> {
            val flat = topLevel(inner)
            val cuts = flat.mapIndexedNotNull { i, ch -> i.takeIf { ch == ',' } }
            val bounds = listOf(-1) + cuts + listOf(flat.length)
            return bounds.zipWithNext().mapNotNull { (from, to) ->
                val chunk = inner.substring(from + 1, to)
                val name = parameter.find(topLevel(chunk))?.groupValues?.get(1) ?: return@mapNotNull null
                name to ('=' in topLevel(chunk))
            }
        }
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
        val known = mutableMapOf<String, MutableList<List<Pair<String, Boolean>>>>()
        files.forEach { file ->
            val text = file.readText()
            declaration.findAll(text).forEach { match ->
                val opening = text.indexOf('(', match.range.last)
                val inner = text.substring(opening + 1, balanced(text, opening))
                known.getOrPut(match.groupValues[1]) { mutableListOf() } += parameters(inner)
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
                        fun unsatisfied(parameters: List<Pair<String, Boolean>>): List<String> {
                            val supplied = parameters.size - (if (trailingLambda) 1 else 0)
                            return parameters.withIndex()
                                .filter { (index, parameter) ->
                                    !parameter.second &&
                                        index >= positional &&
                                        parameter.first !in named &&
                                        !(trailingLambda && index >= supplied)
                                }
                                .map { it.value.first }
                        }

                        fun satisfies(parameters: List<Pair<String, Boolean>>): Boolean =
                            named.all { given -> parameters.any { it.first == given } } &&
                                unsatisfied(parameters).isEmpty()

                        if (overloads.any(::satisfies)) return@forEach

                        // Nothing accepted it. Report the more specific of the
                        // two failures rather than both — a wrong name is the
                        // one someone can act on immediately.
                        val declared = overloads.flatten().map { it.first }.toSet()
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
