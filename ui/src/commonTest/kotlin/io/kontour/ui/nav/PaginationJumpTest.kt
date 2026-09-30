package io.kontour.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.LocalSizing
import io.kontour.ui.theme.Sizing
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Turning `allowJump` on does not move the row.
 *
 * Reported as *"when you enable jump, it suddenly becomes very expanded and spread
 * out"*. A tappable ellipsis reserved a whole touch target round its 12dp glyph,
 * so each gap in the row grew to a button's width, and on a phone the row had
 * room for fewer pages. It now takes the room a plain gap takes.
 *
 * At a 44dp touch target, the web's and iOS's, since the JVM's 24dp minimum is
 * already an ellipsis's width and would show nothing.
 */
@OptIn(ExperimentalTestApi::class)
class PaginationJumpTest {

    @Composable
    private fun Rows(width: Dp?, jump: Boolean) {
        KontourTheme(reduceMotion = true) {
            CompositionLocalProvider(LocalSizing provides Sizing(minTouchTarget = 44.dp)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(if (width == null) Modifier.wrapContentWidth(unbounded = true) else Modifier.width(width)) {
                        Pagination(
                            value = 19,
                            pageCount = 40,
                            onValueChange = {},
                            allowJump = jump,
                            modifier = Modifier.testTag(Row),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun aRowThatCanJumpIsAsWideAsOneThatCannot() {
        val widths = listOf(false, true).map { jump ->
            var width = 0.dp
            runComposeUiTest {
                setContent { Rows(width = null, jump = jump) }
                val bounds = onNodeWithTag(Row).getBoundsInRoot()
                width = bounds.right - bounds.left
            }
            width
        }
        assertTrue(
            abs((widths[0] - widths[1]).value) < 1f,
            "the row is ${widths[0]} wide without jump and ${widths[1]} with it",
        )
    }

    @Test
    fun onAPhoneItShowsAsManyPages() {
        val shown = listOf(false, true).map { jump ->
            var pages = 0
            runComposeUiTest {
                setContent { Rows(width = 360.dp, jump = jump) }
                pages = onAllNodesWithContentDescription("Page", substring = true).fetchSemanticsNodes().size
            }
            pages
        }
        assertEquals(shown[0], shown[1], "a 360dp row shows ${shown[0]} pages without jump and ${shown[1]} with it")
    }

    private companion object {
        const val Row = "row"
    }
}
