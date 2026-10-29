import json, os, sys, tempfile, unittest
from unittest import mock
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from omarchy_tv import extend


class FakeHypr:
    def __init__(self):
        self.mons, self.calls = ["eDP-1"], []

    def __call__(self, cmd, **kw):
        self.calls.append((cmd, kw.get("env", {}).get("OMARCHY_LINK_SCREEN_OUTPUT")))
        out = ""
        if cmd[:2] == ["hyprctl", "monitors"]:
            out = json.dumps([{"name": n} for n in self.mons])
        elif cmd[:4] == ["hyprctl", "output", "create", "headless"]:
            self.mons.append("HEADLESS-2")
        elif cmd[:3] == ["hyprctl", "output", "remove"]:
            self.mons.remove(cmd[3])
        return type("P", (), {"returncode": 0, "stdout": out})()


class TestExtend(unittest.TestCase):
    def setUp(self):
        d = tempfile.mkdtemp()
        p = mock.patch.dict(os.environ, {"XDG_CACHE_HOME": d}); p.start(); self.addCleanup(p.stop)

    def test_mode(self):
        self.assertEqual(extend.parse_mode(None), "1920x1080@30")
        self.assertEqual(extend.parse_mode("1280x720"), "1280x720@30")
        for bad in ("4k", "8000x8000", "1920x1080@144", "1920x1080;rm"):
            with self.assertRaises(ValueError):
                extend.parse_mode(bad)

    def test_start_and_stop(self):
        h = FakeHypr()
        self.assertEqual(extend.start("1280x720@30", runner=h, say=lambda s: None), 0)
        self.assertIn((["hyprctl", "keyword", "monitor", "HEADLESS-2,1280x720@30,auto,1"], None), h.calls)
        self.assertEqual(h.calls[-1], (["omarchy-link", "cast", "--screen"], "HEADLESS-2"))
        extend.stop(runner=h, say=lambda s: None)
        self.assertEqual(h.mons, ["eDP-1"])
        self.assertFalse(os.path.exists(extend.state_file()))

    def test_no_headless_support(self):
        h = FakeHypr()
        h.__class__ = type("NoCreate", (FakeHypr,), {"__call__": lambda self, cmd, **kw: type("P", (), {"returncode": 0, "stdout": "[]"})()})
        self.assertEqual(extend.start(None, runner=h, say=lambda s: None), 1)


if __name__ == "__main__":
    unittest.main()
