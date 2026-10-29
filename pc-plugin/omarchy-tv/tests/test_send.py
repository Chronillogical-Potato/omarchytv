import os, sys, tempfile, unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))
from omarchy_tv import send


class FakeClient:
    host = "192.168.1.50"
    def __init__(self):
        self.calls = []
    def request(self, m, p, body=None, **k):
        self.calls.append((m, p, body))
        return {"ok": True}


class SendTest(unittest.TestCase):
    def test_parse_args(self):
        o = send.parse_args(["https://x/a.m3u8", "--queue", "--sub", "s.srt", "--title", "T"])
        self.assertEqual((o["target"], o["queue"], o["sub"], o["title"]), ("https://x/a.m3u8", True, "s.srt", "T"))
        with self.assertRaises(send.SendError):
            send.parse_args(["a", "--bogus"])

    def test_subtitle_rules(self):
        self.assertIsNone(send.subtitle_source(None))
        self.assertEqual(send.subtitle_source("https://x/s.vtt"), {"url": "https://x/s.vtt"})
        with tempfile.TemporaryDirectory() as d:
            p = Path(d, "m.srt"); p.write_text("1\n00:00:01,000 --> 00:00:02,000\nhi\n")
            self.assertEqual(send.subtitle_source(str(p))["mime"], "application/x-subrip")
            bad = Path(d, "m.txt"); bad.write_text("x")
            with self.assertRaises(send.SendError):
                send.subtitle_source(str(bad))
            v = Path(d, "m.mkv"); v.write_bytes(b"0")
            self.assertEqual(send.auto_subtitle(str(v)), str(p))

    def test_queue_link_posts_to_queue(self):
        c = FakeClient(); out = []
        rc = send.run(["https://example.org/v.mp4", "--queue", "--title", "Demo"], lambda: c, out.append)
        self.assertEqual(rc, 0)
        self.assertEqual(c.calls[0][:2], ("POST", "/v1/cinemarchy/queue"))
        self.assertEqual(c.calls[0][2]["title"], "Demo")

    def test_queue_subtitles_url_and_inline(self):
        c = FakeClient()
        self.assertEqual(send.run(["https://example.org/v.mp4", "--queue", "--sub", "https://example.org/v.vtt"], lambda: c, lambda *_: None), 0)
        self.assertEqual(c.calls[0][2]["subtitle"], "https://example.org/v.vtt")
        with tempfile.TemporaryDirectory() as d:
            s = Path(d, "v.srt"); s.write_text("1\n00:00:01,000 --> 00:00:02,000\nciao\n")
            c = FakeClient(); out = []
            self.assertEqual(send.run(["https://example.org/v.mp4", "--queue", "--sub", str(s)], lambda: c, out.append), 0)
            body = c.calls[0][2]
            self.assertNotIn("subtitle", body)
            self.assertEqual((body["subtitleFormat"], "ciao" in body["subtitleData"]), ("srt", True))
            self.assertIn("with subtitles", out[-1])

    def test_queue_refuses_local_file(self):
        with tempfile.TemporaryDirectory() as d:
            v = Path(d, "clip.mp4"); v.write_bytes(b"0")
            c = FakeClient(); out = []
            self.assertEqual(send.run([str(v), "--queue"], lambda: c, out.append), 1)
            self.assertEqual(c.calls, [])

    def test_rejects_images_and_help(self):
        with tempfile.TemporaryDirectory() as d:
            i = Path(d, "a.png"); i.write_bytes(b"0")
            self.assertEqual(send.run([str(i)], FakeClient, lambda *_: None), 1)
        self.assertEqual(send.run(["--help"], FakeClient, lambda *_: None), 0)


if __name__ == "__main__":
    unittest.main()
