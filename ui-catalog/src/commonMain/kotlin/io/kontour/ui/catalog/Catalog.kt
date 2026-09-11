package io.kontour.ui.catalog

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.LayoutDirection
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AdjustmentsHorizontal
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.Click
import com.composables.icons.tabler.outline.Forms
import com.composables.icons.tabler.outline.InfoCircle
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
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.Scaffold
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.adaptive.WindowWidthClass
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.demo.DemoCard
import io.kontour.ui.demo.DemoFamily
import io.kontour.ui.demo.demoFamilies
import io.kontour.ui.demo.theme.DemoThemeProvider
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.nav.ModalNavDrawer
import io.kontour.ui.nav.NavItem
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.TopBar
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.ToastHost
import io.kontour.ui.overlay.rememberToastHostState
import io.kontour.ui.platform.platformPrefersHighContrast
import io.kontour.ui.platform.platformPrefersReducedMotion
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.theme.Theme

/** One page of the gallery. */
internal class Page(
    val title: String,
    val icon: ImageVector,
    val content: @Composable (Modifier) -> Unit,
)

/**
 * Every page, in order: two written by hand and eleven generated.
 *
 * **The generated eleven are the point.** They used to be hand-written too — a
 * fixed list of thirteen, last changed structurally in August, while the library
 * gained three weeks of components and an entire demo layer nine days after it.
 * By the time anyone looked, 31 public composables appeared in the gallery
 * nowhere at all, and 3,870 lines of panels were drawing statically what the
 * demos already drew with knobs on.
 *
 * The fix is not a check that notices the drift. It is the removal of the second
 * list: a demo can only be added to `demoFamilies`, and these pages are that.
 *
 * Two survive by hand because they are not component demos and never could be:
 * `About` is what the gallery is, and `Tokens` is the palette, the type scale
 * and the shape ladder — the one page where a *theme* can be judged rather than
 * a component.
 *
 * `internal` rather than private so the test suite can render each one on its
 * own. That is not a courtesy: the shell's goldens only ever show the page that
 * happens to be selected, so a list the tests cannot walk is a list where most
 * pages are drawn by nothing — which is how a crash on two of them reached a
 * phone.
 */
internal val pages: List<Page> = listOf(
    // First, and it is a change of purpose rather than of order: a gallery
    // whose first page is a colour ramp tells someone who has just been
    // handed the library nothing about what it is or where the writing is.
    Page("About", Tabler.Outline.InfoCircle) { Scrolling(it) { AboutShowcase(Modifier.fillMaxWidth()) } },
    Page("Tokens", Tabler.Outline.Palette) { Scrolling(it) { ThemeShowcase(Modifier.fillMaxWidth()) } },
) + demoFamilies.map { family ->
    Page(family.name, family.icon) { modifier -> DemoFamilyPage(family, modifier) }
}

/**
 * One family's demos, each in the same card the documentation site draws.
 *
 * `DemoCard` is public for exactly this: the gallery and the site were two
 * things supposed to look alike, and now they are one thing drawn twice.
 *
 * Lazy rather than a `Column`. `Display` holds eighteen demos, several of them
 * with their own overlay host, and the enclosing scroller would compose all of
 * them to measure the page — the same lesson the documentation site learned on
 * its component index.
 */
/** A scroller for the two pages that are a plain column of content. */
@Composable
private fun Scrolling(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState())) { content() }
}

