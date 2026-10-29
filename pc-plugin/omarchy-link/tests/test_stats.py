"""omarchy · pc widget: CPU, RAM and now playing (Linux /proc + playerctl, macOS Mach ticks + vm_stat + osascript)."""
import subprocess
import sys
import unittest
from unittest import mock

import helpers  # noqa: F401  (hermetic env + sys.path)
from omarchy_link import stats

VM_STAT = """Mach Virtual Memory Statistics: (page size of 16384 bytes)
Pages free:                                     4839.
Pages active:                                 163137.
Pages inactive:                               162248.
Pages wired down:                             442858.
Pages purgeable:                                  20.
"Translation faults":                    77603136123.
Anonymous pages:                              232657.
Pages occupied by compressor:                 234586.
"""


def fake_run(argv, **kw):
    out = {"vm_stat": VM_STAT, "sysctl": "17179869184\n"}[argv[0]]
    return subprocess.CompletedProcess(argv, 0, out, "")


class Stats(unittest.TestCase):
    def setUp(self):
        stats._last_cpu = None

    def test_cpu_is_a_delta(self):
        ticks = iter([(900, 1000), (1200, 2000), (1200, 2000)])
        with mock.patch.object(stats, "_cpu_ticks", lambda: next(ticks)):
            self.assertIsNone(stats.cpu_percent())     # first sample: nothing to compare with
            self.assertEqual(stats.cpu_percent(), 70)  # 300 idle of 1000 ticks
            self.assertIsNone(stats.cpu_percent())     # no ticks passed

    def test_cpu_counter_wrap_is_skipped(self):
        ticks = iter([(900, 4_000_000_000), (10, 100)])
        with mock.patch.object(stats, "_cpu_ticks", lambda: next(ticks)):
            stats.cpu_percent()
            self.assertIsNone(stats.cpu_percent())

    def test_macos_ram_from_vm_stat(self):
        # Activity Monitor's "Memory Used": wired + compressor + anonymous - purgeable, over hw.memsize.
        with mock.patch.object(sys, "platform", "darwin"), mock.patch.object(stats.subprocess, "run", fake_run):
            used = (442858 + 234586 + 232657 - 20) * 16384
            self.assertEqual(stats.ram_percent(), round(100 * used / 17179869184))

    def test_macos_ram_unknown_format(self):
        bad = lambda argv, **kw: subprocess.CompletedProcess(argv, 0, "nothing useful\n", "")
        with mock.patch.object(sys, "platform", "darwin"), mock.patch.object(stats.subprocess, "run", bad):
            self.assertIsNone(stats.ram_percent())

    @unittest.skipUnless(sys.platform == "darwin", "macOS only")
    def test_macos_real_readers(self):
        self.assertIsNone(stats.cpu_percent())
        subprocess.run(["sleep", "0.3"])
        cpu, ram = stats.cpu_percent(), stats.ram_percent()
        self.assertTrue(cpu is None or 0 <= cpu <= 100, cpu)
        self.assertTrue(0 < ram <= 100, ram)

    def players(self, running, states):
        """subprocess.run stand-in: pgrep finds the apps in `running`; osascript answers from `states`."""
        calls = []

        def run(argv, **kw):
            calls.append(argv)
            if argv[0] == "pgrep":
                return subprocess.CompletedProcess(argv, 0 if argv[-1] in running else 1)
            app = argv[2].split('"')[1]
            return subprocess.CompletedProcess(argv, 0, states[app] + "\n", "")
        return run, calls

    def test_macos_now_playing(self):
        run, calls = self.players({"Spotify", "Music"}, {"Spotify": "paused\tJobim\tWave", "Music": "playing\tGal\tBaby"})
        with mock.patch.object(sys, "platform", "darwin"), mock.patch.object(stats.subprocess, "run", run):
            self.assertEqual(stats.now_playing(), {"artist": "Gal", "title": "Baby"})  # paused Spotify is skipped
        run, calls = self.players({"Spotify", "Music"}, {"Spotify": "playing\tJobim\tWave", "Music": "playing\tGal\tBaby"})
        with mock.patch.object(sys, "platform", "darwin"), mock.patch.object(stats.subprocess, "run", run):
            self.assertEqual(stats.now_playing(), {"artist": "Jobim", "title": "Wave"})  # Spotify first

    def test_macos_now_playing_never_launches_an_app(self):
        run, calls = self.players(set(), {})
        with mock.patch.object(sys, "platform", "darwin"), mock.patch.object(stats.subprocess, "run", run):
            self.assertIsNone(stats.now_playing())
        self.assertEqual([c[0] for c in calls], ["pgrep", "pgrep"])  # no osascript for apps that aren't running

    def test_linux_now_playing_playerctl(self):
        out = lambda argv, **kw: subprocess.CompletedProcess(argv, 0, "Playing\tGal\tBaby\n", "")
        with mock.patch.object(sys, "platform", "linux"), mock.patch.object(stats.shutil, "which", lambda c: "/usr/bin/" + c), \
                mock.patch.object(stats.subprocess, "run", out):
            self.assertEqual(stats.now_playing(), {"artist": "Gal", "title": "Baby"})
        with mock.patch.object(sys, "platform", "linux"), mock.patch.object(stats.shutil, "which", lambda c: None):
            self.assertIsNone(stats.now_playing())


if __name__ == "__main__":
    unittest.main()
