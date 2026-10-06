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
 */
internal expect fun Modifier.opensKeyboard(enabled: Boolean = true): Modifier

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
    IosDevice.containsMatchIn(userAgent) || (platform == "MacIntel" && maxTouchPoints > 1)

private val IosDevice = Regex("iPhone|iPad|iPod")

/**
 * Whether this browser is on a device whose text is selected by touch: a phone
 * or a tablet that says so, or anything whose main pointer is a finger. iOS is
 * [isIosWebKit]'s to say, as an iPad says it is a Mac.
 */
internal fun isTouchFirst(userAgent: String, coarsePointer: Boolean): Boolean =
    TouchDevice.containsMatchIn(userAgent) || coarsePointer

private val TouchDevice = Regex("Android|Mobi")

/**
 * The marked regions, in CSS pixels from the top left of the page's Compose
 * surface, keyed by the element that marked each.
 */
internal class KeyboardPrimerRegions {
    private val regions = LinkedHashMap<Any, Rect>()

    fun put(key: Any, bounds: Rect) {
        regions[key] = bounds
    }

    fun remove(key: Any) {
        regions.remove(key)
    }

    /** Whether ([x], [y]) is inside a marked region, edges included. */
    fun contains(x: Float, y: Float): Boolean = regions.values.any {
        x >= it.left && x <= it.right && y >= it.top && y <= it.bottom
    }

    val isEmpty: Boolean get() = regions.isEmpty()
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
