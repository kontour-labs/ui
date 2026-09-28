package io.kontour.ui.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Follow device" gives all four settings back, and any pin takes it away.
 *
 * Two symptoms were reported together from an iPhone and only one of them is a
 * bug. This file is the other one, written down as the intention it is, so the
 * next reader does not go looking for a second fault.
 *
 * The report: the phone was in light mode; turning **Dark** on worked; turning
 * **Follow device** on afterwards did *not* turn dark off; and then turning
 * **Dark** off turned **Follow device** off as well.
 *
 * The third of those is this object working. [CatalogSettings.followsDevice] is
 * derived rather than stored — it is "are all four still null" — so writing any
 * pin at all necessarily clears it. A reader who reaches for the Dark switch is
 * saying they want dark, not that they want the device's answer, and the switch
 * that says "I am taking the device's answer" has to stop claiming so.
 *
 * It only looked wrong because of the second symptom, which is a real bug and is
 * not here: `platformReportAppearance` fed its own reader on iOS, so "follow the
 * device" followed the application. `AppearanceReportTest` holds that invariant,
 * and `deviceInDarkTheme` is the fix.
 *
 * ### What this deliberately does not assert
 *
 * That `followDevice()` makes the resolved theme the device's again. The plan
 * for this round called for it and it is worth saying why it is absent: the
 * resolution is `settings.dark ?: systemDark`, so a test of it restates the
 * elvis operator. Every bit of the defect lived in what `systemDark` *was*, one
 * layer down and on a platform this host cannot run.
 */
class FollowDeviceTest {

    @Test
    fun aFreshSettingsObjectIsFollowingTheDevice() {
        assertTrue(
            CatalogSettings().followsDevice,
            "the gallery starts by pinning something, so a reader has to " +
                "discover the Follow device switch before the accessibility " +
                "settings on their phone reach it",
        )
    }

    @Test
    fun pinningAnyOneOfTheFourStopsItFollowing() {
        val pins = listOf<Pair<String, (CatalogSettings) -> Unit>>(
            "dark" to { it.dark = true },
            "high contrast" to { it.highContrast = true },
            "reduced motion" to { it.reduceMotion = true },
            "type size" to { it.textScale = 1.3f },
        )

        for ((name, pin) in pins) {
            val settings = CatalogSettings()
            pin(settings)
            assertFalse(
                settings.followsDevice,
                "pinning $name left the gallery claiming to follow the device. " +
                    "The claim is about all four at once: a reader who has set " +
                    "one of them by hand is no longer taking the device's answer " +
                    "for everything, and the switch that says so must say so.",
            )
        }
    }

    @Test
    fun followingTheDeviceHandsBackAllFourAtOnce() {
        val settings = CatalogSettings().apply {
            pinToDevice(dark = true, highContrast = true, reduceMotion = true, textScale = 1.3f)
        }
        assertFalse(settings.followsDevice, "pinning all four left it still following")

        settings.followDevice()

        assertTrue(settings.followsDevice, "following the device did not take")
        assertEquals(null, settings.dark, "dark was left pinned")
        assertEquals(null, settings.highContrast, "high contrast was left pinned")
        assertEquals(null, settings.reduceMotion, "reduced motion was left pinned")
        assertEquals(
            null,
            settings.textScale,
            "type size was left pinned — the one of the four that used to default " +
                "to a hard 1f rather than to the device's own scale",
        )
    }
}
