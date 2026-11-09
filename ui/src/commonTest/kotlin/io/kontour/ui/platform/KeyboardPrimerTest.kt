package io.kontour.ui.platform

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The parts of the iOS keyboard primer that are decisions rather than DOM calls.
 *
 * Reported: *"on Safari on iOS, the keyboard doesn't open when selecting a text
 * field."* What reaches the browser cannot be run here — no WebKit, no keyboard —
 * so the decisions are held to account instead: which browsers get the primer,
 * which taps prime it, and when it lets go. Whether the keyboard then opens is a
 * thing to try on an iPhone.
 */
class KeyboardPrimerTest {

    @Test
    fun onlyWebKitOnIosAndIpadOsIsPrimed() {
        val cases = listOf(
            Triple("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile/15E148 Safari/604.1", "iPhone", 5) to true,
            // Chrome on an iPhone is WebKit with another name.
            Triple("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 CriOS/130.0 Mobile/15E148 Safari/604.1", "iPhone", 5) to true,
            // An iPad asking for the desktop site says it is a Mac, with a touch screen.
            Triple("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/18.0 Safari/605.1.15", "MacIntel", 5) to true,
            // A Mac is a Mac.
            Triple("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/18.0 Safari/605.1.15", "MacIntel", 0) to false,
            Triple("Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36", "Linux armv81", 5) to false,
            Triple("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0 Safari/537.36", "Win32", 10) to false,
        )
        for ((browser, primed) in cases) {
            val (agent, platform, touches) = browser
            assertEquals(primed, isIosWebKit(agent, platform, touches), "$platform, $touches touch points: $agent")
        }
    }

    @Test
    fun aRegionContainsItsEdgesAndNothingOutside() {
        val regions = KeyboardPrimerRegions()
        val field = Any()
        regions.put(field, Rect(16f, 100f, 344f, 156f))
        assertTrue(regions.contains(16f, 100f))
        assertTrue(regions.contains(180f, 128f))
        assertTrue(regions.contains(344f, 156f))
        assertFalse(regions.contains(15.5f, 128f))
        assertFalse(regions.contains(180f, 157f))

        // Moved, and then gone.
        regions.put(field, Rect(16f, 300f, 344f, 356f))
        assertFalse(regions.contains(180f, 128f), "a region that moved should be where it is now")
        regions.remove(field)
        assertTrue(regions.isEmpty)
        assertFalse(regions.contains(180f, 328f))
    }

    /**
     * A button in a field is cut out of the field's region: the password field's
     * eye, tapped on an iPhone, opened the keyboard and closed it again.
     */
    @Test
    fun aButtonInAFieldIsCutOutOfItsRegion() {
        val regions = KeyboardPrimerRegions()
        val field = Any()
        val eye = Any()
        regions.put(field, Rect(16f, 100f, 344f, 156f))
        regions.putHole(eye, Rect(304f, 112f, 336f, 144f))
        assertTrue(regions.contains(180f, 128f), "the field's text should still open the keyboard")
        assertFalse(regions.contains(320f, 128f), "the eye should not open the keyboard")
        assertTrue(regions.contains(340f, 128f), "the field's padding beside the eye should still open it")

        regions.remove(eye)
        assertTrue(regions.contains(320f, 128f), "a button that has gone should leave its field whole")
    }

    /** A field's stand-in input leaves its buttons out: the eye at the end, an icon button at the start. */
    @Test
    fun aStandInLeavesTheFieldsButtonsOut() {
        val regions = KeyboardPrimerRegions()
        val field = Any()
        regions.put(field, Rect(16f, 100f, 344f, 156f))
        assertEquals(Rect(16f, 100f, 344f, 156f), regions.tapArea(field))

        val eye = Any()
        regions.putHole(eye, Rect(304f, 112f, 336f, 144f))
        assertEquals(Rect(16f, 100f, 304f, 156f), regions.tapArea(field), "the eye at the end should be left out")

        val leading = Any()
        regions.putHole(leading, Rect(20f, 112f, 52f, 144f))
        assertEquals(Rect(52f, 100f, 304f, 156f), regions.tapArea(field), "a button at the start should be left out too")

        val elsewhere = Any()
        regions.putHole(elsewhere, Rect(100f, 300f, 140f, 340f))
        assertEquals(Rect(52f, 100f, 304f, 156f), regions.tapArea(field), "a button in another row should not touch the field")
        assertEquals(null, regions.tapArea(Any()))
    }

