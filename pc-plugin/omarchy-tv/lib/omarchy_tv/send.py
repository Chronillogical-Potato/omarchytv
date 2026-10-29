"""omarchy-tv send: stream a local file or a link from this PC to Cinemarchy on the paired TV.

Local files are served over the LAN by omarchy-link's one-time FileServer (TV IP only, random URL, closed
after playback); links go straight to the TV. Optional subtitles (.srt / .vtt, local or http) and an
"up next" queue mode. Every send is recorded on the TV under Cinemarchy > omarchy-link://pc > From PC.
"""
from __future__ import annotations

import os
import re
import shutil
import subprocess
import tempfile
import urllib.parse
from pathlib import Path

SUB_MIME = {".srt": "application/x-subrip", ".vtt": "text/vtt"}
ASS_EXT = (".ass", ".ssa")   # the TV players read SRT/VTT only: converted to SRT on the PC before sending
VIDEO_EXT = (".mp4", ".mkv", ".webm", ".m4v", ".mov", ".avi", ".mpg", ".mpeg", ".ts", ".m2ts", ".wmv", ".flv")
USAGE = ("Usage: omarchy-tv send <FILE|URL> [--sub FILE.srt|.vtt|.ass|URL] [--queue] [--title TITLE] [--at MM:SS]\n"
         "       omarchy-tv send --pick   (file picker; used by the Omarchy menu entry)\n"
         "  Streams a local video/audio file over the LAN (TV only, one-time URL) or sends a link to Cinemarchy.\n"
         "  --queue adds the link to Cinemarchy's Up-Next queue instead of playing now (links only).")


class SendError(Exception):
    pass


def parse_args(args: list[str]) -> dict:
    o = {"target": None, "sub": None, "queue": False, "title": None, "at": None, "help": False, "pick": False}
    i = 0
    while i < len(args):
        a = args[i]
        if a in ("-h", "--help"):
            o["help"] = True
        elif a == "--queue":
            o["queue"] = True
        elif a == "--pick":
            o["pick"] = True
        elif a in ("--sub", "--subs", "--subtitle", "--title", "--at") and i + 1 < len(args):
            o[{"--subs": "sub", "--subtitle": "sub"}.get(a, a[2:])] = args[i + 1]
            i += 1
        elif not a.startswith("-") and o["target"] is None:
            o["target"] = a
        else:
            raise SendError(f"unknown option {a}\n{USAGE}")
        i += 1
    return o


def is_link(s: str) -> bool:
    return urllib.parse.urlparse(s).scheme in ("http", "https")


def subtitle_source(sub: str | None) -> dict | None:
    """None, {"url": ...} for a link, or {"path", "mime"} for a local .srt/.vtt."""
    if not sub:
        return None
    if sub.startswith("file://"):
        sub = urllib.parse.unquote(urllib.parse.urlparse(sub).path)
    if is_link(sub):
        return {"url": sub}
    p = Path(sub).expanduser()
    ext = p.suffix.lower()
    if ext in ASS_EXT and p.is_file():
        p, ext = ass_to_srt_file(p), ".srt"
    if ext not in SUB_MIME:
        raise SendError(f"{p.name}: subtitles must be .srt, .vtt or .ass")
    if not p.is_file():
        raise SendError(f"{sub}: no such subtitle file")
    if p.stat().st_size > 4 << 20:
        raise SendError(f"{p.name}: subtitle file larger than 4 MiB")
    return {"path": str(p.resolve()), "mime": SUB_MIME[ext]}


def auto_subtitle(target: str) -> str | None:
    """movie.mkv -> movie.srt / .vtt / .ass / .ssa / movie.en.srt / movie.it.srt next to it, if present."""
    if is_link(target):
        return None
    p = Path(target).expanduser()
    for cand in (*(p.with_suffix(e) for e in (".srt", ".vtt", ".ass", ".ssa")),
                 p.with_suffix(".en.srt"), p.with_suffix(".it.srt")):
        if cand.is_file():
            return str(cand)
    return None


def _srt_time(t: str) -> str:
    h, m, rest = t.strip().split(":")
    sec, _, cs = rest.partition(".")
    return f"{int(h):02d}:{int(m):02d}:{int(sec):02d},{(cs + '00')[:2]}0"


def ass_to_srt(text: str) -> str:
    """Dialogue lines of an ASS/SSA [Events] section -> SRT (style tags dropped, \\N -> new line)."""
    fmt, cues = None, []
    for line in text.splitlines():
        line = line.strip()
        if line.lower().startswith("format:") and fmt is None:
            cols = [c.strip().lower() for c in line[7:].split(",")]
            if "text" in cols and "start" in cols:
                fmt = cols
        elif line.lower().startswith("dialogue:") and fmt:
            parts = line[9:].split(",", len(fmt) - 1)
            if len(parts) < len(fmt):
                continue
            f = dict(zip(fmt, (x.strip() if k != "text" else x for k, x in zip(fmt, parts))))
            body = re.sub(r"\{[^}]*\}", "", f["text"]).replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ").strip()
            try:
                if body:
                    cues.append((_srt_time(f["start"]), _srt_time(f["end"]), body))
            except ValueError:
                continue
    cues.sort()
    return "".join(f"{i}\n{a} --> {b}\n{t}\n\n" for i, (a, b, t) in enumerate(cues, 1))


