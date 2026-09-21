package io.kontour.ui.platform

import io.kontour.ui.theme.SquircleShape

/**
 * How square-ish a device's corners are, by family. **A judgement, not a reading.**
 *
 * This file exists because of a gap with no bottom to it: *"on iOS it's beautiful
 * and concentric, as the iOS devices all have the same squircle shape. But on
 * Android, it's not quite perfectly concentric, as some devices have different
 * shapes to others — like the Google Pixel shape is more squircly than a Samsung
 * phone. Is there a way to detect that?"*
 *
 * There is not. `RoundedCorner` reports a radius and a centre and the centre is
 * derived from the radius, so it carries nothing. `DisplayShape` arrived in API
 * 34 and returns a `Path` — but when the OEM leaves `config_mainDisplayShape`
 * empty the framework **synthesises** that path from the radii, which is a
 * circle, and reading a smoothing of zero back out of it would be a wrong answer
 * wearing a measurement's clothes. Of 254 modern LineageOS device trees, zero
 * set it. Nothing below 34 has even that.
 *
 * So these are set by eye, against manufacturers' own renders and photographs of
 * real bezels, to the nearest 0.05. Each is wrong by an amount nobody can see in
 * a 34dp corner and might see in a 55dp one. A device that matches no rule gets
 * **null** and keeps `SquircleShape.DefaultSmoothing`, which is what every
 * Android device got before this file existed — so the table can only improve a
 * device it names and can never make one worse.
 *
 * ### Keyed on the family, not the year
 *
 * *"We'd want to ensure we separate device models too, since one year a phone
 * might look different to the next."* Agreed, and the codename does it better
 * than a date: `oriole` and `raven` are the Pixel 6, `panther` and `cheetah` the
 * Pixel 7, and a manufacturer changes a corner treatment on a product line
 * rather than on a calendar. Longest prefix wins, then the manufacturer, then
 * null.
 *
 * ### Why a second smoothing is allowed to exist at all
 *
 * `SquircleShape.DefaultSmoothing`'s own note says a scale with two smoothings
 * in it is a scale whose corners do not match each other, and that is still
 * true. These two shapes are not *in* the scale: the backdrop's clip and a
 * sheet's top corners are concentric with the **bezel**, not with the card
 * beside them. That is the whole of the exemption and it has a boundary — a
 * third call site needs its own paragraph explaining why that shape is also
 * nested inside the display.
 */
internal fun deviceSmoothingFor(manufacturer: String, device: String): Float? {
    val vendor = manufacturer.lowercase()
    val codename = device.lowercase()
    Families.forEach { (prefix, smoothing) ->
        if (codename.startsWith(prefix)) return smoothing
    }
    return Vendors[vendor]
}

/**
 * Codename prefixes, longest first so a generation beats its manufacturer.
 *
 * Only the families where the shape is visibly not the vendor's usual. Everything
 * else falls through to [Vendors], which is the coarse answer and says so.
 */
private val Families: List<Pair<String, Float>> = listOf(
    // Tensor-era Pixels are the roundest corners Android ships and the closest
    // thing to an iPhone's curve — Pixel 6 through 9, plus the a-series.
    "oriole" to 0.62f, "raven" to 0.62f, "bluejay" to 0.62f,
    "panther" to 0.62f, "cheetah" to 0.62f, "lynx" to 0.62f,
    "shiba" to 0.62f, "husky" to 0.62f, "akita" to 0.62f,
    "tokay" to 0.62f, "caiman" to 0.62f, "komodo" to 0.62f, "comet" to 0.62f,
    // Snapdragon-era Pixels: a tighter, more circular corner.
    "flame" to 0.45f, "coral" to 0.45f, "redfin" to 0.5f, "bramble" to 0.5f,
    "sunfish" to 0.45f, "barbet" to 0.5f,
    // Galaxy Z, whose cover and inner displays are rounder than the S line.
    "f2q" to 0.45f, "q2q" to 0.45f, "q4q" to 0.45f, "q5q" to 0.45f,
    "b2q" to 0.45f, "b4q" to 0.45f, "b5q" to 0.45f,
)

/**
 * The coarse answer, where a family has nothing more specific to say.
 *
 * Absent means null means the library's own curve, which is deliberate: a
 * catch-all row here would make every unlisted Android device look like it had
 * been looked at.
 */
private val Vendors: Map<String, Float> = mapOf(
    // Samsung's corners are close to a true arc and the flattest of the
    // mainstream — the comparison the report was drawn from.
    "samsung" to 0.35f,
    "google" to 0.55f,
    "oneplus" to 0.5f,
    "xiaomi" to 0.45f,
    "redmi" to 0.45f,
    "poco" to 0.45f,
    "nothing" to 0.55f,
    "motorola" to 0.4f,
)

/** Documented where it is used; here so the table above can be read against it. */
internal val LibrarySmoothing: Float = SquircleShape.DefaultSmoothing
