"""omarchy-tv send: stream a local file or a link from this PC to Cinemarchy on the paired TV.

Local files are served over the LAN by omarchy-link's one-time FileServer (TV IP only, random URL, closed
after playback); links go straight to the TV. Optional subtitles (.srt / .vtt, local or http) and an
"up next" queue mode. Every send is recorded on the TV under Cinemarchy > omarchy-link://pc > From PC.
"""
from __future__ import annotations

import os
import urllib.parse
from pathlib import Path

SUB_MIME = {".srt": "application/x-subrip", ".vtt": "text/vtt"}
USAGE = ("Usage: omarchy-tv send <FILE|URL> [--sub FILE.srt|.vtt|URL] [--queue] [--title TITLE] [--at MM:SS]\n"
         "  Streams a local video/audio file over the LAN (TV only, one-time URL) or sends a link to Cinemarchy.\n"
         "  --queue adds the link to Cinemarchy's Up-Next queue instead of playing now (links only).")


class SendError(Exception):
    pass


def parse_args(args: list[str]) -> dict:
    o = {"target": None, "sub": None, "queue": False, "title": None, "at": None, "help": False}
    i = 0
    while i < len(args):
        a = args[i]
        if a in ("-h", "--help"):
            o["help"] = True
        elif a == "--queue":
            o["queue"] = True
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
    if ext not in SUB_MIME:
        raise SendError(f"{p.name}: subtitles must be .srt or .vtt")
    if not p.is_file():
        raise SendError(f"{sub}: no such subtitle file")
    if p.stat().st_size > 4 << 20:
        raise SendError(f"{p.name}: subtitle file larger than 4 MiB")
    return {"path": str(p.resolve()), "mime": SUB_MIME[ext]}


def auto_subtitle(target: str) -> str | None:
    """movie.mkv -> movie.srt / movie.vtt / movie.en.srt next to it, if present."""
    if is_link(target):
        return None
    p = Path(target).expanduser()
    for cand in (p.with_suffix(".srt"), p.with_suffix(".vtt"), p.with_suffix(".en.srt"), p.with_suffix(".it.srt")):
        if cand.is_file():
            return str(cand)
    return None


def queue_body(item: dict) -> dict:
    body = {"title": item.get("title") or item.get("url"), "url": item["url"]}
    if item.get("subtitle"):
        body["subtitle"] = item["subtitle"]
    return body


def run(args: list[str], client_factory, out=print) -> int:
    from omarchy_link import cast
    try:
        o = parse_args(args)
    except SendError as e:
        out(str(e))
        return 2
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
