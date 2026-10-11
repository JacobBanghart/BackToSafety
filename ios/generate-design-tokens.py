#!/usr/bin/env python3
"""spec/design-tokens.json -> Generated/DesignTokens.swift.

The Swift twin of android/buildSrc's GenerateDesignTokens: the same names and shapes the Kotlin
framework exported (DesignTokens.Light.shared.text, DesignTokens.Spacing.shared.md,
DesignTokens.Type_ ...), so the SwiftUI screens read the tokens as they always have. The
design tokens are UI, so they live in the app and not in the Rust core.

  generate-design-tokens.py [output-dir]   (default: Generated, next to this script)
"""
import json
import os
import re
import sys

here = os.path.dirname(os.path.abspath(__file__))
spec = os.path.join(here, "..", "spec", "design-tokens.json")
out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "Generated")

with open(spec) as f:
    tokens = json.load(f)


def ident(key):
    return "c" + key if key[0].isdigit() else key


def argb(css):
    """'#3f2875' or 'rgba(0,0,0,0.4)' -> 0xAARRGGBB."""
    m = re.fullmatch(r"rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)", css)
    if m:
        r, g, b = (int(m.group(i)) for i in (1, 2, 3))
        a = int(float(m.group(4)) * 255 + 0.5)
        value = (a << 24) | (r << 16) | (g << 8) | b
    else:
        value = 0xFF000000 | int(css.removeprefix("#"), 16)
    return "0x%08X" % value


def number(v):
    return repr(float(v))


out = [
    "// Generated from spec/design-tokens.json by generate-design-tokens.py. Do not edit.",
    "",
    "enum DesignTokens {",
]


def colors(name, table):
    out.append(f"    final class {name} {{")
    out.append(f"        static let shared = {name}()")
    for k, v in table.items():
        if isinstance(v, str):
            out.append(f"        let {ident(k)}: Int64 = {argb(v)}")
    out.append("    }")


colors("Light", tokens["colors"]["light"])
colors("Dark", tokens["colors"]["dark"])
for name, table in tokens["palette"].items():
    colors(name[0].upper() + name[1:], table)


def numbers(name, table):
    out.append(f"    final class {name} {{")
    out.append(f"        static let shared = {name}()")
    for k, v in table.items():
        out.append(f"        let {k}: Float = {number(v)}")
    out.append("    }")


numbers("Spacing", tokens["spacing"])
numbers("Radius", tokens["radius"])

out += [
    "    final class Type_ {",
    "        let fontSize: Float",
    "        let fontWeight: Int32",
    "        let lineHeight: Float",
    "        let letterSpacing: Float",
    "        init(_ fontSize: Float, _ fontWeight: Int32, _ lineHeight: Float, _ letterSpacing: Float) {",
    "            self.fontSize = fontSize",
    "            self.fontWeight = fontWeight",
    "            self.lineHeight = lineHeight",
    "            self.letterSpacing = letterSpacing",
    "        }",
    "    }",
    "    final class Typography {",
    "        static let shared = Typography()",
]
for k, t in tokens["typography"].items():
    out.append(
        f"        let {k} = Type_({number(t['fontSize'])}, {int(t['fontWeight'])}, "
        f"{number(t['lineHeight'])}, {number(t['letterSpacing'])})"
    )
out += ["    }", "}", ""]

os.makedirs(out_dir, exist_ok=True)
path = os.path.join(out_dir, "DesignTokens.swift")
text = "\n".join(out)
# Rewrite only on change, so an Xcode build with unchanged tokens doesn't recompile.
if not (os.path.exists(path) and open(path).read() == text):
    with open(path, "w") as f:
        f.write(text)
print(path)
