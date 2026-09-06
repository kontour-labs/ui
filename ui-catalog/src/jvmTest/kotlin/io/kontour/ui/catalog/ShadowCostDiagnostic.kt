package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Card
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the *second* layer of a two-layer shadow costs.
 *
 * `Modifier.elevation` folds each `ShadowSpec` into its own `dropShadow`
 * modifier, and `kontourElevation` gives every tier two of them — a tight
 * contact shadow and a wide ambient one. `Card` defaults to `CardVariant.Elevated`,
 * so a screen of twenty cards draws forty blurred silhouettes per frame.
 *
 * That sentence has been in this repository for two rounds without a number
 * beside it. `OverlayRecompositionTest` and `SheetFramePressureTest` both name
 * the mechanism in their own docstrings — "the sheet's two `dropShadow` layers …
 * are re-rasterised every frame … That is the frame rate" — and both went on to
 * fix the *invalidation* rather than the count, because the count is a design
 * decision and nobody had costed it.
 *
 * ### What is isolated, and how
 *
 * The three scenes differ in exactly one thing: how many `ShadowSpec`s the cards
 * are given. Not `CardVariant`, which would also change the fill and the border
 * and make the comparison a comparison of three different pictures —
 * `Shadow(emptyList())` keeps an elevated card in every respect except the one
 * being measured, because `Modifier.elevation` returns the chain untouched when
 * there are no layers.
 *
 * Swept over three card counts rather than measured once, so the answer is a
 * slope. A per-frame cost that is flat in the number of cards is not the
 * shadows; one that doubles with them is.
 *
 * Timed with `Scene.advance`, which renders and discards. `Scene.frame` encodes
 * every frame to PNG and reads it back, which is several times the cost of
 * drawing and would bury the thing being measured — that is the mistake
 * `BackdropCostDiagnostic` was rebuilt to stop making, and this file inherits
 * the fix rather than repeating the error.
 *
 * ### The result
 *
 * ```
 *  5 cards — no shadow 0.57ms · one layer 1.05 (+0.48) · two layers 1.64 (+0.59)
 * 20 cards — no shadow 1.28ms · one layer 3.45 (+2.17) · two layers 5.59 (+2.14)
 * 60 cards — no shadow 3.23ms · one layer 9.43 (+6.21) · two layers 16.79 (+7.36)
 * ```
 *
 * **The second layer costs what the first one does** — 0.99 to 1.23 times it,
 * across a twelvefold change in card count. That is the answer a pair of blurs
 * over the same bounds should give, and it means the inference that started
 * this ("forty blurred passes per frame") was arithmetic rather than an
 * overstatement: the shadows really are two passes and the second is not free.
 *
 * It also means shadows are **the majority of a card-heavy frame**. At twenty
 * cards the frame is 5.59ms and 4.31ms of it is shadow; without any it is 1.28.
 * Dropping the ambient layer would take that frame to 3.45ms.
 *
 * ### Why nothing changed
 *
 * Two reasons, and the first is the binding one.
 *
 * `Modifier.dropShadow` is `androidx.compose.ui.draw.dropShadow` and takes one
 * shadow. There is no list form, and no portable way to blur inside a draw
 * scope, so there is no arrangement of this API that draws a contact and an
 * ambient shadow in a single pass. Halving the cost means halving the layers,
 * which means a different picture — a hard edge or a vague smudge rather than
 * both, which is the trade `Shadow`'s own KDoc exists to refuse.
 *
 * And this is a **software rasteriser**, where a blur is a per-pixel loop rather
 * than a shader pass. The `low` tier's radii are 2dp and 6dp; on a GPU those are
 * close to free, and the 77% above is the worst case rather than the common one.
 * The same caveat `BackdropCostDiagnostic` carries, for the same reason.
 *
 * So this file exists to hold the number, not to have caused a change. A
 * **diagnostic, not a gate**: the assertion is a catastrophe bound no reasonable
 * machine trips, and the useful output is the table it prints.
 *
 * Which is a claim the code has to earn rather than assert. It did not, for one
 * round: the ratio was taken whenever the first layer cost more than 0.01ms,
 * and the noise on one of these samples is a few tenths of a millisecond — so a
 * scheduling hiccup could put a 0.05ms denominator under a 1ms numerator and
 * report that the second shadow layer cost twenty times the first. It failed a
 * build that way, on a tree where nothing about shadows had changed. The floor
 * is a real one now, and a row under it is skipped and said so rather than
 * scored.
 */
class ShadowCostDiagnostic {

