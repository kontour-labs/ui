package io.kontour.ui.platform

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The generated radius table, read.
 *
 * The parser lives in commonMain precisely so this file can exist: a
 * `Build.DEVICE` cannot be set in a test and there is no Android test source set
 * in this repository, so the Android actual is kept down to tier selection and
 * everything with a decision in it is here, on the JVM, against a fixture.
 *
 * The fixture is three records in the real format rather than the shipped table.
 * A test that read `DeviceRadiusTable` would be asserting what a scraper found
 * this month, which is data rather than behaviour — and it would fail the next
 * time somebody runs `--pull` on a repository that has gained a phone.
 */
class DeviceCornerTableTest {

    @Test
    fun aListedDeviceGetsItsOwnRadii() {
        val corners = deviceRadiiFrom(Table, "grus")
        assertEquals(DeviceCorners(43.dp, 43.dp, 50.dp, 50.dp), corners)
    }

    /**
     * And the top and bottom pairs are allowed to differ, which is the point.
     *
     * Four of the twenty-seven devices in the shipped table declare them
     * differently — `grus` at 43 and 50, `monet` at 50 and 45 — and AOSP's own
     * resources are a `_top` and a `_bottom` rather than one number. A reader
     * that collapsed them would be throwing away the only thing the table knows
     * that a single radius does not.
     */
    @Test
    fun theTopAndBottomPairsAreIndependent() {
        val corners = deviceRadiiFrom(Table, "grus")!!
        assertEquals(corners.topLeft, corners.topRight, "the top pair disagreed")
        assertEquals(corners.bottomLeft, corners.bottomRight, "the bottom pair disagreed")
        assertEquals(50.dp - 43.dp, corners.bottomLeft - corners.topLeft)
    }

    @Test
    fun theLookupIgnoresCase() {
        assertEquals(deviceRadiiFrom(Table, "grus"), deviceRadiiFrom(Table, "GRUS"))
    }

    /**
     * A codename that is a *prefix* of a listed one is not that device.
     *
     * `gru` is not `grus`, and a substring match would hand it a Xiaomi's bezel.
     * The record's own separator is what bounds the key, which is why the
     * parser compares the region up to the first `|` rather than using
     * `startsWith`.
     */
    @Test
    fun aPrefixIsNotAMatch() {
        assertNull(deviceRadiiFrom(Table, "gru"))
        assertNull(deviceRadiiFrom(Table, "grusx"))
    }

    @Test
    fun anUnlistedDeviceIsNull() {
        assertNull(deviceRadiiFrom(Table, "sailfish"))
        assertNull(deviceRadiiFrom(Table, ""))
    }

    /**
     * A malformed record is skipped rather than thrown on.
     *
     * This is a thousand scraped repositories parsed by a script, and the right
     * failure mode for a bad row is the corner nobody could describe anyway — not
     * an exception on the first composition of a screen, which is the shape of
     * failure `Accessibility.android.kt` records shipping once already.
     */
    @Test
    fun aMalformedRecordIsSkippedAndTheRestStillRead() {
        val broken = "bad;also|bad;|12|12;zero|0|0;grus|43|50"
        assertNull(deviceRadiiFrom(broken, "bad"))
        assertNull(deviceRadiiFrom(broken, "also"))
        assertNull(deviceRadiiFrom(broken, "zero"))
        assertEquals(DeviceCorners(43.dp, 43.dp, 50.dp, 50.dp), deviceRadiiFrom(broken, "grus"))
    }

    /** The table carries radii and never a curve; the curve is a separate judgement. */
    @Test
    fun theTableSaysNothingAboutTheCurve() {
        assertNull(deviceRadiiFrom(Table, "grus")!!.smoothing)
    }

    @Test
    fun anAllSquareDeviceReadsAsKnowingNothing() {
        assertEquals(true, DeviceCorners.uniform(0.dp).isSquare)
        assertEquals(false, DeviceCorners.uniform(1.dp).isSquare)
        assertEquals(false, DeviceCorners(0.dp, 0.dp, 0.dp, 8.dp).isSquare)
    }

    private companion object {
        /** Three real records, in the shipped format. */
        const val Table = "akita|51|51;grus|43|50;taimen|26|26"
    }
}
