package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Marks a region a tap on which puts a caret in a text field, so the browser
 * opens the on-screen keyboard for it.
 *
 * Reported: *"on Safari on iOS, the keyboard doesn't open when selecting a text
 * field."* WebKit on iOS opens the keyboard only for an input focused inside the
 * handler of the touch that asked for it. Compose for the web starts its text
 * input later: the tap arrives as a pointer event, the field takes focus, and
 * the hidden `<input>` Compose types through is focused a frame on — after the
 * touch has ended, so the caret blinks with no keyboard under it. A field that
 * focuses itself from an effect — a combobox's editor, the nav bar's search, a
 * knob's typed value — is later still.
 *
 * So a region marked with this primes the keyboard: on the touch's end, while
 * the browser still counts it as the reader's, [InstallKeyboardPrimer]'s hidden
 * input takes focus and the keyboard opens for it. When Compose's own input
 * takes focus a moment later the keyboard stays, and if none does within
 * [KeyboardPrimerHandover.Hold] the primer lets go and the keyboard closes.
 *
 * Only in a browser on iOS or iPadOS, every one of which is WebKit. Nothing
 * anywhere else, where a field's focus already opens the keyboard.
 *
 * **With [standIn], a real input lies over the region before it is touched**,
 * which is what a text field needs: see the web actual. Not for anything a
 * finger drags, as a knob is, whose drag the input would take.
 */
internal expect fun Modifier.opensKeyboard(enabled: Boolean = true, standIn: Boolean = false): Modifier

/**
 * Cuts this element out of any [opensKeyboard] region it sits in: a button in a
 * field, whose tap does what the button does and puts no caret anywhere.
 *
 * Found on an iPhone in November 2026 as the password field's eye: tapped while
 * the field was not focused, it was inside the field's region, so the primer
 * took the focus and the keyboard opened; no field took it over, and after
 * [KeyboardPrimerHandover.Hold] the primer let go and the keyboard went away
 * again. Every icon button marks itself, so a field's clear button, its eye and
 * an app's own button in a field's trailing slot are all left out.
 *
 * Nothing anywhere [opensKeyboard] is nothing.
 */
internal expect fun Modifier.keepsKeyboardClosed(): Modifier

/**
 * The hidden input [opensKeyboard] focuses, and the listener that decides when.
 * Once per window, from `KontourTheme`; nothing off the web.
 */
@Composable
internal expect fun InstallKeyboardPrimer()

/**
 * Whether this browser is WebKit on iOS or iPadOS, where an input focused after
 * the touch has ended gets no keyboard.
 *
 * Every browser there is WebKit, whatever it is called, so Chrome and Firefox on
 * an iPhone have the same refusal as Safari. iPadOS asks for the desktop site by
 * default and says it is a Mac: a "Mac" with a touch screen is an iPad.
 */
internal fun isIosWebKit(userAgent: String, platform: String, maxTouchPoints: Int): Boolean =
    IosDevice.any { it in userAgent } || (platform == "MacIntel" && maxTouchPoints > 1)

private val IosDevice = listOf("iPhone", "iPad", "iPod")

/**
 * Whether this browser is on a device whose text is selected by touch: a phone
 * or a tablet that says so, or anything whose main pointer is a finger. iOS is
 * [isIosWebKit]'s to say, as an iPad says it is a Mac.
 */
internal fun isTouchFirst(userAgent: String, coarsePointer: Boolean): Boolean =
    TouchDevice.any { it in userAgent } || coarsePointer

private val TouchDevice = listOf("Android", "Mobi")

/**
 * The marked regions, in CSS pixels from the top left of the page's Compose
 * surface, keyed by the element that marked each.
 */
internal class KeyboardPrimerRegions {
    private val regions = LinkedHashMap<Any, Rect>()
    private val holes = LinkedHashMap<Any, Rect>()

    fun put(key: Any, bounds: Rect) {
        regions[key] = bounds
    }

    /** Marks [bounds] as cut out of every region: see [keepsKeyboardClosed]. */
    fun putHole(key: Any, bounds: Rect) {
        holes[key] = bounds
    }

