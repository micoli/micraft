#!/usr/bin/env bash
# Locate an mkdocs install regardless of RUN_MODE:
#   1. repo-root .venv       (RUN_MODE=HOST, manual `python -m venv .venv`)
#   2. /opt/mkdocs           (dev container, baked by .docker/dev/Dockerfile)
#   3. whatever is on PATH   (pipx, system install)
set -euo pipefail
cd "$(dirname "$0")/../.."

# `mkdocs serve` under a supervisor (pitchfork `retry = true`) races itself: a
# crashed/slow-to-die instance keeps the port bound and the respawn dies with
# "OSError: [Errno 48] Address already in use". Reap a stale mkdocs on the bind
# port before starting.
if [ "${1:-}" = "serve" ]; then
  addr="127.0.0.1:8000"
  prev=""
  for arg in "$@"; do
    case "$prev" in
      -a | --dev-addr) addr="$arg" ;;
    esac
    prev="$arg"
  done
  port="${addr##*:}"

  if [ -n "$port" ] && command -v lsof >/dev/null 2>&1; then
    for _ in $(seq 1 40); do
      pids="$(lsof -ti "tcp:${port}" -sTCP:LISTEN 2>/dev/null || true)"
      [ -z "$pids" ] && break
      for pid in $pids; do
        case "$(ps -p "$pid" -o comm= 2>/dev/null || true)" in
          *python* | *mkdocs*) kill "$pid" 2>/dev/null || true ;;
        esac
      done
      sleep 0.1
    done
  fi
fi

for candidate in .venv/bin/mkdocs /opt/mkdocs/bin/mkdocs; do
  if [ -x "$candidate" ]; then exec "$candidate" "$@"; fi
done

if command -v mkdocs >/dev/null 2>&1; then exec mkdocs "$@"; fi

echo "mkdocs not found. Install it: python -m venv .venv && .venv/bin/pip install -r docs/requirements.txt" >&2
exit 1
