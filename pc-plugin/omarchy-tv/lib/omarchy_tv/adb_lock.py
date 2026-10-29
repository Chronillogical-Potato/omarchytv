"""omarchy-tv adb-lock: close the TV's network ADB port (5555) when you are done with it.

`adb usb` makes adbd stop listening on TCP until "Network debugging" / "ADB over network" is
switched on again in the TV's Developer options. No root needed. Anyone on the LAN could
otherwise try to connect to an open :5555 (the TV still asks to approve unknown keys).
"""
import subprocess

USAGE = "omarchy-tv adb-lock --tv IP:5555 [--yes]   (re-enable later in Developer options on the TV)"


def lock_cmd(serial: str) -> list[str]:
    return ["adb", "-s", serial, "usb"]


def run(args, runner=subprocess.run, say=print, confirm=None) -> int:
    serial = args[args.index("--tv") + 1] if "--tv" in args and args.index("--tv") + 1 < len(args) else None
    if not serial:
        say(USAGE); return 2
    if "--yes" not in args and confirm is not None and not confirm(
            f"Close network ADB on {serial}? omarchy-tv update/wg will stop working until you re-enable it on the TV."):
        say("cancelled"); return 1
    if ":" in serial:
        runner(["adb", "connect", serial], capture_output=True, timeout=15)
    r = runner(lock_cmd(serial))
    if r.returncode == 0:
        say(f"✓ network ADB closed on {serial}. Re-enable: TV Settings → Developer options → Network debugging.")
    return r.returncode