    fun remove(key: Any) {
        regions.remove(key)
        holes.remove(key)
    }

    /** Whether ([x], [y]) is inside a marked region and outside every hole, edges included. */
    fun contains(x: Float, y: Float): Boolean =
        regions.values.any { it.has(x, y) } && holes.values.none { it.has(x, y) }

    private fun Rect.has(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom

    val isEmpty: Boolean get() = regions.isEmpty()

    /**
     * The part of [key]'s region a stand-in input may cover: the region with every
     * hole across its height cut off the side it is on. A field's buttons sit at
     * its ends, the eye or the clear button at the trailing one, and a rectangle
     * can only lose a strip, not a hole in its middle; one in the middle takes
     * the side its centre is nearer. Null for an unknown key or nothing left.
     */
    fun tapArea(key: Any): Rect? {
        val region = regions[key] ?: return null
        var left = region.left
        var right = region.right
        for (hole in holes.values) {
            if (hole.bottom <= region.top || hole.top >= region.bottom || hole.right <= left || hole.left >= right) continue
            if (hole.center.x >= region.center.x) right = minOf(right, hole.left) else left = maxOf(left, hole.right)
        }
        return if (right > left) Rect(left, region.top, right, region.bottom) else null
    }
}

/**
 * When to prime the keyboard, and when to let go of it.
 *
 * Three states. Idle, until a tap ends in a marked region with nothing being
 * typed into — then primed, the hidden input focused. Handed over when focus
 * moves to anything else, which is Compose's own input taking it, or the reader
 * going elsewhere; either way the primer has nothing more to do. Released if it
 * is still primed after [Hold], so a tap that turned out not to focus a field
 * does not leave a keyboard open over nothing.
 */
internal class KeyboardPrimerHandover {
    private var primedAt: Duration? = null
    private var startX = Float.NaN
    private var startY = Float.NaN

    val primed: Boolean get() = primedAt != null

    /** A touch has started at ([x], [y]), in CSS pixels. */
    fun onTouchStart(x: Float, y: Float) {
        startX = x
        startY = y
    }

    /** Whether a touch ending at ([x], [y]) is a tap, rather than a drag. Leaves the touch as it was. */
    fun isTap(x: Float, y: Float): Boolean {
        val dx = x - startX
        val dy = y - startY
        // NaN when no start was seen, which compares false: not a tap.
        return dx * dx + dy * dy <= TapSlop * TapSlop
    }

    /**
     * A touch has ended at ([x], [y]). Returns whether to focus the primer now:
     * a tap rather than a drag — a list scrolled under the finger, a knob turned
     * — inside a region, and only when nothing is being typed into already, as a
     * keyboard that is up stays up as focus moves between fields.
     */
    fun onTouchEnd(x: Float, y: Float, inRegion: Boolean, editing: Boolean, now: Duration): Boolean {
        val dx = x - startX
        val dy = y - startY
        startX = Float.NaN
        startY = Float.NaN
        // NaN when no start was seen, which compares false: not a tap.
        val tap = dx * dx + dy * dy <= TapSlop * TapSlop
        if (!tap || !inRegion || editing) return false
        primedAt = now
        return true
    }

    /** Focus has moved; [toPrimer] when it moved to the primer itself. */
    fun onFocusMoved(toPrimer: Boolean) {
        if (!toPrimer) primedAt = null
    }

    /** Whether to blur the primer [now]: still holding, and for long enough. */
    fun shouldRelease(now: Duration): Boolean {
        val at = primedAt ?: return false
        if (now - at < Hold) return false
        primedAt = null
        return true
    }

    companion object {
        /**
         * How long the primer holds the keyboard for a field to take it.
         *
         * Long enough for the slowest path here, a field focused from an effect
         * after a menu opens, which lands within a few frames; short enough that a
         * tap on a field that never takes focus — a disabled one that changed
         * state under the finger — shows a keyboard only in passing.
         */
        val Hold: Duration = 750.milliseconds

        /**
         * How far a touch can travel, in CSS pixels, and still be a tap: about a
         * finger's wobble, and less than any scroll or turn begins with.
         */
        const val TapSlop: Float = 10f
    }
}
