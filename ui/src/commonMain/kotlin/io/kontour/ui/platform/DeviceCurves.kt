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
 * ### The exceptions are listed, not the rule
 *
 * The first version of this listed every *current* device family and let
 * everything else fall to a per-manufacturer number. It was wrong within a
 * fortnight, and reported: *"I'm on a Pixel 11 Pro XL, so you haven't catered
 * for it."* The Pixel 11 is `kodiak` and the list stopped at the Pixel 9, so the
 * roundest corners Android ships were being drawn with the cautious fallback
 * meant for a Pixel 4.
 *
 * A list of current devices is a list that is wrong every autumn. So it is
 * inverted: a manufacturer's **present** treatment is its default, and the
 * devices that predate it are the ones named. `kodiak`, and whatever Google
 * calls next year's phone, are right without anybody touching this file; only
 * the past is a closed set, and a closed set can be finished.
 *
 * Codenames rather than years, which is the same instinct answered better —
 * *"one year a phone might look different to the next"* — because a manufacturer
 * changes a corner treatment on a product line and not on a calendar.
 * `docs/pull-device-corners.py` checks every codename here against Google's own
 * device registry, so a typo or an invented name fails a gate rather than
 * silently matching nothing.
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
    LegacyFamilies[vendor]?.let { legacy ->
        if (legacy.codenames.any { codename == it }) return legacy.smoothing
    }
    return Current[vendor]
}

/**
 * What a manufacturer draws **now**, and therefore what its next phone draws.
 *
 * Absent means null means the library's own curve. There is deliberately no
 * catch-all row: one would make every unlisted Android device look like it had
 * been looked at.
 */
private val Current: Map<String, Float> = mapOf(
    // Tensor-era Pixels are the roundest corners Android ships and the closest
    // thing to an iPhone's curve. Unchanged from the Pixel 6 through the Pixel
    // 11, which is what makes it the default rather than a list.
    "google" to 0.62f,
    // Samsung's corners are close to a true arc and the flattest of the
    // mainstream — the comparison the whole report was drawn from.
    "samsung" to 0.35f,
    "oneplus" to 0.5f,
    "xiaomi" to 0.45f,
    "redmi" to 0.45f,
    "poco" to 0.45f,
    "nothing" to 0.55f,
    "motorola" to 0.4f,
)

/** A manufacturer's earlier treatment, and the devices that still wear it. */
private class Legacy(val smoothing: Float, val codenames: List<String>)

/**
 * The closed sets: devices that predate their manufacturer's current corner.
 *
 * Only Google's is filled in, and only because the change there is large and
 * datable — the Pixel 6 moved to a much rounder, much more continuous corner and
 * everything before it is visibly tighter. Where a manufacturer's treatment has
 * drifted rather than changed, there is nothing here to say and the current
 * number covers the range.
 */
private val LegacyFamilies: Map<String, Legacy> = mapOf(
    "google" to Legacy(
        // Snapdragon-era Pixels: a tighter corner, much closer to a plain arc.
        smoothing = 0.45f,
        codenames = listOf(
            // Pixel 2 and 2 XL, 3 and 3 XL, 3a and 3a XL.
            "walleye", "taimen", "blueline", "crosshatch", "sargo", "bonito",
            // Pixel 4 and 4 XL, 4a, 4a 5G.
            "flame", "coral", "sunfish", "bramble",
            // Pixel 5 and 5a.
            "redfin", "barbet",
        ),
    ),
)

/** Documented where it is used; here so the table above can be read against it. */
internal val LibrarySmoothing: Float = SquircleShape.DefaultSmoothing
