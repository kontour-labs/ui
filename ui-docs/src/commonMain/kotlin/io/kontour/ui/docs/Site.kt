package io.kontour.ui.docs

import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.foundation.text.selection.DisableSelection
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AdjustmentsHorizontal
import com.composables.icons.tabler.outline.Api
import com.composables.icons.tabler.outline.FileOff
import com.composables.icons.tabler.outline.FileText
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.LayoutGrid
import com.composables.icons.tabler.outline.Menu2
import io.kontour.ui.adaptive.Scaffold
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.adaptive.windowSizeClass
import io.kontour.ui.catalog.Catalog
import io.kontour.ui.catalog.CatalogSettings
import io.kontour.ui.catalog.hostDensity
import io.kontour.ui.catalog.rememberCatalogSettings
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.display.EmptyState
import io.kontour.ui.components.display.Tag
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.theme.Tone
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.action.TextButton
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.display.CardVariant
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.contract.componentRegistry
import io.kontour.ui.demo.DemoCard
import io.kontour.ui.demo.componentDemos
import io.kontour.ui.foundation.SelectableText
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.VerticalDivider
import io.kontour.ui.nav.NavScope
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.NavigationSuiteType
import io.kontour.ui.nav.rememberNavigationSuiteState
import io.kontour.ui.nav.TopBar
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.Popover
import io.kontour.ui.platform.platformPrefersHighContrast
import io.kontour.ui.platform.platformPrefersReducedMotion
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.demo.theme.DemoThemeProvider
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.deviceInDarkTheme

/**
 * The documentation site.
 *
 * A page per component: the prose, the component itself running above it, and a
 * link into the API reference. One bundle serves every route, so the download is
 * paid once and every page after the first is instant.
 *
 * ### Built out of the library it documents
 *
 * `Scaffold`, `TopBar` and `NavigationSuiteScaffold` — not a hand-rolled `Row`
 * with a hardcoded breakpoint, which is what this was. Dogfooding is the honest
 * reason and there is a practical one too: the site is the largest real
 * application of these components anybody has written, so a component that is
 * awkward here is a component that is awkward.
 *
 * The index is a hundred pages in eleven families: a `NavScope` tree of
 * sections and groups, drawn as a sidebar beside the content where there is
 * room and as a modal rail behind the menu button where there is not.
 */
