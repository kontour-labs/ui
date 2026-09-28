package io.kontour.haptics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * iOS's generators are kept for a burst and made afresh after a pause — the
 * policy that replaced holding one of each for the life of the process, after
 * haptics were reported to stop for good on iPhones.
 */
class GeneratorCacheTest {

    private var clock = 0.0
    private var made = 0

    private fun cache(gap: Double?) = GeneratorCache<String, Any>(gap, { clock }) { key ->
        made++
        "$key #$made"
    }

    @Test
    fun oneGeneratorAnswersAWholeBurst() {
        val cache = cache(gap = 1.5)
        val first = cache["impact"]
        repeat(20) {
            clock += 0.08
            assertSame(first, cache["impact"])
        }
        assertEquals(1, made)
    }

    @Test
    fun aPauseLongerThanTheGapMakesANewOne() {
        val cache = cache(gap = 1.5)
        val first = cache["impact"]
        clock += 1.6
        assertNotSame(first, cache["impact"])
        assertEquals(2, made)
    }

    @Test
    fun aNewBurstReplacesEveryKindNotJustTheOneAsked() {
        val cache = cache(gap = 1.5)
        cache["impact"]
        cache["selection"]
        clock += 2.0
        cache["impact"]
        assertEquals(1, cache.size)
    }

    @Test
    fun withoutAGapOneIsKeptForGood() {
        val cache = cache(gap = null)
        val first = cache["impact"]
        clock += 3600.0
        assertSame(first, cache["impact"])
        assertEquals(1, made)
    }

    @Test
    fun clearingLetsGoOfEverything() {
        val cache = cache(gap = 1.5)
        val first = cache["impact"]
        cache.clear()
        assertEquals(0, cache.size)
        assertNotSame(first, cache["impact"])
    }
}
