package io.kontour.ui.docs.icons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Check
import io.kontour.icons.tabler.outline.Copy
import io.kontour.icons.tabler.outline.MoodEmpty
import io.kontour.icons.tabler.outline.WifiOff
import io.kontour.icons.tabler.outline.X
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.display.EmptyState
import io.kontour.ui.components.display.ErrorState
import io.kontour.ui.components.display.Spinner
import io.kontour.ui.components.list.LoadMore
import io.kontour.ui.components.list.LoadMoreStatus
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.docs.copyText
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.VerticalDivider
import io.kontour.ui.foundation.redacted
import io.kontour.ui.interaction.pressable
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.mono
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Every icon the library has, to search and to copy.
 *
 * Asked for as *"an icon searcher/picker, similar to how fontawesome or google
 * fonts (icons) do it. for now, it should list the icons alphabetically. if you
 * can include some sort of smart/synonym search (where searching for things
 * like "house" would bring up "home", etc.), that would be awesome. it shouldn't
 * load in every icon at once, that would be dumb. it should be infinite scroll
 * though."*
 *
 * **Drawn from data, not from the library.** Naming all six thousand
 * `Tabler.Outline.*` properties here would compile all six thousand into the
 * site's binary. So `docs/icondata.py` lays their paths out beside the site, in
 * chunks of 128, from the same Tabler release the library is generated from,
 * and [IconStore] fetches the index once and a chunk only when one of its icons
 * comes into view.
 *
 * **Infinite, in pages.** The list holds the first [PageSize] results; the
 * library's own [LoadMore] at its foot adds the next page as it comes into
 * view. Alphabetical when nothing is typed; closest first when something is —
 * see [searchIcons].
 *
 * A pressed icon opens beside the list on a wide window and in a sheet on a
 * narrow one, with what to write to use it.
 */
