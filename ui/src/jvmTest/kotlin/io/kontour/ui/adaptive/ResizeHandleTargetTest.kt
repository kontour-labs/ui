package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The pane splitter is a full touch target when there is no pointer to aim it.
 *
 * It is 12dp wide, which a mouse hits without thinking and a thumb does not,
 * and every other interactive part of the library reserves the platform minimum
 * under touch. It is also the one control `WindowAdaptiveInfo.isPrecise` was
 * written to govern — the KDoc names a resize handle as its first example — and
 * until now nothing read it.
 *
 * Measured as the gap between the two panes, which is exactly the room the
 * handle takes.
 */
class ResizeHandleTargetTest {

    private fun gap(modality: InputModality): Pair<Float, Float> {
        var list = 0f
        var detail = 0f
        var target = Dp.Unspecified
        val scene = ImageComposeScene(width = 1600, height = 600, density = Density(1f)) {
            KontourTheme {
                CompositionLocalProvider(LocalInputModality provides modality) {
                    target = Theme.sizing.minTouchTarget
                    ListDetailPaneScaffold(
                        focus = PaneFocus.List,
                        onBack = {},
                        list = {
                            Box(Modifier.fillMaxSize().onGloballyPositioned { list = it.boundsInRoot().right })
                        },
                        detail = {
                            Box(Modifier.fillMaxSize().onGloballyPositioned { detail = it.boundsInRoot().left })
                        },
                        twoPane = true,
                        resizable = true,
                    )
                }
            }
        }
        try {
            scene.render().close()
            scene.render().close()
        } finally {
            scene.close()
        }
        return (detail - list) to target.value
    }

    @Test
    fun underTouchTheSplitterIsAFullTarget() {
        val (width, minimum) = gap(InputModality.Touch)
        assertEquals(minimum, width, "the splitter under touch, against the platform minimum")
    }

    @Test
    fun underAMouseItStaysNarrow() {
        val (width, _) = gap(InputModality.Mouse)
        assertEquals(PaneScaffoldDefaults.HandleWidth.value, width, "the splitter under a mouse")
    }
}
