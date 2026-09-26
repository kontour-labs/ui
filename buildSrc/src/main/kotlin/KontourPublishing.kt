import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * Publishing, for every module that ships: the version, the coordinate task, the
 * repository and the POM.
 *
 * Here rather than in `ui/build.gradle.kts` because there are two published
 * modules now, `:ui` and `:ui-nav3`, and they must never disagree about which
 * release they are. A second copy of the version rule below would be a second
 * set of answers to the question it exists to settle.
 *
 * ### The version
 *
 * A release is `git tag v0.2.0` and nothing else. Three sources, in this order:
 *
 *  1. `-Pkontour.version=…`, for a deliberate publish that is not a tag.
 *  2. The tag being built — and **only** a tag.
 *  3. `0.1.0-SNAPSHOT`, for everything else.
 *
 * Rule 2 used to read `GITHUB_REF_NAME` without asking what kind of ref it was,
 * which meant a branch build took the *branch name* as its version. This would
 * have published `io.kontour:ui:claude/compose-multiplatform-components-lz1c97`
 * — a coordinate with a slash in it — the first time anything ran `publish`
 * outside a tag, while the comment above it claimed a branch build was a
 * snapshot. `GITHUB_REF_TYPE` is the field that answers the question.
 *
 * The result is then checked, because the failure mode is silent: an unpublish
 * on a package registry is a support ticket, and the check costs one regex.
 *
 * Everything is read through `providers`, never `System.getenv`. The
 * configuration cache is on (`gradle.properties`), and a direct environment read
 * at configuration time is invisible to it: the cached entry would be reused
 * with last run's token baked in.
 *
 * **This publishes from Linux.** Kotlin 2.x cross-compiles Apple *klibs* — which
 * is what a library consumer resolves — so a Linux publish uploads the complete
 * set, verified by publishing locally and reading `native_targets=ios_arm64` out
 * of the klib manifest. What still needs Xcode is *linking a framework*, and
 * that happens in the consuming app, not here.
 *
 * @param displayName The POM's `name`.
 * @param summary The POM's `description`.
 * @param licenceNote The licence's `comments`. The default names the fonts `:ui`
 *   bundles; a module that bundles none says so rather than inheriting it.
 */
fun Project.kontourPublishing(
    displayName: String,
    summary: String,
    licenceNote: String = "All rights reserved. The bundled Outfit fonts are SIL OFL 1.1 " +
        "and redistributable in this artifact; see ui/licenses/Outfit-OFL.txt.",
) {
    group = "io.kontour"
    version = providers.gradleProperty("kontour.version")
        .orElse(
            providers.environmentVariable("GITHUB_REF_TYPE")
                .zip(providers.environmentVariable("GITHUB_REF_NAME")) { type, name ->
                    if (type == "tag") name.removePrefix("v") else ""
                }
                .filter { it.isNotEmpty() }
        )
        .orElse("0.1.0-SNAPSHOT")
        .map { candidate ->
            require(Regex("""^\d+\.\d+\.\d+(?:-[0-9A-Za-z.]+)?$""").matches(candidate)) {
                "`$candidate` is not a publishable version. A release tag is `v1.2.3`, " +
                    "optionally with a `-rc1`-style suffix; anything else has to come " +
                    "through -Pkontour.version. This check exists because the alternative " +
                    "is noticing after the upload."
            }
            candidate
        }
        .get()

    // What this build would publish as:
    //
    //     ./gradlew :ui:coordinate
    //
    // The version comes from three sources with a precedence between them, and
    // without this there is no way to ask which one won short of running a
    // publish and reading the upload. That is how a branch build came to be
    // publishable as `io.kontour:ui:some/branch` unnoticed.
    val coordinate = "$group:$name:$version"
    tasks.register("coordinate") {
        group = "help"
        description = "Prints the group:artifact:version this build would publish as."
        // Captured above, at configuration time: a `doLast` that read `project`
        // would hold the project reference and break the configuration cache.
        doLast { println(coordinate) }
    }

    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/kontour-labs/ui")
                credentials {
                    username = providers.environmentVariable("GITHUB_ACTOR").orNull
                    password = providers.environmentVariable("GITHUB_TOKEN").orNull
                }
            }
        }

        publications.withType<MavenPublication>().configureEach {
            pom {
                name.set(displayName)
                description.set(summary)
                url.set("https://github.com/kontour-labs/ui")
                licenses {
                    license {
                        name.set("Proprietary")
                        comments.set(licenceNote)
                    }
                }
                scm {
                    url.set("https://github.com/kontour-labs/ui")
                    connection.set("scm:git:https://github.com/kontour-labs/ui.git")
                }
            }
        }
    }
}