@Composable
fun Site(settings: CatalogSettings = rememberCatalogSettings()) {
    // `deviceInDarkTheme()`, not Compose's own: this app has a dark switch
    // *and* reports its appearance to the host, and on iOS those are the
    // same property. See `deviceInDarkTheme`.
    val systemDark = deviceInDarkTheme()
    val route = rememberRoute()

    // Outside the theme, exactly as `Catalog` does it: font scale is a platform
    // setting and the type ramp is in sp, so this is what makes sp mean
    // something different. Scaling the ramp instead would look similar and
    // prove nothing. Layout direction is not a theme parameter either.
    //
    // `hostDensity` rather than a `Density(...)` written out here, because the
    // one written out here dropped the browser's own font scale on the floor —
    // and so did the identical line in `Catalog`.
    CompositionLocalProvider(
        LocalDensity provides hostDensity(LocalDensity.current, settings.textScale),
        LocalLayoutDirection provides
            if (settings.rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        // One function, both surfaces, all nine token arguments — see
        // `DemoThemeProvider`. The site used to spell three of them out here and
        // the gallery spelled three out of its own, which is how the two came to
        // disagree about the other six.
        DemoThemeProvider(
            settings = settings,
            systemDark = systemDark,
            systemHighContrast = platformPrefersHighContrast(),
            systemReduceMotion = platformPrefersReducedMotion(),
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
private fun Shell(settings: CatalogSettings, systemDark: Boolean, route: Route) {
    // The library's own answer to "is there room beside the content", rather
    // than a `>= Medium` comparison written out at the call site. A threshold
    // spelled as a comparison is a threshold that drifts when somebody moves it.
    val besideContent = windowSizeClass.width.hasRoomBeside
    val suite = rememberNavigationSuiteState()
    var settingsOpen by remember { mutableStateOf(false) }

    // The modal rail closes itself a frame after a page is chosen from it.
    LaunchedEffect(route) {
        // A family you have just navigated into is one you can see the inside
        // of, whatever you last did to its chevron.
        //
        // Not tidiness: a collapsed group composes none of its rows, and the
        // selection pill is driven by the selected row *reporting* its bounds.
        // `SelectionIndicator` has a branch for the item that stops reporting —
        // "a drawer group collapsed over it, say" — but it only runs when the
        // target is cleared, and `SelectionIndicatorState.clear()` is internal
        // with no callers anywhere in `:ui`. So the pill would stay drawn at the
        // vanished row's last rectangle, over whichever row moved up into it.
        // Forgetting the manual choice for the family you are entering is the
        // one line that makes that unreachable from here.
        (route as? Route.Doc)?.let { doc ->
            docPagesByPath[doc.path]?.let { familyOpen = familyOpen - it.family }
        }
    }

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
                            onClick = suite::showModal,
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
                    // The generated reference, which is Dokka's HTML mounted at
                    // `api/` beside the app rather than a route inside it — so a
                    // new tab, as the per-entry "reference" links open, and never
                    // the selected style: the site is never showing it.
                    IconButton(
                        icon = Tabler.Outline.Api,
                        contentDescription = "API reference",
                        onClick = { openExternal(ApiReferenceHome) },
                        variant = ButtonVariant.Ghost,
                    )
                    // A `Popover` anchored on its own button, which is what
                    // this always wanted to be. It used to be a `Card` inside a
                    // full-size `Box` laid over the content area, and that had
                    // three faults: the comment said "dismissed by pressing
                    // anywhere else" and the Box carried no click handler, so
                    // only "Done" closed it; it was drawn *inside* the content
                    // pane rather than over the whole window, so it appeared
                    // below the bar it belongs to; and nothing connected it to
                    // the control that opened it.
                    //
                    // `Popover` is the library's answer to all three and the
                    // site was already mounting the `OverlayHost` it needs.
                    Box {
                        IconButton(
                            icon = Tabler.Outline.AdjustmentsHorizontal,
                            contentDescription = "Display settings",
                            onClick = { settingsOpen = !settingsOpen },
                        )
                        Popover(
                            visible = settingsOpen,
                            onDismissRequest = { settingsOpen = false },
                            alignment = OverlayAlignment.End,
                        ) {
                            Text("Display", style = Theme.typography.titleSmall)
                            SettingsPanel(settings, systemDark)
                        }
                    }
                },
                showDivider = true,
            ) {
                +"Kontour UI"
            }
        },
    ) { padding ->
        // Beside the content where there is room; below that, a modal rail
        // behind the menu button. It used to be nothing at all under 600dp, so
        // a reader who arrived on a page from a search engine had no route to
        // any other page in the site.
        NavigationSuiteScaffold(
            destinations = { indexItems(route) },
            modifier = Modifier.fillMaxSize().padding(padding),
            state = suite,
            type = if (besideContent) NavigationSuiteType.ExpandedRail else NavigationSuiteType.ModalRail,
            railExpandedWidth = IndexWidth,
            header = { IndexSearchHeader() },
        ) {
            Row(Modifier.fillMaxSize()) {
                if (besideContent) VerticalDivider(Modifier.fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight()) { Content(route, settings) }
            }
        }
    }
}

@Composable
private fun Content(route: Route, settings: CatalogSettings) {
    when (route) {
        Route.Home -> Home()
        // The gallery still brings its own theme, size-class provider and
        // overlay host — on a wide window, nesting inside this one put a
        // documentation sidebar beside the gallery's own nav rail, and the four
        // standalone hosts each need a theme of their own anyway.
        //
        // What is no longer true is that the masthead's switches stop at the
        // boundary. They used to: a nested `KontourTheme` re-resolves every
        // argument it was not given, so the inner one read the platform and drew
        // light however the outer was set. Both now take their values from the
        // same [CatalogSettings], so they agree by construction rather than by
        // anybody keeping two argument lists in step.
        Route.Gallery -> Catalog(settings)
        is Route.Doc -> DocPageView(route.path)
    }
}

/** The search field the index keeps above its destinations. */
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
 * The filter, hoisted out of the index.
 *
 * The index is a sidebar or a modal rail, and only ever one on screen, so
 * holding the query in either would lose it at the breakpoint. It is small and
 * it is genuinely site-wide state.
 */
private var indexQuery by mutableStateOf("")

/**
 * Which families the reader has opened or closed by hand.
 *
 * Absent means "no opinion", and the default below applies. A `Set` of the open
 * ones would not do: a family is open by default while you are reading it, and a
 * set could only ever add to that — pressing the chevron to close the family you
 * are in would compute `open` and get `true` straight back, which is a control
 * that does nothing. An explicit `false` is a different thing from no entry, so
 * the map stores which and the reader always wins.
 *
 * Hoisted for the same reason as [indexQuery]: two surfaces, one on screen.
 */
private var familyOpen by mutableStateOf(emptyMap<String, Boolean>())

