import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget


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
// The version rule, the `coordinate` task, the repository and the POM, shared
// with `:ui-nav3` so the two published modules can never disagree about which
// release they are. The reasoning, and the history behind each rule, is with the
// code: `buildSrc/src/main/kotlin/KontourPublishing.kt`.
kontourPublishing(
    displayName = "Kontour UI",
    summary = "A Compose Multiplatform design system built on Foundation, without Material.",
)

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
// signature and no reasons; `ui-docs/content/` carries the comparisons, the "reach
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
    // ---------------------------------------------------------------------
    // The public API, checked in
    // ---------------------------------------------------------------------
    //
    // `checkKotlinAbi` compares what this module exports with the dump under
    // `api/`, and fails on any difference; `updateKotlinAbi` rewrites the dump.
    // A rename that reaches a consumer is then a line in a diff a reviewer reads,
    // rather than something noticed after a release. The Kotlin plugin's own
    // validator, so there is no second plugin to keep in step with it.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        // The Compose compiler's holders for lambdas with no captures. Public
        // in the bytecode and named by a hash of each lambda, so without this
        // any edit to a default slot would fail the check without the API
        // having moved.
        filters {
            exclude {
                byNames.add("**.ComposableSingletons**")
            }
        }
    }

    // ---------------------------------------------------------------------
    // Warnings are errors, in the library's own code
    // ---------------------------------------------------------------------
    //
    // Nothing here failed on a compiler warning until now, which is how a
    // deprecated `AnchoredDraggableState` constructor, two redundant `else`
    // branches and a safe call on a non-null receiver all sat in the published
    // library at once. Dokka has always failed on *its* warnings, and the KDoc
    // has stayed correct as a direct result; this is the same bargain for the
    // code.
    //
    // **Main compilations only.** Test sources stay permissive deliberately, and
    // this round is the argument: bumping Compose deprecated `runComposeUiTest`
    // at ninety-odd call sites, and a build that refuses to compile until every
    // one of them is migrated is a build that makes upgrading the dependency the
    // most expensive thing you can do. A deprecation in *shipped* code is a
    // defect; a deprecation in a test is a migration waiting for a quiet
    // afternoon.
    targets.configureEach {
        compilations.configureEach {
            if (name == "main") {
                compileTaskProvider.configure {
                    compilerOptions.allWarningsAsErrors.set(true)
                }
            }
        }
    }

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
            // absent — see `checkNoMaterial`, registered further down this file.
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

            // Also `api`: the haptics every component reports through are this
            // module's effects, and `LocalHaptics` hands a caller its player.
            // No Compose in it — see its build — so this adds nothing to a
            // consumer's graph but the effects themselves.
            api(project(":haptics"))
            implementation(libs.kotlinx.coroutines.core)

            // `implementation`: the overlay host answers back through it, and
            // no type of it appears in a public signature — `BackStyle` is
            // this library's own word for how back looks.
            implementation(libs.navigationevent.compose)

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
// ---------------------------------------------------------------------------
//
// Fails the build if any Material module reaches the resolved JVM runtime graph.
// Shared with `:ui-nav3`, which needs it more: see
// `buildSrc/src/main/kotlin/MaterialContainment.kt`.
checkNoMaterial()

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
            //
            // ### Except where the marker would break the scope
            //
            // A `@DslMarker` makes only the *innermost* receiver carrying it
            // reachable without qualification. That is the whole point for a
            // builder — and it is fatal for a scope whose members are meant to
            // be reached from arbitrarily deep inside layout code.
            // `PageTransitionScope.sharedElement` is a `Modifier` extension
            // called on a card three `Column`s down, and `ColumnScope` carries
            // the marker too, so with the marker on both the call does not
            // compile at all. Compose's own `SharedTransitionScope` is
            // unmarked for exactly this reason.
            //
            // The line between the two kinds is not a judgement: a builder's
            // members *emit*, and a capability scope's members return a
            // `Modifier`. So a scope whose every public function extends
            // `Modifier` is exempt from the marker — and still owes `@Stable`,
            // which is about something else entirely.
            val declarationsHere = KotlinSignatures.declarations(text)
            KotlinSignatures.types(text)
                .filter { it.isPublic && it.name.endsWith("Scope") }
                .forEach { type ->
                    val members = declarationsHere.filter {
                        it.enclosing == type.name && it.kind == KotlinSignatures.Kind.Function
                    }
                    val emitsContent = members.isEmpty() ||
                        members.any { it.receiver?.trim() != "Modifier" }
                    val required = if (emitsContent) {
                        listOf("@LayoutScopeMarker", "@Stable")
                    } else {
                        listOf("@Stable")
                    }
                    required
                        .filterNot { it in type.annotations }
                        .forEach { missing ->
                            problems += "$rel:${type.line} :: ${type.name}: a scope must " +
                                "carry `$missing`"
                        }
                    if (!emitsContent && "@LayoutScopeMarker" in type.annotations) {
                        problems += "$rel:${type.line} :: ${type.name}: every member is a " +
                            "`Modifier` extension, so `@LayoutScopeMarker` makes them " +
                            "unreachable from inside a `Column` or a `Box` — drop it"
                    }
                }

            // A lambda parameter never takes its function's parameter's name.
            //
            // Renaming `RadioGroup(selected)` to `value` left `options.forEach
            // { value -> val isSelected = value == value }` behind: it compiled,
            // warnings-as-errors said nothing, and every option drew selected.
            // A rename is exactly when a lambda's own name starts to collide,
            // so this is checked rather than remembered. Same-line `{ name ->`
            // only, which a `when` branch never is.
            run {
                val clean = KotlinSignatures.withoutComments(text)
                val function = Regex("""(?m)^[ \t]*(?:[\w@]+[ \t]+)*fun[ \t]+(?:<[^>]*>[ \t]*)?(?:[\w.<>]+\.)?\w+[ \t]*\(""")
                val lambda = Regex("""\{[ \t]*(\w+)(?:[ \t]*,[ \t]*(\w+))?[ \t]*->""")
                function.findAll(clean).forEach { header ->
                    val opening = header.range.last
                    val closing = KotlinSignatures.balanced(clean, opening)
                    val names = KotlinSignatures.parameters(clean.substring(opening + 1, closing))
                        .map { it.name }.toSet()
                    if (names.isEmpty()) return@forEach
                    val brace = Regex("""^[^{=]*\{""").find(clean.substring(closing + 1))
                        ?: return@forEach
                    val start = closing + 1 + brace.range.last
                    val end = KotlinSignatures.balancedBrace(clean, start)
                    lambda.findAll(clean.substring(start + 1, end)).forEach { match ->
                        listOfNotNull(match.groups[1]?.value, match.groups[2]?.value)
                            .filter { it in names }
                            .forEach { shadowed ->
                                val at = start + 1 + match.range.first
                                val line = clean.substring(0, at).count { it == '\n' } + 1
                                problems += "$rel:$line :: a lambda parameter `$shadowed` shadows " +
                                    "the function's own `$shadowed` — name it `each`, or for what it holds"
                            }
                    }
                }
            }

            // Two KDoc blocks in a row. The first documents nothing — KDoc
            // attaches to the declaration that follows it, and the one that
            // follows the first block is the second block — so a component's
            // own documentation went missing from the reference while reading
            // perfectly well in the source. `KontourTheme`, `Chip`, `Kbd` and
            // `CalendarMonth` were all caught like this at once.
            Regex("""/\*[\s\S]*?\*/""").findAll(text).toList()
                .zipWithNext()
                .filter { (a, b) ->
                    a.value.startsWith("/**") && b.value.startsWith("/**") &&
                        text.substring(a.range.last + 1, b.range.first).isBlank()
                }
                .forEach { (_, b) ->
                    val line = text.substring(0, b.range.first).count { it == '\n' } + 1
                    problems += "$rel:$line :: two KDoc blocks in a row — the first " +
                        "documents nothing; merge them, or make the first a `//` comment"
                }

            // The things a caller holds or starts from have a KDoc.
            //
            // A component's own KDoc has always been checked by its page; the
            // state holders, their `remember` functions and the Defaults
            // objects were not, and 51 of them reached the reference as a bare
            // name — `rememberCarouselState` with nothing saying that the
            // count is read lazily, `TimelineDefaults` with nothing saying it
            // is the whole family's geometry.
            val held = Regex(
                """^(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:data|sealed|abstract|open|value)\s+)*""" +
                    """(?:class|interface|object|fun)\s+(?:<[^>]*>\s*)?""" +
                    """(\w*State|remember\w+|\w+Defaults|\w+Colours)\b(?!\.)""",
            )
            val lines = text.lines()
            lines.forEachIndexed { index, line ->
                val name = held.find(line)?.takeIf { it.range.first == 0 }?.groupValues?.get(1)
                    ?: return@forEachIndexed
                var above = index - 1
                while (above >= 0 && lines[above].startsWith("@")) above--
                if (above < 0 || !lines[above].trimEnd().endsWith("*/")) {
                    problems += "$rel:${index + 1} :: $name has no KDoc — say what a caller " +
                        "holds it for, or what it is the default of"
                }
            }

            // No English in a component's body either.
            //
            // The rule above covers defaults. This covers the words a
            // component announces on its own — "Hour", "AM or PM", "Page 3" —
            // which reached a screen reader in English whatever the app's
            // language, with no parameter to change them. A template made only
            // of interpolations passes: it is assembling words from elsewhere.
            if (!rel.endsWith("theme/Strings.kt")) {
                val code = text
                    .replace(Regex("""/\*[\s\S]*?\*/""")) { m -> "\n".repeat(m.value.count { it == '\n' }) }
                    .replace(Regex("//[^\n]*"), "")
                Regex("(contentDescription|stateDescription)\\s*=\\s*\"([^\"]*)\"")
                    .findAll(code)
                    .filter { m ->
                        val words = m.groupValues[2]
                            .replace(Regex("""\$\{[^}]*\}"""), "")
                            .replace(Regex("""\$\w+"""), "")
                        Regex("[A-Za-z]{2}").containsMatchIn(words)
                    }
                    .forEach { m ->
                        val line = code.substring(0, m.range.first).count { it == '\n' } + 1
                        problems += "$rel:$line :: `${m.groupValues[1]}` is the literal " +
                            "\"${m.groupValues[2]}\" — put the words in `Theme.strings`"
                    }
            }

            // Every private or internal name at the top of the file, for the
            // defaults rule below.
            val hiddenHere = Regex("""(?m)^(?:private|internal) (?:const )?(?:val|var|fun) (?:<[^>]*> )?(?:[\w.<>?]+\.)?(\w+)""")
                .findAll(text).map { it.groupValues[1] }.toSet()

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
                                // A range held as its two ends pairs too:
                                // `DateRangePicker(start, end, onRangeChange)`.
                                val range = noun == "Range" && "start" in names && "end" in names
                                if (subject !in names && "is$noun" !in names && !range) {
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

                    // No fully-qualified `io.kontour` type in a signature, for the
                    // reason `androidx.*` is banned just above: it is what the
                    // API reference prints.
                    params.filter { "io.kontour." in it.type }.forEach { parameter ->
                        problems += "$where: `${parameter.name}` is typed " +
                            "`${parameter.type.trim()}` — import it"
                    }

                    // A default a caller can read.
                    //
                    // A default that calls a private helper renders in the API
                    // reference as `icon = calloutIcon(tone)`, and there is no
                    // `calloutIcon` to call — a caller who wants the default
                    // back after overriding it has nothing to write. Put it on
                    // the component's Defaults object.
                    params.forEach { parameter ->
                        val default = parameter.default ?: return@forEach
                        Regex("""(?<![.\w])([A-Za-z_]\w*)""").findAll(default)
                            .map { it.groupValues[1] }
                            .filter { it in hiddenHere && it !in names }
                            .distinct()
                            .forEach { hidden ->
                                problems += "$where: `${parameter.name}` defaults through " +
                                    "`$hidden`, which a caller cannot reach — publish it on " +
                                    "the component's Defaults object"
                            }
                    }

                    // Time is a `Duration`. A `Long` of milliseconds is a unit
                    // the type does not carry, and it was how five components
                    // disagreed about whether a delay was an `Int` or a `Long`.
                    params
                        .filter {
                            it.name.endsWith("Millis") &&
                                it.type.trim().removeSuffix("?") in setOf("Long", "Int")
                        }
                        .forEach { parameter ->
                            problems += "$where: `${parameter.name}` is a " +
                                "${parameter.type.trim()} of milliseconds — take a `Duration`"
                        }

                    // A starting value is `initial<What>`. A bare `initial`
                    // says it is a starting value and not of what, and
                    // `initiallyExpanded` was the one adverb among them.
                    params
                        .filter { it.name == "initial" || it.name.startsWith("initially") }
                        .forEach { parameter ->
                            problems += "$where: `${parameter.name}` — name a starting value " +
                                "`initial<What>`, as `initialDetent` and `initialDate` are"
                        }

                    // A switch that shows a part is `show<Part>`.
                    //
                    // `tail`, `legend`, `dividers` and `backdrop` all read as the
                    // part itself — a caller passing `legend = false` could as
                    // easily be passing the legend. The heuristic is the part
                    // names this library has; a new part that slips past it is
                    // still wrong.
                    val partName = Regex(
                        "(?i).*(label|labels|line|field|slider|background|legend|tail|" +
                            "divider|dividers|backdrop|shadow)$",
                    )
                    // How it is drawn, rather than whether: `uppercaseLabels`
                    // says what the labels look like.
                    val treatment = Regex("^(uppercase|lowercase|single|multi|wrap|allow|is|has|use)[A-Z].*")
                    params
                        .filter {
                            it.type.trim() == "Boolean" &&
                                !it.name.startsWith("show") &&
                                !treatment.matches(it.name) &&
                                partName.matches(it.name)
                        }
                        .forEach { parameter ->
                            val shown = parameter.name.replaceFirstChar { it.uppercase() }
                            problems += "$where: `${parameter.name}` switches a part on and " +
                                "off — name it `show$shown`"
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
                    // And a shorthand's trailing action is its slot: dsls.md
                    // puts it last so that `item("Zoom in", icon) { zoomIn() }`
                    // reads as a call, which is the one position it cannot share.
                    val trailingAction = declaration.enclosing?.endsWith("Scope") == true &&
                        params.lastOrNull()?.let { it.name.startsWith("on") && "->" in it.type } == true
                    if (interactionIndex >= 0) {
                        val trailing = params.drop(interactionIndex + 1)
                            .filterNot {
                                "Composable" in it.type || builderType.containsMatchIn(it.type) ||
                                    (trailingAction && it === params.last())
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
// This is not a compiler, and it deliberately answers only the questions that
// actually went wrong. The first two:
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
// A third came later, and went wrong the same way: inside a builder lambda,
// does each statement belong to the scope the lambda receives? `Banner {
// supporting { … } }` names a real component and a real scope member — just
// `StateScope`'s, not `BannerScope`'s. A statement is only reported when it is
// a member of some *other* library scope, so a caller's own function or a
// Compose one is left alone.
//
// Types and the local variables a fragment refers to are out of scope;
// checking those needs a real frontend.
//
// One question looks adjacent and is left unanswered, because it cannot be
// answered here: does every capitalised call resolve to something that exists? 27 names in the
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
    description = "Fails if a KDoc sample names a parameter that does not exist, omits a required one, " +
        "or calls another scope's member inside a builder lambda."

    val sources = layout.projectDirectory.dir("src/commonMain/kotlin")
    inputs.dir(sources).withPropertyName("commonMain")
    val repository = rootProject.layout.projectDirectory
    val pages = files(
        repository.dir("ui-docs/content").asFileTree.matching { include("**/*.md") },
        repository.dir("docs").asFileTree.matching { include("**/*.md") },
        repository.asFileTree.matching { include("README.md", "*/README.md") },
    )
    inputs.files(pages).withPropertyName("pages")
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
        // `Modifier.tabSwipe(…)` is as much a call as `TabBar(…)`, and was
        // left stale by a rename just the same. Kept apart so a modifier is
        // judged only against the modifiers of that name.
        val modifiers = mutableMapOf<String, MutableList<List<KotlinSignatures.Parameter>>>()
        files.forEach { file ->
            KotlinSignatures.declarations(file.readText()).forEach { declaration ->
                known.getOrPut(declaration.name) { mutableListOf() } += declaration.parameters
                if (declaration.receiver == "Modifier") {
                    modifiers.getOrPut(declaration.name) { mutableListOf() } += declaration.parameters
                }
            }
        }
        val modifierCall = Regex("""\.([a-z]\w*)\s*\(""")

        // What each builder scope offers inside its lambda, by scope name.
        //
        // A capitalised call is only half of a sample: `Banner { supporting {
        // … } }` names a real component and passes it a lambda, and the call
        // inside that lambda is the part that had gone stale — `BannerScope`
        // says `message`. So every member a library scope declares, in its
        // body or as an extension on it, is collected here along with the
        // scopes it extends, and a statement in a scoped lambda that belongs
        // to some *other* scope is reported.
        val scopeMembers = mutableMapOf<String, MutableSet<String>>()
        val scopeParents = mutableMapOf<String, MutableSet<String>>()
        val scopeHeader = Regex(
            """^[ \t]*(?:\w+\s+)*(?:class|interface)\s+(\w+Scope)\b[^:{\n]*(?::\s*([^{\n]+))?""",
            RegexOption.MULTILINE,
        )
        files.forEach { file ->
            val text = file.readText()
            scopeHeader.findAll(text).forEach { header ->
                val parents = scopeParents.getOrPut(header.groupValues[1]) { mutableSetOf() }
                Regex("""\b(\w+Scope)\b""").findAll(header.groupValues[2]).forEach {
                    parents += it.groupValues[1]
                }
            }
            KotlinSignatures.declarations(text).forEach { declaration ->
                val owner = (declaration.receiver ?: declaration.enclosing)
                    ?.substringBefore('<')
                    ?.takeIf { it.endsWith("Scope") && declaration.kind == KotlinSignatures.Kind.Function }
                    ?: return@forEach
                if (declaration.isPublic) {
                    scopeMembers.getOrPut(owner) { mutableSetOf() } += declaration.name
                }
            }
        }
        fun membersOf(scope: String, seen: MutableSet<String> = mutableSetOf()): Set<String> {
            if (!seen.add(scope)) return emptySet()
            return scopeMembers[scope].orEmpty() +
                scopeParents[scope].orEmpty().flatMap { membersOf(it, seen) }
        }
        val anyScopeMember = scopeMembers.values.flatten().toSet()
        val scopedLambda = Regex("""\b(\w+Scope)\s*(?:<[^>]*>)?\s*\.\s*\(""")
        val statement = Regex("""^[ \t]*([a-z]\w*)""", RegexOption.MULTILINE)
        val keywords = setOf("if", "when", "for", "while", "do", "try", "return", "val", "var", "fun", "else")

        val problems = mutableListOf<String>()
        var samples = 0

        // The statements directly inside the lambda opened at [brace], each
        // checked against the scope that lambda receives. Only the lambda's
        // own level: a nested lambda has its own receiver, and a call inside
        // it is judged — if it is a component's — at its own site.
        fun checkScopedLambda(
            name: String,
            overloads: List<List<KotlinSignatures.Parameter>>,
            code: String,
            brace: Int,
            line: Int,
            rel: String,
        ) {
            // Every overload has to agree on the receiver. A trailing lambda
            // that some overload takes as plain content can hold anything.
            val scopes = overloads.map { parameters ->
                parameters.lastOrNull()?.type?.let { scopedLambda.find(it)?.groupValues?.get(1) }
            }
            if (scopes.isEmpty() || scopes.any { it == null || it !in scopeMembers && it !in scopeParents }) return
            val allowed = scopes.filterNotNull().flatMap { membersOf(it) }.toSet()
            val end = KotlinSignatures.balancedBrace(code, brace)
            val inner = code.substring(brace + 1, end.coerceAtMost(code.length))
            val flat = topLevel(inner)
            statement.findAll(flat).forEach { match ->
                val callee = match.groupValues[1]
                if (callee in keywords || callee in allowed || callee !in anyScopeMember) return@forEach
                val next = inner.drop(match.range.last + 1).trimStart().firstOrNull()
                if (next != '(' && next != '{') return@forEach
                val receiver = scopes.filterNotNull().distinct().joinToString(" or ")
                problems += "$rel:$line: `$callee` is not in `$receiver`, the receiver of `$name { … }`"
            }
        }

        // One code block, wherever it came from: every component and
        // modifier call in it, and every builder lambda it passes.
        fun checkBlock(rel: String, line: Int, code: String) {
            // `Component { … }` with no parentheses: only its
            // trailing lambda is there to check.
            Regex("""\b([A-Z]\w*)\s*\{""").findAll(code).forEach { site ->
                val overloads = known[site.groupValues[1]] ?: return@forEach
                checkScopedLambda(site.groupValues[1], overloads, code, site.range.last, line, rel)
            }
            val sites = call.findAll(code).mapNotNull { site ->
                known[site.groupValues[1]]?.let { Triple(site, site.groupValues[1], it) }
            } + modifierCall.findAll(code).mapNotNull { site ->
                modifiers[site.groupValues[1]]?.let { Triple(site, "Modifier.${site.groupValues[1]}", it) }
            }
            sites.forEach { (site, name, overloads) ->
                val opening = site.range.last
                val closing = balanced(code, opening)
                val after = code.drop(closing + 1)
                if (after.trimStart().startsWith("{")) {
                    val brace = closing + 1 + (after.length - after.trimStart().length)
                    checkScopedLambda(name, overloads, code, brace, line, rel)
                }
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
                    checkBlock(rel, line, code)
                }
            }
        }

        // The guides' own code blocks, which nothing compiled either — that is
        // how `DatePicker(selected = …)` outlived the rename on the date picker
        // page. A `<!--sample:…-->` block is copied from `:ui-samples`, where
        // the compiler checks it; this is for everything typed into a page.
        var pageBlocks = 0
        pages.files.sortedBy { it.path }.forEach { file ->
            val text = file.readText()
            val rel = file.relativeTo(repository.asFile).path
            Regex("""```kotlin\n([\s\S]*?)```""").findAll(text).forEach { block ->
                pageBlocks++
                val line = text.substring(0, block.range.first).count { it == '\n' } + 1
                checkBlock(rel, line, block.groupValues[1])
            }
        }

        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                if (problems.isEmpty()) "Every argument and scope call in $samples KDoc samples and $pageBlocks page blocks resolves.\n"
                else problems.distinct().joinToString("\n", postfix = "\n")
            )
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("${problems.distinct().size} sample call(s) that do not resolve:")
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

// ---------------------------------------------------------------------------
// The web test tasks, and the check that exists to say they do not work
// ---------------------------------------------------------------------------
//
// The Compose plugin registers a `checkComposeUiTestConfiguration<Target>` task
// for each web target of any project depending on `compose.uiTest`, and fails it
// unless that target declares `binaries.executable()` — the webpack bundling a
// browser test runner needs in order to load Skiko. `:ui-catalog` and `:ui-docs`
// both declare one, because both genuinely ship a web bundle, and both pass.
// This module is a library and does not, so `check` and `build` have never run
// to completion here; CI names its test tasks one by one to route around it.
//
// **The check is right.** `commonTest` asks for `compose.uiTest`, so js and
// wasmJs test compilations exist, and running them would fail to load Skiko.
// There are two coherent answers and no third: make those tests run, or stop
// declaring them.
//
// Making them run means `binaries.executable()`, and the price is on the wrong
// task. `:ui:assemble` — which builds what gets published — completes in about
// four seconds with no network at all; adding the executable pulls
// `kotlinNpmInstall` and `kotlinWasmToolingSetup` into it, so building the
// library would first fetch the npm registry and karma from GitHub. A hard
// dependency on two external hosts, on the publish path, for a bundle nothing
// consumes.
//
// So the web test tasks are switched off and the check goes with them, which
// makes the existing arrangement deliberate rather than accidental: **this suite
// runs on the JVM.** `runComposeUiTest` renders through Skiko, and Skiko is also
// what iOS and desktop draw with, so a JVM run exercises the same rasteriser two
// of the four platforms ship. The web targets are covered by compiling every one
// of them in the `targets` job, and by `:ui-docs` building and deploying a real
// wasm bundle of this library on every commit — which puts more of it through a
// browser than a unit test would.
//
// One thing this does *not* buy: `check` still triggers `kotlinNpmInstall`,
// because declaring `browser()` puts the browser test environment in the task
// graph whether or not anything runs in it, and a disabled task does not prune
// its own dependencies. That is unavoidable short of dropping the browser
// target, and costs nothing on a machine that can reach the npm registry.
//
// To reverse this: add `binaries.executable()`, drop the two lines below, run
// `:ui:jsTest` and `:ui:wasmJsTest` in CI, and accept npm on `assemble`.
tasks.matching {
    it.name.startsWith("checkComposeUiTestConfiguration") ||
        it.name in setOf("jsTest", "jsBrowserTest", "wasmJsTest", "wasmJsBrowserTest")
}.configureEach { enabled = false }

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
    // Raised here and not at the call sites, because `runComposeUiTest`'s own
    // `testTimeout` parameter cannot raise it: the v2 runner wraps every test
    // in a `runTest` whose timeout argument it leaves at the sixty-second
    // default, so that argument can only ever lower the cap. `:ui-catalog`'s
    // copy of this line carries the full account and the bytecode that shows
    // it; this module gets the same number so a slow machine fails the same
    // way in all three.
    systemProperty("kotlinx.coroutines.test.default_timeout", "5m")
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
// On a real screen
// ---------------------------------------------------------------------------
//
//     xvfb-run -a ./gradlew :ui:jvmOnScreenTest
//
// Every other test stops one step short of the screen: it records the cursor a
// scene asks the platform for. `OnScreenCursorTest` opens a real window, moves the
// real pointer with `java.awt.Robot` and reads back what the window system was
// given. It needs a display, so it is kept out of `jvmTest` — which runs anywhere —
// and has a task of its own, which CI runs under `xvfb-run`. Without a display it
// fails rather than passing having checked nothing.
tasks.named<Test>("jvmTest") {
    filter { excludeTestsMatching("*.OnScreen*Test") }
}

tasks.register<Test>("jvmOnScreenTest") {
    group = "verification"
    description = "Moves a real pointer over a real window and checks the cursor it shows. Needs a display."
    val jvmTest = tasks.named<Test>("jvmTest").get()
    testClassesDirs = jvmTest.testClassesDirs
    classpath = jvmTest.classpath
    filter { includeTestsMatching("*.OnScreen*Test") }
    // One window at a time: two forks would fight over the one pointer.
    maxParallelForks = 1
    systemProperty(
        "kontour.onScreen.captureDir",
        layout.buildDirectory.dir("on-screen").get().asFile.absolutePath,
    )
}
