# Copyright (c) 2026 Marco Porro (x.com/MarcoPorro · www.marcoporro.it, MIT)
"""Browse the official Omarchy desktop plugin registry (https://plugins.omarchy.org/catalog.json).

That catalog lists Omarchy DESKTOP plugins (Quattro bar widgets, shell suites...). They do not run on a TV,
so every entry is browse-only here: name, author, repo link and the PC install command. An entry is
installable on the TV only when a TV manifest for the same id exists locally (built-in or `plugins add`).
Unknown fields are ignored; malformed entries are skipped, never fatal.
"""
from __future__ import annotations

from . import plugins as P

CATALOG_URL = "https://plugins.omarchy.org/catalog.json"

# TV view: media and visual plugins (or ones with our TV manifest) are shown; desktop-only ones are hidden by default.
TV_WORDS = {"media", "video", "music", "audio", "iptv", "tv", "movie", "movies", "stream", "streaming", "radio", "podcast",
            "theme", "themes", "wallpaper", "wallpapers", "screensaver", "screensavers", "ambience", "ambient", "visual", "visualizer"}
DESKTOP_WORDS = {"bar", "shell", "quickshell", "waybar", "launcher", "power-management", "hyprland", "keybinds", "tray"}


def tv_relevant(e: dict) -> bool:
    if e.get("tvInstallable"):
        return True
    words = {w.lower() for w in e.get("tags", [])} | {e.get("category", "").lower()}
    return bool(words & TV_WORDS)


def _s(v, limit=200) -> str:
    return P.clean_title(v, limit=limit) if isinstance(v, str) else ""


def parse(doc, tv_ids=()) -> list[dict]:
    if not isinstance(doc, dict) or not isinstance(doc.get("plugins"), list):
        raise ValueError("not an Omarchy catalog (no plugins[])")
    tv_ids = set(tv_ids)
    out = []
    for e in doc["plugins"]:
        if not isinstance(e, dict):
            continue
        pid = e.get("id")
        if not isinstance(pid, str) or not P.ID_RE.match(pid):
            continue
        tags = [t for t in e.get("tags") or [] if isinstance(t, str)][:8] if isinstance(e.get("tags"), list) else []
        raw = e.get("installCommand") if e.get("installAvailable") is True else ""
        cmd = raw if isinstance(raw, str) and len(raw) < 300 and not P.CTRL_RE.search(raw) else ""
        out.append({
            "id": pid, "name": _s(e.get("name")) or pid, "author": _s(e.get("author"), 80),
            "description": _s(e.get("description"), 300), "version": _s(e.get("version"), 40),
            "category": _s(e.get("category"), 40), "kind": _s(e.get("kind"), 40), "status": _s(e.get("status"), 40),
            "tags": [_s(t, 30) for t in tags], "license": _s(e.get("license"), 60),
            "repo": P.safe_url(e.get("repo")) or "", "pcInstallCommand": cmd,
            "tvInstallable": pid in tv_ids,
        })
        out[-1]["tvRelevant"] = tv_relevant(out[-1])
    return out


def run(args, say=print, fetch=P.fetch_json) -> int:
    url, q, show_all = CATALOG_URL, "", False
    rest = list(args)
    while rest:
        a = rest.pop(0)
        if a == "--all":
            show_all = True
        elif a == "--url" and rest:
            url = rest.pop(0)
        else:
            q = (q + " " + a).strip().lower()
    try:
        entries = parse(fetch(url), tv_ids=P.load_all().keys())
    except Exception as ex:  # network or format problems: report, do not crash
        say(f"✗ catalog unavailable: {ex}")
        return 1
    total = len(entries)
    if not show_all:
        entries = [e for e in entries if e["tvRelevant"]]
    if q:
        entries = [e for e in entries if q in (e["id"] + " " + e["name"] + " " + e["description"] + " " + " ".join(e["tags"])).lower()]
    for e in entries:
        tv = "TV ✓" if e["tvInstallable"] else "PC only"
        say(f"[{tv}] {e['id']} — {e['name']} by {e['author']} {e['repo']}".rstrip())
        if e["pcInstallCommand"]:
            say(f"    install on PC: {e['pcInstallCommand']}")
    hidden = "" if show_all else f", {total - len(entries)} not TV-relevant hidden (--all to show)" if not q else ""
    say(f"{len(entries)} entries{hidden} (desktop registry; browse-only on TV unless marked TV ✓)")
    return 0
