#!/usr/bin/env python3
"""Checks the spec contracts against the two apps (CI: contracts job).

- spec/testids.json: every declared ID is in the Android app's Kotlin and the iOS app's Swift
  (iosOnly ones only in Swift), with no ID declared twice and only known placeholders, and the
  option placeholders (role, category, risk, hand) match the database's CHECK constraints.
- spec/features.json: unique IDs, every vector and flow reference resolves, and
  maxWithoutFlows matches the features still without a Maestro flow (it may only go down;
  maestro/sync_features.py lowers it).
- i18n/locales: every locale has English's keys, no empty strings, the same placeholders.

Exits non-zero with a list of problems.
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
problems: list[str] = []


def string_literals(files: list[Path], interpolation: str) -> set[str]:
    """Every "..." literal, with interpolations ($index, ${x}, \\(x)) turned into *."""
    out = set()
    for f in files:
        for m in re.finditer(r'"((?:[^"\\\n]|\\.)*)"', f.read_text()):
            out.add(re.sub(interpolation, "*", m.group(1)))
    return out


def check_testids() -> None:
    contract = json.loads((ROOT / "spec/testids.json").read_text())
    declared = [i for ids in contract["screens"].values() for i in ids]
    ios_only = set(contract.get("iosOnly", {}).get("ids", []))
    shape = lambda s: re.sub(r"\{\w+\}", "*", s)

    dupes = sorted({i for i in declared if declared.count(i) > 1})
    if dupes:
        problems.append(f"testids: declared twice: {dupes}")
    known = set(contract["placeholders"]) | {"index"}
    unknown = sorted({p for i in declared for p in re.findall(r"\{(\w+)\}", i)} - known)
    if unknown:
        problems.append(f"testids: undefined placeholders: {unknown}")
    for screen, ids in contract["screens"].items():
        misfiled = [i for i in ids if i != screen and not i.startswith(f"{screen}-")]
        if misfiled:
            problems.append(f"testids: filed under {screen} without its prefix: {misfiled}")

    # The UI's option lists stay inside the database's CHECK constraints.
    schema = json.loads((ROOT / "spec/db-schema.json").read_text())

    def allowed(table: str, column: str) -> list[str]:
        check = next((c for c in schema["tables"][table]["checks"] if c.startswith(f"{column} IN")), "")
        return sorted(re.findall(r"'([^']+)'", check))

    for placeholder, table, column in (("role", "contacts", "role"), ("category", "destinations", "category"),
                                       ("risk", "destinations", "risk_level"), ("hand", "profile", "dominant_hand")):
        if sorted(contract["placeholders"][placeholder]) != allowed(table, column):
            problems.append(f"testids: {{{placeholder}}} options differ from {table}.{column}'s CHECK: "
                            f"{sorted(contract['placeholders'][placeholder])} vs {allowed(table, column)}")

    kotlin = string_literals(
        sorted((ROOT / "kmp/androidApp/src/main").rglob("*.kt")), r"\$\{[^}]*\}|\$\w+"
    )
    swift = string_literals(sorted((ROOT / "kmp/iosApp/BackToSafety").rglob("*.swift")), r"\\\((?:[^()]|\([^()]*\))*\)")
    # Shared headers derive <id>-back and <id>-title from the screen's ID.
    derived = {
        suffix.replace("{id}", screen)
        for screen in contract["screenHeaders"]
        for suffix in contract["derived"]["screenHeader"]
    }
    for app, literals, skip in (("Android", kotlin, ios_only), ("iOS", swift, set())):
        missing = [i for i in declared if i not in skip | derived and shape(i) not in literals]
        if missing:
            problems.append(f"testids: missing from the {app} app: {missing}")
    for app, literals, header in (("Android", kotlin, "*-back"), ("iOS", swift, "*-back")):
        if header not in literals:
            problems.append(f"testids: the {app} screen header doesn't derive <id>-back")


def check_features() -> None:
    inventory = json.loads((ROOT / "spec/features.json").read_text())
    features = inventory["features"]
    ids = [f["id"] for f in features]
    dupes = sorted({i for i in ids if ids.count(i) > 1})
    if dupes:
        problems.append(f"features: duplicate IDs: {dupes}")
    for f in features:
        for ref in f["coverage"]["vectors"]:
            file, fn = ref.split("#")
            path = ROOT / "spec/vectors" / file
            if not path.exists() or fn not in json.loads(path.read_text())["functions"]:
                problems.append(f"features: {f['id']}: no vector {ref}")
        for flow in f["coverage"]["flows"]:
            if not (ROOT / flow).exists():
                problems.append(f"features: {f['id']}: no flow {flow}")
    without = sum(1 for f in features if not f["coverage"]["flows"] and "flowExempt" not in f)
    if without > inventory["maxWithoutFlows"]:
        problems.append(f"features: {without} lack a flow, more than maxWithoutFlows ({inventory['maxWithoutFlows']})")
    elif without < inventory["maxWithoutFlows"]:
        problems.append(f"features: coverage improved: lower maxWithoutFlows to {without}")


def check_locales() -> None:
    """Every locale matches English key for key, with no empty strings and the same
    {{placeholders}}. An empty string renders as blank text, not as the English fallback."""
    locales = ROOT / "i18n/locales"

    def flat(obj: dict, prefix: str = "") -> dict[str, str]:
        out: dict[str, str] = {}
        for key, value in obj.items():
            if isinstance(value, dict):
                out.update(flat(value, f"{prefix}{key}."))
            else:
                out[prefix + key] = str(value)
        return out

    def holders(text: str) -> list[str]:
        return sorted(re.findall(r"\{\{\s*(\w+)\s*\}\}", text))

    namespaces = sorted(f.name for f in (locales / "en").glob("*.json"))
    for ns in namespaces:
        base = flat(json.loads((locales / "en" / ns).read_text()))
        empty = [k for k, v in base.items() if not v.strip()]
        if empty:
            problems.append(f"locales: en/{ns}: empty strings: {empty}")
    for locale in sorted(d.name for d in locales.iterdir() if d.is_dir() and d.name != "en"):
        files = sorted(f.name for f in (locales / locale).glob("*.json"))
        if files != namespaces:
            problems.append(f"locales: {locale}: namespaces {files} differ from en's {namespaces}")
        for ns in namespaces:
            if not (locales / locale / ns).exists():
                continue
            base = flat(json.loads((locales / "en" / ns).read_text()))
            target = flat(json.loads((locales / locale / ns).read_text()))
            if sorted(base) != sorted(target):
                problems.append(f"locales: {locale}/{ns}: keys differ from en "
                                f"(missing {sorted(set(base) - set(target))}, extra {sorted(set(target) - set(base))})")
            empty = [k for k, v in target.items() if not v.strip()]
            if empty:
                problems.append(f"locales: {locale}/{ns}: empty strings: {empty}")
            mismatched = [k for k in base if k in target and holders(base[k]) != holders(target[k])]
            if mismatched:
                problems.append(f"locales: {locale}/{ns}: placeholders differ: {mismatched}")


check_testids()
check_features()
check_locales()
for p in problems:
    print(p)
print("spec contracts ok" if not problems else f"{len(problems)} problem(s)")
sys.exit(1 if problems else 0)
