#!/usr/bin/env bash
# Whether a change needs the full checks (.github/workflows/android-ci.yml, step "Runner: scope").
#   scripts/ci-scope.sh <base commit or empty>    prints code=true or code=false
# code=false only when every path the change touches, on both sides of a rename, is
# docs/**/*.md. Anything unknown (no base, a base this clone lacks, an empty diff) is code=true.
set -euo pipefail
base=${1:-}
code=true
if [ -n "$base" ] && git cat-file -e "$base^{commit}" 2>/dev/null; then
  changed=$(git diff --no-renames --name-only "$base" HEAD)
  if [ -n "$changed" ] && ! printf '%s\n' "$changed" | grep -vE '^docs/.*\.md$' >/dev/null; then
    code=false
  fi
fi
echo "code=$code"
