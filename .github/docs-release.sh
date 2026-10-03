#!/usr/bin/env bash
# The release the API reference is labelled with, decided as
# `kontourDocsVersion` in buildSrc decides it: a tag build is its own tag, and
# anything else is the newest plain `vX.Y.Z` tag, or `snapshot` before there is
# one. CI names the site's artifact with it, because it is the one input to the
# site that is not in the tree. Needs the tags fetched as refs.
set -uo pipefail
if [ "${GITHUB_REF_TYPE:-}" = tag ]; then
  echo "$GITHUB_REF_NAME"
  exit 0
fi
latest=$(git tag --list 'v*' --sort=-v:refname | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | head -n 1)
echo "${latest:-snapshot}"
