import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The documentation site. One Wasm bundle, hash routing, a page per component —
// the prose from `content/`, the live component from
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

    // Not a target anybody ships. It is the one that can be *rendered in a
    // test*: the shell, the index and the prose are ordinary Compose in
    // `commonMain`, and this is what lets `jvmTest` walk all 84 pages at four
    // widths without a browser.
    //
    // Its absence is why the site went out with a landing page that threw on
    // any window under 600dp. `wasmJs` alone has no test source set that runs
    // here, so there was nowhere to put a test even if someone had wanted one.
    jvm()

    sourceSets {
        commonMain.dependencies {
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

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            // Skiko, so the render sweep has a real canvas to draw into.
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.uiTest)
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
    description = "Turns ui-docs/content/**/*.md into DocPages.kt"

    val pages = layout.projectDirectory.dir("content")
    val script = rootProject.layout.projectDirectory.file("docs/generate-doc-pages.py")
    // `doctree.py` is an input too, and it became a load-bearing one when the
    // guides took their order from `GUIDES`: reordering that tuple with only the
    // script declared leaves this task up to date, so the generated pages keep
    // the old order until somebody happens to touch a markdown file.
    val shape = rootProject.layout.projectDirectory.file("docs/doctree.py")
    inputs.dir(pages).withPropertyName("pages")
    inputs.file(script).withPropertyName("script")
    inputs.file(shape).withPropertyName("doctree")
    outputs.dir(generatedDocs).withPropertyName("generated")

    workingDir = rootProject.layout.projectDirectory.asFile
    commandLine("python3", script.asFile.path, generatedDocs.get().asFile.path)
}

// The parameter tables, as Kotlin.
//
// Read out of `:ui`'s own source by the same `KotlinSignatures` that
// `checkApiConventions` and `checkKdocSamples` use — which is the whole reason
// it was moved to `buildSrc`. Two readers would be two answers, and the value
// of a generated table is precisely that it cannot disagree with the rules the
// conventions check enforces.
val generatedApi = layout.buildDirectory.dir("generated/apiTables")

