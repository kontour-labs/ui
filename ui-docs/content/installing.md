# Installing

Getting `io.kontour:ui` to resolve, on a laptop and in CI.

The library is published privately to **GitHub Packages** from
`kontour-labs/ui`. That registry authenticates *every* request, including the
ones that come back 404, so there is no anonymous read and no way to try it
without a credential first. Most of this page is about that credential.

## The repository

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.pkg.github.com/kontour-labs/ui") {
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("KONTOUR_PACKAGES_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("KONTOUR_PACKAGES_TOKEN")).orNull
            }
            content { includeGroup("io.kontour") }
        }
    }
}
```

```kotlin
// build.gradle.kts
implementation("io.kontour:ui:0.1.0")
```

**`content { includeGroup("io.kontour") }` is not tidiness.** Without it Gradle
will try this repository for every dependency in your build — several hundred
credentialed requests, all of which 404, and all of which fail outright for
anyone whose token is missing or wrong. Scoped, the only thing that can fail
here is the design system, which is what makes the error mean something.

---

## The token

A **classic** personal access token with the `read:packages` scope, and nothing
else. Create it at **Settings → Developer settings → Personal access tokens →
Tokens (classic)**.

> **Fine-grained tokens do not work.** They cannot read GitHub Packages at the
> time of writing, whatever permissions you give them. If you are looking at a
> permissions matrix that has no "Packages" row, that is the wrong kind of token.

The account that owns the token needs read access to `kontour-labs/ui` — the
package inherits the repository's access, so someone who cannot see the repo
cannot see its packages either.

### On a laptop

`~/.gradle/gradle.properties`, **not** the repository:

```properties
gpr.user=your-github-username
gpr.key=ghp_…
```

### In CI

Store the same pair as secrets in the *consuming* repository (or as organisation
secrets, if you are on a plan that offers them for private repositories) and put
them in the environment of any step that resolves dependencies:

```yaml
permissions:
  contents: read
  # No `packages: read` — see below for why it would not help.

steps:
  - name: Test
    env:
      KONTOUR_PACKAGES_ACTOR: ${{ secrets.KONTOUR_PACKAGES_ACTOR }}
      KONTOUR_PACKAGES_TOKEN: ${{ secrets.KONTOUR_PACKAGES_TOKEN }}
    run: ./gradlew test
```

---

## Why the workflow's own token will not do

This is the part that wastes an afternoon, so it is worth stating plainly.

**`secrets.GITHUB_TOKEN` cannot read this package from another repository, and
no permissions block changes that.** A workflow's own token is scoped to the
repository it runs in: it reads *that* repository's packages and no others.

GitHub does have a per-package grant that would widen it — **Manage Actions
access**, where you name other repositories that may read the package. It only
exists for **container** packages (`ghcr.io`). A Maven package has no such
screen: its access is inherited from the repository that published it and cannot
be extended. If you go looking for that setting on a Maven package you will not
find it, because it is not there.

So a consumer in another repository needs a credential belonging to an
**account** rather than to a workflow. Options, in the order most people should
consider them:

| | |
|---|---|
| **Classic PAT in a secret** | What `kontour-labs/anyways` does. Least setup, definitely works. Tied to whoever created it, and needs rotating when it expires |
| **Machine-user PAT** | The same, on a dedicated bot account added to the org. No individual's credentials in CI, at the cost of a seat |
| **GitHub App token** | An org-owned app with `packages: read`, installed on both repositories, minting a short-lived token per run via `actions/create-github-app-token`. Cleanest and self-expiring; the most setup |

Publishing *to* the registry is different and simpler: the release workflow lives
in this repository, so its own `GITHUB_TOKEN` with `packages: write` is enough.
Only reading from elsewhere needs an account credential.

---

## When it fails

| What you see | What it is |
|---|---|
| `401 Unauthorized` on `io.kontour:ui` | No credential, or one without `read:packages`. A fine-grained token looks exactly like this |
| `401` in CI while your laptop works | The workflow is still using `secrets.GITHUB_TOKEN`, or the secrets are not in that step's `env` |
| `404 Not Found` on a version | That version was never published. `Packages → ui` lists what exists |
| Hundreds of failing requests for unrelated dependencies | The `content { includeGroup(…) }` filter is missing |
| Resolution succeeds but the classes are missing on one platform | A variant did not publish. There should be seven modules per version — `ui`, and one each for `-android`, `-iosarm64`, `-iossimulatorarm64`, `-js`, `-jvm`, `-wasm-js` |

## Working without a token

A consumer that has this repository checked out beside it can substitute the
sources for the published artifact and skip the registry entirely. That is what
`anyways` does under `-Pkontour.ui.local=true`; the pattern is a conditional
`includeBuild`:

```kotlin
if (providers.gradleProperty("kontour.ui.local").orNull.toBoolean()) {
    includeBuild("../../ui")
}
```

Useful when changing the library and the app together, and a reasonable fallback
for someone who cannot get a token. It should stay off by default: the published
artifact has to be what CI builds and what a fresh clone gets, or it is only ever
exercised by the release job.
