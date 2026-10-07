#!/usr/bin/env python3
"""Sets each feature's `flows` coverage in spec/features.json from the `# Features:`
header of every maestro/flows/, maestro/states/ (screen captures) and maestro/upgrade/, and
lowers maxWithoutFlows to match. Features with a `flowExempt` reason don't count.
Run after adding or changing a flow; spec/features.test.ts checks the result."""

import glob
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FEATURE_ID = re.compile(r"\b([a-z]+\.[a-z0-9-]+)\b")

coverage: dict[str, set[str]] = {}
for flow in sorted(glob.glob(str(ROOT / "maestro/flows/*.yaml")) + glob.glob(str(ROOT / "maestro/states/*.yaml")) + glob.glob(str(ROOT / "maestro/upgrade/*.yaml"))):
    header = []
    for line in open(flow):
        if not line.startswith("#"):
            break
        header.append(line[1:].strip())
    text = " ".join(header)
    if "Features:" not in text:
        continue
    # IDs run from "Features:" up to the first sentence that isn't a list of IDs.
    listed = re.split(r"(?<=[a-z0-9-])\s+(?=[A-Z])", text.split("Features:", 1)[1])[0]
    for fid in FEATURE_ID.findall(listed):
        coverage.setdefault(fid, set()).add(str(Path(flow).relative_to(ROOT)))

spec_path = ROOT / "spec/features.json"
spec = json.loads(spec_path.read_text())
known = {f["id"] for f in spec["features"]}
unknown = sorted(set(coverage) - known)
if unknown:
    raise SystemExit(f"flows name unknown features: {unknown}")
for feature in spec["features"]:
    feature["coverage"]["flows"] = sorted(coverage.get(feature["id"], []))
without = [f["id"] for f in spec["features"] if not f["coverage"]["flows"] and not f.get("flowExempt")]
spec["maxWithoutFlows"] = len(without)
spec_path.write_text(json.dumps(spec, indent=2, ensure_ascii=False) + "\n")
print(f"{len(spec['features']) - len(without)} of {len(spec['features'])} features have flows; without: {without}")
