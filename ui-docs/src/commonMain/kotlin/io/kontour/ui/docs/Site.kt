package io.kontour.ui.docs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AdjustmentsHorizontal
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.LayoutGrid
import com.composables.icons.tabler.outline.Menu2
import io.kontour.ui.adaptive.Scaffold
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.adaptive.windowSizeClass
import io.kontour.ui.catalog.Catalog
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.display.CardVariant
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.contract.componentRegistry
import io.kontour.ui.demo.DemoCard
import io.kontour.ui.demo.componentDemos
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.VerticalDivider
import io.kontour.ui.nav.ModalNavDrawer
import io.kontour.ui.nav.NavDrawer
import io.kontour.ui.nav.NavDrawerScope
import io.kontour.ui.nav.TopBar
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * The documentation site.
 *
 * A page per component: the prose, the component itself running above it, and a
 * link into the API reference. One bundle serves every route, so the download is
 * paid once and every page after the first is instant.
 *
 * ### Built out of the library it documents
 *
 * `Scaffold`, `TopBar`, `NavDrawer`, `ModalNavDrawer` — not a hand-rolled `Row`
 * with a hardcoded breakpoint, which is what this was. Dogfooding is the honest
 * reason and there is a practical one too: the site is the largest real
 * application of these components anybody has written, so a component that is
 * awkward here is a component that is awkward.
 *
 * **Not `NavigationSuiteScaffold`**, though it looks like the obvious fit. That
 * takes a flat `List<NavItem>` of three to five icon destinations with a
 * travelling indicator, and this index is a hundred pages in eleven families.
 * Using it would mean either inventing four fake top-level destinations — giving
 * a drawer of four beside a drawer of a hundred — or flattening a tree into a
 * list, which its own KDoc argues against. `NavDrawerScope` is a tree, which is
 * what this is.
 */
@Composable
fun Site() {
    val settings = rememberDisplaySettings()
    val systemDark = isSystemInDarkTheme()
    val route = rememberRoute()

    // Outside the theme, exactly as `Catalog` does it: font scale is a platform
    // setting and the type ramp is in sp, so this is what makes sp mean
    // something different. Scaling the ramp instead would look similar and
    // prove nothing. Layout direction is not a theme parameter either.
    CompositionLocalProvider(
        LocalDensity provides Density(
            LocalDensity.current.density,
            settings.textScale,
        ),
        LocalLayoutDirection provides
            if (settings.rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        KontourTheme(
            darkTheme = settings.dark ?: systemDark,
            contrast = if (settings.highContrast) ContrastLevel.High else ContrastLevel.Standard,
            reduceMotion = settings.reduceMotion,
        ) {
            WindowSizeClassProvider {
                OverlayHost(Modifier.fillMaxSize()) {
                    Shell(settings, systemDark, route.value)
                }
            }
        }
    }
}

@Composable
private fun Shell(settings: DisplaySettings, systemDark: Boolean, route: Route) {
    // The library's own answer to "is there room beside the content", rather
    // than a `>= Medium` comparison written out at the call site. A threshold
    // spelled as a comparison is a threshold that drifts when somebody moves it.
    val besideContent = windowSizeClass.width.hasRoomBeside
    var indexOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

    // A drawer still open over the page you just chose is a drawer you have to
    // dismiss before you can read anything.
    LaunchedEffect(route) { indexOpen = false }

    Scaffold(
        topBar = {
            TopBar(
                navigation = if (besideContent) {
                    null
                } else {
                    {
                        IconButton(
                            icon = Tabler.Outline.Menu2,
                            contentDescription = "Components",
                            onClick = { indexOpen = true },
                        )
                    }
                },
                actions = {
                    IconButton(
                        icon = Tabler.Outline.Home,
                        contentDescription = "Home",
                        onClick = { navigate(Route.Home) },
                        variant = if (route == Route.Home) {
                            ButtonVariant.Tertiary
                        } else {
                            ButtonVariant.Ghost
                        },
                    )
                    IconButton(
                        icon = Tabler.Outline.LayoutGrid,
                        contentDescription = "Gallery",
                        onClick = { navigate(Route.Gallery) },
                        variant = if (route == Route.Gallery) {
                            ButtonVariant.Tertiary
                        } else {
                            ButtonVariant.Ghost
                        },
                    )
                    IconButton(
                        icon = Tabler.Outline.AdjustmentsHorizontal,
                        contentDescription = "Display settings",
                        onClick = { settingsOpen = !settingsOpen },
                    )
                },
                showDivider = true,
            ) {
                +"Kontour UI"
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (besideContent) {
                NavDrawer(
                    width = IndexWidth,
                    header = { IndexSearchHeader() },
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    indexItems(route)
                }
                VerticalDivider(Modifier.fillMaxHeight())
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Content(route)
                if (settingsOpen) {
                    SettingsCard(settings, systemDark) { settingsOpen = false }
                }
            }
        }
    }

    // Below Medium the index is a modal drawer behind the menu button. It used
    // to be nothing at all: the sidebar simply was not rendered under 600dp and
    // no menu replaced it, so a reader who arrived on a component page from a
    // search engine had no route to any other page in the site.
    if (!besideContent) {
        ModalNavDrawer(
            visible = indexOpen,
            onDismissRequest = { indexOpen = false },
            header = { IndexSearchHeader() },
        ) {
            indexItems(route)
        }
    }
}

@Composable
private fun Content(route: Route) {
    when (route) {
        Route.Home -> Home()
        // The gallery brings its own theme, size-class provider and overlay host,
        // so it is not nested inside this one — on a wide window that produced a
        // documentation sidebar beside the gallery's own nav rail, and the
        // masthead's dark switch had no effect on anything inside it.
        Route.Gallery -> Catalog()
        is Route.Component -> ComponentPage(route.slug)
    }
}

/** The search field the drawer keeps above its destinations. */
@Composable
private fun IndexSearchHeader() {
    val search = rememberTextFieldState()
    SearchField(
        state = search,
        modifier = Modifier.fillMaxWidth().padding(Theme.spacing.sm),
        placeholder = "Find a component",
        onQuery = { indexQuery = it },
    )
}

/**
 * The filter, hoisted out of the drawer.
 *
 * There are two drawers — permanent and modal — and only ever one on screen, so
 * holding the query in either would lose it at the breakpoint. It is small and
 * it is genuinely site-wide state.
 */
private var indexQuery by mutableStateOf("")

/** Every page, by family, filtered as you type. */
@Composable
private fun NavDrawerScope.indexItems(current: Route) {
    val matching = remember(indexQuery) {
        if (indexQuery.isBlank()) {
            docPagesByFamily
        } else {
            docPagesByFamily.mapNotNull { (family, pages) ->
                val hits = pages.filter { page ->
                    page.symbols.any { it.contains(indexQuery, ignoreCase = true) } ||
                        page.title.contains(indexQuery, ignoreCase = true)
                }
                if (hits.isEmpty()) null else family to hits
            }
        }
    }

    matching.forEach { (family, pages) ->
        section(family) {
            pages.forEach { page ->
                // `label` is the drawer's selection-indicator key, so two
                // entries sharing one would collide. Page symbols are unique
                // across the tree and `check-components.py` rule 2 keeps them
                // that way.
                item(
                    label = page.symbols.firstOrNull() ?: page.title,
                    selected = current == Route.Component(page.slug),
                ) {
                    navigate(Route.Component(page.slug))
                }
            }
        }
    }
}

/** The display switches, over the content, dismissed by pressing anywhere else. */
@Composable
private fun SettingsCard(
    settings: DisplaySettings,
    systemDark: Boolean,
    onDismissRequest: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        Card(
            variant = CardVariant.Elevated,
            modifier = Modifier.padding(Theme.spacing.md).widthIn(max = 320.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Display", style = Theme.typography.titleSmall)
                Button(
                    onClick = onDismissRequest,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Small,
                ) { +"Done" }
            }
            SettingsPanel(settings, systemDark)
        }
    }
}

@Composable
private fun Home() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PagePadding()),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        Text("Kontour UI", style = Theme.typography.displaySmall)
        Text(
            text = "A Compose Multiplatform design system. ${docPages.size} components, " +
                "each documented on its own page with the component itself running " +
                "beside the words — and running, not pictured: press it.",
            style = Theme.typography.bodyLarge,
            color = Theme.colors.contentMuted,
            modifier = Modifier.widthIn(max = ProseWidth),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
            Button(onClick = { navigate(Route.Gallery) }) { +"Open the gallery" }
            Button(
                onClick = { openExternal("$Repository/tree/main/docs") },
                variant = ButtonVariant.Secondary,
            ) { +"Read the docs on GitHub" }
        }
    }
}

