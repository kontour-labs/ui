package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AdjustmentsHorizontal
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.Click
import com.composables.icons.tabler.outline.Forms
import com.composables.icons.tabler.outline.LayoutBottombar
import com.composables.icons.tabler.outline.LayoutGrid
import com.composables.icons.tabler.outline.LayoutList
import com.composables.icons.tabler.outline.LayoutSidebar
import com.composables.icons.tabler.outline.Menu2
import com.composables.icons.tabler.outline.Palette
import com.composables.icons.tabler.outline.SquareRoundedLetterT
import com.composables.icons.tabler.outline.Stack2
import com.composables.icons.tabler.outline.Typography
import com.composables.icons.tabler.outline.Windmill
import io.kontour.ui.adaptive.Scaffold
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.adaptive.WindowWidthClass
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.nav.ModalNavDrawer
import io.kontour.ui.nav.NavItem
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.TopBar
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.ToastHost
import io.kontour.ui.overlay.rememberToastHostState
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme

/** One page of the gallery. */
private class Page(
    val title: String,
    val icon: ImageVector,
    val content: @Composable (Modifier) -> Unit,
)

private val pages = listOf(
    Page("Tokens", Tabler.Outline.Palette) { ThemeShowcase(it) },
    Page("Actions", Tabler.Outline.Click) { ButtonShowcase(it) },
    Page("Selection", Tabler.Outline.Forms) { SelectionShowcase(it) },
    Page("Text", Tabler.Outline.SquareRoundedLetterT) { TextShowcase(it) },
    Page("Forms", Tabler.Outline.Typography) { SelectShowcase(it) },
    Page("Date & time", Tabler.Outline.Calendar) { DateTimeShowcase(it) },
    Page("Display", Tabler.Outline.LayoutGrid) { DisplayShowcase(it) },
    Page("Lists", Tabler.Outline.LayoutList) { ListShowcase(it) },
    Page("Overlays", Tabler.Outline.Stack2) { OverlayShowcase(it) },
    Page("Sheets", Tabler.Outline.LayoutBottombar) { SheetShowcase(it) },
    Page("Navigation", Tabler.Outline.Windmill) { NavShowcase(it) },
    Page("Adaptive", Tabler.Outline.LayoutSidebar) { AdaptiveShowcase(it) },
)

/**
 * The component gallery — every component, in every state, on every platform.
 *
 * Runs on all five targets from the same source. That is the point: a component
 * that looks right in a JVM golden and wrong on iOS is a component the goldens
 * cannot catch, and this is where you find out.
 *
 * The controls in the sheet are the ones that matter and are hardest to check by
 * eye. Each corresponds to a real user setting the app cannot refuse:
 *
 * | | |
 * |---|---|
 * | Dark, high contrast | The four built-in schemes |
 * | Text size | Up to 200%, the accessibility maximum on both platforms |
 * | Right to left | Arabic and Hebrew locales, where every start/end mistake shows |
 * | Input modality | Forces the touch/pointer/keyboard branch — focus rings, hover, scrollbars and tooltip triggers all key off it, and on a desktop host you would otherwise only ever see the pointer branch |
 * | Reduce motion | Every animation in the system honours it; this is how you check |
 *
 * Above compact, navigation is a rail on medium and a drawer on expanded,
 * decided by [NavigationSuiteScaffold]. Resizing the window is itself a test.
 * Compact gets a menu button and a modal drawer instead — see [CompactCatalog]
 * for why a gallery is not the shape a bottom bar is for.
 */
