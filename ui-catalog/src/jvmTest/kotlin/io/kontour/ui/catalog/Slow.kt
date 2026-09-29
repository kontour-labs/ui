package io.kontour.ui.catalog

/**
 * A test class left out of the everyday run: `@Category(Slow::class)`.
 *
 * The catalog suite was about 83 minutes of test time, and 26 classes were
 * 70 of them — the sweeps that render every component at eight widths, under
 * reduced motion or at 200% type, that press every control in every demo,
 * and the gesture suites that drive a sheet or a picker through dozens of
 * frames. What is left runs in a few minutes and still holds most of the
 * behaviour, and every golden except the phone pages.
 *
 * They are not optional. `-Pkontour.tests.full=true` runs them:
 *
 *     ./gradlew :ui-catalog:jvmTest -Pkontour.tests.full=true
 *
 * which is the gate before a pull request is opened, and what CI's "Run
 * workflow" does with **full** ticked. A change to a component's layout,
 * motion or input deserves the full run; a docs change does not.
 *
 * ### Which classes
 *
 * The ones measured at half a minute or more of a full run, and nothing
 * chosen for what it tests. A new class that turns out slow joins them the
 * same way. Categories are inherited, so the four width-sweep shards are
 * marked once, on their base.
 *
 * `PhoneWidthTest` is the one among them that renders goldens, `phone/…`, which
 * is why the golden-orphan check leaves that directory alone on a run without
 * it — see `checkGoldenOrphans` in the build script.
 */
interface Slow
