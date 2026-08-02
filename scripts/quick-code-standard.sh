#!/usr/bin/env bash
# Apply the project coding standards to the files modified since HEAD only.
#
# Same tools as the documented pre-commit pass (spotless + prettier), plus eslint,
# but scoped to the working-tree diff instead of the whole repo.
#
#   Kotlin / .kts / YAML  -> ./gradlew spotlessApply -PspotlessRatchet=HEAD  (rewrites files)
#   ts-src TS/JS/CSS/JSON -> prettier --write                                (rewrites files)
#   ts-src TS/JS          -> eslint                                          (reports only)
#
# The file list is passed as arguments because the dev container has no git binary:
# `make quick-code-standard` computes it on the host. With no arguments the script
# falls back to asking git itself, so it also works when run directly on the host.
#
# Exits non-zero if eslint reports an error, so it can gate a commit.
set -euo pipefail

if command -v git >/dev/null 2>&1; then
    cd "$(git rev-parse --show-toplevel)"
fi

TS_DIR="app/webApp/ts-src"

if [ $# -gt 0 ]; then
    candidates=("$@")
else
    mapfile -t candidates < <(
        {
            git diff --name-only HEAD
            git ls-files --others --exclude-standard
        } | sort -u
    )
fi

# Keep only paths that still exist (a deleted file needs no formatting).
CHANGED=()
for f in "${candidates[@]}"; do
    [ -n "$f" ] && [ -f "$f" ] && CHANGED+=("$f")
done

if [ ${#CHANGED[@]} -eq 0 ]; then
    echo "quick-code-standard: no file modified since HEAD — nothing to do."
    exit 0
fi

kotlin_files=()
prettier_files=()
eslint_files=()

for f in "${CHANGED[@]}"; do
    case "$f" in
    # spotless covers Kotlin via its `kotlin` block and YAML via its `misc` block
    *.kt | *.kts | *.yml | *.yaml) kotlin_files+=("$f") ;;
    esac
    # prettier/eslint configs live in ts-src, so only files below it are covered —
    # this mirrors `npm run format`, which also runs from that directory.
    case "$f" in
    "$TS_DIR"/*)
        case "$f" in
        *.ts | *.tsx | *.js | *.jsx | *.mjs | *.css | *.json | *.md)
            prettier_files+=("${f#"$TS_DIR"/}")
            ;;
        esac
        case "$f" in
        *.ts | *.tsx | *.js | *.jsx | *.mjs) eslint_files+=("${f#"$TS_DIR"/}") ;;
        esac
        ;;
    esac
done

echo "quick-code-standard: ${#CHANGED[@]} file(s) modified since HEAD"

status=0

if [ ${#kotlin_files[@]} -gt 0 ]; then
    echo "→ spotless (${#kotlin_files[@]} Kotlin/YAML file(s))"
    ./gradlew spotlessApply -PspotlessRatchet=HEAD --console=plain -q
else
    echo "→ spotless: skipped (no Kotlin/YAML change)"
fi

if [ ${#prettier_files[@]} -gt 0 ]; then
    echo "→ prettier (${#prettier_files[@]} file(s))"
    (cd "$TS_DIR" && npx --no-install prettier --write --log-level warn "${prettier_files[@]}")
else
    echo "→ prettier: skipped (no ts-src change)"
fi

if [ ${#eslint_files[@]} -gt 0 ]; then
    echo "→ eslint (${#eslint_files[@]} file(s))"
    (cd "$TS_DIR" && npx --no-install eslint "${eslint_files[@]}") || status=$?
else
    echo "→ eslint: skipped (no ts-src change)"
fi

if [ $status -ne 0 ]; then
    echo "quick-code-standard: eslint reported problems."
    exit $status
fi

echo "quick-code-standard: done."
