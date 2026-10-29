import os, sys, unittest
from types import SimpleNamespace as NS
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from omarchy_tv import adb_pair


class AdbPairTest(unittest.TestCase):
    def test_bad_args(self):
        for a in ([], ["1.2.3.4:5555"], ["1.2.3.4:5555", "12345"], ["1.2.3.4", "123456"], ["x:99999", "123456"],
                  ["1.2.3.4:37000", "123456", "--connect", "bad"]):
            self.assertEqual(adb_pair.run(a, say=lambda *_: None, which=lambda _: "/usr/bin/adb"), 2, a)

    def test_pair_and_connect(self):
        calls = []
        def runner(cmd, **k):
            calls.append(cmd)
            if cmd[1] == "pair":
                return NS(returncode=0, stdout="Successfully paired to 192.168.1.9:37000 [guid=adb-X]\n", stderr="")
            return NS(returncode=0, stdout="connected to 192.168.1.9:5555\n", stderr="")
        out = []
        rc = adb_pair.run(["192.168.1.9:37000", "482913", "--connect", "192.168.1.9:5555"], say=out.append,
                          runner=runner, which=lambda _: "/usr/bin/adb")
        self.assertEqual(rc, 0)
        self.assertEqual(calls, [["adb", "pair", "192.168.1.9:37000", "482913"], ["adb", "connect", "192.168.1.9:5555"]])

    def test_wrong_code_and_missing_adb(self):
        bad = lambda cmd, **k: NS(returncode=1, stdout="", stderr="Failed: Wrong password or connection was dropped.")
        out = []
        self.assertEqual(adb_pair.run(["10.0.0.2:40000", "000000"], say=out.append, runner=bad, which=lambda _: "x"), 1)
        self.assertIn("Wrong password", out[-1])
        self.assertEqual(adb_pair.run(["10.0.0.2:40000", "000000"], say=out.append, which=lambda _: None), 1)


if __name__ == "__main__":
    unittest.main()
