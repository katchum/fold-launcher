#!/usr/bin/env bash
# Record an acceptance verdict on one exact commit (CONTRIBUTING.md, "How a change reaches main").
#   scripts/acceptance-status.sh <40-hex sha> approve|reject "<note>"
# Posts the commit status `acceptance` (success or failure) on that sha.
set -euo pipefail
REPO=${FOLD_GH_REPO:-katchum/fold-launcher}
die() { echo "acceptance-status: $*" >&2; exit 2; }

[ $# -eq 3 ] || die 'usage: acceptance-status.sh <sha> approve|reject "<note>"'
sha=$1 verdict=$2 note=$3
[[ $sha =~ ^[0-9a-f]{40}$ ]] || die "the sha must be the full 40-hex commit id, not '$sha'"
case $verdict in
  approve) state=success ;;
  reject) state=failure ;;
  *) die "the verdict is approve or reject, not '$verdict'" ;;
esac
[ -n "${note// /}" ] || die "a note is required (what was reviewed, or why it was rejected)"
desc=${note:0:140}

gh api "repos/$REPO/commits/$sha" --jq .sha >/dev/null 2>&1 || die "$sha is not a commit in $REPO"
gh api --method POST "repos/$REPO/statuses/$sha" \
  -f state="$state" -f context=acceptance -f description="$desc" --jq '"\(.context) \(.state) on \(.url | split("/") | last)"'
