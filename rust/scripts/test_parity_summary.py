#!/usr/bin/env python3
"""Off-device checks of parity_summary.py: python3 rust/scripts/test_parity_summary.py"""

import tempfile
import unittest
from pathlib import Path

import parity_summary as ps

COMPARE = """oracle light: cached
== light: rust vs oracle
ok   home: 
FAIL settings: settings-title moved 3.0dp: [0, 1, 2, 3] -> [0, 4, 2, 3]; missing settings-theme
CANDIDATE readout (no golden)

2 ok, 1 failed, 1 without goldens
== dark: rust vs oracle
ok   home: 
"""
FLOWS = """ok   contacts
FAIL onboarding (/tmp/x/onboarding.log)
     Element not found: Text matching regex: Continue
     Assertion is false
"""


class ParityTests(unittest.TestCase):
    def test_compare_lines(self):
        r = ps.parse_compare(COMPARE)
        self.assertEqual(r[("light", "home")], {"result": "pass", "deltas": []})
        self.assertEqual(r[("light", "settings")]["deltas"], ["settings-title moved 3.0dp: [0, 1, 2, 3] -> [0, 4, 2, 3]", "missing settings-theme"])
        self.assertEqual(r[("light", "readout")]["result"], "fail")
        self.assertEqual(r[("dark", "home")]["result"], "pass")

    def test_flow_lines(self):
        r = ps.parse_flows(FLOWS)
        self.assertEqual(r["contacts"]["result"], "pass")
        self.assertEqual(r["onboarding"]["log"], "/tmp/x/onboarding.log")
        self.assertIn("Element not found", r["onboarding"]["detail"])
        self.assertIn("Assertion is false", r["onboarding"]["detail"])

    def test_gesture_lines(self):
        r = ps.parse_gestures("contacts: reorder ok\ndestinations: setup failed (/tmp/reorder-destinations.log)\n")
        self.assertEqual(r["contacts"]["result"], "pass")
        self.assertEqual(r["destinations"]["result"], "fail")

    def test_rows_and_missing_results(self):
        with tempfile.TemporaryDirectory() as t:
            out, captures = Path(t) / "out", Path(t) / "captures"
            dev = out / "emulator-5570"
            (dev / "compare/rust/light").mkdir(parents=True)
            (dev / "compare/rust/dark").mkdir(parents=True)
            (captures / "light").mkdir(parents=True)
            (dev / "compare.log").write_text(COMPARE)
            (dev / "flows.log").write_text(FLOWS)
            (dev / "compare/rust/light/home.png").write_bytes(b"x")
            (captures / "light/home.png").write_bytes(b"x")
            (dev / "compare/rust/dark/profile.log").write_text("")  # captured flow failed: no png
            rows = ps.build(out, captures, ["light", "dark"], ["home", "profile"], ["contacts", "onboarding", "alerts"], True)
            by = {(r["kind"], r["name"], r.get("mode")): r for r in rows}
            home = by[("state", "home", "light")]
            self.assertEqual(home["result"], "pass")
            self.assertTrue(home["rust"].endswith("compare/rust/light/home.png"))
            self.assertTrue(home["oracle"].endswith("captures/light/home.png"))
            self.assertIn("Rust capture failed", by[("state", "profile", "dark")]["deltas"][0])
            self.assertEqual(by[("state", "profile", "light")]["deltas"], ["oracle capture missing"])
            self.assertEqual(by[("flow", "alerts", None)]["detail"], "not run")
            self.assertEqual(by[("gesture", "reorder contacts", None)]["result"], "fail")
            md = ps.markdown(rows, {"when": "now", "rust": "abc", "oracle": "def", "serials": "emulator-5570"})
            self.assertIn("## Failing", md)
            self.assertIn("| home | light | pass | none |", md)


if __name__ == "__main__":
    unittest.main()
