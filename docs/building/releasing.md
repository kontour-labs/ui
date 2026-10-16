# Releasing

A release is a tag. Push `v0.4.5` and CI tests what has not been tested for
that tree, publishes `io.kontour:ui`, `io.kontour:ui-nav3` and
`io.kontour:haptics` at `0.4.5` to Kontour's Maven repository,
**`https://maven.kontour.io`**, and deploys the documentation labelled with it.
Nothing else publishes.

```sh
git tag v0.4.5
git push origin v0.4.5
```

Before tagging:

1. The changelog's `## Unreleased` becomes `## 0.4.5 — <date>`, with a fresh
   empty `## Unreleased` above it, and [`migrating.md`](../../ui-docs/content/migrating.md)
   has a section for every break in it.
2. `./gradlew :ui:coordinate -Pkontour.version=0.4.5` shows what the tag will
   publish; without the property it is the snapshot an untagged build is.
3. The API dumps are current: `./gradlew checkKotlinAbi`.

The version rule — a tag, `-Pkontour.version`, or a snapshot — and the reasons
for it are in [`buildSrc/src/main/kotlin/KontourPublishing.kt`](../../buildSrc/src/main/kotlin/KontourPublishing.kt).
What CI runs for a tag, and what it skips because `main` already ran it, is in
[`testing.md`](testing.md#what-ci-actually-runs-and-what-it-does-not-run-twice).

## The repository

`maven.kontour.io` is an R2 bucket, public through its custom domain, holding
nothing but files laid out the way Maven lays them out — the same bucket
serves `kontour-labs/tabler`'s icons. An app reads it with no token, and it
is the only place Gradle is told to look for `io.kontour`
([installing](../../ui-docs/content/installing.md#the-repositories)).

The `publish` job, through [`.github/scripts/r2-release.sh`](../../.github/scripts/r2-release.sh):

1. **Refuses a version that is already there.** A released version is never
   replaced: an app that resolved it once must get the same files every time.
   A release that went out wrong is fixed forward, with the next patch.
2. **Fetches each artifact's `maven-metadata.xml`** into `build/release-repo`,
   so that Gradle publishing there adds the new version to the list rather than
   writing a list of one.
3. **Publishes** into `build/release-repo` — `publishAllPublicationsToReleaseRepository`.
4. **Uploads** the new files, cached for a year, and then the version lists,
   cached for five minutes, so a list never names a version whose files are not
   there yet.

A release that failed *before* the upload published nothing, and its version
is still free. Delete the tag, fix, and push it again on the fixed commit:

```sh
git push --delete origin v0.4.5
git tag -d v0.4.5
git tag v0.4.5          # on the fixed commit
git push origin v0.4.5
```

### Why not Maven Central

Its free tier caps how much an organisation's releases may weigh in a month,
and one release of the icons is about that much. A bucket of static files has no
such limit, costs nothing to download from, and keeps the library's address
Kontour's own whatever serves it later. What it gives up is Central's
discoverability, and Central's own refusal to replace a version, which the
upload script does instead.

## Setting it up

Once, and shared with [`kontour-labs/tabler`](https://github.com/kontour-labs/tabler),
whose release workflow uses the same bucket and secrets. Organisation secrets on
`kontour-labs` serve both; repository secrets work too.

1. **The bucket.** Cloudflare → R2 → Create bucket, named `kontour-maven`.
   Another name works if the organisation or repository variable `R2_BUCKET`
   says so.
2. **Its domain.** The bucket → Settings → Public access → Custom Domains →
   Connect Domain → `maven.kontour.io`. `kontour.io` is a Cloudflare zone, so the
   record is made for you. Leave the `r2.dev` address disabled: the custom domain
   is the one that is cached at the edge.
3. **A token.** R2 → Manage R2 API Tokens → Create API token, with *Object Read &
   Write*, applied to `kontour-maven` only. Keep the access key ID and the secret
   access key: they are shown once. The account ID is on the R2 overview page.
4. **The secrets.** `kontour-labs` → Settings → Secrets and variables → Actions:
   `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID` and `R2_SECRET_ACCESS_KEY`, available to
   `ui` and `tabler`.
5. **Check.** After the first release, in a private window:

   ```sh
   curl -fsS https://maven.kontour.io/io/kontour/ui/maven-metadata.xml
   ```

The upload is the AWS CLI that GitHub's Ubuntu runners have, pointed at R2's
S3-compatible endpoint. To publish into the folder without uploading, on any
machine and with no secrets: `./gradlew publishAllPublicationsToReleaseRepository`,
then look in `build/release-repo`.

## Before 0.4.5

Releases up to 0.4.4 went to GitHub Packages, which needs a token even to read,
and the icons came from there too. Those versions stay there and are not on
`maven.kontour.io`; [`migrating.md`](../../ui-docs/content/migrating.md#044-to-045)
has what an app changes to move.
