#!/usr/bin/env python3
"""uiautomator XML dump -> {testID: [left, top, right, bottom]} in dp, on stdout.

RN exposes testID as the Android resource-id (Compose does the same with
testTagsAsResourceId), so this works for both implementations. System IDs
(`android:id/...`) are skipped. Usage: extract_layout.py dump.xml DENSITY_DPI
"""

import json
import re
import sys
import xml.etree.ElementTree as ET

dump, dpi = sys.argv[1], int(sys.argv[2])
scale = dpi / 160
layout = {}
for node in ET.parse(dump).iter("node"):
    rid = node.get("resource-id", "")
    if not rid or ":" in rid:
        continue
    l, t, r, b = map(int, re.findall(r"\d+", node.get("bounds", "")))
    layout[rid] = [round(v / scale, 1) for v in (l, t, r, b)]
json.dump(dict(sorted(layout.items())), sys.stdout, indent=2)
print()
