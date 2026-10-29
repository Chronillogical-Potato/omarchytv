"""v2.9 home widgets: "omarchy · pc" (CPU, RAM, now playing), "deploys · pc" (opt-in) and screensaver.txt.

Cheap reads only: /proc/stat delta, /proc/meminfo, `playerctl metadata` (if installed); on macOS the Mach CPU
tick counters and `vm_stat` instead of /proc, and Spotify or Music through `osascript` instead of playerctl.
Deploys only when the user configured a source in config.json:
  "deploys": {"command": ["my-deploys", "--json"]}   # argv, no shell; prints [{"name","status"}]
  "deploys": {"file": "~/.cache/deploys.json"}       # same JSON
status: green|yellow|red (or ok|building|failed). Nothing configured = no deploys widget on the TV.
"""

from __future__ import annotations

import ctypes
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

from . import config

SCREENSAVER = config.HOME / ".config/omarchy/branding/screensaver.txt"
_last_cpu: tuple[int, int] | None = None


def _cpu_ticks() -> tuple[int, int] | None:
    """(idle, total) CPU ticks since boot, all cores."""
    if sys.platform == "darwin":
        return _mach_cpu_ticks()
    try:
        f = [int(x) for x in Path("/proc/stat").read_text().split("\n", 1)[0].split()[1:]]
    except (OSError, ValueError):
        return None
    return f[3] + (f[4] if len(f) > 4 else 0), sum(f)


_mach = None


def _mach_cpu_ticks() -> tuple[int, int] | None:
    """macOS: host_statistics(HOST_CPU_LOAD_INFO) = user, system, idle, nice ticks (what top reads)."""
    global _mach
    try:
        if _mach is None:
            lib = ctypes.CDLL("/usr/lib/libSystem.dylib")
            lib.mach_host_self.restype = ctypes.c_uint
            _mach = lib, lib.mach_host_self()
        lib, host = _mach
        ticks, count = (ctypes.c_uint * 4)(), ctypes.c_uint(4)
        if lib.host_statistics(host, 3, ticks, ctypes.byref(count)) != 0:   # 3 = HOST_CPU_LOAD_INFO
            return None
    except (OSError, AttributeError):
        return None
    return ticks[2], sum(ticks)


def cpu_percent() -> int | None:
    global _last_cpu
    now = _cpu_ticks()
    if now is None:
        return None
    idle, total = now
    prev, _last_cpu = _last_cpu, now
    if not prev or total <= prev[1]:   # first call, no time passed, or a 32-bit Mach counter wrapped
        return None
    return max(0, min(100, round(100 * (1 - (idle - prev[0]) / (total - prev[1])))))


def ram_percent() -> int | None:
    if sys.platform == "darwin":
        return _vm_stat_ram()
    try:
        m = {l.split(":")[0]: int(l.split()[1]) for l in Path("/proc/meminfo").read_text().splitlines() if ":" in l}
        return max(0, min(100, round(100 * (1 - m["MemAvailable"] / m["MemTotal"]))))
    except (OSError, KeyError, ValueError, ZeroDivisionError, IndexError):
        return None


def _vm_stat_ram() -> int | None:
    """macOS: Activity Monitor's "Memory Used" (app memory + wired + compressed) over hw.memsize."""
    try:
        out = subprocess.run(["vm_stat"], capture_output=True, text=True, timeout=2).stdout
        total = int(subprocess.run(["sysctl", "-n", "hw.memsize"], capture_output=True, text=True, timeout=2).stdout)
        page = int(out.split("page size of ", 1)[1].split()[0])
        v = {k.strip().strip('"'): int(n.strip().rstrip(".")) for k, n in
             (l.rsplit(":", 1) for l in out.splitlines()[1:] if ":" in l)}
        used = v["Pages wired down"] + v["Pages occupied by compressor"] + v["Anonymous pages"] - v["Pages purgeable"]
        return max(0, min(100, round(100 * used * page / total)))
    except (OSError, subprocess.SubprocessError, KeyError, ValueError, IndexError, ZeroDivisionError):
        return None


def now_playing() -> dict | None:
    for argv in _player_commands():
        try:
            r = subprocess.run(argv, capture_output=True, text=True, timeout=2)
        except (OSError, subprocess.SubprocessError):
            continue
        parts = r.stdout.strip().split("\t")
        if r.returncode == 0 and len(parts) >= 3 and parts[0].lower() == "playing" and parts[2]:
            return {"artist": parts[1][:64], "title": parts[2][:96]}
    return None


def _player_commands() -> list[list[str]]:
    """Each prints "status<TAB>artist<TAB>title". macOS: Spotify, then Music, asked only while already running
    (telling an app something launches it, and a missing one makes osascript ask where it is)."""
    if sys.platform == "darwin":
        tell = ('tell application "%s" to (player state as text) & tab & artist of current track'
                ' & tab & name of current track')
        return [["osascript", "-e", tell % app] for app in ("Spotify", "Music") if _running(app)]
    if shutil.which("playerctl"):
        return [["playerctl", "metadata", "--format", "{{status}}\t{{artist}}\t{{title}}"]]
    return []


def _running(app: str) -> bool:
    try:
        return subprocess.run(["pgrep", "-xq", app], timeout=2).returncode == 0
    except (OSError, subprocess.SubprocessError):
        return False


def deploys(cfg: dict) -> list | None:
    src = cfg.get("deploys")
    if not isinstance(src, dict):
        return None
    try:
        if isinstance(src.get("command"), list) and src["command"]:
            r = subprocess.run([str(x) for x in src["command"]], capture_output=True, text=True, timeout=10)
            data = json.loads(r.stdout or "[]")
        elif src.get("file"):
            data = json.loads(Path(os.path.expanduser(str(src["file"]))).read_text())
        else:
            return None
    except (OSError, ValueError, subprocess.SubprocessError):
        return None
    out = []
    for d in data if isinstance(data, list) else []:
        st = str(d.get("status", "")).lower() if isinstance(d, dict) else ""
        if st in ("green", "yellow", "red", "ok", "building", "failed"):
            out.append({"name": str(d.get("name", ""))[:24], "status": st})
    return out[:8]


def payload(cfg: dict) -> dict:
    p: dict = {}
    c, r = cpu_percent(), ram_percent()
    if c is not None:
        p["cpu"] = c
    if r is not None:
        p["ram"] = r
    np = now_playing()
    if np:
        p["nowPlaying"] = np
    d = deploys(cfg)
    if d is not None:
        p["deploys"] = d
    return p


def screensaver_text() -> tuple[str, float]:
    """(text, mtime) of ~/.config/omarchy/branding/screensaver.txt; ("", 0) when missing."""
    try:
        return SCREENSAVER.read_text(errors="replace")[:16384], SCREENSAVER.stat().st_mtime
    except OSError:
        return "", 0.0
