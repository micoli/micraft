#!/usr/bin/env bash
# Locate an mkdocs install regardless of RUN_MODE:
#   1. repo-root .venv       (RUN_MODE=HOST, manual `python -m venv .venv`)
#   2. /opt/mkdocs           (dev container, baked by .docker/dev/Dockerfile)
#   3. whatever is on PATH   (pipx, system install)
set -euo pipefail
cd "$(dirname "$0")/../.."

for candidate in .venv/bin/mkdocs /opt/mkdocs/bin/mkdocs; do
  if [ -x "$candidate" ]; then exec "$candidate" "$@"; fi
done

if command -v mkdocs >/dev/null 2>&1; then exec mkdocs "$@"; fi

echo "mkdocs not found. Install it: python -m venv .venv && .venv/bin/pip install -r docs/requirements.txt" >&2
exit 1