/** Every page, by family, filtered as you type. */
@Composable
private fun NavScope.indexItems(current: Route) {
    val matching = remember(indexQuery) {
        if (indexQuery.isBlank()) {
            docPagesByFamily
        } else {
            docPagesByFamily.mapNotNull { family ->
                val hits = family.pages.filter { it.matches(indexQuery) }
                if (hits.isEmpty()) null else DocFamily(family.name, family.index, hits)
            }
        }
    }
    val searching = indexQuery.isNotBlank()

    matching.forEach { family ->
        // Guides stays flat. It is eight rows and it is the way in — putting the
        // map, installing and the tokens behind a chevron hides the things a
        // first-time reader is looking for in order to tidy away the shortest
        // section on the page.
        //
        // Asked as "has no index page", which is true of Guides and only Guides.
        // Reading the kind off `pages.first()` answered the same question by
        // asking a page about its family, and would throw on an empty one.
        if (family.index == null) {
            section(family.name) { indexFamily(family, current) }
            return@forEach
        }
        group(
            // A search reveals its hits whatever the reader last did to a
            // chevron, otherwise typing a name and getting nothing back is the
            // behaviour, and it looks like the search is broken.
            expanded = searching || (familyOpen[family.name] ?: family.holds(current)),
            onExpandedChange = { familyOpen = familyOpen + (family.name to it) },
            key = "family:${family.name}",
            icon = null,
            header = { +family.name },
        ) {
            indexFamily(family, current)
        }
    }
}

/** One family's rows: its own page, then its components. */
@Composable
private fun NavScope.indexFamily(family: DocFamily, current: Route) {
    // The family's own page first, where it has one — the "which one" table and
    // the prose that is about the family rather than any component in it. Round
    // 16 gave those pages routes; before that they were the only part of the
    // tree the site could not show.
    family.index?.let { index -> indexItem(index, current, "Overview") }
    family.pages.forEach { page -> indexItem(page, current) }
}

/**
 * Whether the page being read is in this family.
 *
 * Which is what decides whether it starts open, and is also what keeps the
 * travelling selection pill honest: a collapsed group composes none of its rows,
 * so a selected row inside a closed family would be a selection with nothing to
 * draw on.
 */
private fun DocFamily.holds(route: Route): Boolean =
    route is Route.Doc && (index?.path == route.path || pages.any { it.path == route.path })

/**
 * One destination, keyed by its path: unique across the tree, where a label is
 * not — every family's "Overview" row shares one.
 */
@Composable
private fun NavScope.indexItem(page: DocPage, current: Route, label: String? = null) {
    item(
        selected = current == Route.Doc(page.path),
        onClick = { navigate(Route.Doc(page.path)) },
        key = page.path,
        icon = null,
    ) {
        +(label ?: page.indexLabel)
    }
}

/** Whether a page answers what was typed into the index's search field. */
private fun DocPage.matches(query: String): Boolean =
    symbols.any { it.contains(query, ignoreCase = true) } ||
        title.contains(query, ignoreCase = true)

@Composable
private fun Home() {
    // Counted, not claimed. This said `docPages.size` and meant "components",
    // which was wrong the moment the guides got routes of their own — and the
    // README has carried a hand-written "138 components" for four rounds that
    // matched nothing measurable.
    val components = remember { docPages.count { it.kind == DocKind.Component } }
    // The same order the sidebar uses, which is `doctree.GUIDES` — the map, then
    // how to install it, then the tokens and the theme. Sorted by title, this
    // list and the sidebar's showed the same eight pages in two different
    // orders, and neither was a reading order.
    val guides = remember { docPages.filter { it.kind == DocKind.Guide }.sortedBy { it.order } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), flingBehavior = ScrollDefaults.flingBehavior())
            .padding(PagePadding()),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        Text("Kontour UI", style = Theme.typography.displaySmall)
        Text(
            text = "A Compose Multiplatform design system built on Compose Foundation, " +
                "with no Material. $components components, each on its own page with the " +
                "component itself running beside the words — running, not pictured: press it.",
            style = Theme.typography.bodyLarge,
            colour = Theme.colours.contentMuted,
            modifier = Modifier.widthIn(max = ProseWidth),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
            Button(onClick = { navigate(Route.Doc("components")) }) { +"All components" }
            Button(
                onClick = { navigate(Route.Gallery) },
                variant = ButtonVariant.Secondary,
            ) { +"Open the gallery" }
        }

        Text(
            text = "Guides",
            style = Theme.typography.titleMedium,
            modifier = Modifier.padding(top = Theme.spacing.lg),
        )
        ListGroup(modifier = Modifier.widthIn(max = ProseWidth).fillMaxWidth()) {
            guides.forEach { page ->
                item(
                    label = page.heading,
                    supporting = page.shortSummary,
                    onClick = { navigate(Route.Doc(page.path)) },
                )
            }
        }
    }
}