@Composable
internal fun IconsPage(source: IconSource = SiteIconSource) {
    val scope = rememberCoroutineScope()
    val store = remember(source) { IconStore(source, scope) }
    LaunchedEffect(store) { store.load() }

    val search = rememberTextFieldState()
    var query by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(IconStyle.Outline) }
    var selected by remember { mutableStateOf<IconEntry?>(null) }
    val formats = Theme.formats
    val index = store.index
    val results = remember(index, query, style, formats) {
        val all = index?.entries.orEmpty()
        val pool = if (style == IconStyle.Filled) all.filter { it.hasFilled } else all
        searchIcons(pool, query, formats::foldForSearch)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val beside = maxWidth >= DetailBesideWidth
        val stacked = maxWidth < StackedBelowWidth
        val pageWidth = maxWidth
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Header(
                    index = index,
                    found = results.size,
                    query = query,
                    style = style,
                    onStyle = { style = it },
                    stacked = stacked,
                    search = { modifier ->
                        SearchField(
                            state = search,
                            modifier = modifier,
                            // What the list under it holds, which the styles
                            // change: filled is searched on its own.
                            placeholder = index?.let {
                                "Search ${formats.integer(it.count(style))} ${style.objectName.lowercase()} icons"
                            } ?: "Search the icons",
                            onQuery = { query = it },
                        )
                    },
                )
                when {
                    store.failed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        ErrorState(onRetry = { scope.launch { store.load() } }) {
                            +"The icons did not load"
                            message { +"They are fetched beside the page. Check the connection, and try again." }
                            leading { +Tabler.Outline.WifiOff }
                        }
                    }
                    index == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Spinner(size = 32.dp, contentDescription = "Loading the icons")
                    }
                    results.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        EmptyState {
                            +"No icon answers “$query”"
                            message { +"Try another word for it: Tabler tags its icons with what they show and what they are for." }
                            leading { +Tabler.Outline.MoodEmpty }
                        }
                    }
                    else -> IconGrid(
                        results = results,
                        style = style,
                        store = store,
                        selected = selected,
                        onSelect = { selected = if (selected == it) null else it },
                        restingWidth = if (beside && selected != null) {
                            pageWidth - DetailWidth - Theme.sizing.dividerThickness
                        } else {
                            pageWidth
                        },
                    )
                }
            }
            if (beside) {
                DetailBeside(selected = selected, store = store, onClose = { selected = null })
            }
        }
        if (!beside) {
            // Kept while the sheet slides away, so it does not empty as it goes.
            var inSheet by remember { mutableStateOf<IconEntry?>(null) }
            selected?.let { inSheet = it }
            ModalBottomSheet(
                visible = selected != null,
                onDismissRequest = { selected = null },
                paneTitle = inSheet?.name,
            ) { padding ->
                inSheet?.let { entry ->
                    IconDetail(entry = entry, store = store, onClose = { selected = null }, modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun Header(
    index: IconIndex?,
    found: Int,
    query: String,
    style: IconStyle,
    onStyle: (IconStyle) -> Unit,
    /** On a phone: the search across the page, and the styles under it. */
    stacked: Boolean,
    search: @Composable (Modifier) -> Unit,
) {
    val formats = Theme.formats
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Theme.spacing.lg, vertical = Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        Text("Icons", style = Theme.typography.headlineSmall)
        val styles = @Composable { modifier: Modifier ->
            SegmentedControl(
                options = IconStyle.entries.map { it.objectName },
                selectedIndex = style.ordinal,
                onSelectedIndexChange = { onStyle(IconStyle.entries[it]) },
                modifier = modifier,
            )
        }
        if (stacked) {
            search(Modifier.fillMaxWidth())
            styles(Modifier.fillMaxWidth())
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                search(Modifier.weight(1f).widthIn(max = 480.dp))
                styles(Modifier.width(200.dp))
            }
        }
        if (index != null && query.isNotBlank()) {
            Text(
                text = if (found == 1) "1 icon" else "${formats.integer(found)} icons",
                style = Theme.typography.labelSmall,
                colour = Theme.colours.contentMuted,
            )
        }
    }
}

/**
 * The results as rows of cells, as many to a row as fit, [PageSize] at a time.
 *
 * A `LazyColumn` of rows rather than a lazy grid: nothing else on the site uses
 * a lazy grid, and the binary is measured against a ceiling.
 *
 * @param restingWidth The width the list will have once the detail panel beside
 *   it has finished sliding, which is what the columns are counted for. Counted
 *   for the width it had on the way, the grid reflowed at every column the
 *   panel's edge passed; counted for this, it reflows once, as the icon is
 *   pressed, and the cells narrow with the panel's slide.
 */
@Composable
private fun IconGrid(
    results: List<IconEntry>,
    style: IconStyle,
    store: IconStore,
    selected: IconEntry?,
    onSelect: (IconEntry) -> Unit,
    restingWidth: Dp,
) {
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var shown by remember(results) { mutableIntStateOf(PageSize) }
    var loading by remember(results) { mutableStateOf(false) }
    // A new search starts at its top.
    LaunchedEffect(results) { list.scrollToItem(0) }

    Box(Modifier.fillMaxSize()) {
        val gutter = Theme.spacing.lg - CellInset
        val columns = ((restingWidth - gutter * 2) / CellWidth).toInt().coerceAtLeast(1)
        val rows = remember(results, shown, columns) { results.take(shown).chunked(columns) }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = Theme.spacing.lg),
            flingBehavior = ScrollDefaults.flingBehavior(),
        ) {
            // Keyed by the columns too: a row that kept its first icon through a
            // reflow kept its key, and was drawn with its old icons for a frame.
            items(rows.size, key = { "$columns ${rows[it].first().name}" }) { row ->
                Row(Modifier.fillMaxWidth()) {
                    for (entry in rows[row]) {
                        IconCell(
                            entry = entry,
                            style = style,
                            store = store,
                            selected = entry == selected,
                            onClick = { onSelect(entry) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - rows[row].size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item(key = "more") {
                LoadMore(
                    status = when {
                        shown >= results.size -> LoadMoreStatus.End
                        loading -> LoadMoreStatus.Loading
                        else -> LoadMoreStatus.Idle
                    },
                    onLoadMore = {
                        // A frame of Loading between pages, so a foot that is
                        // still in view after a page lands asks again.
                        scope.launch {
                            loading = true
                            shown += PageSize
                            withFrameNanos { }
                            loading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun IconCell(
    entry: IconEntry,
    style: IconStyle,
    store: IconStore,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vector by produceState(store.cached(style, entry), entry, style) {
        if (value == null) value = store.vector(style, entry)
    }
    val shape = Theme.shapes.medium
    val accent = Theme.colours.accent
    Column(
        modifier = modifier
            .padding(CellInset)
            .clip(shape)
            .background(if (selected) accent.container else Color.Transparent)
            .pressable(onClick = onClick, shape = shape, onClickLabel = "Show how to use it")
            .padding(horizontal = Theme.spacing.xs, vertical = Theme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        Box(Modifier.size(GlyphSize), contentAlignment = Alignment.Center) {
            val drawn = vector
            if (drawn != null) {
                Icon(drawn, contentDescription = null, size = GlyphSize, tint = if (selected) accent.onContainer else Theme.colours.content)
            } else {
                Box(Modifier.size(GlyphSize).redacted(enabled = true))
            }
        }
        Text(
            text = entry.name,
            style = Theme.typography.labelSmall,
            colour = if (selected) accent.onContainer else Theme.colours.contentMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The pressed icon beside the list, sliding in from the end and back out.
 *
 * Asked for as *"on desktop, can we make the list/detail side animate in, rather
 * than just appearing"*. The same motion as the library's
 * `SupportingPaneScaffold`: the panel's width goes from nothing to
 * [DetailWidth] while its content is laid out at the full width and clipped, so
 * it slides in rather than being squeezed narrower on every frame, and the list
 * gives up the room as it comes. Under reduced motion it takes its width at once
 * and fades in where it will rest.
 *
 * The icon is kept while the panel goes, so it does not empty as it slides out.
 */
@Composable
private fun DetailBeside(selected: IconEntry?, store: IconStore, onClose: () -> Unit) {
    val motion = Theme.motion
    var kept by remember { mutableStateOf(selected) }
    if (selected != null) kept = selected
    val open = selected != null
    val share by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = if (motion.reduceMotion) snap() else motion.springOrTween(motion.springGentle),
        label = "iconDetail",
    )
    val appear by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = motion.tweenFast(),
        label = "iconDetailFade",
    )
    val fraction = share.coerceIn(0f, 1f)
    val entry = kept
    if (fraction <= 0f || entry == null) return
    VerticalDivider(Modifier.fillMaxHeight().alpha(fraction))
    Box(Modifier.width(DetailWidth * fraction).fillMaxHeight().clipToBounds()) {
        IconDetail(
            entry = entry,
            store = store,
            onClose = onClose,
            modifier = Modifier
                .then(if (motion.reduceMotion) Modifier.alpha(appear) else Modifier)
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .requiredWidth(DetailWidth)
                .fillMaxHeight(),
        )
    }
}

/** One icon, large, in each style it has, with the lines that use it. */
@Composable
private fun IconDetail(
    entry: IconEntry,
    store: IconStore,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val styles = if (entry.hasFilled) IconStyle.entries else listOf(IconStyle.Outline)
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(Theme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.name, style = Theme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(icon = Tabler.Outline.X, contentDescription = "Close", onClick = onClose, size = ButtonSize.Small)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
            for (style in styles) {
                val vector by produceState(store.cached(style, entry), entry, style) {
                    if (value == null) value = store.vector(style, entry)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                    Box(
                        Modifier.size(LargeTile).background(Theme.colours.surfaceSunken, Theme.shapes.large),
                        contentAlignment = Alignment.Center,
                    ) {
                        vector?.let { Icon(it, contentDescription = null, size = LargeGlyph) }
                    }
                    Text(style.objectName, style = Theme.typography.labelSmall, colour = Theme.colours.contentMuted)
                }
            }
        }
        for (style in styles) {
            CodeLine(entry.reference(style), label = "Copy ${style.objectName.lowercase()} property")
            CodeLine(entry.import(style), label = "Copy ${style.objectName.lowercase()} import")
        }
        if (entry.category.isNotEmpty()) {
            Text("Category: ${entry.category}", style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted)
        }
        if (entry.tags.isNotEmpty()) {
            Text("Tagged: ${entry.tags.joinToString(", ")}", style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted)
        }
    }
}

/** A line of code, scrolling sideways if it has to, with a Copy button that says it copied. */
@Composable
private fun CodeLine(code: String, label: String) {
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().background(Theme.colours.surfaceSunken, Theme.shapes.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = Theme.spacing.sm, vertical = Theme.spacing.xs)) {
            Text(code, style = Theme.typography.bodySmall.mono(), colour = Theme.colours.code.plain, softWrap = false)
        }
        IconButton(
            icon = if (copied) Tabler.Outline.Check else Tabler.Outline.Copy,
            contentDescription = if (copied) "Copied" else label,
            onClick = {
                copyText(code)
                copied = true
            },
            size = ButtonSize.Small,
        )
    }
}

/** How many results the list holds at first, and how many each page adds. */
internal const val PageSize = 120

/** A cell's width, or close to it: the row's width is shared out among as many as fit. */
private val CellWidth: Dp = 104.dp

private val CellInset: Dp = 2.dp

private val GlyphSize: Dp = 28.dp

private val LargeTile: Dp = 96.dp

private val LargeGlyph: Dp = 48.dp

/** How wide the page has to be for a pressed icon to open beside the list rather than in a sheet. */
private val DetailBesideWidth: Dp = 840.dp

private val DetailWidth: Dp = 360.dp

/** Below this, the search and the styles stack rather than share a row. */
private val StackedBelowWidth: Dp = 600.dp
