#!/usr/bin/env python3
"""Tier-2 fidelity ratchet (PARITY_PLAN L4): the Kotlin app against the RN goldens.

Pixel-identical isn't possible across renderers (text anti-aliasing, image filtering),
so each captured state has a mark in spec/fidelity/android.json: the share of pixels
that may differ from the RN golden. A state fails when its layout is outside
--layout-tolerance-dp or its pixel difference is worse than its mark. When a state beats
its mark by more than the slack, the check also fails, asking you to lower the mark, so
marks only go down. --update writes the current numbers as the new marks; commit that
on its own (rule 1).

Usage: fidelity.py CAPTURED_ROOT [--update]   (CAPTURED_ROOT/<mode>/<state>.png from capture.sh)
"""

import argparse
import json
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
MARKS = ROOT / "spec/fidelity/android.json"
GOLDENS = ROOT / "spec/goldens/android"
SLACK = 0.05  # percentage points of run-to-run headroom before a mark must be lowered


def measure(captured: Path, mode: str) -> dict[str, tuple[float | None, list[str]]]:
    out = subprocess.run(
        [sys.executable, str(HERE / "compare_screens.py"), str(captured / mode), str(GOLDENS / mode),
         "--threshold-percent", "100"],
        capture_output=True, text=True,
    ).stdout
    results = {}
    for line in out.splitlines():
        if not line.startswith(("ok ", "FAIL ")):
            continue
        state = line.split()[1].rstrip(":")
        pct = float(line.split("pixels ")[1].split("%")[0]) if "pixels " in line else None
        problems = [p for p in line.split("; ") if "moved" in p or "missing" in p or "unexpected" in p]
        results[state] = (pct, problems)
    return results


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("captured", type=Path)
    p.add_argument("--update", action="store_true")
    args = p.parse_args()

    marks = json.loads(MARKS.read_text()) if MARKS.exists() else {}
    failed = False
    for mode in sorted(d.name for d in args.captured.iterdir() if d.is_dir()):
        for state, (pct, layout_problems) in sorted(measure(args.captured, mode).items()):
            key = f"{mode}/{state}"
            mark = marks.get(key)
            if args.update:
                marks[key] = round(pct, 2)
                print(f"mark {key} = {marks[key]}%")
                continue
            verdict = "ok"
            if layout_problems:
                verdict = "FAIL layout: " + "; ".join(layout_problems)
            elif mark is None:
                verdict = "FAIL no mark (run with --update)"
            elif pct > mark + SLACK:
                verdict = f"FAIL worse than mark {mark}%"
            elif pct < mark - SLACK:
                verdict = f"FAIL better than mark {mark}%: lower it (--update)"
            failed |= verdict != "ok"
            print(f"{verdict:4} {key}: {pct:.2f}%")
    if args.update:
        MARKS.write_text(json.dumps(dict(sorted(marks.items())), indent=2) + "\n")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
