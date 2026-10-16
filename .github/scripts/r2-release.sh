#!/usr/bin/env bash
# Publishes a release to the Maven repository at https://maven.kontour.io: an
# R2 bucket, served in public through its custom domain, that is nothing but
# files laid out as Maven lays them out. The same script in kontour-labs/tabler
# and kontour-labs/ui.
#
#   r2-release.sh prepare <repo dir> <version> <artifact path>... -- <metadata glob>...
#   r2-release.sh upload  <repo dir>
#
# **prepare**, before Gradle publishes into <repo dir>:
#   - refuses a version that is already in the bucket, for every artifact path
#     (`io/kontour/ui`): a release is never replaced, so a fix goes out as the
#     next version, as it would on Maven Central;
#   - downloads the `maven-metadata.xml` of every artifact matching the globs
#     (`ui*`, `tabler-icons-*`) into <repo dir>, so that Gradle publishing there
#     adds the new version to each list rather than writing a list of one.
#
# **upload**, after: every file of the new version, cached for a year — a
# released file never changes — and then the metadata, cached for five
# minutes, so that no list names a version before its files are there.
#
# Reads R2_ACCOUNT_ID, R2_BUCKET, AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
# (the bucket's R2 API token). Set up in kontour-labs/ui's
# docs/building/releasing.md.

set -euo pipefail

: "${R2_ACCOUNT_ID:?R2_ACCOUNT_ID is not set}"
: "${R2_BUCKET:?R2_BUCKET is not set}"
: "${AWS_ACCESS_KEY_ID:?AWS_ACCESS_KEY_ID is not set: the R2 token's access key ID}"
: "${AWS_SECRET_ACCESS_KEY:?AWS_SECRET_ACCESS_KEY is not set: the R2 token's secret}"

# R2 takes the region `auto`, and does not answer the checksums newer AWS CLIs
# send by default unless asked to only when an operation requires one.
export AWS_DEFAULT_REGION=auto
export AWS_REQUEST_CHECKSUM_CALCULATION=when_required
export AWS_RESPONSE_CHECKSUM_VALIDATION=when_required
endpoint="https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com"
bucket="s3://${R2_BUCKET}"

s3() { aws --endpoint-url "$endpoint" s3 "$@"; }
s3api() { aws --endpoint-url "$endpoint" s3api "$@"; }

command=${1:?prepare or upload}
shift

case "$command" in
  prepare)
    repo=$1 version=$2
    shift 2
    paths=()
    while [ $# -gt 0 ] && [ "$1" != "--" ]; do paths+=("$1"); shift; done
    [ "${1:-}" = "--" ] && shift
    globs=("$@")
    mkdir -p "$repo"

    for path in "${paths[@]}"; do
      artifact=${path##*/}
      key="$path/$version/$artifact-$version.pom"
      if s3api head-object --bucket "$R2_BUCKET" --key "$key" > /dev/null 2>&1; then
        echo "::error::$key is already published. A release is never replaced: publish the next version."
        exit 1
      fi
    done

    includes=()
    for glob in "${globs[@]}"; do
      includes+=(--include "$glob/maven-metadata.xml*")
    done
    # Nothing to fetch on the first release of an artifact, and that is fine.
    s3 sync "$bucket/io/kontour" "$repo/io/kontour" --exclude "*" "${includes[@]}" --only-show-errors
    echo "Fetched the version lists:"
    find "$repo" -name maven-metadata.xml | sed "s|^$repo/|  |" | sort
    ;;

  upload)
    repo=$1
    s3 cp "$repo" "$bucket" --recursive --exclude "*maven-metadata.xml*" \
      --cache-control "public, max-age=31536000, immutable" --only-show-errors
    s3 cp "$repo" "$bucket" --recursive --exclude "*" --include "*maven-metadata.xml*" \
      --cache-control "public, max-age=300" --only-show-errors
    echo "Uploaded $(find "$repo" -type f | wc -l | tr -d ' ') files to https://maven.kontour.io/"
    ;;

  *)
    echo "usage: $0 prepare <repo> <version> <path>... -- <glob>... | upload <repo>" >&2
    exit 2
    ;;
esac
