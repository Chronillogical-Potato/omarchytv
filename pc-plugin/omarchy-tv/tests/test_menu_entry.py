import os, subprocess, sys, tempfile, unittest
from pathlib import Path
from unittest import mock
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))
from omarchy_tv import menu_entry, send

ROOT = Path(__file__).resolve().parents[1]
SAMPLE = """// Omarchy menu extensions
// Add entries keyed by dotted id.
{
}
"""


class MenuEntryTest(unittest.TestCase):
    def setUp(self):
        self.d = tempfile.TemporaryDirectory()
        self.f = Path(self.d.name, "omarchy", "extensions", "omarchy-menu.jsonc")
        self.refreshed = []

    def tearDown(self):
        self.d.cleanup()

    def refresh(self):
        self.refreshed.append(1)

    def test_parse_jsonc_matches_omarchy_dialect(self):
        self.assertEqual(menu_entry.parse_jsonc('// c\n{\n  "a": {"label": "A"},\n}\n'), {"a": {"label": "A"}})
        self.assertEqual(menu_entry.parse_jsonc(SAMPLE), {})
        self.assertEqual(menu_entry.parse_jsonc(""), {})
        with self.assertRaises(ValueError):
            menu_entry.parse_jsonc('{"a": 1} // inline comments break Omarchy too')

    def test_install_on_sample_keeps_header_and_adds_root_action(self):
        self.f.parent.mkdir(parents=True); self.f.write_text(SAMPLE)
        menu_entry.install("/opt/omarchy-tv/bin/omarchy-tv", self.f, refresh=self.refresh)
        text = self.f.read_text()
        self.assertTrue(text.startswith("// Omarchy menu extensions"))
        e = menu_entry.parse_jsonc(text)["omarchytv-send"]
        self.assertEqual(e["label"], "Send to TV (Cinemarchy)")
        self.assertEqual(e["action"], "/opt/omarchy-tv/bin/omarchy-tv send --pick")
        self.assertNotIn(".", "omarchytv-send")  # no dot = root menu
        self.assertEqual(self.f.with_name(self.f.name + ".bak").read_text(), SAMPLE)
        self.assertEqual(self.refreshed, [1])

    def test_install_is_idempotent_and_keeps_other_entries(self):
        self.f.parent.mkdir(parents=True)
        self.f.write_text('{"personal": {"label": "Personal"},\n "personal.notes": {"label": "Notes", "action": "x"},}\n')
        menu_entry.install("/a b/omarchy-tv", self.f, refresh=self.refresh)
        msg = menu_entry.install("/a b/omarchy-tv", self.f, refresh=self.refresh)
        self.assertIn("already", msg)
        data = menu_entry.parse_jsonc(self.f.read_text())
        self.assertEqual(list(data), ["personal", "personal.notes", "omarchytv-send"])
        self.assertEqual(data["omarchytv-send"]["action"], "'/a b/omarchy-tv' send --pick")
        self.assertEqual(len(self.refreshed), 1)

    def test_remove_takes_only_our_entry(self):
        self.f.parent.mkdir(parents=True); self.f.write_text('{"personal": {"label": "P"}}')
        menu_entry.install("omarchy-tv", self.f, refresh=self.refresh)
        self.assertTrue(menu_entry.status(self.f))
        menu_entry.remove(self.f, refresh=self.refresh)
        self.assertEqual(menu_entry.parse_jsonc(self.f.read_text()), {"personal": {"label": "P"}})
        self.assertFalse(menu_entry.status(self.f))
        self.assertIn("not in", menu_entry.remove(self.f, refresh=self.refresh))
        self.assertIn("not in", menu_entry.remove(Path(self.d.name, "missing.jsonc"), refresh=self.refresh))

    def test_broken_file_is_never_touched(self):
        self.f.parent.mkdir(parents=True); bad = '{"x": 1,, }'; self.f.write_text(bad)
        with self.assertRaises(ValueError):
            menu_entry.install("omarchy-tv", self.f, refresh=self.refresh)
        self.assertEqual(self.f.read_text(), bad)
        self.assertFalse(self.f.with_name(self.f.name + ".bak").exists())

    def test_cli_and_install_script_round_trip(self):
        home = Path(self.d.name)
        (home / "omarchy").mkdir()
        env = dict(os.environ, HOME=str(home), XDG_CONFIG_HOME=str(home), XDG_DATA_HOME=str(home / "data"),
                   PATH="/usr/bin:/bin")
        env.pop("WAYLAND_DISPLAY", None)
        r = subprocess.run(["sh", str(ROOT / "share/install-file-manager.sh")], env=env, capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stderr + r.stdout)
        data = menu_entry.parse_jsonc(self.f.read_text())
        self.assertTrue(data["omarchytv-send"]["action"].endswith("bin/omarchy-tv send --pick"))
        self.assertTrue((home / "data/applications/omarchy-tv-send.desktop").exists())
        r = subprocess.run(["sh", str(ROOT / "share/install-file-manager.sh"), "--uninstall"], env=env,
                           capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stderr + r.stdout)
        self.assertNotIn("omarchytv-send", menu_entry.parse_jsonc(self.f.read_text()))
        self.assertFalse((home / "data/applications/omarchy-tv-send.desktop").exists())

    def test_cli_skips_without_omarchy(self):
        out = []
        with mock.patch.dict(os.environ, {"XDG_CONFIG_HOME": str(Path(self.d.name, "none"))}):
            self.assertEqual(menu_entry.run(["install"], "omarchy-tv", say=out.append), 0)
        self.assertIn("skipped", out[0])
        self.assertFalse(Path(self.d.name, "none").exists())