/**
 * The page's opening line, cut to fit a list row.
 *
 * The extraction moved to `docs/generate-doc-pages.py` and arrives as
 * [DocPage.summary]; only the truncation is left here, which is where it
 * belongs — [SummaryLength] is how much room this list has, not a fact about
 * the page.
 *
 * What that bought: this used to read [DocPage.blocks], so drawing the landing
 * page built the prose of all seven guides on it. `CorpusLazinessTest` has the
 * assertion that would have caught it, now that there is one.
 */
internal val DocPage.shortSummary: String
    get() = if (summary.length <= SummaryLength) {
        summary
    } else {
        summary.take(SummaryLength).substringBeforeLast(' ') + "…"
    }

/**
 * "LIVE", on the card holding a component a reader can actually press.
 *
 * A [Tag] rather than accent-coloured text, which is what it was. The label is
 * a badge in everything but implementation — it names a *state* of the thing
 * beneath it — and drawing it as a bare coloured word left it with no ground,
 * no shape and no relationship to the `Tag` on any page that documents one.
 */
@Composable
private fun LiveTag() {
    Tag(tone = Tone.Accent) { +"LIVE" }
}

/** Two lines of supporting text at the narrowest width the index is drawn at. */
private const val SummaryLength = 130

/**
 * What to put at the top of the page, and in the index.
 *
 * A component page is named by what it documents — "NavBar / NavRail /
 * ModalNavRail" is better than "Nav surfaces". A guide is named by its title, with
 * the markdown taken out: `dsls.md` is called "Slots, and the `+` that keeps
 * them short", whose only backticked run is `+`, so reading symbols off it gave
 * a page headed "+" — and, while there was a page-level *API reference* button,
 * one that searched Dokka for a plus sign. The button is gone, replaced by a
 * link beside each entry in the table, but the heading rule it exposed is the
 * same rule and is still what stops a guide being titled after its punctuation.
 */
private val DocPage.heading: String
    get() = when (kind) {
        DocKind.Component -> symbols.joinToString(" / ").ifEmpty { plainTitle }
        else -> plainTitle
    }

/**
 * The name in the sidebar.
 *
 * The *first* symbol rather than all of them: a page that documents three
 * would wrap to three lines in a 280dp sidebar.
 */
private val DocPage.indexLabel: String
    get() = if (kind == DocKind.Component) symbols.firstOrNull() ?: plainTitle else plainTitle

/** The title with its markdown removed, for the places that draw it as text. */
private val DocPage.plainTitle: String get() = title.replace("`", "")

/**
 * A page's padding, which is not the same on a phone as on a desktop.
 *
 * 32dp on each edge of a 390dp window leaves 326dp for the thing the reader came
 * for, and a component demo inside a card inside that has under 300.
 */
@Composable
private fun PagePadding(): PaddingValues =
    PaddingValues(if (windowSizeClass.width.hasRoomBeside) Theme.spacing.xl else Theme.spacing.md)

/**
 * One page: the prose, and — for a component — the thing it is about.
 *
 * Every page in `ui-docs/content/` comes through here, which it did not before:
 * this took a component slug, so the guides had no route and a link to one
 * ejected the reader to GitHub.
 */
