package io.kontour.ui.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How hard each intent is meant to feel, and the ordering that makes it a scale.
 *
 * Reported from a phone: *"all the haptics feel heavy, there doesn't seem to be the
 * concept of a 'soft' interaction for anything"*, with the example that scrolling a
 * wheel picker and swiping a switch ought to feel different and do not.
 *
 * They did not, and the cause was that nothing in the library said how hard
 * anything should be. Ten intents named what *happened*; each platform's own actual
 * then chose a constant per intent independently; and on Android below 14 the two
 * intents that were supposed to be the light ones resolved to the same `VirtualKey`
 * click — which is that platform's middle weight. So the library had two tiers of
 * meaning and one tier of sensation.
 *
 * This file is the policy. There is no test of the platform tables — Android has no
 * test source set in this repository and no SDK here to build one with — so what
 * can be held is the assignment, the ordering, and that the rate floor is asking a
 * different question from this one.
 */
class FeedbackFeelTest {

    /**
     * Every intent is placed, and the partition is written out rather than sampled.
     *
     * The `when` behind [FeedbackIntent.feel] is exhaustive, so an intent added
     * without a feel does not compile. What that cannot catch is an intent being
     * moved between tiers by accident, which is what this is for: the table is here
     * in full, so changing it is an edit somebody makes on purpose.
     */
    @Test
    fun everyIntentIsPlacedAndTheTableIsWrittenDown() {
        assertEquals(
            mapOf(
                FeedbackFeel.Light to listOf(
                    FeedbackIntent.Tick,
                    FeedbackIntent.Hold,
                    FeedbackIntent.GestureEnd,
                    FeedbackIntent.KeyPress,
                ),
                FeedbackFeel.Medium to listOf(
                    FeedbackIntent.Selection,
                    FeedbackIntent.Tap,
                    FeedbackIntent.DragThreshold,
                ),
                FeedbackFeel.Heavy to listOf(FeedbackIntent.LongPress),
                FeedbackFeel.Success to listOf(FeedbackIntent.Confirm),
                FeedbackFeel.Danger to listOf(FeedbackIntent.Reject, FeedbackIntent.Warn),
            ),
            FeedbackIntent.entries.groupBy { it.feel },
        )
    }

    /**
     * The report, as an assertion.
     *
     * A detent going past under a finger is lighter than a control answering a
     * press. Both halves matter: that they differ at all, and which way round.
     */
    @Test
    fun aDetentIsLighterThanAPress() {
        assertEquals(FeedbackFeel.Light, FeedbackIntent.Tick.feel, "a wheel row")
        assertEquals(FeedbackFeel.Medium, FeedbackIntent.Tap.feel, "a switch answering a tap")
        assertTrue(
            FeedbackIntent.Tick.feel.ordinal < FeedbackIntent.Tap.feel.ordinal,
            "a stream of detents is supposed to be the softest thing the library " +
                "does, and a press a step above it. This is the distinction the " +
                "report said was missing, and before there was a word for weight " +
                "the two resolved to the same constant on every Android below 14.",
        )
    }

    /** And the tier above that, so the scale has three rungs rather than two. */
    @Test
    fun theWeightsAreAScaleDeclaredLightestFirst() {
        assertTrue(FeedbackFeel.Light.ordinal < FeedbackFeel.Medium.ordinal)
        assertTrue(FeedbackFeel.Medium.ordinal < FeedbackFeel.Heavy.ordinal)
    }

    /**
     * And the rhythms are not on it.
     *
     * [FeedbackFeel.Success] and [FeedbackFeel.Danger] are several beats that mean
     * something rather than a bigger pulse, so "is `Danger` heavier than `Heavy`"
     * has no answer. They are declared after the scale so that reading the enum in
     * order does not suggest one, and this is the assertion that keeps them there.
     */
    @Test
    fun theRhythmsSitAfterTheScaleRatherThanOnIt() {
        assertEquals(
            listOf(
                FeedbackFeel.Light,
                FeedbackFeel.Medium,
                FeedbackFeel.Heavy,
                FeedbackFeel.Success,
                FeedbackFeel.Danger,
            ),
            FeedbackFeel.entries,
        )
    }

    /**
     * The floor asks about rate; this file asks about weight. Two questions.
     *
     * They used to share a word — `FeedbackFloor` gated whatever an intent's
     * `isLight` said — and once a weight existed that was a collision waiting to
     * mislead: [FeedbackIntent.Tap] is [FeedbackFeel.Medium] and is *still* thinned,
     * because three chips answering inside eighty milliseconds are one rattle to the
     * hand whatever each press weighs.
     *
     * What must stay true is the other direction: nothing that reports an **outcome**
     * is ever dropped. A threshold swallowed because a slider ticked forty
     * milliseconds ago is a gesture that silently changed meaning.
     */
    @Test
    fun noOutcomeIsEverThinnedByTheRateFloor() {
        val thinned = FeedbackIntent.entries.filter { it.arrivesInStreams }
        assertEquals(listOf(FeedbackIntent.Tap, FeedbackIntent.Tick), thinned)
        for (intent in FeedbackIntent.entries) {
            if (intent.feel == FeedbackFeel.Heavy ||
                intent.feel == FeedbackFeel.Success ||
                intent.feel == FeedbackFeel.Danger
            ) {
                assertTrue(
                    !intent.arrivesInStreams,
                    "$intent reports an outcome and the rate floor may drop it",
                )
            }
        }
    }
}
