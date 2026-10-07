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

Ignored (per maestro/masks.json, also anything under a `below:<testID>` entry, such as
the system keyboard) and always: system UI, which isn't the app's to match: the top --ignore-top-px (status bar;
demo mode occasionally draws an icon twice) and the bottom --ignore-bottom-px (gesture
bar, which tints with whatever is under it) and, per maestro/masks.json, testIDs whose content is random
by design (the per-install device ID).

A state with no golden is reported as a candidate (exit 0 unless --require-goldens);
bless it by copying it into GOLDENS in its own commit (rule 1).

Exit codes: 0 pass, 1 regression, 2 usage/IO error.
"""

import argparse
import json
import sys
from pathlib import Path

from PIL import Image, ImageChops


def blank(img: Image.Image, boxes: list[tuple[int, int, int, int]]) -> Image.Image:
    img = img.copy()
    for box in boxes:
        img.paste((255, 0, 255), box)
    return img


def pixel_diff_percent(
    a: Path, b: Path, tolerance: int, diff_out: Path | None, masks: list[tuple[int, int, int, int]]
) -> float:
    img_a = Image.open(a).convert("RGB")
    img_b = Image.open(b).convert("RGB")
    if img_a.size != img_b.size:
        raise ValueError(f"size mismatch: {a} {img_a.size} vs {b} {img_b.size}")
    img_a, img_b = blank(img_a, masks), blank(img_b, masks)
    r, g, b_ = ImageChops.difference(img_a, img_b).split()
    worst = ImageChops.lighter(ImageChops.lighter(r, g), b_)
    mask = worst.point(lambda v: 255 if v > tolerance else 0)
    differing = mask.histogram()[255]
    if diff_out and differing:
        mask.save(diff_out)
    return differing / (img_a.size[0] * img_a.size[1]) * 100


def load_layout(path: Path) -> dict[str, list[float]]:
    return json.loads(path.read_text()) if path.exists() else {}


TOUCH_TARGET_DP = 48.0


def normalize_touch_target(got: list[float], want: list[float]) -> list[float]:
    """Compose reports a clickable smaller than 48dp with its minimum touch target: the same
    center, grown to 48dp. When one side is exactly that and the other smaller, compare the
    other side's real extent around the shared center instead."""
    l, t, r, b = got
    out = [l, t, r, b]
    for lo, hi in ((0, 2), (1, 3)):
        g, w = got[hi] - got[lo], want[hi] - want[lo]
        if abs(g - TOUCH_TARGET_DP) < 0.6 and w < TOUCH_TARGET_DP - 0.6:
            center = (got[lo] + got[hi]) / 2
            out[lo], out[hi] = center - w / 2, center + w / 2
    return out


def layout_diff(a: Path, b: Path, tolerance_dp: float, masked: set[str]) -> list[str]:
    if not a.exists() or not b.exists():
        return [] if not b.exists() else [f"missing captured layout {a.name}"]
    got = {k: v for k, v in load_layout(a).items() if k not in masked}
    want = {k: v for k, v in load_layout(b).items() if k not in masked}
    problems = [f"missing {tid}" for tid in sorted(set(want) - set(got))]
    problems += [f"unexpected {tid}" for tid in sorted(set(got) - set(want))]
    for tid in sorted(set(got) & set(want)):
        delta = max(abs(x - y) for x, y in zip(normalize_touch_target(got[tid], want[tid]), want[tid]))
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
    p.add_argument("--ignore-top-px", type=int, default=136, help="Android status bar (52dp at 420dpi)")
    p.add_argument("--ignore-bottom-px", type=int, default=63, help="Android gesture bar (24dp at 420dpi)")
    p.add_argument("--dpi", type=int, default=420)
    p.add_argument("--masks", type=Path, default=Path(__file__).with_name("masks.json"))
    p.add_argument("--skip-pixels", action="store_true", help="layout only (cross-implementation runs)")
    p.add_argument("--require-goldens", action="store_true")
    args = p.parse_args()

    shots = sorted(p for p in args.captured.glob("*.png") if not p.name.endswith(".diff.png"))
    if not shots:
        print(f"no captures in {args.captured}", file=sys.stderr)
        return 2

    mask_ids = json.loads(args.masks.read_text()) if args.masks.exists() else {}
    scale = args.dpi / 160

    failed, candidates = [], []
    for shot in shots:
        state = shot.stem
        golden = args.goldens / shot.name
        if not golden.exists():
            candidates.append(state)
            print(f"CANDIDATE {state} (no golden)")
            continue
        masked = {m for m in mask_ids.get(state, []) if not m.startswith("below:")}
        below = [m.split(":", 1)[1] for m in mask_ids.get(state, []) if m.startswith("below:")]
        problems = layout_diff(
            args.captured / f"{state}.layout.json",
            args.goldens / f"{state}.layout.json",
            args.layout_tolerance_dp,
            masked,
        )
        width, height = Image.open(shot).size
        boxes = [(0, 0, width, args.ignore_top_px), (0, height - args.ignore_bottom_px, width, height)]
        for layout in (load_layout(args.captured / f"{state}.layout.json"), load_layout(args.goldens / f"{state}.layout.json")):
            for tid in masked & set(layout):
                l, t, r, b = layout[tid]
                boxes.append((int(l * scale), int(t * scale), int(r * scale) + 1, int(b * scale) + 1))
            for tid in set(below) & set(layout):
                boxes.append((0, int(layout[tid][3] * scale) + 1, width, height))
        line = f"{state}:"
        if not args.skip_pixels:
            try:
                pct = pixel_diff_percent(
                    shot, golden, args.channel_tolerance, args.captured / f"{state}.diff.png", boxes
                )
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
