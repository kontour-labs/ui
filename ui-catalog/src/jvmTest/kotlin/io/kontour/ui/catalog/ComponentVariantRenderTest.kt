package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.contract.ComponentSpec
import io.kontour.ui.contract.componentRegistry
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A few components again, right to left and at twice the type size.
 *
 * `ComponentRenderTest` draws every specimen left to right at 1×, and the
 * sweeps ask the other two questions without a picture: `PseudoLocaleSweepTest`
 * whether anything is left in English or clipped, `WidthSweepTest` whether ink
 * leaves its box at 200%. Neither can see a chevron that points the wrong way,
 * a page number on the wrong side of its arrows, or a label that wrapped into a
 * shape nobody meant. A picture can, so these are the components where that is
 * the likely fault, and the ones a reader is most likely to meet in either.
 *
 * - **Right to left:** the ones with a direction in them. A bar's back arrow,
 *   a pager's arrows, a rail's indicator, a select's chevron, a carousel's
 *   scroll, a menu opening from its anchor's start, a sheet from the end.
 * - **At 2×:** the ones with text in a box that has to grow round it. A
 *   button's label, a field's label and value, a list row's two lines, and a
 *   toast beside its action.
 *
 * Light only, and resting only. The schemes and states are
 * `ComponentRenderTest`'s question and do not change with direction or type.
 */
class ComponentVariantRenderTest {

    @AfterTest
    fun assertGoldensMatched() = Screenshot.assertAllMatched()

    @Test
    fun theDirectionalComponentsRenderRightToLeft() {
        val drawn = render(RightToLeft, folder = "components-rtl", heightScale = 1) { content ->
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
        }
        assertEquals(RightToLeft, drawn)
    }

    @Test
    fun theTextComponentsRenderAtTwiceTheType() {
        val drawn = render(TwiceTheType, folder = "components-fontscale2", heightScale = 2) { content ->
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f), content = content)
        }
        assertEquals(TwiceTheType, drawn)
    }

    /** Each of [names]'s specimens in [folder], under [variant]; the names that were found and drawn. */
    private fun render(
        names: List<String>,
        folder: String,
        heightScale: Int,
        variant: @Composable (@Composable () -> Unit) -> Unit,
    ): List<String> {
        val specs: List<ComponentSpec> = names.mapNotNull { name -> componentRegistry.firstOrNull { it.name == name } }
        for (spec in specs) {
            Screenshot.render(
                name = "$folder/${spec.slug}-light",
                width = CanvasWidth,
                // Taller at 2×, so a label that wraps has somewhere to go; what
                // it does not use is cropped off again.
                height = (spec.renderHeight ?: CanvasHeightDp) * 2 * heightScale,
                trim = InkMargin,
            ) {
                variant {
                    KontourTheme(darkTheme = false, reduceMotion = true) {
                        OverlayHost(Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Theme.colours.background)
                                    .padding(Theme.spacing.md),
                                contentAlignment = Alignment.Center,
                            ) {
                                spec.content(Modifier, true) {}
                            }
                        }
                    }
                }
            }
        }
        return specs.map { it.name }
    }

    private companion object {
        /** `ComponentRenderTest`'s canvas, so the two sets of pictures are at one scale. */
        const val CanvasWidth = 600
        const val CanvasHeightDp = 120
        const val InkMargin = 24

        val RightToLeft = listOf("TopBar", "Pagination", "NavRail", "Select", "Carousel", "DropdownMenu", "SideSheet")
        val TwiceTheType = listOf("Button (Primary)", "TextField", "ListItem", "Toast")
    }
}
