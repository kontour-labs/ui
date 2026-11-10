package io.kontour.ui.components.selection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Labels in a segmented control keep apart, or the control stacks.
 *
 * Reported: *"`SegmentedControl` labels crowd together at phone width."* A
 * segment had no room of its own round its label: the control stacked only once
 * a label was wider than its whole share, so one a hair narrower than that was
 * drawn edge to edge with its neighbour's — "StandardKeyboard", read as one word.
 *
 * Swept rather than picked: labels grown a letter at a time across a phone's
 * width (360dp, less a 16dp margin a side), from short enough to sit apart to
 * long enough to stack. At every length, two neighbours on one row are at least
 * a segment's padding apart on each side, twice [SegmentedControlDefaults.LabelPadding].
 */
@OptIn(ExperimentalTestApi::class)
class SegmentedLabelGapTest {

    @Test
    fun neighboursKeepApartOrStack() = runComposeUiTest {
        var letters by mutableStateOf(1)
        var padding = 0.dp
        setContent {
            KontourTheme {
                padding = SegmentedControlDefaults.LabelPadding
                Box(Modifier.width(328.dp)) {
                    SegmentedControl(
                        options = labels(letters),
                        selectedIndex = 0,
                        onSelectedIndexChange = {},
                    )
                }
            }
        }
        val crowded = mutableListOf<String>()
        var stackedAt: Int? = null
        for (n in 1..24) {
            letters = n
            waitForIdle()
            val bounds = labels(n).map { label ->
                onNode(hasText(label), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            }
            if (bounds.map { it.top }.distinct().size > 1) {
                stackedAt = stackedAt ?: n
                continue
            }
            val gaps = bounds.zipWithNext { a, b -> with(density) { (b.left - a.right).toDp() } }
            val least = gaps.min()
            if (least < padding * 2 - 0.5.dp) crowded += "$n letters: ${least.value}dp"
        }
        assertTrue(stackedAt != null, "the sweep should reach a length that stacks; it never did")
        assertTrue(
            crowded.isEmpty(),
            "neighbouring labels sat closer than ${(padding * 2).value}dp on one row — " +
                crowded.joinToString(),
        )
    }

    /**
     * With an icon before each label, the icon's room counts in the fit: the
     * control stacks sooner than it does without, and on one row a label still
     * keeps its padding from the next segment's icon. Icons were added for
     * 0.5.5; counted only by the words, a row of them would have drawn the
     * icon over its neighbour's last letters.
     */
    @Test
    fun iconsCountInTheFit() = runComposeUiTest {
        var letters by mutableStateOf(1)
        var withIcons by mutableStateOf(false)
        var padding = 0.dp
        var iconRoom = 0.dp
        setContent {
            KontourTheme {
                padding = SegmentedControlDefaults.LabelPadding
                iconRoom = Theme.sizing.iconSmall + 6.dp
                Box(Modifier.width(328.dp)) {
                    SegmentedControl(
                        options = labels(letters),
                        selectedIndex = 0,
                        onSelectedIndexChange = {},
                        icons = if (withIcons) List(3) { SystemIcons.Default.check } else emptyList(),
                    )
                }
            }
        }
        fun sweep(): Pair<Int?, List<String>> {
            val crowded = mutableListOf<String>()
            var stackedAt: Int? = null
            for (n in 1..24) {
                letters = n
                waitForIdle()
                val bounds = labels(n).map { label ->
                    onNode(hasText(label), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                }
                if (bounds.map { it.top }.distinct().size > 1) {
                    stackedAt = stackedAt ?: n
                    continue
                }
                val room = padding * 2 + if (withIcons) iconRoom else 0.dp
                val least = bounds.zipWithNext { a, b -> with(density) { (b.left - a.right).toDp() } }.min()
                if (least < room - 0.5.dp) crowded += "$n letters: ${least.value}dp"
            }
            return stackedAt to crowded
        }
        val (plain, _) = sweep()
        withIcons = true
        val (iconed, crowded) = sweep()
        assertTrue(plain != null && iconed != null, "the sweeps should both reach a length that stacks")
        assertTrue(iconed!! < plain!!, "with icons it stacked at $iconed letters, without at $plain: the icons were not counted")
        assertTrue(crowded.isEmpty(), "a label sat closer to the next segment's icon than its padding: ${crowded.joinToString()}")
    }

    private fun labels(n: Int) = listOf("1", "2", "3").map { "n".repeat(n) + it }
}
