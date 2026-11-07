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
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import io.kontour.ui.dragdrop.LocalOverlayLayer
import io.kontour.ui.overlay.LocalOverlayHostOrNull
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.time.Duration
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

private fun now(): Duration = clock.elapsedNow()

/**
 * How many themes have installed the primer. `KontourTheme` nests, and a nested
 * theme is another call to [InstallKeyboardPrimer]; the listeners go on with the
 * first and come off with the last.
 */
private var installs = 0

internal actual fun Modifier.opensKeyboard(enabled: Boolean, standIn: Boolean): Modifier =
    if (primerNeeded) this then OpensKeyboardElement(enabled, standIn = standIn) else this

internal actual fun Modifier.keepsKeyboardClosed(): Modifier =
    if (primerNeeded) this then OpensKeyboardElement(enabled = true, hole = true) else this

private class OpensKeyboardElement(
    val enabled: Boolean,
    val hole: Boolean = false,
    val standIn: Boolean = false,
) : ModifierNodeElement<OpensKeyboardNode>() {
    override fun create() = OpensKeyboardNode(enabled, hole, standIn)

    override fun update(node: OpensKeyboardNode) {
        node.enabled = enabled
        if (!enabled) regions.remove(node)
        node.placeStandIn()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = if (hole) "keepsKeyboardClosed" else "opensKeyboard"
        properties["enabled"] = enabled
        properties["standIn"] = standIn
    }

    override fun equals(other: Any?) =
        other is OpensKeyboardElement && other.enabled == enabled && other.hole == hole && other.standIn == standIn

    override fun hashCode() = 31 * (31 * enabled.hashCode() + hole.hashCode()) + standIn.hashCode()
}

/** The nodes with a stand-in input, placed again when a hole moves: see [KeyboardPrimerRegions.tapArea]. */
private val standIns = LinkedHashSet<OpensKeyboardNode>()

/**
 * Keeps its element's bounds in [regions] while it is placed and enabled, in CSS
 * pixels: Compose's window is the canvas, at the device's pixel ratio, and a touch
 * is in CSS pixels from the page. As a [hole], cut out of them instead.
 *
 * With [standIn], also keeps a real `<input>` over the region: see [standInInput].
 * Only while the element is on the topmost layer — the page with nothing modal
 * open, or the top overlay — so it never takes a tap meant for a sheet, a dialog
 * or a menu drawn over the field.
 */
private class OpensKeyboardNode(var enabled: Boolean, val hole: Boolean, val standIn: Boolean) :
    Modifier.Node(),
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode,
    ObserverModifierNode {

    private var input: HTMLElement? = null
    private var onTop = true

    override fun onAttach() {
        if (!standIn) return
        input = standInInput().also { document.body?.appendChild(it) }
        standIns += this
        onObservedReadsChanged()
    }

    override fun onObservedReadsChanged() {
        if (!standIn) return
        val layer = currentValueOf(LocalOverlayLayer)
        val host = currentValueOf(LocalOverlayHostOrNull)
        observeReads {
            onTop = if (host == null) true else if (layer == 0) !host.isBusy else layer == host.rendered.size
        }
        placeStandIn()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!enabled || !coordinates.isAttached) {
            regions.remove(this)
            relayStandIns()
            return
        }
        val scale = currentValueOf(LocalDensity).density
        val bounds = coordinates.boundsInWindow()
        val css = Rect(bounds.left / scale, bounds.top / scale, bounds.right / scale, bounds.bottom / scale)
        if (hole) regions.putHole(this, css) else regions.put(this, css)
        if (hole) relayStandIns() else placeStandIn()
    }

    fun placeStandIn() {
        val element = input ?: return
        val area = if (enabled && onTop) regions.tapArea(this) else null
        if (area == null) {
            hideStandIn(element)
        } else {
            showStandIn(element, area.left.toDouble(), area.top.toDouble(), area.width.toDouble(), area.height.toDouble())
        }
    }

    override fun onDetach() {
        val wasHole = regions.tapArea(this) == null && hole
        regions.remove(this)
        standIns -= this
        input?.remove()
        input = null
        if (wasHole || hole) relayStandIns()
    }
}

private fun relayStandIns() {
    standIns.forEach { it.placeStandIn() }
}

