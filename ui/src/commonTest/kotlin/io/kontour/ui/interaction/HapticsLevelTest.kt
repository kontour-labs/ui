package io.kontour.ui.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What each of the four levels lets through, stated once rather than per component.
 *
 * `DetentHapticsTest` asserts what each component *performs*, which is the
 * stronger claim where the answer is "nothing": an intent never performed is
 * silent at every level and under a consumer's replacement dispatcher too. This
 * is the other half — for the components that do perform something, which levels
 * they survive — and it belongs here because it is a property of four enum
 * constants and not of twenty-eight components. Asserting it component by
 * component would be the same four facts repeated until one of them drifted.
 *
 * ### Why the levels are a pair of tiers and not a dial
 *
 * The library has one axis of real choice in it: **progress** against **outcome**.
 * A drag reporting where it is passing is progress, and it is the feedback most
 * likely to be unwelcome — delightful once, wearing on a long form, and actively
 * unpleasant for some readers. A drag reporting that it has arrived, refused,
 * or committed is an outcome, and dropping those makes the app quieter about
 * things the reader cannot otherwise tell.
 *
 * So `Reduced` is the interesting level and the other three are its neighbours:
 * `Off` below it, `Standard` above it with progress back, and `Full` above that
 * with the one decorative intent nothing performs. A five-position dial would be
 * two positions of meaning and three of decoration.
 */
class HapticsLevelTest {

    /** Everything a component in this library can ask for. */
    private val performed = listOf(
        FeedbackIntent.Tap,
        FeedbackIntent.ToggleOn,
        FeedbackIntent.ToggleOff,
        FeedbackIntent.Tick,
        FeedbackIntent.Snap,
        FeedbackIntent.DragThreshold,
        FeedbackIntent.DragThresholdBack,
        FeedbackIntent.Limit,
        FeedbackIntent.Hold,
        FeedbackIntent.LongPress,
        FeedbackIntent.GestureEnd,
        FeedbackIntent.Confirm,
        FeedbackIntent.Warn,
    )

    @Test
    fun offAllowsNothingAtAll() {
        val allowed = FeedbackIntent.entries.filter { HapticsLevel.Off.allows(it) }
        assertEquals(
            emptyList(), allowed,
            "`Off` let $allowed through. It is the level a reader picks when they " +
                "have asked for silence, and a single exception makes it a lie — " +
                "there is no intent important enough to override that, the " +
                "destructive warning included.",
        )
    }

    @Test
    fun reducedKeepsOutcomesAndDropsProgress() {
        val progress = listOf(
            FeedbackIntent.Tap,
            FeedbackIntent.ToggleOn,
            FeedbackIntent.ToggleOff,
            FeedbackIntent.Tick,
            FeedbackIntent.Snap,
            FeedbackIntent.Selection,
            FeedbackIntent.KeyPress,
            FeedbackIntent.Hold,
            // A dragged row landing: the snaps before it were the news.
            FeedbackIntent.GestureEnd,
        )
        for (intent in progress) {
            assertTrue(
                !HapticsLevel.Reduced.allows(intent),
                "`Reduced` let $intent through. This is the level's whole point: a " +
                    "drag still reports arriving somewhere without buzzing the " +
                    "whole way there.",
            )
        }
        val outcomes = FeedbackIntent.entries - progress.toSet()
        for (intent in outcomes) {
            assertTrue(
                HapticsLevel.Reduced.allows(intent),
                "`Reduced` dropped $intent. It is not a quieter `Standard` — it " +
                    "keeps every report of something the reader could not " +
                    "otherwise tell, and drops only the running commentary.",
            )
        }
    }

    @Test
    fun standardAllowsEverythingTheLibraryPerforms() {
        for (intent in performed) {
            assertTrue(
                HapticsLevel.Standard.allows(intent),
                "the default level dropped $intent, which some component in the " +
                    "library performs — so that component is silent out of the box " +
                    "and nothing in its own tests would say so.",
            )
        }
        assertTrue(
            !HapticsLevel.Standard.allows(FeedbackIntent.KeyPress),
            "`Standard` allowed `KeyPress`, which leaves nothing between it and " +
                "`Full`. `KeyPress` is the one intent nothing here performs, which " +
                "is exactly what makes it the honest differentiator: the level " +
                "above the default is for a consumer's own decorative feedback.",
        )
    }

    @Test
    fun fullAllowsEveryIntentThereIs() {
        val dropped = FeedbackIntent.entries.filterNot { HapticsLevel.Full.allows(it) }
        assertEquals(
            emptyList(), dropped,
            "`Full` dropped $dropped. It is the one level with no editorial in it.",
        )
    }

    @Test
    fun theLevelsAreDeclaredQuietestFirst() {
        // Not cosmetic. The settings control offers `HapticsLevel.entries` in
        // declaration order, and a reader reading a row of four segments takes
        // left-to-right as less-to-more. A level inserted in the wrong place
        // would put `Off` in the middle of the scale with nothing failing.
        val counts = HapticsLevel.entries.map { level ->
            FeedbackIntent.entries.count { level.allows(it) }
        }
        assertEquals(
            counts.sorted(), counts,
            "the levels allow $counts intents in declaration order, which is not " +
                "ascending — so the settings control draws them out of order.",
        )
    }
}
