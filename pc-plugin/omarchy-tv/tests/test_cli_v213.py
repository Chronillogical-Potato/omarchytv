"""2.13.0-beta CLI checks: snippets and iptv (no TV needed; the link client is faked)."""
import contextlib, importlib.machinery, importlib.util, io, os, unittest

BIN = os.path.join(os.path.dirname(__file__), "..", "bin", "omarchy-tv")
loader = importlib.machinery.SourceFileLoader("omarchy_tv_cli", BIN)
spec = importlib.util.spec_from_loader("omarchy_tv_cli", loader)
cli = importlib.util.module_from_spec(spec)
loader.exec_module(cli)


class FakeClient:
    def __init__(self, state):
        self.state, self.puts = state, []
    def request(self, method, path, body=None):
        if method == "PUT":
            self.puts.append(body); self.state = body
        return self.state


class TestCliV213(unittest.TestCase):
    def setUp(self):
        self.out = []
        self._say, cli.ui.say = cli.ui.say, self.out.append
    def tearDown(self):
        cli.ui.say = self._say

    def use(self, state):
        fc = FakeClient(state); cli.client = lambda: fc; return fc

    def test_snippets_lists_chapters(self):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            self.assertEqual(cli.cmd_snippets([]), 0)
        lines = buf.getvalue().strip().splitlines()
        self.assertEqual(len(lines), 5)
        self.assertTrue(lines[0].startswith("01:15"))

    def test_iptv_add_dedupes_and_names(self):
        fc = self.use({"playlists": [{"name": "a", "url": "https://x/a.m3u"}], "epg": ""})
        self.assertEqual(cli.cmd_iptv(["add", "https://x/a.m3u", "--name", "A2"]), 0)
        self.assertEqual(fc.puts[-1]["playlists"], [{"name": "A2", "url": "https://x/a.m3u"}])
        cli.cmd_iptv(["add", "https://host.tv/b.m3u8"])
        self.assertEqual(fc.puts[-1]["playlists"][-1]["name"], "host.tv")

    def test_iptv_epg_only_and_remove(self):
        fc = self.use({"playlists": [{"name": "A", "url": "https://x/a"}], "epg": ""})
        cli.cmd_iptv(["add", "--epg", "https://x/epg.xml"])
        self.assertEqual(fc.puts[-1]["epg"], "https://x/epg.xml")
        cli.cmd_iptv(["remove", "A"])
        self.assertEqual(fc.puts[-1]["playlists"], [])

    def test_iptv_add_without_url_fails(self):
        self.use({"playlists": [], "epg": ""})
        self.assertEqual(cli.cmd_iptv(["add"]), 1)


if __name__ == "__main__":
    unittest.main()
