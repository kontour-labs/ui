package io.kontour.ui.components.list

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The heights a scrollbar keeps of a lazy list's rows, and the sum of those
 * before a row that places its thumb.
 *
 * The sum is kept from where it was last asked and walked to the next
 * question rather than counted from the top each time, so it has to stay what
 * counting from the top would give through rows measured above and below it,
 * measured again at new heights, and the list growing and shrinking under it.
 */
class RowSizesTest {

    @Test
    fun theSumBeforeARowIsWhatCountingFromTheTopGives() {
        val random = Random(20261014)
        repeat(20) { run ->
            val rows = RowSizes()
            var model = IntArray(0)
            fun resize(total: Int) {
                rows.resize(total)
                model = IntArray(total) { if (it < model.size) model[it] else 0 }
            }
            resize(random.nextInt(0, 400))
            repeat(2_000) { step ->
                when (random.nextInt(10)) {
                    0 -> resize(random.nextInt(0, 400))
                    in 1..5 -> if (model.isNotEmpty()) {
                        val index = random.nextInt(model.size)
                        val size = random.nextInt(1, 120)
                        rows.record(index, size, key = index)
                        model[index] = size
                    }
                    else -> {
                        // Asked anywhere, past the end included, as a list's first
                        // row in view can briefly be.
                        val index = random.nextInt(0, model.size + 5)
                        var sum = 0L
                        var known = 0
                        for (i in 0 until minOf(index, model.size)) {
                            if (model[i] > 0) {
                                sum += model[i]
                                known++
                            }
                        }
                        assertEquals(sum to known, rows.sumBefore(index), "run $run, step $step: the sum before row $index")
                    }
                }
            }
            assertEquals(model.sumOf { it.toLong() }, rows.knownSum, "run $run: the sum of every row known")
            assertEquals(model.count { it > 0 }, rows.knownCount, "run $run: how many rows are known")
        }
    }
}
