#!/usr/bin/env python3
"""Compare captured screen states against blessed goldens (PARITY_PLAN L4).

For each <state>.png in CAPTURED there should be a golden of the same name in GOLDENS.

- Pixels: a pixel differs when any RGB channel moves more than --channel-tolerance; a
  screen fails when more than --threshold-percent of its pixels differ. Same metric as
  reactor's tools/compare_screenshots.py.
- Layout: <state>.layout.json (testID -> [left, top, right, bottom] in dp) must match
  within --layout-tolerance-dp, and the same testIDs must be present. This is the
  cross-implementation measure: a Compose or SwiftUI screen will never be byte-identical
  to the RN one, but its elements must sit in the same places.

A state with no golden is reported as a candidate (exit 0 unless --require-goldens);
bless it by copying it into GOLDENS in its own commit (rule 1).

Exit codes: 0 pass, 1 regression, 2 usage/IO error.
"""

import argparse
import json
import sys
from pathlib import Path

from PIL import Image, ImageChops


def pixel_diff_percent(a: Path, b: Path, tolerance: int, diff_out: Path | None) -> float:
    img_a = Image.open(a).convert("RGB")
    img_b = Image.open(b).convert("RGB")
    if img_a.size != img_b.size:
        raise ValueError(f"size mismatch: {a} {img_a.size} vs {b} {img_b.size}")
    r, g, b_ = ImageChops.difference(img_a, img_b).split()
    worst = ImageChops.lighter(ImageChops.lighter(r, g), b_)
    mask = worst.point(lambda v: 255 if v > tolerance else 0)
    differing = mask.histogram()[255]
    if diff_out and differing:
        mask.save(diff_out)
    return differing / (img_a.size[0] * img_a.size[1]) * 100


def layout_diff(a: Path, b: Path, tolerance_dp: float) -> list[str]:
    if not a.exists() or not b.exists():
        return [] if not b.exists() else [f"missing captured layout {a.name}"]
    got, want = json.loads(a.read_text()), json.loads(b.read_text())
    problems = [f"missing {tid}" for tid in sorted(set(want) - set(got))]
    problems += [f"unexpected {tid}" for tid in sorted(set(got) - set(want))]
    for tid in sorted(set(got) & set(want)):
        delta = max(abs(x - y) for x, y in zip(got[tid], want[tid]))
        if delta > tolerance_dp:
            problems.append(f"{tid} moved {delta:.1f}dp: {want[tid]} -> {got[tid]}")
    return problems


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("captured", type=Path)
    p.add_argument("goldens", type=Path)
    p.add_argument("--channel-tolerance", type=int, default=10)
    p.add_argument("--threshold-percent", type=float, default=0.02)
    p.add_argument("--layout-tolerance-dp", type=float, default=2.0)
    p.add_argument("--skip-pixels", action="store_true", help="layout only (cross-implementation runs)")
    p.add_argument("--require-goldens", action="store_true")
    args = p.parse_args()

    shots = sorted(args.captured.glob("*.png"))
    if not shots:
        print(f"no captures in {args.captured}", file=sys.stderr)
        return 2

    failed, candidates = [], []
    for shot in shots:
        state = shot.stem
        golden = args.goldens / shot.name
        if not golden.exists():
            candidates.append(state)
            print(f"CANDIDATE {state} (no golden)")
            continue
        problems = layout_diff(
            args.captured / f"{state}.layout.json", args.goldens / f"{state}.layout.json", args.layout_tolerance_dp
        )
        line = f"{state}:"
        if not args.skip_pixels:
            try:
                pct = pixel_diff_percent(shot, golden, args.channel_tolerance, args.captured / f"{state}.diff.png")
            except ValueError as e:
                print(f"ERROR {state}: {e}", file=sys.stderr)
                return 2
            line += f" pixels {pct:.4f}%"
            if pct > args.threshold_percent:
                problems.append(f"pixels differ {pct:.4f}% > {args.threshold_percent}%")
        if problems:
            failed.append(state)
            print(f"FAIL {line} " + "; ".join(problems))
        else:
            print(f"ok   {line}")

    print(f"\n{len(shots) - len(failed) - len(candidates)} ok, {len(failed)} failed, {len(candidates)} without goldens")
    if failed or (args.require_goldens and candidates):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