/**
 * **A real input over a text field before it is touched, so the first tap opens
 * the keyboard.** On an iPhone, WebKit opens the keyboard for a tap that lands
 * on an input, and only as that tap's own default action; and it decides what
 * a tap lands on as the finger goes down, so nothing moved under the finger
 * later counts. A Compose field is drawn on the canvas, and its own input does
 * not exist until the field has focus, so the first tap on it lands on the
 * canvas and gets no keyboard. Every way of opening it from that touch was
 * tried on an iPhone on iOS 27.2 in November 2026: focusing Compose's input
 * again during the touchend (WebKit takes it for no change, never opened),
 * moving it under the finger at the touchend (opened about half the time),
 * moving another input in as the touch started (never). An input that is
 * already there is what a page's own fields have, and it opens every time.
 *
 * Transparent, the size of the field's text area less its buttons, and above
 * the canvas. Compose still sees the whole gesture: each pointer event on it is
 * sent on to the canvas, so the field takes focus and puts its caret where the
 * finger was, and a drag that starts on the field scrolls what it is in. The
 * touch events are sent on too, as plain events, for Compose's own bookkeeping,
 * and a move Compose cancels is cancelled here. Its tap is WebKit's own, which
 * focuses it and opens the keyboard; it hands the focus straight to Compose's
 * input, which WebKit allows with the keyboard staying up, and every stand-in
 * stands aside while Compose's input has the focus.
 */
private fun standInInput(): HTMLElement = js(
    """{
        var input = document.createElement('input');
        input.type = 'text';
        input.tabIndex = -1;
        input.setAttribute('aria-hidden', 'true');
        input.setAttribute('autocomplete', 'off');
        input.setAttribute('data-kontour', 'keyboard-stand-in');
        input.style.cssText = 'position:fixed;display:none;margin:0;padding:0;border:0;outline:none;font-size:16px;' +
            'background:transparent;color:transparent;caret-color:transparent;z-index:2147483646;' +
            '-webkit-touch-callout:none;-webkit-user-select:none;user-select:none;-webkit-tap-highlight-color:transparent';
        function canvas() {
            var all = document.querySelectorAll('div');
            for (var i = 0; i < all.length; i++) {
                var root = all[i].shadowRoot;
                var found = root && root.querySelector('canvas');
                if (found) return found;
            }
            return null;
        }
        ['pointerdown', 'pointermove', 'pointerup', 'pointercancel'].forEach(function (type) {
            input.addEventListener(type, function (e) {
                var target = canvas();
                if (!target) return;
                target.dispatchEvent(new PointerEvent(type, {
                    bubbles: true, cancelable: true, composed: true,
                    pointerId: e.pointerId, pointerType: e.pointerType, isPrimary: e.isPrimary,
                    clientX: e.clientX, clientY: e.clientY, screenX: e.screenX, screenY: e.screenY,
                    button: e.button, buttons: e.buttons, pressure: e.pressure,
                    width: e.width, height: e.height, tiltX: e.tiltX, tiltY: e.tiltY
                }));
            });
        });
        var state = window.__kontourStandIn || (window.__kontourStandIn = { touching: false });
        ['touchstart', 'touchmove', 'touchend', 'touchcancel'].forEach(function (type) {
            input.addEventListener(type, function (e) {
                if (type === 'touchstart') state.touching = true;
                if (type === 'touchend' || type === 'touchcancel') {
                    setTimeout(function () { state.touching = false; state.sync(); }, 450);
                }
                var target = canvas();
                if (!target) return;
                var relay;
                try { relay = new TouchEvent(type, { bubbles: true, cancelable: true, composed: true }); } catch (err) { return; }
                target.dispatchEvent(relay);
                if (type === 'touchmove' && relay.defaultPrevented && e.cancelable) e.preventDefault();
            }, { passive: false });
        });
        input.addEventListener('focus', function () {
            var tries = 0;
            (function hand() {
                var compose = state.composeInput();
                if (compose) { compose.focus(); state.touching = false; state.sync(); return; }
                if (tries++ < 20) requestAnimationFrame(hand); else input.blur();
            })();
        });
        return input;
    }"""
)

