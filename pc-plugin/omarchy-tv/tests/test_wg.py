import os, sys, unittest
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from omarchy_tv import wireguard as wg


class TestWg(unittest.TestCase):
    def test_toggle(self):
        c = wg.toggle_cmd("1.2.3.4:5555", "home", True)
        self.assertEqual(c[:3], ["adb", "-s", "1.2.3.4:5555"])
        self.assertEqual(c[-2:], ["tunnel", "home"])

    def test_name(self):
        self.assertEqual(wg.tunnel_name("/x/wg0.conf"), "wg0")
        with self.assertRaises(ValueError):
            wg.tunnel_name("/x/this-name-is-too-long.conf")

    def test_usage(self):
        self.assertEqual(wg.run([], runner=None, say=lambda s: None), 2)


if __name__ == "__main__":
    unittest.main()
