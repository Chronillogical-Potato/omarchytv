#!/bin/sh
# Installs the "Send to TV" file-manager actions and the Omarchy menu entry for the current user (no root). Undo: --uninstall
set -e
here=$(cd "$(dirname "$0")" && pwd)
apps="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
scripts="${XDG_DATA_HOME:-$HOME/.local/share}/nautilus/scripts"
tv="$here/../bin/omarchy-tv"
if [ "$1" = "--uninstall" ]; then
  rm -f "$apps/omarchy-tv-send.desktop" "$scripts/Send to TV"
  "$tv" menu-entry remove || true
  echo "Removed."; exit 0
fi
mkdir -p "$apps" "$scripts"
install -m 644 "$here/applications/omarchy-tv-send.desktop" "$apps/"
install -m 755 "$here/nautilus-scripts/Send to TV" "$scripts/"
command -v update-desktop-database >/dev/null && update-desktop-database "$apps" || true
"$tv" menu-entry install || echo "Omarchy menu entry skipped (needs Omarchy 4.5+)."
echo "Installed: Open With > Send to TV (Cinemarchy), Scripts > Send to TV, and Send to TV (Cinemarchy) in the Omarchy menu."
