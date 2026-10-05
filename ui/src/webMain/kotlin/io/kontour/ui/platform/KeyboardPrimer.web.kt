@file:OptIn(ExperimentalWasmJsInterop::class)

package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.time.TimeSource
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.TouchEvent
import org.w3c.dom.events.Event

/**
 * Whether this page needs the primer at all. Read once: a browser does not stop
 * being WebKit on an iPhone.
 */
private val primerNeeded: Boolean by lazy {
    isIosWebKit(window.navigator.userAgent, window.navigator.platform, maxTouchPoints())
}

private fun maxTouchPoints(): Int = js("(navigator.maxTouchPoints || 0)")

/** The marked regions, for the whole page: one set, as there is one keyboard. */
private val regions = KeyboardPrimerRegions()

private val handover = KeyboardPrimerHandover()

private val clock = TimeSource.Monotonic.markNow()

private fun nowMillis(): Long = clock.elapsedNow().inWholeMilliseconds

/**
 * How many themes have installed the primer. `KontourTheme` nests, and a nested
 * theme is another call to [InstallKeyboardPrimer]; the listeners go on with the
 * first and come off with the last.
 */
private var installs = 0

internal actual fun Modifier.opensKeyboard(enabled: Boolean): Modifier =
    if (primerNeeded) this then OpensKeyboardElement(enabled) else this

private class OpensKeyboardElement(val enabled: Boolean) : ModifierNodeElement<OpensKeyboardNode>() {
    override fun create() = OpensKeyboardNode(enabled)

    override fun update(node: OpensKeyboardNode) {
        node.enabled = enabled
        if (!enabled) regions.remove(node)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "opensKeyboard"
        properties["enabled"] = enabled
    }

    override fun equals(other: Any?) = other is OpensKeyboardElement && other.enabled == enabled

    override fun hashCode() = enabled.hashCode()
}

/**
 * Keeps its element's bounds in [regions] while it is placed and enabled, in CSS
 * pixels: Compose's window is the canvas, at the device's pixel ratio, and a touch
 * is in CSS pixels from the page.
 */
private class OpensKeyboardNode(var enabled: Boolean) :
    Modifier.Node(),
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!enabled || !coordinates.isAttached) {
            regions.remove(this)
            return
        }
        val scale = currentValueOf(LocalDensity).density
        val bounds = coordinates.boundsInWindow()
        regions.put(this, Rect(bounds.left / scale, bounds.top / scale, bounds.right / scale, bounds.bottom / scale))
    }

    override fun onDetach() {
        regions.remove(this)
    }
}

@Composable
internal actual fun InstallKeyboardPrimer() {
    if (!primerNeeded) return
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        if (installs++ > 0) return@DisposableEffect onDispose { installs-- }

        val primer = primerInput()
        document.body?.appendChild(primer)

        // Capturing, and on the touch's *end*: the last moment WebKit counts as the
        // reader's own gesture, and the one a tap is decided at.
        val onTouchStart: (Event) -> Unit = handler@{ event ->
            val touch = (event as? TouchEvent)?.changedTouches?.item(0) ?: return@handler
            handover.onTouchStart(touch.clientX.toFloat(), touch.clientY.toFloat())
        }
        val onTouchEnd: (Event) -> Unit = handler@{ event ->
            val touch = (event as? TouchEvent)?.changedTouches?.item(0) ?: return@handler
            val surface = (event.target as? Element)?.getBoundingClientRect() ?: return@handler
            val x = (touch.clientX - surface.left).toFloat()
            val y = (touch.clientY - surface.top).toFloat()
            val active = document.activeElement
            val editing = active != primer && (active is HTMLInputElement || active is HTMLTextAreaElement)
            val prime = handover.onTouchEnd(
                x = touch.clientX.toFloat(),
                y = touch.clientY.toFloat(),
                inRegion = regions.contains(x, y),
                editing = editing,
                nowMillis = nowMillis(),
            )
            if (prime) {
                // Where the finger was, so WebKit has nothing to scroll into view.
                primer.style.left = "${touch.clientX}px"
                primer.style.top = "${touch.clientY}px"
                primer.focus()
                scope.launch {
                    delay(KeyboardPrimerHandover.Hold)
                    if (handover.shouldRelease(nowMillis()) && document.activeElement == primer) primer.blur()
                }
            }
        }
        val onFocusIn: (Event) -> Unit = { event -> handover.onFocusMoved(toPrimer = event.target == primer) }

        window.addEventListener("touchstart", onTouchStart, true)
        window.addEventListener("touchend", onTouchEnd, true)
        document.addEventListener("focusin", onFocusIn, true)
        onDispose {
            installs--
            window.removeEventListener("touchstart", onTouchStart, true)
            window.removeEventListener("touchend", onTouchEnd, true)
            document.removeEventListener("focusin", onFocusIn, true)
            primer.remove()
        }
    }
}

/**
 * The input the keyboard opens for.
 *
 * At 16px, below which WebKit zooms the page to a focused input; invisible, out
 * of the accessibility tree and the tab order, and touched by nothing — it exists
 * to be focused for a few frames and handed on.
 */
private fun primerInput(): HTMLInputElement {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "text"
    input.tabIndex = -1
    input.setAttribute("aria-hidden", "true")
    input.setAttribute("autocomplete", "off")
    input.setAttribute("data-kontour", "keyboard-primer")
    (input as HTMLElement).style.apply {
        position = "fixed"
        width = "1px"
        height = "1px"
        padding = "0"
        border = "0"
        opacity = "0"
        fontSize = "16px"
        setProperty("caret-color", "transparent")
        setProperty("pointer-events", "none")
    }
    return input
}
