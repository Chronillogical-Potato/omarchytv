"""omarchy-tv screen extend: use the TV as a second (extended) display on Hyprland (2.13, experimental).

1. `hyprctl output create headless` adds a virtual monitor (Hyprland names it, e.g. HEADLESS-2).
2. `hyprctl keyword monitor NAME,WxH@FPS,auto,1` sizes it and places it next to your screens.
3. `omarchy-link cast --screen` streams only that output (OMARCHY_LINK_SCREEN_OUTPUT=NAME).
`omarchy-tv screen stop` stops the stream and removes the virtual monitor again.
Expect ~0.3-1 s latency (H.264 over the LAN): good for slides, dashboards and video, not for gaming.
"""
import json, os, re, subprocess

RES_RE = re.compile(r"^(\d{3,4})x(\d{3,4})(?:@(\d{2,3}))?$")


def state_file() -> str:
    base = os.environ.get("XDG_CACHE_HOME") or os.path.expanduser("~/.cache")
    return os.path.join(base, "omarchy-tv", "extend-output")


def parse_mode(s: str | None) -> str:
    m = RES_RE.match(s or "1920x1080@30")
    if not m:
        raise ValueError("mode must look like 1920x1080 or 1920x1080@30")
    w, h, hz = int(m[1]), int(m[2]), int(m[3] or 30)
    if not (640 <= w <= 3840 and 480 <= h <= 2160 and 10 <= hz <= 60):
        raise ValueError("mode out of range (640x480..3840x2160, 10-60 Hz)")
    return f"{w}x{h}@{hz}"


def monitors(runner) -> set[str]:
    r = runner(["hyprctl", "monitors", "all", "-j"], capture_output=True, text=True)
    try:
        return {m.get("name") for m in json.loads(r.stdout or "[]")}
    except ValueError:
        return set()


def start(mode=None, audio=False, runner=subprocess.run, say=print) -> int:
    mode = parse_mode(mode)
    before = monitors(runner)
    runner(["hyprctl", "output", "create", "headless"], capture_output=True, text=True)
    new = sorted(monitors(runner) - before)
    if not new:
        say("✗ Hyprland did not create a headless output (needs Hyprland with 'hyprctl output')"); return 1
    name = new[0]
    runner(["hyprctl", "keyword", "monitor", f"{name},{mode},auto,1"], capture_output=True, text=True)
    os.makedirs(os.path.dirname(state_file()), exist_ok=True)
    with open(state_file(), "w") as f:
        f.write(name)
    env = dict(os.environ, OMARCHY_LINK_SCREEN_OUTPUT=name)
    if audio:
        env["OMARCHY_LINK_SCREEN_AUDIO"] = "1"
    say(f"✓ extended display {name} ({mode}); move windows to it with Super+Shift+arrow or 'hyprctl dispatch movewindow mon:{name}'")
    return runner(["omarchy-link", "cast", "--screen"], env=env).returncode


def stop(runner=subprocess.run, say=print) -> None:
    try:
        with open(state_file()) as f:
            name = f.read().strip()
    except OSError:
        return
    if re.match(r"^[A-Za-z0-9_-]{1,32}$", name):
        runner(["hyprctl", "output", "remove", name], capture_output=True, text=True)
        say(f"removed virtual display {name}")
    os.remove(state_file())
