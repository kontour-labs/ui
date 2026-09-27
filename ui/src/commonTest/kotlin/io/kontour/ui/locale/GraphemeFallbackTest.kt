package io.kontour.ui.locale

import kotlin.test.Test
import kotlin.test.assertEquals

/** The first character of a name is the one a reader would count, whatever it is made of. */
class GraphemeFallbackTest {

    @Test
    fun theFirstCharacterOfAName() {
        assertEquals("", Graphemes.first(""))
        assertEquals("A", Graphemes.first("Ada"))
        assertEquals("é", Graphemes.first("émile"), "a decomposed accent stays with its letter")
        assertEquals("🇦🇺", Graphemes.first("🇦🇺🇳🇿"), "one flag, not half of two")
        val family = "👨‍👩‍👧"
        assertEquals(family, Graphemes.first(family + "x"), "a joined family is one")
        assertEquals("👋🏽", Graphemes.first("👋🏽hi"), "a skin tone stays with its hand")
        assertEquals("क्षि", Graphemes.first("क्षितिज"), "a Devanagari conjunct and its vowel sign")
        assertEquals("한", Graphemes.first("한국"))
        assertEquals("한", Graphemes.first("한ᄀ"), "Hangul written as jamo")
        assertEquals("\r\n", Graphemes.first("\r\nx"))
    }

    @Test
    fun codePointsNotUnits() {
        assertEquals(listOf("a", "😀", "b"), Graphemes.codePoints("a😀b"))
    }
}