/**
 * A page's padding, which is not the same on a phone as on a desktop.
 *
 * 32dp on each edge of a 390dp window leaves 326dp for the thing the reader came
 * for, and a component demo inside a card inside that has under 300.
 */
@Composable
private fun PagePadding(): PaddingValues =
    PaddingValues(if (windowSizeClass.width.hasRoomBeside) Theme.spacing.xl else Theme.spacing.md)

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
            .padding(PagePadding()),
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
        // `widthIn` outside `fillMaxWidth`, and the order is the whole fix.
        //
        // It was written the other way round. `fillMaxWidth` fixes the
        // constraints at [W, W] first, and `widthIn(max = 760)` then coerces
        // [0, 760] against a minimum of W — so on any window wider than 760dp
        // the cap was discarded and the line ran the full width. On a 1600px
        // display that is a 1600px measure, which is exactly what the comment
        // on `ProseWidth` says it prevents. Written outside, the ceiling is in
        // place before anything fills to it.
        //
        // The sidebar two hundred lines up has always had it in this order,
        // which is why that one worked.
        Prose(page.blocks, Modifier.widthIn(max = ProseWidth).fillMaxWidth())
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
    // A hand-written demo where there is one — real state, and controls for the
    // parameters that are the story.
    componentDemos[page.slug]?.let { demo ->
        Column(
            // The same measure as the prose, and for a sharper reason than
            // tidiness: a component that fills the width it is given gets drawn
            // at whatever the card is, and on a wide display an unbounded card
            // made `DatePicker` a 1,090px calendar with date circles the size of
            // a thumbnail. Nothing on a real screen is that wide. One measure
            // for the page also means the demo and the paragraph explaining it
            // line up, which is most of why a page reads as one thing.
            modifier = Modifier.widthIn(max = ProseWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            Text(
                text = "LIVE",
                style = Theme.typography.monoLabel,
                color = Theme.colors.accent.solid,
            )
            DemoCard(demo)
        }
        return
    }

    // Otherwise the registry specimen, which is better than nothing and worse
    // than a demo: it is stateless by design, so it can be pressed and will not
    // change. Every page that reaches this branch is a page still owed a demo,
    // and `check-components.py` counts them down.
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