@Composable
fun Catalog() {
    var dark by remember { mutableStateOf(false) }
    var highContrast by remember { mutableStateOf(false) }
    var fontScale by remember { mutableStateOf(1f) }
    var rtl by remember { mutableStateOf(false) }
    var reduceMotion by remember { mutableStateOf(false) }
    var modality by remember { mutableStateOf<InputModality?>(null) }
    var selected by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }

    val density = LocalDensity.current

    CompositionLocalProvider(
        // Font scale is applied here rather than inside the theme because it is a
        // *platform* setting: the theme's type ramp is in sp, and this is what
        // makes sp mean something different. Scaling the ramp instead would look
        // similar and prove nothing.
        LocalDensity provides Density(density.density, fontScale),
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        KontourTheme(
            darkTheme = dark,
            contrast = if (highContrast) ContrastLevel.High else ContrastLevel.Standard,
            reduceMotion = reduceMotion,
        ) {
            // Overriding the modality has to happen *inside* the theme, which
            // installs the tracker that would otherwise set it from real input.
            val overridden = modality
            CompositionLocalProvider(
                LocalInputModality provides (overridden ?: LocalInputModality.current)
            ) {
                WindowSizeClassProvider(Modifier.fillMaxSize()) {
                    OverlayHost {
                        // Where a specimen with no state of its own sends its
                        // press, so that nothing in the gallery is wired to a
                        // callback you cannot tell is being called. Remembered
                        // rather than written inline: the local is static, and a
                        // fresh lambda every recomposition would recompose the
                        // whole gallery under it.
                        val toasts = rememberToastHostState()
                        ToastHost(toasts)

                        val settingsButton = @Composable {
                            IconButton(
                                icon = Tabler.Outline.AdjustmentsHorizontal,
                                contentDescription = "Display settings",
                                onClick = { settingsOpen = true },
                            )
                        }

                        CompositionLocalProvider(
                            LocalCatalogEcho provides remember(toasts) {
                                { what: String -> toasts.show(what) }
                            }
                        ) {
                            // Twelve destinations is more than a bottom bar can hold
                            // — the labels truncate to three letters each — so on
                            // compact the catalog drives its own modal drawer rather
                            // than taking what `NavigationSuiteScaffold` would pick.
                            // That is not a gap in the scaffold: it chooses correctly
                            // for an app with three to five destinations, which a
                            // gallery is not. Above compact the scaffold's choice is
                            // right and it makes it.
                            if (LocalWindowSizeClass.current.width == WindowWidthClass.Compact) {
                                CompactCatalog(
                                    selected = selected,
                                    onSelectedChange = { selected = it },
                                    action = settingsButton,
                                )
                            } else {
                                NavigationSuiteScaffold(
                                    items = pages.mapIndexed { index, page ->
                                        NavItem(
                                            label = page.title,
                                            icon = page.icon,
                                            onClick = { selected = index },
                                        )
                                    },
                                    selectedIndex = selected,
                                    action = settingsButton,
                                ) { contentPadding ->
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(bottom = contentPadding)
                                    ) {
                                        pages[selected].content(Modifier.fillMaxWidth())
                                    }
                                }
                            }

                            SettingsSheet(
                                visible = settingsOpen,
                                dark = dark,
                                onDarkChange = { dark = it },
                                highContrast = highContrast,
                                onHighContrastChange = { highContrast = it },
                                fontScale = fontScale,
                                onFontScaleChange = { fontScale = it },
                                rtl = rtl,
                                onRtlChange = { rtl = it },
                                reduceMotion = reduceMotion,
                                onReduceMotionChange = { reduceMotion = it },
                                modality = modality,
                                onModalityChange = { modality = it },
                                onDismiss = { settingsOpen = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The compact layout: a top bar with a menu button, and the destinations in a
 * modal drawer.
 *
 * A gallery is a browsing surface, not an app with a handful of places you flick
 * between, so the list is somewhere you go rather than something always present.
 */
@Composable
private fun CompactCatalog(
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    action: @Composable () -> Unit,
) {
    var drawerOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopBar(
                navigation = {
                    IconButton(
                        icon = Tabler.Outline.Menu2,
                        contentDescription = "Destinations",
                        onClick = { drawerOpen = true },
                    )
                },
                actions = { action() },
            ) {
                +pages[selected].title
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
        ) {
            pages[selected].content(Modifier.fillMaxWidth())
        }
    }

    ModalNavDrawer(visible = drawerOpen, onDismissRequest = { drawerOpen = false }) {
        pages.forEachIndexed { index, page ->
            destination(page.title, page.icon, selected = index == selected) {
                onSelectedChange(index)
                drawerOpen = false
            }
        }
    }
}

/** The switches, in a sheet so they are reachable on a phone. */
@Composable
private fun SettingsSheet(
    visible: Boolean,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    highContrast: Boolean,
    onHighContrastChange: (Boolean) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    rtl: Boolean,
    onRtlChange: (Boolean) -> Unit,
    reduceMotion: Boolean,
    onReduceMotionChange: (Boolean) -> Unit,
    modality: InputModality?,
    onModalityChange: (InputModality?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(visible = visible, onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = Theme.spacing.md, vertical = Theme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        ) {
            SheetHeader() {
                +"Display settings"
            }

            Toggle("Dark", dark, onDarkChange)
            Toggle("High contrast", highContrast, onHighContrastChange)
            Toggle("Right to left", rtl, onRtlChange)
            Toggle("Reduce motion", reduceMotion, onReduceMotionChange)

            Text("Text size", style = Theme.typography.labelMedium)
            SegmentedControl(
                options = fontScales.map { it.first },
                selectedIndex = fontScales.indexOfFirst { it.second == fontScale }
                    .coerceAtLeast(0),
                onSelectedChange = { onFontScaleChange(fontScales[it].second) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Input modality", style = Theme.typography.labelMedium)
            SegmentedControl(
                options = modalities.map { it.first },
                selectedIndex = modalities.indexOfFirst { it.second == modality }
                    .coerceAtLeast(0),
                onSelectedChange = { onModalityChange(modalities[it].second) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val fontScales = listOf(
    "85%" to 0.85f,
    "100%" to 1f,
    "130%" to 1.3f,
    "200%" to 2f,
)

/**
 * "Auto" first, because it is the honest default — the tracker follows real
 * input, and forcing a modality is for checking a branch you cannot reach on the
 * host you happen to be on.
 */
private val modalities = listOf<Pair<String, InputModality?>>(
    "Auto" to null,
    "Touch" to InputModality.Touch,
    "Mouse" to InputModality.Mouse,
    "Keyboard" to InputModality.Keyboard,
)

@Composable
private fun Toggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SelectionRow(
        selected = checked,
        onSelectedChange = onCheckedChange,
        role = Role.Switch,
    ) {
        +label
        trailing { Switch(checked = checked, onCheckedChange = null) }
    }
}

