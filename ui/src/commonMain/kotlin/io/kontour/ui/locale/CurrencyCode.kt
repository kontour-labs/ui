package io.kontour.ui.locale

/**
 * A currency as ISO 4217 names it: three letters, upper case, so `"aud"` and
 * `" AUD"` find the same format as `"AUD"`.
 *
 * In a file of its own because it changes case, which everywhere else asks the
 * reader's language — `check-settings` holds that, and exempts this file by
 * name. A currency code is not language: the standard says it is three ASCII
 * letters, and the Turkish reader's dotted capital I has no business in one.
 */
internal fun isoCurrencyCode(code: String): String = code.trim().uppercase()
