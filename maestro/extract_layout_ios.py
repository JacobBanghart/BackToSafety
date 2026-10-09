#!/usr/bin/env python3
"""`maestro hierarchy` JSON (iOS) -> {testID: [left, top, right, bottom]} in points, on stdout.

RN exposes testID as the iOS accessibilityIdentifier (SwiftUI's .accessibilityIdentifier does
the same), which Maestro reports as resource-id, with bounds already in points: the iOS
counterpart of extract_layout.py's dp. System elements (alert buttons, the photo picker) carry
their labels as identifiers; only the app's kebab-case testIDs are kept.
Usage: extract_layout_ios.py hierarchy.json
"""

import json
import re
import sys

TEST_ID = re.compile(r"^[a-z0-9]+(-[a-z0-9_]+)+$")
layout = {}


def walk(node):
    attrs = node.get("attributes", {})
    rid = attrs.get("resource-id") or ""
    bounds = attrs.get("bounds") or ""
    if TEST_ID.match(rid) and bounds:
        l, t, r, b = map(float, re.findall(r"-?\d+(?:\.\d+)?", bounds))
        layout.setdefault(rid, [round(v, 1) for v in (l, t, r, b)])
    for child in node.get("children", []):
        walk(child)


walk(json.load(open(sys.argv[1])))
json.dump(dict(sorted(layout.items())), sys.stdout, indent=2)
print()
