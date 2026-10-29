import os, sys, unittest
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from omarchy_tv import adb_lock


class TestAdbLock(unittest.TestCase):
    def test_usage_without_tv(self):
        self.assertEqual(adb_lock.run([], say=lambda s: None), 2)

    def test_cancel(self):
        self.assertEqual(adb_lock.run(["--tv", "1.2.3.4:5555"], runner=None, say=lambda s: None, confirm=lambda p: False), 1)

    def test_lock(self):
        calls = []
        def r(cmd, **kw):
            calls.append(cmd); return type("P", (), {"returncode": 0})()
        self.assertEqual(adb_lock.run(["--tv", "1.2.3.4:5555", "--yes"], runner=r, say=lambda s: None), 0)
        self.assertEqual(calls[-1], ["adb", "-s", "1.2.3.4:5555", "usb"])


if __name__ == "__main__":
    unittest.main()
