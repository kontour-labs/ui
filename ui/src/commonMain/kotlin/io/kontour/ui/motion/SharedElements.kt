@file:OptIn(ExperimentalSharedTransitionApi::class)

package io.kontour.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import io.kontour.ui.theme.Motion
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme

/**
 * What a page can hand to the page after it.
 *
 * Handed to [PageTransition]'s content, and its whole job is to make the two
 * Compose scopes a shared element needs stop being the caller's problem — see
 * [PageTransition].
 */
@Stable
class PageTransitionScope internal constructor(
    private val shared: SharedTransitionScope,
    private val visibility: AnimatedVisibilityScope,
    private val bounds: BoundsTransform,
    /** False under reduced motion, where every one of these is a plain modifier. */
    private val morphs: Boolean,
) {

    /** True while a page change is in flight. */
    val isTransitionActive: Boolean get() = shared.isTransitionActive

    /**
     * Marks this element as **the same thing** as the element with [key] on the
     * other page.
     *
     * Put it on both: the card in the list, and the header it becomes. Compose
     * then animates the bounds of one into the bounds of the other rather than
     * cross-fading two unrelated rectangles, which is what turns "a new screen
     * appeared" into "the thing I tapped opened".
     *
     * The two must be the *same kind of thing* — an image becoming a larger
     * image, a title becoming a larger title. For a container whose contents
     * differ between the pages, use [sharedBounds].
     *
     * Keys are matched across pages, so they have to identify the subject rather
     * than the position: `"stop-${stop.id}"`, never `"card"`.
     */
    @Composable
    fun Modifier.sharedElement(key: Any): Modifier {
        if (!morphs) return this
        with(shared) {
            return this@sharedElement.sharedElement(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = visibility,
                boundsTransform = bounds,
            )
        }
    }

    /**
     * Like [sharedElement], for a **container** whose contents differ.
     *
     * The card in the list holds a name and a time; the header it becomes holds
     * a name, a time and four more rows. Those are not the same content, so
     * matching them as one element would stretch one into the other. This
     * animates the container's bounds and cross-fades what is inside.
     *
     * @param clip Rounds the travelling container, so a card with an 8dp radius
     *   does not turn into a hard-edged rectangle for the length of the
     *   transition. Null keeps whatever clip the parent applies.
     */
    @Composable
    fun Modifier.sharedBounds(key: Any, clip: Shape? = null): Modifier {
        if (!morphs) return this
        with(shared) {
            val state = rememberSharedContentState(key)
            // Two calls rather than a nullable argument: the parameter has no
            // null in it, and its default — clip to the parent — is the right
            // answer whenever the caller names no shape of its own.
            return if (clip == null) {
                this@sharedBounds.sharedBounds(
                    sharedContentState = state,
                    animatedVisibilityScope = visibility,
                    boundsTransform = bounds,
                )
            } else {
                this@sharedBounds.sharedBounds(
                    sharedContentState = state,
                    animatedVisibilityScope = visibility,
                    boundsTransform = bounds,
                    // Outset, or the clip eats the element's shadow.
                    //
                    // `OverlayClip(clip)` clips the travelling element to
                    // *exactly* its own outline, and a drop shadow is drawn
                    // outside that outline by definition. So a card with any
                    // elevation lost its shadow for the whole morph and got it
                    // back the frame it landed and stopped being drawn in the
                    // overlay — which reads as the shadow snapping into place at
                    // the very end of an otherwise smooth transition.
                    //
                    // The slack is a constant rather than a parameter because
                    // the caller would have to know the blur radius of a token
                    // it never named. It only has to exceed the tallest tier in
                    // the elevation scale, and being generous costs nothing: the
                    // clip still bounds the content, just with room around it,
                    // and only while the element is in flight.
                    clipInOverlayDuringTransition = OverlayClip(
                        OutsetShape(clip, ShadowSlack)
                    ),
                )
            }
        }
    }
}

