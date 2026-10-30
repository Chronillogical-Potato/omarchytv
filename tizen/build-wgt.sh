#!/usr/bin/env bash
# Usage: ./build-wgt.sh [OUT_DIR]; TIZEN_PROFILE=<profile> enables Tizen Studio signing.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/out}"
if [[ "$OUT" == "--help" || "$OUT" == "-h" ]]; then
  exec python3 "$HERE/build-wgt.py" --help
fi
if [[ $# -gt 1 ]]; then
  echo "Usage: $0 [OUT_DIR]" >&2
  exit 2
fi
if [[ -n "${TIZEN_PROFILE:-}" ]]; then
  command -v tizen >/dev/null 2>&1 || { echo "TIZEN_PROFILE requires the Tizen Studio CLI on PATH." >&2; exit 1; }
  python3 "$HERE/build-wgt.py" "$OUT" --stage
  mkdir "$OUT/signed"
  tizen package -t wgt -s "$TIZEN_PROFILE" -o "$OUT/signed" -- "$OUT/stage"
  echo "Signed widget is in $OUT/signed"
else
  python3 "$HERE/build-wgt.py" "$OUT"
  echo "Unsigned package (no tizen CLI or TIZEN_PROFILE): sign it in Tizen Studio before installing." >&2
fi