def ass_to_srt_file(p: Path) -> Path:
    srt = ass_to_srt(p.read_text(encoding="utf-8-sig", errors="replace"))
    if not srt:
        raise SendError(f"{p.name}: no dialogue lines found")
    d = Path(tempfile.mkdtemp(prefix="omarchy-tv-sub-"))
    out = d / (p.stem + ".srt")
    out.write_text(srt, encoding="utf-8")
    return out


def _video_candidates(roots=None, limit=200) -> list[Path]:
    roots = roots or [Path.home() / "Videos", Path.home() / "Downloads", Path.home() / "Movies"]
    found = []
    for r in roots:
        if not r.is_dir():
            continue
        for dirpath, dirs, files in os.walk(r):
            if dirpath[len(str(r)):].count(os.sep) >= 2:
                dirs[:] = []
            dirs[:] = [x for x in dirs if not x.startswith(".")]
            found += [Path(dirpath, f) for f in files if f.lower().endswith(VIDEO_EXT)]
    found.sort(key=lambda p: p.stat().st_mtime if p.exists() else 0, reverse=True)
    return found[:limit]


def pick_file(choose=None, which=shutil.which, runner=subprocess.run, roots=None) -> str | None:
    """A graphical file dialog when one is installed (zenity, kdialog, yad), else a list of the newest videos in
    ~/Videos, ~/Downloads and ~/Movies in the Omarchy menu. None = cancelled."""
    pattern = " ".join("*" + e for e in VIDEO_EXT)
    start = str(Path.home() / "Videos") + os.sep
    dialogs = (("zenity", ["--file-selection", "--title=Send to TV (Cinemarchy)", f"--filename={start}",
                           f"--file-filter=Videos | {pattern}", "--file-filter=All files | *"]),
               ("kdialog", ["--getopenfilename", start, f"Videos ({pattern})", "--title", "Send to TV (Cinemarchy)"]),
               ("yad", ["--file", "--title=Send to TV (Cinemarchy)", f"--filename={start}",
                        f"--file-filter=Videos | {pattern}"]))
    for name, args in dialogs:
        exe = which(name)
        if exe:
            r = runner([exe, *args], text=True, capture_output=True)
            path = (r.stdout or "").strip().splitlines()
            return path[0] if r.returncode == 0 and path else None
    vids = _video_candidates(roots)
    if not vids or choose is None:
        return None
    home = str(Path.home())
    labels = {str(v).replace(home, "~", 1): str(v) for v in vids}
    sel = choose("Send to TV…", list(labels))
    return labels.get(sel or "")


def queue_body(item: dict) -> dict:
    body = {"title": item.get("title") or item.get("url"), "url": item["url"]}
    if item.get("subtitle"):
        body["subtitle"] = item["subtitle"]
    return body


def run(args: list[str], client_factory, out=print, picker=None, notify=None) -> int:
    from omarchy_link import cast
    try:
        o = parse_args(args)
    except SendError as e:
        out(str(e))
        return 2
    if o["pick"] and not o["target"]:
        o["target"] = (picker or pick_file)()
        if not o["target"]:
            return 0   # cancelled
        if notify:     # started from the Omarchy menu: nobody sees stdout, so results also go to notifications
            base = out
            def out(msg, _b=base):
                _b(msg)
                if msg[:1] in ("▶", "✗", "＋"):
                    notify(msg)
    if o["help"] or not o["target"]:
        out(USAGE)
        return 0 if o["help"] else 2
    try:
        item = cast.classify(o["target"], title=o["title"], position=o["at"])
        sub = subtitle_source(o["sub"] or auto_subtitle(o["target"]))
    except (cast.CastError, SendError) as e:
        out(f"✗ {e}")
        return 1
    if item["kind"] not in ("video", "audio", "url", "youtube"):
        out(f"✗ {o['target']}: Cinemarchy plays video and audio (use omarchy-link cast for {item['kind']})")
        return 1
    if item["kind"] == "url":
        item["kind"] = "video"   # a bare stream link (HLS/DASH/progressive): let the TV player try it
    c = client_factory()
    sub_server = None
    try:
        if sub and sub.get("path") and not o["queue"]:
            sub_server = cast.FileServer(c.host, Path(sub["path"]).name, sub["mime"], path=sub["path"],
                                         idle=4 * 3600, lifetime=6 * 3600)
            item["subtitle"] = sub_server.url
        elif sub and sub.get("url"):
            item["subtitle"] = sub["url"]
        if o["queue"]:
            if item.get("path"):
                out("✗ --queue needs a link: local files are streamed live from this PC only while playing")
                return 1
            body = queue_body(item)
            if sub and sub.get("path"):   # the PC will not be serving it when the item plays: send it inline
                body["subtitleData"] = Path(sub["path"]).read_bytes()[:1024 * 1024].decode("utf-8", "replace")
                body["subtitleFormat"] = "vtt" if sub["path"].lower().endswith(".vtt") else "srt"
            c.request("POST", "/v1/cinemarchy/queue", body)
            out(f"＋ Queued on Cinemarchy (queue://up-next): {item['title']}" + (" (with subtitles)" if sub else ""))
            return 0
        res = cast.via_link(c, item, serve_ok=True, wait=bool(item.get("path")), out=out)
        out(f"▶ Sent to Cinemarchy: {item['title']}" + (" (with subtitles)" if item.get("subtitle") else "")
            + f" · {res.get('state', 'opening')}")
        return 0 if res.get("state") not in ("error", "failed") else 1
    except cast.CastError as e:
        out(f"✗ {e}")
        return 1
    finally:
        if sub_server:
            sub_server.close()