    /** A tap at (100, 100), ending in a region or not, with a keyboard up or not. */
    private fun KeyboardPrimerHandover.tap(inRegion: Boolean = true, editing: Boolean = false, now: Duration = Duration.ZERO, moved: Float = 0f): Boolean {
        onTouchStart(100f, 100f)
        return onTouchEnd(100f + moved, 100f, inRegion = inRegion, editing = editing, now = now)
    }

    @Test
    fun aTapInARegionPrimesUnlessTheKeyboardIsAlreadyUp() {
        val handover = KeyboardPrimerHandover()
        assertFalse(handover.tap(inRegion = false), "a tap elsewhere primes nothing")
        assertFalse(handover.tap(editing = true), "a keyboard that is up stays up by itself")
        assertTrue(handover.tap())
        assertTrue(handover.primed)
    }

    /** A scroll that ends on a field, or a knob turned, is not asking to type. */
    @Test
    fun aDragThatEndsInARegionPrimesNothing() {
        val handover = KeyboardPrimerHandover()
        assertTrue(handover.tap(moved = KeyboardPrimerHandover.TapSlop), "a finger's wobble is still a tap")
        handover.onFocusMoved(toPrimer = false)
        assertFalse(handover.tap(moved = KeyboardPrimerHandover.TapSlop + 1f))
        assertFalse(handover.primed)
        assertFalse(
            handover.onTouchEnd(100f, 100f, inRegion = true, editing = false, now = Duration.ZERO),
            "an end with no start seen is not a tap",
        )
    }

    @Test
    fun focusMovingOnHandsOverAndNothingIsReleased() {
        val handover = KeyboardPrimerHandover()
        handover.tap(now = 1.seconds)
        handover.onFocusMoved(toPrimer = true)
        assertTrue(handover.primed, "the primer taking focus is the priming, not a handover")
        handover.onFocusMoved(toPrimer = false)
        assertFalse(handover.primed)
        assertFalse(handover.shouldRelease(1.seconds + KeyboardPrimerHandover.Hold), "a field took the keyboard: leave it")
    }

    @Test
    fun aPrimerNobodyTookIsReleasedAfterTheHoldAndOnlyThen() {
        val handover = KeyboardPrimerHandover()
        handover.tap(now = 1.seconds)
        assertFalse(handover.shouldRelease(1.seconds + KeyboardPrimerHandover.Hold - 1.milliseconds))
        assertTrue(handover.shouldRelease(1.seconds + KeyboardPrimerHandover.Hold))
        assertFalse(handover.shouldRelease(1.seconds + KeyboardPrimerHandover.Hold * 2), "released once")
    }

    /**
     * Which browsers hand a focused field's touches to their own input: every
     * phone and tablet, and anything whose main pointer is a finger — and not a
     * desktop with a mouse, whose selection Compose draws as it always has.
     */
    @Test
    fun touchFirstBrowsersAreThePhonesAndTabletsAndTheFingerPointed() {
        assertTrue(isTouchFirst("Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36", coarsePointer = true))
        // A tablet that says only Android, and a pointer it reports as fine.
        assertTrue(isTouchFirst("Mozilla/5.0 (Linux; Android 14; SM-X710) AppleWebKit/537.36 Chrome/130.0 Safari/537.36", coarsePointer = false))
        assertTrue(isTouchFirst("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130.0 Safari/537.36", coarsePointer = true))
        assertFalse(isTouchFirst("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0 Safari/537.36", coarsePointer = false))
        assertFalse(isTouchFirst("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/18.0 Safari/605.1.15", coarsePointer = false))
    }

    /** Whether a touch is a tap is asked without ending it: the end still sees its start. */
    @Test
    fun askingWhetherATouchIsATapLeavesItAsItWas() {
        val handover = KeyboardPrimerHandover()
        handover.onTouchStart(100f, 100f)
        assertTrue(handover.isTap(104f, 100f))
        assertFalse(handover.isTap(100f + KeyboardPrimerHandover.TapSlop + 1f, 100f))
        assertTrue(handover.onTouchEnd(104f, 100f, inRegion = true, editing = false, now = Duration.ZERO))
    }
}