/**
 * Over its area of the canvas, wherever the canvas is on the page, and placed
 * again whenever the page scrolls or the viewport changes. Fixed to the
 * viewport, a stand-in placed only when Compose's layout moved stayed where the
 * canvas had been: an iPhone scrolls the page to keep a focused field above the
 * keyboard and back again when it closes, and in November 2026 that left every
 * stand-in 63px above its field, so a tap on one field after tapping away from
 * the password field below it landed on the password field's stand-in and
 * opened nothing.
 */
private fun showStandIn(input: HTMLElement, left: Double, top: Double, width: Double, height: Double): Unit = js(
    """{
        input.__kontourArea = [left, top, width, height];
        if (!window.__kontourPlaceStandIn) {
            window.__kontourPlaceStandIn = function (field) {
                var area = field.__kontourArea;
                if (!area) return;
                var canvas = null, all = document.querySelectorAll('div');
                for (var i = 0; i < all.length && !canvas; i++) { var root = all[i].shadowRoot; canvas = root && root.querySelector('canvas'); }
                var origin = canvas ? canvas.getBoundingClientRect() : { left: 0, top: 0 };
                field.style.left = (origin.left + area[0]) + 'px';
                field.style.top = (origin.top + area[1]) + 'px';
                field.style.width = area[2] + 'px';
                field.style.height = area[3] + 'px';
            };
            var again = function () {
                var shown = document.querySelectorAll('input[data-kontour="keyboard-stand-in"]');
                for (var i = 0; i < shown.length; i++) if (shown[i].style.display !== 'none') window.__kontourPlaceStandIn(shown[i]);
            };
            window.addEventListener('scroll', again, true);
            window.addEventListener('resize', again);
            if (window.visualViewport) {
                window.visualViewport.addEventListener('scroll', again);
                window.visualViewport.addEventListener('resize', again);
            }
        }
        window.__kontourPlaceStandIn(input);
        input.style.display = '';
    }"""
)

private fun hideStandIn(input: HTMLElement): Unit = js("{ input.style.display = 'none'; }")

/**
 * While Compose's input has the focus and the keyboard is up, the stand-ins
 * stand aside: a tap on the focused field is for the browser's input laid over
 * it (see `browserText`), and a tap on another field moves the focus Compose
 * already has, which keeps the keyboard up by itself. Keyed on the page's root,
 * so it is one rule for every stand-in.
 *
 * **Worked out from where things are, not from the events that should have
 * said so.** It once followed the focus events of Compose's input, and on an
 * iPhone in November 2026 they did not always come: a tap beside a field closed
 * the keyboard and took the focus from the input with no focusout, so the
 * stand-ins stayed aside and a tap back on the field found nothing to open the
 * keyboard with, "tap in, tap away, tap back and it doesn't work". And a field
 * Compose focuses itself, from a tap on the canvas, has the focus with the
 * keyboard down, which a stand-in is needed for too. So it is asked again
 * whenever it may have changed: the focus moving, a touch ending, and the
 * keyboard coming or going, which on a phone shrinks the visual viewport.
 */
private fun watchComposeFocus(): Unit = js(
    """{
        if (window.__kontourStandInFocus) return;
        window.__kontourStandInFocus = true;
        // Whether the on-screen keyboard is up: on a phone it shrinks the
        // visual viewport, and nothing else on the page says so.
        if (!window.__kontourKeyboardUp) window.__kontourKeyboardUp = function () {
            var v = window.visualViewport;
            return !v || window.innerHeight - v.height * v.scale > 120;
        };
        var style = document.createElement('style');
        style.textContent = 'html[data-kontour-editing] input[data-kontour="keyboard-stand-in"]{pointer-events:none!important;visibility:hidden!important}';
        document.head.appendChild(style);
        var state = window.__kontourStandIn || (window.__kontourStandIn = { touching: false });
        state.composeInput = function () {
            var all = document.querySelectorAll('div');
            for (var i = 0; i < all.length; i++) {
                var root = all[i].shadowRoot;
                var found = root && root.querySelector('.compose-backing-field');
                if (found) return found;
            }
            return null;
        };
        state.composeFocused = function () {
            var compose = state.composeInput();
            return !!(compose && compose.getRootNode().activeElement === compose);
        };
        state.editing = function (on) {
            if (on) document.documentElement.setAttribute('data-kontour-editing', '');
            else document.documentElement.removeAttribute('data-kontour-editing');
        };
        // Not while a tap on a stand-in is still going: Compose focuses its
        // input as the finger lifts, before WebKit has finished the tap, and a
        // stand-in gone from under the finger by then would be no tap at all.
        state.sync = function () {
            if (state.touching) return;
            state.editing(state.composeFocused() && window.__kontourKeyboardUp());
        };
        function later() { setTimeout(state.sync, 0); }
        document.addEventListener('focusin', later, true);
        document.addEventListener('focusout', later, true);
        window.addEventListener('touchend', function () { setTimeout(state.sync, 80); setTimeout(state.sync, 500); }, true);
        if (window.visualViewport) window.visualViewport.addEventListener('resize', state.sync);
    }"""
)

