# Releasing

How a version reaches Maven Central, and the one-time set-up that lets it. The
release itself is in the [README](../../README.md#releasing): the changelog, then
a tag.

## What a release does

A `v*` tag runs CI, and its `publish` job, once `test` and `targets` have passed
for that commit, runs:

```sh
./gradlew publishAndReleaseToMavenCentral
```

That is the `com.vanniktech.maven.publish` plugin, applied in each published
module beside `kontourPublishing` (`buildSrc/src/main/kotlin/KontourPublishing.kt`).
It builds every target, adds the sources and javadoc jars Central requires,
signs every file, uploads the three modules to the Central Portal, and releases
them once Central has validated them. They can be resolved about half an hour
later.

**Central never deletes or replaces a version.** A release that went out wrong
is fixed by the next one. That is the reason for every check before the upload:
the version rule in `kontourPublishing`, the coordinates CI prints first, and
the tests the job waits for.

To publish without a tag, run the **CI** workflow by hand with a version. It
publishes from the branch it runs on, so run it on `main`.

## Set-up, once

Nothing here is in the repository; it is all account settings. The icons,
[`kontour-labs/tabler`](https://github.com/kontour-labs/tabler), publish to the
same namespace and need the same four secrets.

### 1. The namespace

Sign in at [central.sonatype.com](https://central.sonatype.com) and add the
namespace `io.kontour` under **Namespaces**. Central asks for a DNS `TXT`
record on `kontour.io` with a verification key it gives you; add it at the DNS
host (Cloudflare, for `kontour.io`) and press **Verify**. It usually verifies
within minutes, and the record can be removed once it has.

### 2. The user token

**Account → Generate User Token** at central.sonatype.com. It is a username and
a password, shown once. They are not your sign-in: they are what the build
uploads with, and can be revoked there without touching the account.

### 3. The signing key

Central refuses unsigned files, and checks each signature against a public key
on a keyserver. Make a key for releases alone:

```sh
gpg --quick-gen-key "Kontour Labs releases <releases@kontour.io>" rsa4096 sign 2y
gpg --list-secret-keys --keyid-format long        # the long key ID
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
gpg --armor --export-secret-keys <KEY_ID> > signing-key.asc
```

Keep the passphrase you give it. The key expires in two years; extend it
(`gpg --quick-set-expire`) and send it to the keyserver again before then, or
releases stop validating. Delete `signing-key.asc` once it is in the secrets.

### 4. The secrets

On each repository — or once, as organisation secrets for both — under
**Settings → Secrets and variables → Actions**:

| Secret | What it is |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | The user token's username |
| `MAVEN_CENTRAL_PASSWORD` | The user token's password |
| `SIGNING_KEY` | The whole of `signing-key.asc`, from `-----BEGIN` to `-----END` |
| `SIGNING_KEY_PASSWORD` | The key's passphrase |

CI hands them to Gradle as `ORG_GRADLE_PROJECT_mavenCentralUsername`,
`…Password`, `…signingInMemoryKey` and `…signingInMemoryKeyPassword`.

## Checking a build locally

```sh
./gradlew :ui:publishToMavenLocal -Pkontour.version=0.0.1-local
```

Unsigned, unless a key is in the Gradle properties: the modules sign only where
there is a key to sign with, which CI always has. The result in
`~/.m2/repository/io/kontour/` is what Central would get, file for file, but for
the signatures.
