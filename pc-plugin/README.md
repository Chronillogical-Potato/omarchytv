# OmarchyTV PC / Mac Plugin (`2.12.0-beta` — Omarchy Linux, macOS, Android TV & LG webOS)

Two parts, both MIT, © 2026 Marco Porro (see [LICENSE](LICENSE), [NOTICE](NOTICE)):

- **`omarchy-link/`**: the link daemon and CLI for Android TV: SPAKE2 pairing, signed requests (`OTV1` HMAC), auto-reconnect, theme sync across all **26 MIT Omarchy themes**, stats (every 5 s while the TV home is visible), zero-API-cost AI CLI bridge (`> ask` / `> grok bot` routed to your local `grok`, `claude`, `codex`, `gemini`, `hermes`, or `ollama` CLI), cast, live screen sharing, remote, Waybar widget, notifications. Protocol: [omarchy-link/docs/PROTOCOL.md](omarchy-link/docs/PROTOCOL.md).
- **`omarchy-tv/`**: the Omarchy-style front-end (`omarchy-tv`): Omarchy menu via `omarchy-menu-select` (Omarchy 4.5+), `walker --dmenu` on older Omarchy, gum / numbered list over SSH, theme push (50 MB cap), custom app banner push, **native MCP server (`omarchy-tv mcp`)** for Grok Bot / Claude / Cursor / Hermes, Android TV `.apk` update (`adb install -r` with certificate/version checks), LG webOS `.ipk` update (`ares-install` with `appinfo.json` verification), and the `omarchy-tv webos-serve` LAN HTTP server for LG webOS TVs. Uses `omarchy-link/lib` from this folder.

Python 3.10+ standard library only. Optional: Pillow (backgrounds → WebP), ffmpeg, gpu-screen-recorder or wf-recorder (screen sharing), android-tools (`adb`, `apksigner`/`aapt` for Android `.apk` updates), `@webos-tools/cli` (`ares-install`/`ares-launch` for LG webOS `.ipk` updates), gum, Walker (Omarchy < 4.5), notify-send.

## Install on an Omarchy PC or macOS
```bash
git clone https://github.com/piermark/omarchytv.git omarchytv && cd omarchytv/pc-plugin
# (or from GitLab mirror: git clone https://gitlab.com/piermark/omarchytv.git omarchytv && cd omarchytv/pc-plugin)
mkdir -p ~/.local/share ~/.local/bin
cp -r omarchy-link ~/.local/share/omarchy-link          # omarchy-tv also finds it here
cp -r omarchy-tv   ~/.local/share/omarchy-tv
ln -sf ~/.local/share/omarchy-link/bin/omarchy-link ~/.local/bin/omarchy-link
ln -sf ~/.local/share/omarchy-tv/bin/omarchy-tv     ~/.local/bin/omarchy-tv

# 1. Pair with Android TV: Logo menu (or Workspace 4) → Connect Omarchy Linux (shows a 6-digit code)
omarchy-link pair --code 123456        # or: omarchy-link pair <tv-ip> --code 123456
omarchy-tv                             # interactive menu

# 2. Optional: MCP Server for Grok Bot / Claude Desktop / Cursor / Hermes
omarchy-tv mcp --stdio                 # stdio JSON-RPC 2.0 MCP server for local desktop AI clients
omarchy-tv mcp --http --port 8990      # HTTP MCP server bound to 127.0.0.1:8990 (Bearer token in ~/.config/omarchy-tv/mcp-token)

# 3. For LG webOS TV (experimental testing build):
omarchy-tv webos-serve --port 8765     # serves /v1/status, /v1/themes, /v1/wallpaper over LAN HTTP
omarchy-tv update omarchytv-webos-2.11.0-testing.ipk --tv lgtv
```

Optional integrations:
- **Waybar module + background session**: see [omarchy-link/README.md](omarchy-link/README.md) (`omarchy-link status --waybar`, `omarchy-link session`).
- **Theme hook**: `omarchy-link/hooks/theme-set` follows `omarchy theme set` (note: on the TV, `Sync PC wallpaper` is off by default so your TV's bundled 4K MIT wallpaper is preserved unless you enable `Sync PC wallpaper` in the TV's Themes or Wallpaper menu).
- **Keybinding** (Hyprland): `bindd = SUPER ALT, T, OmarchyTV, exec, omarchy-tv`.
- **Omarchy menu entry** (Omarchy 4.5+, `~/.config/omarchy/extensions/omarchy-menu.jsonc`):
  add `"omarchytv": {"icon":"󰔂","label":"OmarchyTV","action":"omarchy-tv"},` inside its `{ }`, then `omarchy menu refresh`.
  **Send to TV (Cinemarchy)** is added there for you by `omarchy-tv/share/install-file-manager.sh` (or `omarchy-tv menu-entry install`; `--uninstall` / `menu-entry remove` takes it out).

Everything is LAN-only and opt-in. Screen sharing is off until you start it. `screenAudio`, `screenFps`, `screenMirrorFromTv` (`"confirm"` | `"allow"` | `"off"`) and `statsIntervalS` go in `~/.config/omarchy-link/config.json`.

## Tests (no TV needed)
```bash
(cd omarchy-link/tests && ./run.sh)                    # needs ffmpeg for the mirror test
python3 -m unittest discover -s omarchy-tv/tests -v
```