@Composable
private fun DocPageView(path: String) {
    val page = docPagesByPath[path]
    if (page == null) {
        // `EmptyState` rather than a Box, a Column and two children arranged by
        // hand. It is the component the library ships for exactly this — a
        // title, a line saying how to get out of it, and one action — and the
        // site drawing its own was the site not eating its own cooking.
        //
        // Its KDoc's rule applies here too: the message says how to leave,
        // rather than restating the title. "No page called x" followed by
        // "that page does not exist" would tell a reader nothing.
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            EmptyState {
                +"No page called “$path”"
                message { +"It may have been renamed. The index lists every page." }
                leading { +Tabler.Outline.FileOff }
                action {
                    Button(onClick = { navigate(Route.Home) }, variant = ButtonVariant.Secondary) {
                        +"Back to the index"
                    }
                }
            }
        }
        return
    }

    // Every relative link on this page is relative to *this* page, and nothing
    // below knows which page it is drawing. `Prose` is several layers down and
    // resolves links inside `buildAnnotatedString`, so a parameter would have to
    // be threaded through five composables that have no other use for it.
    CompositionLocalProvider(LocalDocPath provides page.path) {
        // Windowed, and it is the difference between a page and a book.
        //
        // This was a `Column` in a `verticalScroll`, so every block of the page
        // composed, measured and — the expensive part — *shaped its text* at
        // once, and stayed composed. The tokens page is 372 leaf text nodes; a
        // phone shows about ten of them. Shaping is the most expensive thing the
        // renderer does and it was being done for thirty times more text than
        // anybody could see.
        //
        // The whole page is one selection region: the title, the prose, the
        // code and the parameter tables can be selected and copied, and a
        // selection runs across blocks. It wraps the list rather than each
        // block because a region per block would stop a drag at every
        // paragraph. What a lazy list has not composed cannot be selected, so a
        // selection reaches as far as the window does; "Open as text" is for
        // the rest, and for the browser's find-in-page, which a canvas cannot
        // offer.
        SelectableText(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PagePadding(),
                flingBehavior = ScrollDefaults.flingBehavior(),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                        Text(text = page.heading, style = Theme.typography.displaySmall)
                        // The page's Markdown, which `docs/lay-out-site.py` writes
                        // beside the app: plain text a browser can search and an
                        // agent can read. Relative, so it resolves under whatever
                        // path the site is served from.
                        TextButton(onClick = { openExternal(page.textUrl) }) {
                            +Tabler.Outline.FileText
                            +"Open as text"
                        }
                    }
                }
                // No "Edit this page". The markdown behind these pages is a build
                // input — the site's routing table, family tree and demo binding
                // as much as its prose — and sending a reader to a file in a
                // repository is the same mistake as the screenshots that used to
                // sit at the top of every one of them: it treats the repository as
                // a second place to read the documentation, and it is not one.
                //
                // Out of the selection: the demos are things to press and drag,
                // and a drag across a slider that selected the page instead would
                // be a demo that does not work.
                item { DisableSelection { Specimens(page) } }

                // `widthIn` outside `fillMaxWidth`, and the order is the whole fix.
                //
                // It was written the other way round. `fillMaxWidth` fixes the
                // constraints at [W, W] first, and `widthIn(max = 760)` then coerces
                // [0, 760] against a minimum of W — so on any window wider than
                // 760dp the cap was discarded and the line ran the full width. On a
                // 1600px display that is a 1600px measure, which is exactly what the
                // comment on `ProseWidth` says it prevents. Written outside, the
                // ceiling is in place before anything fills to it.
                //
                // The sidebar two hundred lines up has always had it in this order,
                // which is why that one worked.
                prose(page.blocks, Modifier.widthIn(max = ProseWidth).fillMaxWidth())

                item {
                    ApiSection(page, Modifier.widthIn(max = ProseWidth).fillMaxWidth())
                }
            }
        }
    }
}

/**
 * The reference's front page: `docs/lay-out-site.py --api` mounts Dokka's HTML at
 * `api/`, relative to the app, and each entry's "reference" link goes further in.
 */
internal const val ApiReferenceHome = "api/index.html"

/** Where `docs/lay-out-site.py` mirrors this page as Markdown, relative to the site. */
internal val DocPage.textUrl: String get() = "md/$path.md"

/**
 * The component itself, running, above its prose.
 *
 * From `componentRegistry` — the same list the contract suite asserts over and
 * the renders are drawn from. A picture would be the alternative and it is the
 * one thing this site can do that the markdown cannot, so it does not draw one:
 * the specimen *is* the picture, and it can be pressed.
 *
 * Nothing shows for a component the registry does not carry; several are
 * genuinely not specimens — `LocaleFormats` is a set of formats.
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
            LiveTag()
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
        LiveTag()
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
                            colour = Theme.colours.contentMuted,
                        )
                    }
                    spec.content(Modifier, true) {}
                }
            }
        }
    }
}


/**
 * 320, not the 280 it was, and the collapsible index is why.
 *
 * A component's rows sit one nest level inside their family's chevron, which is
 * `NavRailDefaults.NestIndent` — 24dp — off the leading edge, and a rail row
 * draws its label at `maxLines = 1`: it truncates rather than
 * wrapping. At 280 that ate the tail of the longest names in the tree, and
 * `ExtendedFloatingActionButton` came out as "ExtendedFloatingActionButt".
 *
 * Widening here rather than shrinking `NestIndent`, which is a library token
 * every rail in every consumer draws against. This is the site's own measure.
 *
 * Kept below `SiteRenderTest.IndexWidth`, which is where that test starts
 * looking for a page's own ink — if the index grew past it, a page with an empty
 * content area would pass on the strength of the sidebar beside it.
 */
private val IndexWidth = 320.dp

/** Long-form prose is read, and a 1600px line is not. */
private val ProseWidth = 760.dp
