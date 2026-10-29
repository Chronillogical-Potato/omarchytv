# Copyright (c) 2026 Marco Porro (x.com/MarcoPorro · www.marcoporro.it, MIT)
"""`omarchy-tv adb-pair HOST:PORT CODE [--connect HOST:PORT]`: Android 11+ Wireless Debugging pairing.

On the TV: Settings > Developer options > Wireless debugging > Pair device with pairing code. It shows a
6-digit code and a HOST:PAIRPORT (different from the HOST:PORT used afterwards to connect). This wraps the
platform-tools `adb pair` (which does the SPAKE2 + TLS handshake itself), then optionally `adb connect`.
"""
from __future__ import annotations

import re
import shutil
import subprocess

HOSTPORT = re.compile(r"^(\[[0-9a-fA-F:]+\]|[A-Za-z0-9.-]{1,253}):(\d{1,5})$")
CODE = re.compile(r"^\d{6}$")
USAGE = "Usage: omarchy-tv adb-pair HOST:PAIRPORT CODE [--connect HOST:PORT]"


def _hp(s: str) -> bool:
    m = HOSTPORT.match(s or "")
    return bool(m) and 0 < int(m.group(2)) < 65536


def run(args, say=print, runner=subprocess.run, which=shutil.which) -> int:
    args = list(args)
    conn = None
    if "--connect" in args:
        i = args.index("--connect")
        conn = args[i + 1] if i + 1 < len(args) else ""
        del args[i:i + 2]
    if len(args) != 2 or not _hp(args[0]) or not CODE.match(args[1]) or (conn is not None and not _hp(conn)):
        say(USAGE)
        return 2
    if not which("adb"):
        say("✗ adb not found: install android-tools (Arch: sudo pacman -S android-tools); adb pair needs platform-tools 30+")
        return 1
    r = runner(["adb", "pair", args[0], args[1]], capture_output=True, text=True, timeout=60)
    text = ((r.stdout or "") + (r.stderr or "")).strip()
    if r.returncode != 0 or "Successfully paired" not in text:
        say(f"✗ pairing failed: {text or 'no output'}")
        return 1
    say(f"✓ {text.splitlines()[0]}")
    if conn:
        r = runner(["adb", "connect", conn], capture_output=True, text=True, timeout=30)
        out = ((r.stdout or "") + (r.stderr or "")).strip()
        if r.returncode != 0 or "connected" not in out or "failed" in out:
            say(f"✗ connect failed: {out}")
            return 1
        say(f"✓ {out}")
    return 0
