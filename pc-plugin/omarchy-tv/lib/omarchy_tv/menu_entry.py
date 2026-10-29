"""The "Send to TV (Cinemarchy)" row in the Omarchy menu (Super+Alt+Space).

Omarchy 4.5+ overlays ~/.config/omarchy/extensions/omarchy-menu.jsonc on its shipped menu: entries are keyed by
dotted id, an id without a dot sits on the root menu and an entry with "action" runs that shell command, detached.
That file is shared by every user entry, so I merge my one id in and take only that id out again: other entries are
kept, a .bak copy is written before any change, and a file that does not parse is never touched.
The JSONC dialect is Omarchy's: whole-line // comments and trailing commas only.
"""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
from pathlib import Path

ENTRY_ID = "omarchytv-send"


def menu_file() -> Path:
    base = Path(os.environ.get("XDG_CONFIG_HOME") or Path.home() / ".config")
    return base / "omarchy" / "extensions" / "omarchy-menu.jsonc"


def entry(exe: str) -> dict:
    return {"icon": "󰔂", "label": "Send to TV (Cinemarchy)",
            "description": "Pick a video and stream it to Cinemarchy on the paired OmarchyTV",
            "action": f"{_quote(exe)} send --pick"}


def _quote(s: str) -> str:
    return s if re.fullmatch(r"[\w@%+=:,./-]+", s) else "'" + s.replace("'", "'\\''") + "'"


def parse_jsonc(text: str) -> dict:
    """Omarchy's parser: drop whole-line // comments and trailing commas, then plain JSON. Raises ValueError."""
    body = "\n".join(l for l in text.splitlines() if not l.lstrip().startswith("//"))
    body = re.sub(r",(\s*[}\]])", r"\1", body)
    if not body.strip():
        return {}
    data = json.loads(body)
    if not isinstance(data, dict):
        raise ValueError("the menu extension must be a JSON object")
    return data


def _render(old_text: str, entries: dict) -> str:
    """Keep the header comment block (the sample Omarchy ships is only comments), then the entries as JSON."""
    header = []
    for line in old_text.splitlines():
        if line.lstrip().startswith("//") or not line.strip():
            header.append(line)
        else:
            break
    return ("\n".join(header).rstrip() + "\n" if any(h.strip() for h in header) else "") + \
        json.dumps(entries, ensure_ascii=False, indent=2) + "\n"


def _refresh():
    """`omarchy menu refresh` re-reads the files (the shell also watches them, so a failure here is harmless)."""
    for cmd in (["omarchy", "menu", "refresh"], ["omarchy-menu", "refresh"]):
        exe = shutil.which(cmd[0]) or str(Path.home() / ".local/share/omarchy/bin" / cmd[0])
        if os.access(exe, os.X_OK):
            subprocess.run([exe, *cmd[1:]], capture_output=True)
            return


def _load(f: Path) -> tuple[str, dict]:
    text = f.read_text(encoding="utf-8") if f.exists() else ""
    try:
        return text, parse_jsonc(text)
    except ValueError as e:
        raise ValueError(f"{f} does not parse ({e}); fix it first, I did not change it") from e


def _write(f: Path, old_text: str, entries: dict):
    f.parent.mkdir(parents=True, exist_ok=True)
    if f.exists():
        shutil.copy2(f, f.with_name(f.name + ".bak"))
    new = _render(old_text, entries)
    parse_jsonc(new)  # never write something Omarchy cannot read
    tmp = f.with_name(f.name + ".tmp")
    tmp.write_text(new, encoding="utf-8")
    os.replace(tmp, f)


def install(exe: str, f: Path | None = None, refresh=_refresh) -> str:
    f = f or menu_file()
    text, entries = _load(f)
    want = entry(exe)
    if entries.get(ENTRY_ID) == want:
        return f"already in the Omarchy menu: {f}"
    entries[ENTRY_ID] = want
    _write(f, text, entries)
    refresh()
    return f"added \"Send to TV (Cinemarchy)\" to the Omarchy menu ({f})"


def remove(f: Path | None = None, refresh=_refresh) -> str:
    f = f or menu_file()
    if not f.exists():
        return "not in the Omarchy menu"
    text, entries = _load(f)
    if ENTRY_ID not in entries:
        return "not in the Omarchy menu"
    del entries[ENTRY_ID]
    _write(f, text, entries)
    refresh()
    return f"removed \"Send to TV (Cinemarchy)\" from the Omarchy menu ({f})"


def status(f: Path | None = None) -> bool:
    f = f or menu_file()
    try:
        return ENTRY_ID in _load(f)[1]
    except (OSError, ValueError):
        return False


def run(args: list[str], exe: str, say=print) -> int:
    sub = args[0] if args else "status"
    try:
        if sub == "install":
            if not menu_file().parent.parent.is_dir():
                say(f"– no Omarchy config in {menu_file().parent.parent}: menu entry skipped")
                return 0
            say("✓ " + install(exe))
        elif sub in ("remove", "uninstall"):
            say("✓ " + remove())
        elif sub == "status":
            say(("✓ in the Omarchy menu: " if status() else "✗ not in the Omarchy menu: ") + str(menu_file()))
        else:
            say("Usage: omarchy-tv menu-entry install | remove | status")
            return 2
    except (OSError, ValueError) as e:
        say(f"✗ {e}")
        return 1
    return 0
