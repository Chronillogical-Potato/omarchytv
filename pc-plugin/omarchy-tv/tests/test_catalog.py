import json, os, sys, unittest
from pathlib import Path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from omarchy_tv import catalog

SAMPLE = os.path.join(os.path.dirname(__file__), "fixtures", "omarchy-catalog-sample.json")


class TestCatalog(unittest.TestCase):
    def setUp(self):
        with open(SAMPLE, encoding="utf-8") as f:
            self.doc = json.load(f)

    def test_real_sample_parses_browse_only(self):
        es = catalog.parse(self.doc, tv_ids=["com.sighmon.spacex-tv"])
        self.assertEqual(len(es), len(self.doc["plugins"]))
        for e in es:
            self.assertEqual(e["tvInstallable"], e["id"] == "com.sighmon.spacex-tv")
            self.assertTrue(e["repo"].startswith("https://") or e["repo"] == "")
        src = {p["id"]: p for p in self.doc["plugins"]}
        for e in es:
            if not src[e["id"]].get("installAvailable"):
                self.assertEqual(e["pcInstallCommand"], "")
            else:
                self.assertEqual(e["pcInstallCommand"], src[e["id"]]["installCommand"])

    def test_unknown_fields_and_bad_entries_tolerated(self):
        doc = {"generatedAt": "x", "stateSchemaVersion": 99, "newTopLevel": {"a": 1}, "plugins": [
            {"id": "ok.one", "name": "One", "futureField": [1, 2], "tags": "notalist", "repo": "http://insecure"},
            {"id": "BAD ID"}, "junk", None, {"name": "no id"}]}
        es = catalog.parse(doc)
        self.assertEqual([e["id"] for e in es], ["ok.one"])
        self.assertEqual((es[0]["tags"], es[0]["repo"], es[0]["tvInstallable"]), ([], "", False))

    def test_not_a_catalog(self):
        with self.assertRaises(ValueError):
            catalog.parse({"items": []})

    def test_run_never_crashes_on_fetch_error(self):
        out = []
        def boom(url): raise OSError("offline")
        self.assertEqual(catalog.run([], say=out.append, fetch=boom), 1)
        out = []
        self.assertEqual(catalog.run(["spacex"], say=out.append, fetch=lambda u: self.doc), 0)
        self.assertTrue(out[-1].endswith("browse-only on TV unless marked TV ✓)"))


if __name__ == "__main__":
    unittest.main()


class TvFilterTest(unittest.TestCase):
    def test_tv_view_hides_desktop_only(self):
        from omarchy_tv import catalog
        doc = json.loads((Path(__file__).parent / "fixtures" / "omarchy-catalog-sample.json").read_text())
        out = []
        catalog.run([], say=out.append, fetch=lambda u: doc)
        txt = "\n".join(out)
        self.assertIn("com.sighmon.spacex-tv", txt)
        for desk in ("lacuna.shell-suite", "dizziee.power-profiles", "omni"):
            self.assertNotIn(desk, txt)
        out = []
        catalog.run(["--all"], say=out.append, fetch=lambda u: doc)
        self.assertIn("lacuna.shell-suite", "\n".join(out))

