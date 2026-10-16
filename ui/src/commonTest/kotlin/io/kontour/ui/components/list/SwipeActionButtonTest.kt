package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A swipe action's button: what it shows of its icon and label, and when.
 */
@OptIn(ExperimentalTestApi::class)
class SwipeActionButtonTest {

    /**
     * A row composed open shows its action's label as it is, not growing in.
     *
     * The button decides in measure how much of itself it has room for, and
     * that answer used to start at "none". A row composed open — restored, or
     * rendered for a screenshot — measured its button wide on the first frame
     * it had one, and its icon and label then entered as they do mid-swipe,
     * scaling and fading in over a row that had not moved. Motion on, since
     * under reduced motion that entrance is a fade and has no size to see.
     */
    @Test
    fun aRowComposedOpenShowsItsLabelAtOnce() = runComposeUiTest {
        val delete = SwipeAction(label = "Delete", icon = SystemIcons.Default.close, onAction = {}, background = Color.Red)
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(reduceMotion = false) {
                SwipeActions(
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    end = listOf(delete),
                    state = rememberSwipeActionsState(SwipeValue.End),
                ) {
                    Box(Modifier.fillMaxWidth().height(72.dp))
                }
            }
        }
        // The row's width, its anchors, the button's first measure, and the
        // composition that answer asks for: a handful of frames, far short of
        // an entrance.
        repeat(5) { mainClock.advanceTimeByFrame() }

        val label = onNodeWithText("Delete", useUnmergedTree = true).fetchSemanticsNode()
        val drawn = label.boundsInRoot.width
        val laidOut = label.size.width.toFloat()
        assertTrue(laidOut > 0f, "the label has no width, so this checks nothing")
        assertTrue(
            drawn >= laidOut * 0.99f,
            "five frames into a row composed open, its label is drawn at ${drawn / laidOut} of its size: still growing in",
        )
    }
}