    @Test
    fun aSecondShadowLayerCostsLessThanTheFirst() {
        val ratios = mutableListOf<Double>()

        for (cards in CardCounts) {
            val none = millisPerFrame(cards, layers = 0)
            val one = millisPerFrame(cards, layers = 1)
            val two = millisPerFrame(cards, layers = 2)

            val first = one - none
            val second = two - one

            // Only divide by a denominator big enough to mean something.
            //
            // This guard used to be `first > 0.01`, which is not a floor at all:
            // the noise on one of these samples is a few tenths of a
            // millisecond, so a scheduling hiccup between the no-shadow and
            // one-layer scenes can leave `first` at 0.05ms with `second` at a
            // full millisecond, and the ratio comes out at twenty. That is
            // exactly what happened — this failed a gate at 19.25 while the
            // shadows themselves had not changed at all, on a file whose own
            // documentation calls it a catastrophe bound no reasonable machine
            // trips.
            //
            // A row below the floor is *reported and skipped* rather than scored
            // zero. Skipping is honest — the machine was too busy to measure
            // that row — and scoring it zero would have quietly dragged the
            // maximum down on exactly the runs where the number is least
            // trustworthy.
            val measurable = first >= MinimumMeasurableMillis
            if (measurable) ratios += second / first

            println(
                ("%3d cards — no shadow %6.2fms · one layer %6.2f (+%.2f) · " +
                    "two layers %6.2f (+%.2f).  second/first %s")
                    .format(
                        cards, none, one, first, two, second,
                        if (measurable) "%.2f".format(second / first)
                        else "not measurable (first layer under %.2fms)"
                            .format(MinimumMeasurableMillis),
                    )
            )
        }

        if (ratios.isEmpty()) {
            println(
                "every row came out under the floor, so this run measured nothing. " +
                    "That is a fact about the machine rather than about the shadows, " +
                    "and it is not something to fail a build over."
            )
            return
        }

        // Both layers blur the same shape over the same bounds, so the second
        // should cost about what the first did. Much more than that would mean
        // it is not the blur being paid for but something the second modifier
        // node does on its own — an extra layer, a second offscreen buffer —
        // and that is a different finding with a different fix.
        val worst = ratios.max()
        assertTrue(
            worst < 4.0,
            ("the second shadow layer cost %.2f times what the first one did. Both blur " +
                "the same shape over the same bounds, so anything like this means the " +
                "second `dropShadow` is paying for something the first did not — look at " +
                "what stacking the modifier allocates before touching the tiers.")
                .format(worst),
        )
    }

    private fun millisPerFrame(cards: Int, layers: Int): Double {
        return Scene(width = Canvas, height = CanvasHeight, density = 1f, reduceMotion = true) {
            val shadow = when (layers) {
                0 -> Shadow(emptyList())
                1 -> Shadow(Theme.elevation.low.layers.take(1))
                else -> Theme.elevation.low
            }
            Box(Modifier.fillMaxSize()) {
                Column {
                    for (row in 0 until (cards + PerRow - 1) / PerRow) {
                        Row {
                            for (column in 0 until PerRow) {
                                if (row * PerRow + column < cards) {
                                    Card(
                                        modifier = Modifier.padding(6.dp).size(100.dp),
                                        shadow = shadow,
                                    ) { Text("card") }
                                }
                            }
                        }
                    }
                }
            }
        }.use { scene ->
            scene.advance(WarmUpFrames)
            val started = System.nanoTime()
            scene.advance(Samples)
            (System.nanoTime() - started) / 1_000_000.0 / Samples
        }
    }

    private companion object {
        /** Five across and up to twelve down, so sixty cards are all on screen and all drawn. */
        const val PerRow = 5
        const val Canvas = 580
        const val CanvasHeight = 1400

        /** A slope needs more than one point, and a slope through three is harder to misread. */
        val CardCounts = listOf(5, 20, 60)

        const val WarmUpFrames = 10
        const val Samples = 30

        /**
         * How much the first shadow layer has to cost before its cost can be a
         * denominator.
         *
         * Set from the table this test prints rather than guessed. One run of
         * it, on the container this was written on:
         *
         * ```
         *  5 cards — one layer +0.27ms · two layers +0.55.  second/first 2.02
         * 20 cards — one layer +2.08ms · two layers +2.49.  second/first 1.20
         * 60 cards — one layer +6.38ms · two layers +7.48.  second/first 1.17
         * ```
         *
         * The two large rows agree with each other and with the figure this file
         * exists to hold. The five-card row does not, and it is not a finding —
         * it is a quarter of a millisecond of work being timed on a machine whose
         * scheduling noise is about that size, and it is the row that failed a
         * build at 19.25.
         *
         * Set well clear of it — the same row came out at 0.27ms on one run and
         * 0.53ms on the next, a twofold swing on one machine, which is the
         * argument in one number. So five cards is always excluded here, and
         * always included on a machine slow enough for five cards to be real
         * work. The table still prints all three rows: this floor decides what
         * may be divided, not what gets measured.
         */
        const val MinimumMeasurableMillis = 1.0
    }
}