/**
 * Animates between whatever pages [target] names, carrying shared elements across.
 *
 * ```
 * PageTransition(target = route, modifier = Modifier.fillMaxSize()) { page ->
 *     when (page) {
 *         is Route.List -> StopList(onOpen = { route = Route.Detail(it) })
 *         is Route.Detail -> StopDetail(page.id)
 *     }
 * }
 *
 * // in either page, on the card and on the header it becomes:
 * Card(Modifier.sharedElement("stop-${stop.id}")) { … }
 * ```
 *
 * ### It takes your state, not a back stack
 *
 * [target] is any value you already have — a sealed route, an id, a `Boolean`
 * for "is the detail open". That is deliberate, and it is why `:ui` still has no
 * navigation dependency: this works with Navigation 3, with an app's own
 * navigator, or with `var route by remember { mutableStateOf(…) }`, and it never
 * has to know which. `ui-docs/content/components/adaptive.md` carries the
 * Navigation 3 recipe, where it can name Nav3 without the library doing so.
 *
 * ### What it is hiding
 *
 * Compose can already do this. What it cannot do is be *used*: a shared element
 * needs a `SharedTransitionScope` **and** an `AnimatedVisibilityScope`, they come
 * from two different composables, and the call ends up as
 * `Modifier.sharedElement(rememberSharedContentState(key), animatedVisibilityScope)`
 * with two `this@` qualifiers to get them into the same place. That is most of
 * why the feature goes unused. Here both scopes are held by
 * [PageTransitionScope] and the call is `Modifier.sharedElement(key)`.
 *
 * [Transitions.containerTransform] is the cheap version of this, for when the
 * two elements are not literally the same node. This is the real one; reach for
 * it when they are.
 *
 * ### Reduced motion
 *
 * Degrades to a cross-fade with no bounds morph at all — [PageTransitionScope]
 * hands back plain modifiers. An element flying across the screen is the
 * clearest case that preference covers, and there is no gentle version of it:
 * half a morph is still a thing travelling a long way.
 *
 * @param contentKey Which changes to [target] count as a page change. Defaults
 *   to the target itself; give it something coarser when the target carries data
 *   that changes without the page doing — a `Route.Detail(id, scrollPosition)`
 *   should not re-run the transition every time the user scrolls.
 * @param content Draws one page, in a scope that can mark shared elements.
 */
@Composable
fun <T> PageTransition(
    target: T,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any? = { it },
    content: @Composable PageTransitionScope.(T) -> Unit,
) {
    val motion = Theme.motion
    val morphs = !motion.reduceMotion
    val bounds = remember(motion) {
        BoundsTransform { _, _ -> motion.springOrTween(motion.springDefault) }
    }

    SharedTransitionLayout(modifier) {
        AnimatedContent(
            targetState = target,
            transitionSpec = { pageCrossFade(motion) },
            contentKey = contentKey,
            label = "page",
        ) { page ->
            val scope = remember(this@SharedTransitionLayout, this@AnimatedContent, bounds, morphs) {
                PageTransitionScope(
                    shared = this@SharedTransitionLayout,
                    visibility = this@AnimatedContent,
                    bounds = bounds,
                    morphs = morphs,
                )
            }
            scope.content(page)
        }
    }
}

/**
 * The pages' own arrival and departure: a cross-fade, and nothing else.
 *
 * The shared elements carry the movement, so the pages beneath them should get
 * out of each other's way quietly. A slide underneath a morphing card is two
 * animations disagreeing about which direction the change is in.
 *
 * `using(null)` drops the size transform. Without it `AnimatedContent` animates
 * the *container* between the two pages' sizes, which for two pages that both
 * fill the window is an animation between identical numbers on every change —
 * free, until one page is a different size and the whole screen resizes for a
 * fifth of a second.
 */
private fun pageCrossFade(motion: Motion): ContentTransform =
    ContentTransform(
        targetContentEnter = fadeIn(motion.tweenDefault()),
        initialContentExit = fadeOut(motion.tweenExit()),
        sizeTransform = null,
    )


/**
 * How much room a travelling element's clip leaves for its shadow.
 *
 * Comfortably past the `overlay` tier — a 10dp offset with a 28dp blur — so no
 * tier of the elevation scale is cut off in flight. It is not a visual constant
 * and nothing lines up with it; it is a bound.
 */
private val ShadowSlack = 48.dp

/**
 * [base], grown by [by] on every side.
 *
 * For clipping something that draws outside its own bounds. The outline is built
 * at the larger size and shifted back up and left, so it stays centred on the
 * element rather than growing away from its origin.
 */
private class OutsetShape(private val base: Shape, private val by: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val grow = with(density) { by.toPx() }
        if (grow <= 0f) return base.createOutline(size, layoutDirection, density)

        val grown = Size(size.width + grow * 2f, size.height + grow * 2f)
        val path = Path().apply {
            addOutline(base.createOutline(grown, layoutDirection, density))
            translate(Offset(-grow, -grow))
        }
        return Outline.Generic(path)
    }
}