@Composable
internal actual fun InstallKeyboardPrimer() {
    if (!primerNeeded) return
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        if (installs++ > 0) return@DisposableEffect onDispose { installs-- }

        val primer = primerInput()
        document.body?.appendChild(primer)
        letHandedTapsThrough()
        watchComposeFocus()

        // Capturing, and on the touch's *end*: the last moment WebKit counts as the
        // reader's own gesture, and the one a tap is decided at.
        val onTouchStart: (Event) -> Unit = handler@{ event ->
            val touch = (event as? TouchEvent)?.changedTouches?.item(0) ?: return@handler
            handover.onTouchStart(touch.clientX.toFloat(), touch.clientY.toFloat())
        }
        val onTouchEnd: (Event) -> Unit = handler@{ event ->
            val touch = (event as? TouchEvent)?.changedTouches?.item(0) ?: return@handler
            // **A tap on the browser's own input is the browser's.** A focused
            // field's input lies over it (see `browserText`), and a tap there
            // opens the keyboard and places the caret by itself. Blurred and
            // focused again here as well, it lost the caret the tap had just
            // placed, and now and then Compose heard the blur and let the
            // field go, so the next tap had to focus it all over again:
            // reported on an iPhone in November 2026 as a caret that would
            // not go further than near the end, and a field that sometimes
            // took two taps.
            if (touchedComposeInput(event)) return@handler
            val surface = (event.target as? Element)?.getBoundingClientRect() ?: return@handler
            val x = (touch.clientX - surface.left).toFloat()
            val y = (touch.clientY - surface.top).toFloat()
            val inRegion = regions.contains(x, y)
            // **Compose's own input, if it took the focus while the finger was
            // down**, which for a tap of any ordinary length it has: a field
            // asks for focus as it is pressed, and Compose focuses its input
            // before the finger lifts, which WebKit does not count as the
            // reader's, so the keyboard stays down.
            //
            // **WebKit's own tap is let through to it.** The input is moved
            // under the finger, and Compose's `preventDefault` on this touchend
            // is skipped, so WebKit's tap lands on the input itself, exactly as
            // a second tap on the field did, and that is what opens the
            // keyboard. Measured on an iPhone on iOS 27.2 in November 2026:
            // four first taps in four.
            //
            // Two things were tried here before and do not work there.
            // 0.4.2 primed its own input, which took the focus from Compose's,
            // and Compose took it back outside the touch. Until 0.5.4 the
            // input was blurred and focused again during the touchend: WebKit
            // takes a focus that returns to the same element in the same turn
            // for no change at all, and on 27.2 the keyboard never came, so
            // every field took two taps.
            val compose = composeInput(event)
            if (inRegion && compose != null && handover.isTap(touch.clientX.toFloat(), touch.clientY.toFloat())) {
                handover.onTouchEnd(touch.clientX.toFloat(), touch.clientY.toFloat(), inRegion = false, editing = true, now = now())
                handTapToInput(event, compose, touch.clientX.toDouble(), touch.clientY.toDouble())
                return@handler
            }
            val active = document.activeElement
            val editing = compose != null || (active != primer && (active is HTMLInputElement || active is HTMLTextAreaElement))
            val prime = handover.onTouchEnd(
                x = touch.clientX.toFloat(),
                y = touch.clientY.toFloat(),
                inRegion = inRegion,
                editing = editing,
                now = now(),
            )
            if (prime) {
                // Where the finger was, so WebKit has nothing to scroll into view.
                primer.style.left = "${touch.clientX}px"
                primer.style.top = "${touch.clientY}px"
                primer.focus()
                scope.launch {
                    delay(KeyboardPrimerHandover.Hold)
                    if (handover.shouldRelease(now()) && document.activeElement == primer) primer.blur()
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
 * Compose's own input, if the touch [event] is on a Compose surface whose input
 * has the focus: the element it types through, in the surface's shadow root.
 */
private fun composeInput(event: Event): HTMLElement? {
    val root = composeShadowRoot(event) ?: return null
    return focusedBackingField(root)
}

/**
 * Lets WebKit's own tap of the touch [event] land on Compose's [input]: the
 * input under the finger at ([x], [y]) until WebKit has clicked it, and
 * Compose's `preventDefault` on [event] skipped (see [letHandedTapsThrough]).
 *
 * The caret is Compose's. Compose has already put it where the finger was,
 * from its own tap, and WebKit puts its own where the finger was *in the small
 * box the input is for the moment*, which is somewhere else in the text; so
 * Compose's is put back once WebKit's click has been and gone. Then the input
 * goes back to where it was, which `browserText` lays over the field.
 */
private fun handTapToInput(event: Event, input: HTMLElement, x: Double, y: Double): Unit = js(
    """{
        var start = input.selectionStart, end = input.selectionEnd, style = input.style, saved = style.cssText;
        window.__kontourHandedTap = event;
        var place = { position: 'fixed', left: (x - 30) + 'px', top: (y - 15) + 'px', width: '60px', height: '30px',
            'z-index': '2147483647', 'pointer-events': 'auto', 'font-size': '16px', 'clip-path': 'none' };
        for (var k in place) style.setProperty(k, place[k], 'important');
        var done = false;
        function restore() {
            if (done) return;
            done = true;
            style.cssText = saved;
            if (window.__kontourHandedTap === event) window.__kontourHandedTap = null;
            try { if (start != null) input.setSelectionRange(start, end); } catch (e) {}
        }
        input.addEventListener('click', function () { setTimeout(restore, 0); }, { once: true });
        setTimeout(restore, 400);
    }"""
)

/**
 * Compose cancels a touchend it has used, which stops WebKit's own tap; for the
 * one touchend [handTapToInput] is handing on, that is skipped. Once per page.
 */
private fun letHandedTapsThrough(): Unit = js(
    """{
        if (window.__kontourHandedTapGuard) return;
        window.__kontourHandedTapGuard = true;
        var original = TouchEvent.prototype.preventDefault;
        TouchEvent.prototype.preventDefault = function () {
            if (this === window.__kontourHandedTap) return;
            return original.call(this);
        };
    }"""
)

/** Whether the touch [event] landed on Compose's own input itself, rather than its canvas. */
private fun touchedComposeInput(event: Event): Boolean = js(
    """{
        var path = event.composedPath ? event.composedPath() : [];
        var first = path[0];
        return !!(first && first.classList && first.classList.contains('compose-backing-field'));
    }"""
)

private fun composeShadowRoot(event: Event): JsAny? = js(
    """{
        var path = event.composedPath ? event.composedPath() : [];
        for (var i = 0; i < path.length; i++) {
            var node = path[i];
            if (node && node.shadowRoot && node.shadowRoot.querySelector('.compose-backing-field, canvas')) return node.shadowRoot;
        }
        return null;
    }"""
)

private fun focusedBackingField(root: JsAny): HTMLElement? = js(
    "(root.activeElement && root.activeElement.classList.contains('compose-backing-field')) ? root.activeElement : null"
)

/**
 * The input the keyboard opens for, where Compose has none focused yet: a field
 * focused from an effect after the touch, or a tap shorter than a frame.
 *
 * At 16px, below which WebKit zooms the page to a focused input; out of the
 * accessibility tree and the tab order, and touched by nothing — it exists to be
 * focused for a few frames and handed on. **Not `opacity: 0`**, which it was:
 * drawn in nothing rather than not drawn, as Compose draws its own input, so
 * that nothing about it reads to the browser as an input the reader cannot see.
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
        fontSize = "16px"
        background = "transparent"
        color = "transparent"
        outline = "none"
        setProperty("caret-color", "transparent")
        setProperty("pointer-events", "none")
    }
    return input
}
