package io.kontour.ui.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.adaptive.WindowWidthClass
import io.kontour.ui.adaptive.windowSizeClass
import io.kontour.ui.catalog.Catalog
import io.kontour.ui.contract.componentRegistry
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.display.CardVariant
import io.kontour.ui.components.list.ListGroup
import androidx.compose.foundation.text.input.rememberTextFieldState
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme

/**
 * The documentation site.
 *
 * A page per component: the prose from `docs/using/components/`, the component
 * itself running beside it, and a link into the API reference. One bundle
 * serves every route, so the download is paid once and every page after the
 * first is instant.
 */
@Composable
fun Site() {
    var dark by remember { mutableStateOf(false) }
    val route = rememberRoute()

    KontourTheme(darkTheme = dark) {
        WindowSizeClassProvider {
            OverlayHost(Modifier.fillMaxSize()) {
                Surface(Modifier.fillMaxSize(), color = Theme.colors.background) {
                    val wide = windowSizeClass.width >= WindowWidthClass.Medium
                    Column(Modifier.fillMaxSize()) {
                        Masthead(
                            dark = dark,
                            onDarkChange = { dark = it },
                            route = route.value,
                        )
                        HorizontalDivider()
                        Row(Modifier.fillMaxSize()) {
                            // The index is a column beside the content when
                            // there is room, and the home page when there is
                            // not — a 240dp sidebar on a phone leaves 120dp for
                            // the thing the reader came for.
                            if (wide) {
                                Index(
                                    current = route.value,
                                    modifier = Modifier
                                        .widthIn(max = IndexWidth)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState()),
                                )
                                HorizontalDivider(Modifier.fillMaxHeight().widthIn(max = 1.dp))
                            }
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                when (val here = route.value) {
                                    Route.Home -> Home(showIndex = !wide)
                                    Route.Gallery -> Catalog()
                                    is Route.Component -> ComponentPage(here.slug)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Masthead(dark: Boolean, onDarkChange: (Boolean) -> Unit, route: Route) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.colors.surface)
            .padding(horizontal = Theme.spacing.lg, vertical = Theme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { navigate(Route.Home) },
            variant = ButtonVariant.Ghost,
            size = ButtonSize.Small,
        ) { +"Kontour UI" }

        Box(Modifier.weight(1f))

        Button(
            onClick = { navigate(Route.Gallery) },
            variant = if (route == Route.Gallery) {
                ButtonVariant.Tertiary
            } else {
                ButtonVariant.Ghost
            },
            size = ButtonSize.Small,
        ) { +"Gallery" }

        Button(
            onClick = { onDarkChange(!dark) },
            variant = ButtonVariant.Ghost,
            size = ButtonSize.Small,
        ) { +(if (dark) "Light" else "Dark") }
    }
}

/** Every page, by family, filtered as you type. */
@Composable
private fun Index(current: Route, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val matching = remember(query) {
        if (query.isBlank()) {
            docPagesByFamily
        } else {
            docPagesByFamily.mapNotNull { (family, pages) ->
                val hits = pages.filter { page ->
                    page.symbols.any { it.contains(query, ignoreCase = true) } ||
                        page.title.contains(query, ignoreCase = true)
                }
                if (hits.isEmpty()) null else family to hits
            }
        }
    }

    Column(modifier.padding(Theme.spacing.md)) {
        val search = rememberTextFieldState()
        SearchField(
            state = search,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Find a component",
            onQuery = { query = it },
        )
        // A plain Column, not a LazyColumn.
        //
        // It was lazy, and on a phone the home page puts it inside a
        // `verticalScroll` — which measures its children at infinite height, and
        // a lazy list handed infinite height *throws*. So the landing page of
        // this site raised `IllegalStateException` on every window narrower than
        // 600dp, which is to say on every phone. Nothing caught it because
        // `:ui-docs` was a wasmJs-only module: there was no test source set that
        // could run here, so there was nowhere to put a test even if somebody
        // had thought to write one.
        //
        // Laziness bought nothing. It is eighty-odd rows of text with no images
        // and no measurement worth deferring, and being lazy is precisely what
        // made it unable to live inside a scrolling parent. Whoever contains
        // this owns the scrolling now.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            matching.forEach { (family, pages) ->
                Text(
                    text = family.uppercase(),
                    style = Theme.typography.monoLabel,
                    color = Theme.colors.accent.solid,
                    modifier = Modifier.padding(bottom = Theme.spacing.xs),
                )
                ListGroup(spacing = 2.dp) {
                    pages.forEach { page ->
                        item(
                            label = page.symbols.firstOrNull() ?: page.title,
                            selected = current == Route.Component(page.slug),
                            onClick = { navigate(Route.Component(page.slug)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Home(showIndex: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Theme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        Text("Kontour UI", style = Theme.typography.displaySmall)
        Text(
            text = "A Compose Multiplatform design system. ${docPages.size} components, " +
                "each documented on its own page with the component itself running " +
                "beside the words.",
            style = Theme.typography.bodyLarge,
            color = Theme.colors.contentMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
            Button(onClick = { navigate(Route.Gallery) }) { +"Open the gallery" }
            Button(
                onClick = { openExternal("$Repository/tree/main/docs") },
                variant = ButtonVariant.Secondary,
            ) { +"Read the docs on GitHub" }
        }
        if (showIndex) {
            HorizontalDivider(Modifier.padding(vertical = Theme.spacing.md))
            Index(current = Route.Home, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** One component: the prose, and the component. */
@Composable
private fun ComponentPage(slug: String) {
    val page = docPagesBySlug[slug]
    if (page == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No page called “$slug”.", style = Theme.typography.titleMedium)
                Button(onClick = { navigate(Route.Home) }, variant = ButtonVariant.Ghost) {
                    +"Back to the index"
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Theme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        Text(
            text = page.symbols.joinToString(" / ").ifEmpty { page.title },
            style = Theme.typography.displaySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
            page.symbols.firstOrNull()?.let { symbol ->
                Button(
                    onClick = { openExternal(apiUrl(symbol)) },
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Small,
                ) { +"API reference" }
            }
            Button(
                onClick = { openExternal("$Repository/blob/main/docs/using/components/$slug.md") },
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
            ) { +"Edit this page" }
        }

        Specimens(page)
        Prose(page.blocks, Modifier.fillMaxWidth().widthIn(max = ProseWidth))
    }
}

/**
 * The component itself, running, above its prose.
 *
 * From `componentRegistry` — the same list the contract suite asserts over and
 * the renders are drawn from. A picture would be the alternative and it is the
 * one thing this site can do that the markdown cannot, so it does not draw one:
 * the specimen *is* the picture, and it can be pressed.
 *
 * Nothing shows for a component the registry does not carry; several are
 * genuinely not specimens — `DateTimeFormats` is a set of formats.
 */
@Composable
private fun Specimens(page: DocPage) {
    val specimens = remember(page.slug) {
        componentRegistry.filter { spec ->
            page.symbols.any { symbol ->
                spec.name == symbol || spec.name.startsWith("$symbol (")
            }
        }
    }
    if (specimens.isEmpty()) return

    Card(variant = CardVariant.Outlined, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "LIVE",
            style = Theme.typography.monoLabel,
            color = Theme.colors.accent.solid,
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            specimens.forEach { spec ->
                Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                    if (specimens.size > 1) {
                        Text(
                            text = spec.name,
                            style = Theme.typography.labelSmall,
                            color = Theme.colors.contentMuted,
                        )
                    }
                    spec.content(Modifier, true) {}
                }
            }
        }
    }
}

/** Dokka puts a symbol at a path derived from its package and name. */
private fun apiUrl(symbol: String): String =
    "api/index.html?query=${symbol.substringAfterLast('.')}"

private val IndexWidth = 280.dp

/** Long-form prose is read, and a 1600px line is not. */
private val ProseWidth = 760.dp
