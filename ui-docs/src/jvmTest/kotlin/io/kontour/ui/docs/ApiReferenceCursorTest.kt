package io.kontour.ui.docs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.foundation.SelectableText
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.theme.KontourTheme
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The "reference" link beside each API entry shows the hand.
 *
 * Reported from the site: a link there showed the text cursor. A page is one
 * [SelectableText] region, and selectable text puts its own I-beam on its node,
 * underneath whatever the caller's modifier asks for — so the link, a `Text`
 * with a cursor and a click handler of its own, lost to the words it was made
 * of. A `TextButton` keeps its label out of the selection, and the hand with it.
 *
 * Asked of the scene rather than a window: the cursor recorded is the one the
 * page asks the platform for, which is what differed.
 */
@OptIn(InternalComposeUiApi::class)
class ApiReferenceCursorTest {

    @Test
    fun theReferenceLinkShowsTheHand() {
        val page = docPagesByPath.values.first { page ->
            page.symbols.any { symbol -> apiFor(symbol).any { it.reference != null } }
        }
        var cursor: PointerIcon? = null
        val owners = mutableListOf<SemanticsOwner>()
        val platform = object : PlatformContext.Empty() {
            override fun setPointerIcon(pointerIcon: PointerIcon) {
                cursor = pointerIcon
            }

            override val semanticsOwnerListener = object : PlatformContext.SemanticsOwnerListener {
                override fun onSemanticsOwnerAppended(semanticsOwner: SemanticsOwner) {
                    owners += semanticsOwner
                }

                override fun onSemanticsOwnerRemoved(semanticsOwner: SemanticsOwner) {
                    owners -= semanticsOwner
                }

                override fun onSemanticsChange(semanticsOwner: SemanticsOwner) = Unit

                override fun onLayoutChange(semanticsOwner: SemanticsOwner, semanticsNodeId: Int) = Unit
            }
        }
        val recomposer = FrameRecomposer(Dispatchers.Unconfined)
        val scene = CanvasLayersComposeScene(
            recomposer,
            Density(1f),
            LayoutDirection.Ltr,
            IntSize(1000, 4000),
            platform,
        )
        var nanos = 0L
        fun frames(count: Int) = repeat(count) {
            nanos += 16_000_000L
            recomposer.performFrame(nanos)
            scene.measureAndLayout()
        }
        try {
            scene.setContent(recomposer.compositionContext) {
                KontourTheme {
                    // Provided rather than earned, as `CursorScene` does: the theme
                    // learns the modality from the first pointer event.
                    CompositionLocalProvider(LocalInputModality provides InputModality.Mouse) {
                        SelectableText { ApiSection(page) }
                    }
                }
            }
            frames(4)

            val link = owners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
                .firstOrNull { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "reference" } == true
                }
            assertNotNull(link, "no \"reference\" link on ${page.path}")
            val at = link.boundsInRoot.center
            // From outside it, then onto it, so the link sees the pointer arrive.
            for (position in listOf(Offset(at.x, at.y - 200f), at, at + Offset(1f, 0f))) {
                scene.sendPointerEvent(
                    PointerEventType.Move,
                    position,
                    timeMillis = nanos / 1_000_000L,
                    type = PointerType.Mouse,
                    buttons = PointerButtons(),
                )
                frames(2)
            }
            assertEquals(PointerIcon.Hand, cursor, "the cursor over \"reference\" on ${page.path}")
        } finally {
            scene.close()
        }
    }
}
