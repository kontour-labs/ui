# Releasing

A release is a tag. Push `v0.4.5` and CI tests what has not been tested for
that tree, publishes `io.kontour:ui`, `io.kontour:ui-nav3` and
`io.kontour:haptics` at `0.4.5` to Maven Central, and deploys the documentation
labelled with it. Nothing else publishes.

```sh
git tag v0.4.5
git push origin v0.4.5
```

Before tagging:

1. The changelog's `## Unreleased` becomes `## 0.4.5 — <date>`, with a fresh
   empty `## Unreleased` above it, and [`migrating.md`](../../ui-docs/content/migrating.md)
   has a section for every break in it.
2. `./gradlew :ui:coordinate` on the tagged commit says `io.kontour:ui:0.5.0-SNAPSHOT`
   — the tag is what turns it into the release. `-Pkontour.version=0.4.5` shows
   what the tag will publish.
3. The API dumps are current: `./gradlew checkKotlinAbi`.

The version rule — a tag, `-Pkontour.version`, or a snapshot — and the reasons
for it are in [`buildSrc/src/main/kotlin/KontourPublishing.kt`](../../buildSrc/src/main/kotlin/KontourPublishing.kt).
What CI runs for a tag, and what it skips because `main` already ran it, is in
[`testing.md`](testing.md#what-ci-actually-runs-and-what-it-does-not-run-twice).

## Maven Central never forgets

A version on Central cannot be deleted or replaced. A release that went out wrong
is fixed forward: the next patch, `v0.4.6`. The build refuses a version that is
not `1.2.3` or `1.2.3-rc1`-shaped before anything is uploaded, and the `publish`
job prints the coordinates first, for the same reason.

A release that failed *before* the upload — a compile, a test, a refused
signature — published nothing, and its version is still free. Delete the tag,
fix, and push the tag again on the fixed commit:

```sh
git push --delete origin v0.4.5
git tag -d v0.4.5
git tag v0.4.5          # on the fixed commit
git push origin v0.4.5
```

Central validates a deployment whole — POMs, signatures, the sources and javadoc
jars of every module — and publishes all of it or none. A release is resolvable
about half an hour after the job finishes.

## Setting it up

Once, and shared with [`kontour-labs/tabler`](https://github.com/kontour-labs/tabler),
whose release workflow uses the same four secrets. Organisation secrets on
`kontour-labs` serve both; repository secrets work too.

1. **The namespace.** At [central.sonatype.com](https://central.sonatype.com),
   sign in and verify `io.kontour` under Namespaces: Central gives a TXT record
   to add to `kontour.io`'s DNS. One namespace covers every `io.kontour:*`
   artifact, the icons included.
2. **A user token.** Account → Generate User Token. The two halves are
   `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`. They are not the
   account's sign-in.
3. **A signing key.** Central rejects unsigned artifacts.

   ```sh
   gpg --quick-generate-key "Kontour Labs <hello@kontour.io>" rsa4096 sign 0
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY ID>
   gpg --export-secret-keys --armor <KEY ID>       # SIGNING_KEY
   ```

   The armoured private key, header and footer included, is `SIGNING_KEY`, and
   its passphrase `SIGNING_KEY_PASSWORD`. The public key has to be on a key
   server Central reads — `keyserver.ubuntu.com` or `keys.openpgp.org` — before
   the first upload, or the deployment fails validation.
4. **The secrets.** `kontour-labs` → Settings → Secrets and variables → Actions:
   `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY` and
   `SIGNING_KEY_PASSWORD`, available to `ui` and `tabler`.

CI hands them to Gradle as `ORG_GRADLE_PROJECT_mavenCentralUsername`,
`…mavenCentralPassword`, `…signingInMemoryKey` and `…signingInMemoryKeyPassword`,
which `com.vanniktech.maven.publish` reads. Without a key the build skips signing,
so `./gradlew publishToMavenLocal` works on any machine with no secrets at all.

## Before 0.4.5

Releases up to 0.4.4 went to GitHub Packages, which needs a token even to read,
and the icons came from there too. Those versions stay there and are not on
Central; [`migrating.md`](../../ui-docs/content/migrating.md#044-to-045) has what a
consumer changes to move.