val generateApiTables = tasks.register("generateApiTables") {
    group = "documentation"
    description = "Turns :ui's public declarations into ApiTables.kt"

    // Captured here rather than read inside `doLast`: reaching for another
    // project during execution is invisible to the configuration cache, which
    // is on for this build.
    val sources = project(":ui").layout.projectDirectory.dir("src/commonMain/kotlin")
    val out = generatedApi
    inputs.dir(sources).withPropertyName("uiSources")
    outputs.dir(out).withPropertyName("generated")

    doLast {
        val files = sources.asFile.walkTopDown().filter { it.extension == "kt" }.sortedBy { it.path }.toList()

        // Dokka's own file naming: every capital becomes a dash and a lower
        // case letter, so `ButtonGroup` is `-button-group` and `shape` stays
        // `shape`. Transcribed from a real run rather than from the docs, and
        // `docs/check-api-links.py` re-checks it against one.
        fun dokkaSlug(name: String): String =
            name.map { if (it.isUpperCase()) "-" + it.lowercaseChar() else it.toString() }.joinToString("")

        val declarations = mutableListOf<KotlinSignatures.Declaration>()
        val enumValues = sortedMapOf<String, List<String>>()

        // Where Dokka puts each symbol, so the site can link *at* it rather
        // than at the reference's front door — see `apiReferencePaths` below.
        //
        // A *set* per name, because a simple name is not unique across packages
        // and picking one arbitrarily would send a reader to the wrong page
        // while looking exactly like a working link. There is one such name
        // today — `shape`, an extension of `ButtonGroupPosition` in one package
        // and of `ListItemPosition` in another — and it is dropped rather than
        // guessed at. Ambiguity falls back to the reference's index, which has
        // a search box.
        val referencePaths = sortedMapOf<String, MutableSet<String>>()

        for (file in files) {
            val text = file.readText()
            val declared = KotlinSignatures.declarations(text)
            declarations += declared
            enumValues += KotlinSignatures.enums(text)

            val pkg = KotlinSignatures.packageName(text) ?: continue
            // A function is a file; a type is a directory with an index in it.
            // Types are written second so that a name which is both keeps the
            // class page, which is the one carrying the members.
            fun claim(name: String, path: String) {
                referencePaths.getOrPut(name) { sortedSetOf() } += path
            }

            for (declaration in declared) {
                // Indentation is the test, and neither "no receiver" nor "no
                // enclosing type" can replace it. An `override fun onPreScroll`
                // inside an `object : NestedScrollConnection { }` has neither,
                // and produced four `components.datetime/on-pre-scroll.html`
                // the reference has never had a page for — 24 of 381 entries
                // were that shape, each a 404 waiting for something to link it.
                //
                // A receiver is fine: `fun Modifier.marquee` is a top-level
                // declaration and the reference publishes it as one. Excluding
                // it cost three component pages their link — `marquee`,
                // `fadingEdges` and `ReorderableItem` — which is what
                // `check-api-links.py` reported the first time it ran.
                // Functions only. `declarations` reports a class's primary
                // constructor too, so a `data class NavItem(…)` claimed the
                // *function* path as well as the type path its own loop below
                // adds, read as two candidates, and was dropped as ambiguous —
                // taking `Toast`, `WindowSizeClass` and `DateTimeFormats` with
                // it. Four component pages, silently back on the index.
                if (declaration.isPublic &&
                    declaration.indent == 0 &&
                    declaration.kind == KotlinSignatures.Kind.Function
                ) {
                    claim(declaration.name, "$pkg/${dokkaSlug(declaration.name)}.html")
                }
            }
            for (type in KotlinSignatures.types(text)) {
                if (type.isPublic && type.isTopLevel) {
                    claim(type.name, "$pkg/${dokkaSlug(type.name)}/index.html")
                }
            }
        }

        val public = declarations.filter { it.isPublic }

        // A page's title says `ListItem`; what a caller writes is `ListItem { }`
        // with four `ListItemScope` methods inside it. So a symbol claims its
        // own declaration *and* the members of the scope named after it.
        val bySymbol = sortedMapOf<String, MutableList<KotlinSignatures.Declaration>>()
        for (declaration in public) {
            val owner = declaration.receiver ?: declaration.enclosing
            val symbol = when {
                owner == null -> declaration.name
                owner.endsWith("Scope") -> owner.removeSuffix("Scope")
                // `fun Modifier.marquee(…)` is named `Modifier.marquee` on its
                // page, and that is also how it is called.
                else -> declaration.qualified
            }
            bySymbol.getOrPut(symbol) { mutableListOf() } += declaration
        }

        fun literal(text: String?): String = when (text) {
            null -> "null"
            else -> "\"" + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\\$")
                .replace("\n", " ") + "\""
        }

        fun tidy(text: String): String = text.replace(Regex("\\s+"), " ").trim()

        // A default's job on the page is to answer "what happens if I leave it
        // out". `MultiSelect.summary` defaults to a 250-character lambda with a
        // `when` in it, and pasting that into a table answers the question with
        // noise. Long ones are cut, and the reference has the whole of it.
        fun short(text: String?): String? = when {
            text == null || text.length <= 72 -> text
            else -> text.take(71).trimEnd() + "…"
        }

        val body = StringBuilder()
        body.append("package io.kontour.ui.docs\n\n")
        body.append("// Generated by :ui-docs:generateApiTables. Do not edit.\n\n")
        body.append("internal val apiBySymbol: Map<String, () -> List<ApiEntry>> = mapOf(\n")
        for ((symbol, group) in bySymbol) {
            body.append("    ").append(literal(symbol)).append(" to {\n        listOf(\n")
            for (declaration in group.sortedWith(compareBy({ it.enclosing != null }, { it.line }))) {
                val parameters = declaration.parameters.map { it.copy(type = tidy(it.type), default = short(it.default?.let(::tidy))) }
                val named = parameters.flatMap { parameter ->
                    enumValues.keys.filter { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(parameter.type) }
                }.distinct().sorted()
                body.append("            ApiEntry(\n")
                body.append("                name = ").append(literal(declaration.name)).append(",\n")
                body.append("                owner = ").append(literal(declaration.receiver ?: declaration.enclosing)).append(",\n")
                body.append("                isComposable = ").append(declaration.isComposable).append(",\n")
                body.append("                isClass = ").append(declaration.kind == KotlinSignatures.Kind.Class).append(",\n")
                if (parameters.isEmpty()) {
                    body.append("                parameters = emptyList(),\n")
                } else {
                    body.append("                parameters = listOf(\n")
                    for (parameter in parameters) {
                        body.append("                    ApiParameter(")
                            .append(literal(parameter.name)).append(", ")
                            .append(literal(parameter.type)).append(", ")
                            .append(literal(parameter.default)).append("),\n")
                    }
                    body.append("                ),\n")
                }
                body.append("                enums = ")
                    .append(if (named.isEmpty()) "emptyList()" else named.joinToString(", ", "listOf(", ")") { literal(it) })
                    .append(",\n")
                body.append("            ),\n")
            }
            body.append("        )\n    },\n")
        }
        body.append(")\n\n")

        body.append("/**\n")
        body.append(" * Where the API reference puts each symbol, relative to `api/kontour-ui/`.\n")
        body.append(" *\n")
        body.append(" * Derived rather than read out of Dokka's own `pages.json`, and the reason\n")
        body.append(" * is build order: CI builds this site *before* it builds the reference, so a\n")
        body.append(" * dependency on that file would reverse the two. The slug rule was checked\n")
        body.append(" * against a real Dokka run over all 1,546 published pages — every one of the\n")
        body.append(" * 103 symbols a page links to resolves exactly, and none resolves wrongly.\n")
        body.append(" * `docs/check-api-links.py` is what keeps that true.\n")
        body.append(" */\n")
        body.append("internal val apiReferencePaths: Map<String, String> = mapOf(\n")
        for ((symbol, candidates) in referencePaths) {
            val path = candidates.singleOrNull() ?: continue
            body.append("    ").append(literal(symbol)).append(" to ").append(literal(path)).append(",\n")
        }
        body.append(")\n\n")

        body.append("internal val apiEnums: Map<String, ApiEnum> = mapOf(\n")
        for ((name, values) in enumValues) {
            body.append("    ").append(literal(name)).append(" to ApiEnum(")
                .append(literal(name)).append(", listOf(")
                .append(values.joinToString(", ") { literal(it) })
                .append(")),\n")
        }
        body.append(")\n")

        val directory = out.get().asFile
        directory.mkdirs()
        directory.resolve("ApiTables.kt").writeText(body.toString())
        logger.lifecycle("generateApiTables: ${bySymbol.size} symbols, ${public.size} public declarations, ${enumValues.size} enums")
    }
}

kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generatedDocs)
    kotlin.srcDir(generatedApi)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateDocPages, generateApiTables)
}

// `wasmJsBrowserDistribution` writes to `build/dist/wasmJs/productionExecutable`,
// which is what the `site` job in `.github/workflows/ci.yml` copies. That path
// is a convention of the Kotlin plugin rather than a promise, so the workflow
// asserts the files it expects are there rather than trusting the copy.

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