class PickAndSubtitleTest(unittest.TestCase):
    def test_zenity_dialog_used_first(self):
        calls = []
        def runner(argv, **k):
            calls.append(argv)
            return subprocess.CompletedProcess(argv, 0, "/home/m/Videos/film.mkv\n", "")
        p = send.pick_file(which=lambda n: "/usr/bin/" + n if n in ("zenity", "kdialog") else None, runner=runner)
        self.assertEqual(p, "/home/m/Videos/film.mkv")
        self.assertEqual(calls[0][0], "/usr/bin/zenity")

    def test_dialog_cancel(self):
        r = lambda argv, **k: subprocess.CompletedProcess(argv, 1, "", "")
        self.assertIsNone(send.pick_file(which=lambda n: "/usr/bin/kdialog" if n == "kdialog" else None, runner=r))

    def test_menu_fallback_lists_videos(self):
        with tempfile.TemporaryDirectory() as d:
            v = Path(d, "Show", "ep1.mkv"); v.parent.mkdir(); v.write_bytes(b"0")
            Path(d, "notes.txt").write_text("x")
            seen = {}
            def choose(prompt, items):
                seen["items"] = items
                return items[0]
            p = send.pick_file(choose=choose, which=lambda n: None, roots=[Path(d)])
            self.assertEqual(p, str(v))
            self.assertEqual(len(seen["items"]), 1)
            self.assertIsNone(send.pick_file(choose=choose, which=lambda n: None, roots=[Path(d, "nope")]))

    def test_auto_subtitle_finds_ass_and_converts(self):
        ass = ("[Script Info]\nTitle: x\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
               "Dialogue: 0,0:00:03.50,0:00:05.00,Default,,0,0,0,,{\\i1}Second{\\i0}\\Nline, with comma\n"
               "Dialogue: 0,0:00:01.00,0:00:02.25,Default,,0,0,0,,First\n")
        with tempfile.TemporaryDirectory() as d:
            v = Path(d, "film.mkv"); v.write_bytes(b"0")
            a = Path(d, "film.ass"); a.write_text(ass)
            self.assertEqual(send.auto_subtitle(str(v)), str(a))
            src = send.subtitle_source(str(a))
            self.assertEqual(src["mime"], "application/x-subrip")
            srt = Path(src["path"]).read_text()
            self.assertEqual(srt, "1\n00:00:01,000 --> 00:00:02,250\nFirst\n\n"
                                  "2\n00:00:03,500 --> 00:00:05,000\nSecond\nline, with comma\n\n")
            Path(d, "film.srt").write_text("1\n00:00:01,000 --> 00:00:02,000\nhi\n")
            self.assertTrue(send.auto_subtitle(str(v)).endswith("film.srt"))  # .srt still wins

    def test_pick_cancel_and_pick_sends(self):
        out = []
        self.assertEqual(send.run(["--pick"], lambda: self.fail("no client on cancel"), out.append, picker=lambda: None), 0)
        self.assertEqual(out, [])

        class C:
            host = "192.168.1.50"
            def __init__(s): s.calls = []
            def request(s, m, p, body=None, **k): s.calls.append((m, p, body)); return {"ok": True}
        c, notes = C(), []
        rc = send.run(["--pick", "--queue"], lambda: c, out.append, picker=lambda: "https://example.org/v.mp4",
                      notify=notes.append)
        self.assertEqual(rc, 0)
        self.assertEqual(c.calls[0][:2], ("POST", "/v1/cinemarchy/queue"))
        self.assertTrue(notes and notes[0].startswith("＋"))


if __name__ == "__main__":
    unittest.main()
