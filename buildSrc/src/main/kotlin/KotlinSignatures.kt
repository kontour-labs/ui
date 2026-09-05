/**
 * A very small Kotlin signature reader.
 *
 * In `buildSrc` rather than inside a build script because three tasks in two
 * modules need it: `checkApiConventions` and `checkKdocSamples` in `:ui`, and
 * `generateApiTables` in `:ui-docs`, which turns these same declarations into
 * the parameter table on every component page. A second copy of a parser is a
 * second set of answers, and the whole value of generating those tables is that
 * they cannot disagree with the rules the conventions check enforces.
 *
 * The cost, stated rather than discovered: `buildSrc` did not exist here before,
 * every build now compiles it, and a change to it invalidates the whole build.
 * For one object shared by three consumers that is the right trade; for the next
 * thing somebody wants to put here it may not be.
 *
 * The two original consumers had each grown their own answer to "given a
 * declaration, what does it take?" — one line-based and one bracket-based. The
 * line-based one could not see a declaration that was indented, and 130 of
 * `:ui`'s 391 declarations are: every method on every builder scope, which is
 * precisely where the naming conventions drift.
 *
 * A top-level `object` rather than a function: a `doLast` lambda referencing a
 * script function would capture the script instance and break the configuration
 * cache, which is on. A singleton is a static reference and does not. Verified
 * rather than assumed, before any of this was written.
 *
 * This is not a parser. It reads far enough to answer "what is this called and
 * what does it take", which is all any of the three asks.
 */
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

    /**
     * The entries of every enum class in a file, by enum name.
     *
     * `variant: ButtonVariant = ButtonVariant.Filled` tells a reader the type
     * and nothing about the choice, and the choice is the question they had.
     * Reading the entries here means the list on the page cannot fall behind
     * the enum, which is the failure mode of writing it out by hand.
     *
     * Entries only — the members after the `;` are skipped, since those are the
     * enum's implementation rather than its options.
     */
    fun enums(text: String): Map<String, List<String>> {
        val clean = withoutComments(text)
        val header = Regex("""^[ \t]*(?:(?:public|internal|private)\s+)?enum\s+class\s+(\w+)""", RegexOption.MULTILINE)
        return header.findAll(clean).mapNotNull { match ->
            val open = clean.indexOf('{', match.range.last)
            if (open < 0) return@mapNotNull null
            val body = clean.substring(open + 1, balancedBrace(clean, open))
            // An entry may carry a constructor call or a body of its own, so
            // split at depth zero and keep the leading identifier of each.
            val entries = topLevel(body).substringBefore(';')
                .split(',')
                .mapNotNull { Regex("""^\s*([A-Z]\w*)""").find(it)?.groupValues?.get(1) }
            if (entries.isEmpty()) null else match.groupValues[1] to entries
        }.toMap()
    }

    /** Index of the `}` closing the `{` at [start]. */
    fun balancedBrace(text: String, start: Int): Int {
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return i
            }
        }
        return text.length
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
     *
     * A **spaced** `<` or `>` is masked for the mirror-image reason. `MultiSelect`
     * defaults `summary` to a lambda containing `labels.size <= 3`, and that `<`
     * opened a bracket depth nothing ever closed — so every one of the eight
     * parameters after it vanished, and `checkApiConventions` had been silently
     * skipping them. Kotlin never writes a type argument with a space before the
     * angle bracket, so the space is the whole difference between `Set<T>` and a
     * comparison, and it is enough.
     *
     * Every mask is the same length as what it replaces: callers compute offsets
     * here and then read the *original* string at them.
     */
    fun topLevel(raw: String): String {
        val masked = comparisons.replace(withoutComments(raw).replace("->", "  ")) {
            " ".repeat(it.value.length)
        }
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

    /** ` < `, ` <= `, ` > `, ` >= ` — an operator, never a type argument. */
    private val comparisons = Regex("""(?<=\s)(<=|>=|<|>)(?=\s)""")

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
    private val parameterTag = Regex("""^@(?:param|property)\s+(\w+)\s*(.*)$""")

    private val packageHeader = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)

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
        /** Leading whitespace, so a nested type can be told from a top-level one. */
        val indent: Int = 0,
    ) {
        val isPublic: Boolean get() = visibility == "public"

        /** Declared at the top of a file, rather than inside another type. */
        val isTopLevel: Boolean get() = indent == 0
    }

    /**
     * The `@param` and `@property` descriptions in the KDoc above the
     * declaration that starts on [line], keyed by the name each documents.
     *
     * [line] comes from a [Declaration], which is found in the *commentless*
     * text — and that is exactly why this can work at all: [withoutComments]
     * replaces a comment with spaces rather than removing it, so the two texts
     * agree on every line number and a declaration found in one can be located
     * in the other.
     *
     * Returned whole. Whether a table wants the first sentence, all of it, or
     * none is a question about that table, and one `@param` in this library is
     * 1,707 characters with a `###` heading in it.
     */
    fun parameterDocs(text: String, line: Int): Map<String, String> {
        val lines = text.split("\n")

        // Annotations sit between the KDoc and the declaration, and a blank
        // line is legal there too.
        var last = line - 2
        while (last >= 0 && (lines[last].isBlank() || lines[last].trimStart().startsWith("@"))) last--
        if (last < 0 || !lines[last].trimEnd().endsWith("*/")) return emptyMap()

        var first = last
        while (first >= 0 && !lines[first].trimStart().startsWith("/**")) {
            // A plain `/* … */` above a declaration is not its documentation,
            // and walking past one would attach the *previous* declaration's
            // KDoc to this one.
            if (lines[first].trimStart().startsWith("/*")) return emptyMap()
            first--
        }
        if (first < 0) return emptyMap()

        val found = linkedMapOf<String, MutableList<String>>()
        var current: String? = null
        for (raw in lines.subList(first, last + 1)) {
            val body = raw.trim().removePrefix("/**").removePrefix("*/").removePrefix("*").trim()
            val tag = parameterTag.matchEntire(body)
            when {
                tag != null -> {
                    current = tag.groupValues[1]
                    found[current] = mutableListOf(tag.groupValues[2])
                }
                // Any other tag ends the one being read: `@param` descriptions
                // run on across lines, so only a new tag can stop them.
                body.startsWith("@") -> current = null
                current != null -> found.getValue(current) += body
            }
        }

        return found
            .mapValues { (_, parts) -> parts.joinToString(" ").replace(Regex("\\s+"), " ").trim() }
            .filterValues { it.isNotEmpty() }
    }

    /**
     * The package a file declares, or `null` for one that declares none.
     *
     * Read through [withoutComments] like everything else here, so a `package`
     * line inside a KDoc sample is not mistaken for the file's own — the same
     * class of mistake that made a `fun` in a comment read as a declaration.
     */
    fun packageName(text: String): String? =
        packageHeader.find(withoutComments(text))?.groupValues?.get(1)?.trim()

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
                indent = match.groupValues[1].length,
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
