# `LocaleFormats`

*Also on this page: `PseudoLocalised`.*

How the reader writes everything that is not words — numbers, dates, times,
lists and plurals — read through `Theme.formats`. Every component that puts a
number or a date on screen goes through it, so a German reader gets `1.234` and
`14:05` and a reader in Cairo gets Arabic-Indic digits without either being
asked for. The three conventions people change most — the hour cycle, the order
of a date's parts and the first day of the week — are one `withConventions`
away.

<!--sample:LocaleFormatsBasics-->
```kotlin
// Changed once, near the root. Every date and time component below reads
// `Theme.formats`, so a 12-hour clock and a Sunday week start are one
// decision rather than a parameter on nine call sites.
val formats = Theme.formats
ProvideTokens(
    formats = formats.withConventions(
        formats.conventions.copy(
            hourCycle = HourCycle.H12,
            dateOrder = DateOrder.MonthDayYear,
            firstDayOfWeek = DayOfWeek.SUNDAY,
        ),
    ),
) {
    Screen()
}
```

`KontourTheme` builds them for its `locale` — the device's, followed live —
from the platform's own CLDR: ICU on Android, Foundation on iOS, `Intl` in a
browser, the JDK on a desktop. English is the library's own table, with the
region's conventions still read from the platform, so every picture in these
docs stays the same between machines. `LocaleFormats.english()` is that table on
its own, and what a test pins itself to. [Localisation](../localisation.md) has
the whole story: the locale, strings and plurals, scripts and fonts, and
right-to-left.

A locale tag's own keywords win over the platform: `en-AU-u-hc-h23` is a
24-hour clock, `-u-fw-mon` a Monday week, `-u-nu-latn` Western digits.

## Pseudo-locales

`PseudoLocalised(mode) { … }` runs a screen in a language that does not exist,
to find what a real one would break before a translator is involved:
`Accented` makes every word longer and bracketed, `Bidi` lays it out right to
left in Arabic-Indic digits, and `Tall` stacks marks above and below every
letter. It changes the theme's strings and formats together, so a word or a
number that stays plain English was written somewhere they do not reach. The
catalog's settings have all three; [Localisation](../localisation.md#testing-without-a-translation)
says what each one finds.

---

## Accessibility

This decides what is *announced*, not only what is drawn. The hour cycle, the
date's order and the first day of the week feed the full-date `stateDescription`
every day cell carries and the value every time control reports; the digits and
the plural forms feed every count a component says.

Change the conventions once, near the root. A screen that changes them locally
gets a calendar announcing dates in one order and a field announcing them in
another, which is worse than either.

On Android the reader's own "Use 24-hour format" switch overrides the locale's
clock, and is followed while the app runs; on iOS the 24-Hour Time switch does
the same.

Neither high contrast nor reduced motion changes it: it draws nothing and moves
nothing. A pseudo-locale changes the strings, the formats and, for `Bidi`, the
direction, and leaves both of those settings as they were.
