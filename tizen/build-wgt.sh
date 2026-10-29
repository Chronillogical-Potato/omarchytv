#!/usr/bin/env bash
# Build OmarchyTV for Samsung Tizen TVs (.wgt) from the shared LG webOS web app (../webos/app).
# Usage: ./build-wgt.sh [OUT_DIR]   (env TIZEN_PROFILE=<security profile> to sign with Tizen Studio CLI)
# Without the tizen CLI it writes an UNSIGNED .wgt (zip) that Tizen Studio can sign later.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
VER="2.13.0-beta"
OUT="${1:-$HERE/out}"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$OUT"
cp -a "$ROOT/webos/app/." "$STAGE/"
rm -rf "$STAGE/appinfo.json" "$STAGE/rooted"
cp -f "$HERE/config.xml" "$HERE/tizen-shim.js" "$STAGE/"
# The shim must run before compat.js so window.OMARCHY_TIZEN_IDS exists when app.js loads.
sed -i.bak 's#<script src="compat.js"></script>#<script src="tizen-shim.js"></script>\n<script src="compat.js"></script>#' "$STAGE/index.html" && rm -f "$STAGE/index.html.bak"
grep -q tizen-shim.js "$STAGE/index.html" || { echo "index.html: could not inject tizen-shim.js" >&2; exit 1; }
# Unwrap base64-wrapped WebP wallpapers (same as webos/build-ipk.sh).
for f in "$STAGE"/wallpapers/*/*.webp; do
  [ -f "$f" ] || continue
  if [ "$(head -c 4 "$f")" = "UklG" ]; then base64 -d < "$f" > "$f.bin" && mv -f "$f.bin" "$f"; fi
done
if command -v tizen >/dev/null 2>&1 && [ -n "${TIZEN_PROFILE:-}" ]; then
  tizen package -t wgt -s "$TIZEN_PROFILE" -o "$OUT" -- "$STAGE"
  mv -f "$OUT"/*.wgt "$OUT/OmarchyTV-tizen-$VER.wgt"
  WGT="$OUT/OmarchyTV-tizen-$VER.wgt"
else
  WGT="$OUT/OmarchyTV-tizen-$VER-unsigned.wgt"
  rm -f "$WGT"
  if command -v zip >/dev/null 2>&1; then (cd "$STAGE" && zip -qr -X "$WGT" .)
  else python3 -c 'import os,sys,zipfile
z=zipfile.ZipFile(sys.argv[2],"w",zipfile.ZIP_DEFLATED)
for d,_,fs in os.walk(sys.argv[1]):
  for f in sorted(fs): p=os.path.join(d,f); z.write(p,os.path.relpath(p,sys.argv[1]))
z.close()' "$STAGE" "$WGT"; fi
  echo "Unsigned package (no tizen CLI or TIZEN_PROFILE): sign it in Tizen Studio before installing." >&2
fi
sha256sum "$WGT"
