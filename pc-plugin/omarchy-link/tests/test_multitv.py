"""2.13 multi-device theme sync: several paired TVs, primary first, per-TV sync fingerprints."""
import os, sys, tempfile, unittest
from pathlib import Path
from unittest import mock
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from omarchy_link import config


def tv(i):
    return {"id": f"tv{i}", "name": f"TV {i}", "address": f"192.168.1.{i}", "token": "dG9r"}


class TestMultiTv(unittest.TestCase):
    def setUp(self):
        d = tempfile.mkdtemp()
        p = mock.patch.object(config, "PAIRING_FILE", Path(d) / "pairing.json")
        p.start(); self.addCleanup(p.stop)

    def test_pairing_a_second_tv_keeps_the_first(self):
        p = config.Pairing.load()
        p.set_tv(tv(1)); p.set_tv(tv(2))
        p = config.Pairing.load()
        self.assertEqual([t["id"] for t in p.tvs], ["tv2", "tv1"])
        p.set_tv(tv(1))
        self.assertEqual([t["id"] for t in config.Pairing.load().tvs], ["tv1", "tv2"])

    def test_forget_promotes_next(self):
        p = config.Pairing.load()
        p.set_tv(tv(1)); p.set_tv(tv(2)); p.forget()
        p = config.Pairing.load()
        self.assertEqual(p.tv["id"], "tv1")
        self.assertNotIn("tvs", p.data)
        p.forget()
        self.assertFalse(config.Pairing.load().paired)

    def test_repairing_same_tv_no_duplicate(self):
        p = config.Pairing.load()
        p.set_tv(tv(1)); p.set_tv(tv(1))
        self.assertEqual(len(config.Pairing.load().tvs), 1)


if __name__ == "__main__":
    unittest.main()
