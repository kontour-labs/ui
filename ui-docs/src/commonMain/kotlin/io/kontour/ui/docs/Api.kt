package io.kontour.ui.docs

import androidx.compose.runtime.Immutable

/**
 * The parameter tables, read out of `:ui`'s source at build time.
 *
 * ### Why these are generated
 *
 * Before this the tree contained no parameter table at all — not one, on any of
 * 114 pages. The reason is not that nobody thought of it: 114 hand-written
 * tables are 114 things to keep in step with a library that changes every round,
 * and the first renamed parameter makes all of them suspect. A table nobody
 * trusts is worse than no table, because it is read.
 *
 * So `generateApiTables` runs `KotlinSignatures` — the same reader
 * `checkApiConventions` and `checkKdocSamples` use, which is why it lives in
 * `buildSrc` — over `:ui/src/commonMain/kotlin` and emits this as data. A
 * renamed parameter changes the page on the next build. A removed component
 * empties its table, which the page's own prose then contradicts visibly.
 *
 * ### What it is not
 *
 * Not the API reference. Dokka renders types, KDoc, inheritance and links, and
 * it is already published beside this site. This is the sixty per cent of that
 * which answers "what do I pass" without leaving the page — the question a
 * reader has while looking at the demo, and the one for which a trip to another
 * site is a trip they do not take.
 */
@Immutable
class ApiParameter(
    val name: String,
    val type: String,
    /** The default expression as written, or `null` when the parameter is required. */
    val default: String?,
) {
    val required: Boolean get() = default == null
}

/**
 * One declaration a page documents.
 *
 * @param owner The builder scope this is a member of — `ListItemScope` for
 *   `label`, `supporting`, `leading` and `trailing`. Null for the component
 *   itself. A `ListItem` page that showed only `ListItem(…)`'s four parameters
 *   would be hiding the whole of what a caller actually writes.
 * @param enums The enum types named in the parameters, so the page can say what
 *   `ButtonVariant` *is* rather than only that it is required.
 */
@Immutable
class ApiEntry(
    val name: String,
    val owner: String?,
    val isComposable: Boolean,
    val isClass: Boolean,
    val parameters: List<ApiParameter>,
    val enums: List<String>,
)

/** An enum and its entries, for the values note under a table. */
@Immutable
class ApiEnum(val name: String, val values: List<String>)

/**
 * The API entries for one symbol, built on first ask and kept.
 *
 * `apiBySymbol` holds a builder per symbol rather than the entries themselves.
 * Built eagerly it was 491 `ApiEntry` and 1,925 `ApiParameter` objects
 * constructed before the first frame, for 422 symbols, to draw the table of the
 * one component the reader opened. A non-capturing lambda compiles to a
 * singleton, so what stays eager is 422 references to nothing.
 */
internal fun apiFor(symbol: String): List<ApiEntry> =
    apiCache.getOrPut(symbol) { apiBySymbol[symbol]?.invoke() ?: emptyList() }

private val apiCache = HashMap<String, List<ApiEntry>>()
