#!/usr/bin/env python3
"""Tier-2 fidelity ratchet (PARITY_PLAN L4): the Kotlin app against the RN goldens.

Pixel-identical isn't possible across renderers (text anti-aliasing, image filtering),
so each captured state has a mark in spec/fidelity/android.json: the share of pixels
that may differ from the RN golden. A state fails when its layout is outside
--layout-tolerance-dp or its pixel difference is worse than its mark. When a state beats
its mark by more than the slack, the check also fails, asking you to lower the mark, so
marks only go down. --update writes the current numbers as the new marks; commit that
on its own (rule 1).

Layout works the same way. Elements must sit within LAYOUT_TOLERANCE_DP of the RN golden,
unless the state has a layout mark in spec/fidelity/android-layout.json: the largest
movement allowed there. Compose rounds each padding and border to whole pixels, while RN
rounds positions once, so on a long screen the difference adds up past 2dp toward the
bottom. Missing or unexpected testIDs always fail.

--platform ios holds the SwiftUI app to spec/goldens/ios the same way, with its marks in
spec/fidelity/ios.json and ios-layout.json (points, not dp).

Usage: fidelity.py CAPTURED_ROOT [--platform android|ios] [--update]
  (CAPTURED_ROOT/<mode>/<state>.png from capture.sh or capture-ios.sh)
"""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
LAYOUT_TOLERANCE_DP = 2.0
LAYOUT_SLACK = 0.3  # dp
# compare_screens.py options per platform: iOS screenshots are 3x (480 "dpi" makes its dp a
# point), carry no navigation bar to crop, and the home indicator is ignored.
COMPARE_ARGS = {
    "android": [],
    "ios": ["--dpi", "480", "--ignore-top-px", "0", "--ignore-bottom-px", "102"],
}
SLACK = 0.05  # percentage points of run-to-run headroom before a mark must be lowered


def measure(captured: Path, goldens: Path, mode: str, platform: str) -> dict[str, tuple[float | None, float, list[str]]]:
    """Per state: pixel difference %, largest element movement (dp), missing/unexpected IDs."""
    out = subprocess.run(
        [sys.executable, str(HERE / "compare_screens.py"), str(captured / mode), str(goldens / mode),
         "--threshold-percent", "100", "--layout-tolerance-dp", "0", *COMPARE_ARGS[platform]],
        capture_output=True, text=True,
    ).stdout
    results = {}
    for line in out.splitlines():
        if not line.startswith(("ok ", "FAIL ")):
            continue
        state = line.split()[1].rstrip(":")
        pct = float(line.split("pixels ")[1].split("%")[0]) if "pixels " in line else None
        parts = line.split("; ")
        moved = [float(p.split(" moved ")[1].split("dp")[0]) for p in parts if " moved " in p]
        # Whole-word only: a testID may itself contain "missing" (readout-script-missing).
        problems = [m.group(0) for m in re.finditer(r"(?<![\w-])(missing|unexpected) [\w-]+", line)]
        results[state] = (pct, max(moved, default=0.0), problems)
    return results


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("captured", type=Path)
    p.add_argument("--platform", choices=sorted(COMPARE_ARGS), default="android")
    p.add_argument("--update", action="store_true")
    args = p.parse_args()
    marks_file = ROOT / f"spec/fidelity/{args.platform}.json"
    layout_marks_file = ROOT / f"spec/fidelity/{args.platform}-layout.json"
    goldens = ROOT / f"spec/goldens/{args.platform}"

    marks = json.loads(marks_file.read_text()) if marks_file.exists() else {}
    layout_marks = json.loads(layout_marks_file.read_text()) if layout_marks_file.exists() else {}
    failed = False
    for mode in sorted(d.name for d in args.captured.iterdir() if d.is_dir()):
        for state, (pct, moved, layout_problems) in sorted(measure(args.captured, goldens, mode, args.platform).items()):
            key = f"{mode}/{state}"
            mark = marks.get(key)
            layout_mark = layout_marks.get(key)
            if args.update:
                marks[key] = round(pct, 2)
                if moved > LAYOUT_TOLERANCE_DP:
                    layout_marks[key] = round(moved, 1)
                else:
                    layout_marks.pop(key, None)
                print(f"mark {key} = {marks[key]}%" + (f", {layout_marks[key]}dp" if key in layout_marks else ""))
                continue
            verdict = "ok"
            allowed = max(LAYOUT_TOLERANCE_DP, layout_mark or 0)
            if layout_problems:
                verdict = "FAIL layout: " + "; ".join(layout_problems)
            elif moved > allowed + (LAYOUT_SLACK if layout_mark else 0):
                verdict = f"FAIL layout: moved {moved:.1f}dp > {allowed}dp"
            elif layout_mark and moved < layout_mark - LAYOUT_SLACK:
                verdict = f"FAIL layout better than mark {layout_mark}dp ({moved:.1f}dp): lower it (--update)"
            elif mark is None:
                verdict = "FAIL no mark (run with --update)"
            elif pct > mark + SLACK:
                verdict = f"FAIL worse than mark {mark}%"
            elif pct < mark - SLACK:
                verdict = f"FAIL better than mark {mark}%: lower it (--update)"
            failed |= verdict != "ok"
            print(f"{verdict:4} {key}: {pct:.2f}%, moved {moved:.1f}dp")
    if args.update:
        marks_file.write_text(json.dumps(dict(sorted(marks.items())), indent=2) + "\n")
        layout_marks_file.write_text(json.dumps(dict(sorted(layout_marks.items())), indent=2) + "\n")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
