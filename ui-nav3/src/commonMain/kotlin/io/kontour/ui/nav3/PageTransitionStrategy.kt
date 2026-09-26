package io.kontour.ui.nav3

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneDecoratorStrategyScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import io.kontour.ui.motion.LocalBackStyle
import io.kontour.ui.motion.PageMotion
import io.kontour.ui.motion.pageEffects
import io.kontour.ui.motion.rememberPageMotion
import io.kontour.ui.theme.Theme

/**
 * Pages that move the way [LocalBackStyle] says back looks — Android's
 * predictive back, or iOS's swipe — for `NavDisplay`'s `sceneDecoratorStrategies`.
 *
 * ```kotlin
 * NavDisplay(
 *     backStack = backStack,
 *     onBack = { backStack.removeLastOrNull() },
 *     sceneStrategies = listOf(rememberListDetailSceneStrategy()),
 *     sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
 *     entryProvider = entryProvider { … },
 * )
 * ```
 *
 * One line, and every page gets the push, the pop and the back gesture of its
 * platform, with the dim, the shadow or the lifted corners that go with them.
 * `NavDisplay` still owns the back stack and the gesture; this only says how the
 * pages look while it runs. A page whose entry carries its own
 * `NavDisplay.transitionSpec { … }` keeps it — entry metadata wins — and a
 * `transitionSpec` passed to `NavDisplay` itself is ignored for decorated
 * pages, so do not pass one as well.
 *
 * Overlay scenes — the supporting pane as a sheet, a dialog — are left alone:
 * they are overlays, and the overlay host answers back for those.
 *
 * **What is not here.** A gesture let go finishes on the transform's own
 * remaining curve, so the velocity of a flick does not carry into it, and the
 * page does not follow the finger vertically as Material's fullest version does.
 * Both would need `NavDisplay` itself to change.
 *
 * @param contentSwipe Under swipe back, whether a sideways pan from anywhere on
 *   the page goes back, as it does on iOS 26 — not just one from the leading
 *   edge. It yields to anything under the finger that moves sideways first: a
 *   carousel, a row's swipe actions, a slider, a horizontal list. Off, only the
 *   edge goes back.
 */
@Composable
fun <T : Any> rememberPageTransitionStrategy(contentSwipe: Boolean = true): SceneDecoratorStrategy<T> {
    val motion = rememberPageMotion(LocalBackStyle.current)
    return remember(motion, contentSwipe) { PageTransitionStrategy(motion, contentSwipe) }
}

internal class PageTransitionStrategy<T : Any>(
    private val motion: PageMotion,
    private val contentSwipe: Boolean,
) : SceneDecoratorStrategy<T> {

    override fun SceneDecoratorStrategyScope<T>.decorateScene(scene: Scene<T>): Scene<T> =
        if (scene is OverlayScene<T> || scene is PageScene<T>) scene else PageScene(scene, motion, contentSwipe)
}

/** Whatever scene a strategy chose, dressed as a page. */
internal class PageScene<T : Any>(
    internal val inner: Scene<T>,
    private val motion: PageMotion,
    private val contentSwipe: Boolean,
) : Scene<T> {

    /**
     * The inner scene's key, told apart by its kind: a list-detail scene and a
     * single page over the same entry are different pages, and `NavDisplay`
     * must see them as different to move between them.
     */
    override val key: Any = PageSceneKey(inner::class, inner.key)

    override val entries: List<NavEntry<T>> get() = inner.entries

    override val previousEntries: List<NavEntry<T>> get() = inner.previousEntries

    override val metadata: Map<String, Any> =
        NavDisplay.transitionSpec { motion.push() } +
            NavDisplay.popTransitionSpec { motion.pop() } +
            NavDisplay.predictivePopTransitionSpec { edge -> motion.predictivePop(edge) } +
            // Last, so a scene's or an entry's own specs win over the page's.
            inner.metadata

    override val content: @Composable () -> Unit = {
        val scope = LocalNavAnimatedContentScope.current
        Box(
            Modifier
                .fillMaxSize()
                // Opaque, as a page is: a pop reveals the page beneath rather
                // than showing it through the one leaving.
                .background(Theme.colours.background)
                .pageEffects(motion, scope, isPop = { scope.isPop() })
                .then(if (contentSwipe) Modifier.contentSwipeBack(motion.style) else Modifier)
        ) {
            inner.content()
        }
    }

    override fun equals(other: Any?): Boolean =
        other is PageScene<*> && other.inner == inner && other.motion == motion && other.contentSwipe == contentSwipe

    override fun hashCode(): Int = (inner.hashCode() * 31 + motion.hashCode()) * 31 + contentSwipe.hashCode()

    override fun toString(): String = "PageScene($inner)"
}

internal data class PageSceneKey(val kind: Any, val key: Any)

/**
 * Whether the change this page is part of goes back: the scene being moved to
 * has fewer pages behind it than the one being left.
 */
private fun AnimatedContentScope.isPop(): Boolean {
    val scenes = transition.parentTransition ?: return false
    val from = scenes.currentState as? Scene<*> ?: return false
    val to = scenes.targetState as? Scene<*> ?: return false
    return to.previousEntries.size < from.previousEntries.size
}