@Composable
private fun DemoFamilyPage(family: DemoFamily, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
    ) {
        items(family.demos, key = { it.slug }) { demo ->
            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                Text(demo.slug, style = Theme.typography.labelMedium)
                DemoCard(demo)
            }
        }
    }
}

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
fun Catalog(settings: CatalogSettings = rememberCatalogSettings()) {
    var selected by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val systemDark = isSystemInDarkTheme()
    val systemHighContrast = platformPrefersHighContrast()
    val systemReduceMotion = platformPrefersReducedMotion()

    CompositionLocalProvider(
        // Font scale is applied here rather than inside the theme because it is a
        // *platform* setting: the theme's type ramp is in sp, and this is what
        // makes sp mean something different. Scaling the ramp instead would look
        // similar and prove nothing.
        //
        // `hostDensity` rather than a `Density(...)` written out here, because
        // the one written out here dropped the phone's own text-size setting on
        // the floor — and so did the identical line in `Site`.
        LocalDensity provides hostDensity(density, settings.textScale),
        LocalLayoutDirection provides
            if (settings.rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        // Every token argument goes through one function, shared with the
        // documentation site. Passing "the arguments this surface cares about"
        // is what let the two disagree in the first place.
        DemoThemeProvider(
            settings = settings,
            systemDark = systemDark,
            systemHighContrast = systemHighContrast,
            systemReduceMotion = systemReduceMotion,
        ) {
            // Overriding the modality has to happen *inside* the theme, which
            // installs the tracker that would otherwise set it from real input.
            val overridden = settings.modality
            CompositionLocalProvider(
                LocalInputModality provides (overridden ?: LocalInputModality.current)
            ) {
                WindowSizeClassProvider(Modifier.fillMaxSize()) {
                  Box(Modifier.fillMaxSize()) {
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
                                    // **The shell does not scroll.** It used
                                    // to, because every page was a plain
                                    // `Column`; the generated ones are lazy, and
                                    // a `LazyColumn` inside a `verticalScroll`
                                    // is measured with an infinite height and
                                    // throws. Each page brings its own scroller
                                    // now, which is also what keeps the lazy
                                    // ones lazy — the whole reason they are.
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .padding(bottom = contentPadding)
                                    ) {
                                        pages[selected].content(Modifier.fillMaxWidth())
                                    }
                                }
                            }

                            SettingsSheet(
                                visible = settingsOpen,
                                settings = settings,
                                systemDark = systemDark,
                                onDismiss = { settingsOpen = false },
                            )
                        }
                    }

                    // Outside the host on purpose. A readout drawn *inside* it
                    // would be covered by the first sheet that opened, which is
                    // precisely the moment there is something to read.
                    if (settings.frameTimes) {
                        FrameReadout(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(Theme.spacing.sm)
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
                .padding(padding)
        ) {
            pages[selected].content(Modifier.fillMaxWidth())
        }
    }

    ModalNavDrawer(visible = drawerOpen, onDismissRequest = { drawerOpen = false }) {
        pages.forEachIndexed { index, page ->
            item(page.title, page.icon, selected = index == selected) {
                onSelectedChange(index)
                drawerOpen = false
            }
        }
    }
}

/**
 * The switches, in a sheet so they are reachable on a phone.
 *
 * The list itself is `DisplaySettingsControls`, shared with the documentation
 * site's popover. It used to be spelled out here and spelled out again there,
 * which is how the two came to draw the same rows in a different order — and how
 * the font-scale defect above got into both hosts in identical words. What is
 * left here is the sheet's own shape: a header, and the spacing a sheet wants
 * rather than the tighter spacing a popover wants.
 *
 * **It scrolls.** It has to: measured, this panel is 728dp at 100% type and
 * 863dp at 200%, against roughly 867dp of usable window on a Pixel-class device
 * and 640dp on a 5" one. It used to be cropped instead — the sheet measured its
 * content unbounded — and at 200% a reader lost Text size and Input modality,
 * the two controls they had opened the panel to reach.
 * `SettingsPanelHeightTest` still holds those numbers as a ceiling, now as a
 * ratchet on how long the panel is rather than on how much of it survives.
 */
@Composable
internal fun SettingsSheet(
    visible: Boolean,
    settings: CatalogSettings,
    systemDark: Boolean,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(visible = visible, onDismissRequest = onDismiss) {
        SettingsSheetContent(settings, systemDark)
    }
}

/**
 * The sheet's column, without the sheet — so a test can photograph and measure it.
 *
 * Scrolls, because the panel is taller than a phone. The sheet measures its
 * content at the room it has, which is what gives this `verticalScroll` a finite
 * viewport to scroll within. That order matters: measured unbounded, as the
 * sheet once did, Compose refuses a scroller outright and throws — so this line
 * and the `:ui` change are one change, not two.
 *
 * Outside a sheet — the popover on a wide window uses the same controls through
 * [DisplaySettingsControls] — there is room, and this is not the composable
 * being used.
 */
@Composable
internal fun SettingsSheetContent(settings: CatalogSettings, systemDark: Boolean) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Theme.spacing.md, vertical = Theme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        SheetHeader() {
            +"Display settings"
        }
        DisplaySettingsControls(settings, systemDark, showFrameTimes = true)
    }
}

