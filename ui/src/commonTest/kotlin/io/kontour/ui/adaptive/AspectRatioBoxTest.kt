package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The box takes the size its ratio and constraints imply.
 *
 * It used to apply `fillMaxWidth()` of its own before `aspectRatio`, which pins
 * the *minimum* width to the maximum. `aspectRatio` will only return a size its
 * constraints are satisfied by, so any width derived from a height was rejected
 * before it could be considered — and both cases below fell over, one silently
 * and one to nothing at all.
 *
 * Density is pinned at 1 so the numbers here are the numbers on screen.
 */
@OptIn(ExperimentalTestApi::class)
class AspectRatioBoxTest {

    @Test
    fun matchingHeightFirstSizesFromTheHeight() {
        val size = measure(Modifier.size(400.dp, 100.dp)) {
            AspectRatioBox(
                ratio = 2f,
                modifier = Modifier.testTag(Tag),
                matchHeightConstraintsFirst = true,
            ) {}
        }
        assertEquals(
            IntSize(200, 100),
            size,
            "matchHeightConstraintsFirst was ignored — the box took the width it " +
                "was offered and derived the height, which is the other mode",
        )
    }

    @Test
    fun aFixedHeightAndAFreeWidthResolve() {
        val size = measure(Modifier.size(400.dp, 400.dp)) {
            AspectRatioBox(
                ratio = 1f,
                modifier = Modifier.testTag(Tag).height(120.dp),
            ) {}
        }
        assertEquals(
            IntSize(120, 120),
            size,
            "a fixed height with a free width measured to nothing: no size can " +
                "satisfy a fixed width and a fixed height that disagree",
        )
    }

    /** The unchanged case, kept so removing the `fillMaxWidth` has to prove it. */
    @Test
    fun theOrdinaryCaseStillFillsTheWidthItIsOffered() {
        val size = measure(Modifier.size(400.dp, 400.dp)) {
            AspectRatioBox(ratio = 2f, modifier = Modifier.testTag(Tag)) {}
        }
        assertEquals(
            IntSize(400, 200),
            size,
            "the box no longer takes the width it is offered — `aspectRatio` is " +
                "supposed to do that on its own",
        )
    }

    private fun measure(outer: Modifier, content: @Composable () -> Unit): IntSize {
        var size = IntSize.Zero
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    Box(outer) { content() }
                }
            }
            waitForIdle()
            size = onNodeWithTag(Tag).fetchSemanticsNode().size
        }
        return size
    }

    private companion object {
        const val Tag = "aspect"
    }
}
