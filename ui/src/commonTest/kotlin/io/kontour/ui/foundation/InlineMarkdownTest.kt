package io.kontour.ui.foundation

import io.kontour.ui.foundation.InlineSpan.Bold
import io.kontour.ui.foundation.InlineSpan.Code
import io.kontour.ui.foundation.InlineSpan.Italic
import io.kontour.ui.foundation.InlineSpan.Link
import io.kontour.ui.foundation.InlineSpan.Plain
import io.kontour.ui.foundation.InlineSpan.Strikethrough
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The inline Markdown reader, case by case.
 *
 * Every case is here because a string an app ships can contain it — a stray
 * asterisk in a price, an identifier with underscores, an address a label should
 * not be able to smuggle in — and the one thing the reader must never do is drop
 * characters or throw on text it does not understand.
 */
class InlineMarkdownTest {

    private fun read(source: String) = parseInlineMarkdown(source)

    @Test
    fun plainTextIsOnePiece() {
        assertEquals(listOf(Plain("The 950 leaves in 4 minutes.")), read("The 950 leaves in 4 minutes."))
        assertEquals(emptyList(), read(""))
    }

    @Test
    fun eachDelimiterDrawsItsSpan() {
        assertEquals(listOf(Plain("a "), Bold(listOf(Plain("b"))), Plain(" c")), read("a **b** c"))
        assertEquals(listOf(Bold(listOf(Plain("b")))), read("__b__"))
        assertEquals(listOf(Italic(listOf(Plain("i")))), read("*i*"))
        assertEquals(listOf(Italic(listOf(Plain("i")))), read("_i_"))
        assertEquals(listOf(Strikethrough(listOf(Plain("gone")))), read("~~gone~~"))
        assertEquals(listOf(Code("val x = 1")), read("`val x = 1`"))
    }

    @Test
    fun spansNest() {
        assertEquals(
            listOf(Bold(listOf(Plain("a "), Italic(listOf(Plain("b"))), Plain(" c")))),
            read("**a *b* c**"),
        )
        assertEquals(
            listOf(Italic(listOf(Plain("a "), Bold(listOf(Plain("b"))), Plain(" c")))),
            read("*a **b** c*"),
        )
        assertEquals(listOf(Bold(listOf(Italic(listOf(Plain("both")))))), read("***both***"))
    }

    @Test
    fun aDelimiterThatDoesNotCloseIsText() {
        assertEquals(listOf(Plain("a **b")), read("a **b"))
        assertEquals(listOf(Plain("2 * 3 * 4")), read("2 * 3 * 4"))
        assertEquals(listOf(Plain("a ~b~")), read("a ~b~"))
        assertEquals(listOf(Plain("`unclosed")), read("`unclosed"))
        // The spare star is kept, and the pair after it still means bold.
        assertEquals(listOf(Plain("*"), Bold(listOf(Plain("a")))), read("***a**"))
    }

    @Test
    fun anUnderscoreInsideAWordIsALetter() {
        assertEquals(listOf(Plain("snake_case_name")), read("snake_case_name"))
        assertEquals(listOf(Plain("see "), Italic(listOf(Plain("this"))), Plain(".")), read("see _this_."))
    }

    @Test
    fun codeIsOpaque() {
        assertEquals(listOf(Code("**not bold**")), read("`**not bold**`"))
        assertEquals(listOf(Code("a ` b")), read("`` a ` b ``"))
        assertEquals(
            listOf(Italic(listOf(Plain("run "), Code("a*b")))),
            read("*run `a*b`*"),
        )
    }

    @Test
    fun aBackslashMakesPunctuationLiteral() {
        assertEquals(listOf(Plain("*not italic*")), read("""\*not italic\*"""))
        assertEquals(listOf(Plain("""a \ b""")), read("""a \ b"""))
        assertEquals(listOf(Plain("[x](y)")), read("""\[x](y)"""))
    }

    @Test
    fun linksCarryTheirLabelsSpans() {
        assertEquals(
            listOf(Plain("See "), Link(listOf(Plain("timetables")), "https://transperth.wa.gov.au"), Plain(".")),
            read("See [timetables](https://transperth.wa.gov.au)."),
        )
        assertEquals(
            listOf(Link(listOf(Bold(listOf(Plain("Call")))), "tel:131062")),
            read("[**Call**](tel:131062)"),
        )
        assertEquals(listOf(Link(listOf(Plain("mail")), "mailto:help@example.com")), read("[mail](mailto:help@example.com)"))
    }

    @Test
    fun anAddressThatIsNotSafeIsDrawnAsItsLabel() {
        assertEquals(listOf(Plain("click")), read("[click](javascript:alert(1))"))
        assertEquals(listOf(Plain("rel")), read("[rel](../page.md)"))
    }

    @Test
    fun somethingThatIsNotALinkIsText() {
        assertEquals(listOf(Plain("[just brackets]")), read("[just brackets]"))
        assertEquals(listOf(Plain("[a] (b)")), read("[a] (b)"))
        assertEquals(listOf(Plain("[a](has space)")), read("[a](has space)"))
    }

    @Test
    fun blockSyntaxIsLeftAsWritten() {
        assertEquals(listOf(Plain("# Not a heading\n- not a list")), read("# Not a heading\n- not a list"))
    }

    @Test
    fun nothingIsEverDropped() {
        // Whatever the reader makes of a string, reading it back as text has
        // every character the string had that was not markup.
        val source = "a *b **c `d` e** f* ~~g~~ [h](https://i.j) _k_ l__m__n"
        fun flatten(spans: List<InlineSpan>): String = spans.joinToString("") {
            when (it) {
                is Plain -> it.text
                is Code -> it.text
                is Bold -> flatten(it.children)
                is Italic -> flatten(it.children)
                is Strikethrough -> flatten(it.children)
                is Link -> flatten(it.children)
            }
        }
        assertEquals("a b c d e f g h k l__m__n", flatten(read(source)))
    }
}
