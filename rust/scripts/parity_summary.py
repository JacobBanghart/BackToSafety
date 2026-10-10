#!/usr/bin/env python3
"""Builds the rust:parity summary (summary.md and results.json) from the per-device outputs.

Reads <out>/<serial>/{compare.log,flows.log,gestures.log} as written by mise-tasks/rust/parity,
which hold rust:compare's, rust:flows' and reorder.sh's output. One row per (state, mode),
flow and gesture; a row the logs have no result for is a failure, with the reason found from
what was captured. Exit status 1 when any row failed.
"""

import argparse
import json
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path


def parse_compare(log: str) -> dict[tuple[str, str], dict]:
    """compare_screens.py's lines per mode -> {(mode, state): {result, deltas}}."""
    results: dict[tuple[str, str], dict] = {}
    mode = None
    for line in log.splitlines():
        if m := re.match(r"== (\S+): rust vs oracle", line):
            mode = m.group(1)
        elif mode and (m := re.match(r"ok\s+(\S+):", line)):
            results[(mode, m.group(1))] = {"result": "pass", "deltas": []}
        elif mode and (m := re.match(r"FAIL (\S+):\s*(.*)", line)):
            deltas = [d.strip() for d in m.group(2).split(";") if d.strip()]
            results[(mode, m.group(1))] = {"result": "fail", "deltas": deltas}
        elif mode and (m := re.match(r"CANDIDATE (\S+)", line)):
            results[(mode, m.group(1))] = {"result": "fail", "deltas": ["no oracle capture to compare with"]}
    return results


def parse_flows(log: str) -> dict[str, dict]:
    """rust:flows' lines: 'ok   name' or 'FAIL name (log)' followed by indented detail."""
    results: dict[str, dict] = {}
    last = None
    for line in log.splitlines():
        if m := re.match(r"ok\s+(\S+)\s*$", line):
            results[m.group(1)] = {"result": "pass", "detail": "", "log": ""}
            last = None
        elif m := re.match(r"FAIL (\S+) \((.*)\)", line):
            results[m.group(1)] = {"result": "fail", "detail": "", "log": m.group(2)}
            last = m.group(1)
        elif last and line.startswith("     "):
            d = results[last]
            d["detail"] = (d["detail"] + " / " if d["detail"] else "") + line.strip()
    return results


def parse_gestures(log: str) -> dict[str, dict]:
    """reorder.sh: '<screen>: reorder ok' or '<screen>: setup failed (log)' / 'reorder not saved'."""
    results: dict[str, dict] = {}
    for line in log.splitlines():
        if m := re.match(r"(\w+): reorder ok", line):
            results[m.group(1)] = {"result": "pass", "detail": ""}
        elif m := re.match(r"(\w+): (.*)", line):
            results[m.group(1)] = {"result": "fail", "detail": m.group(2)}
    return results


def short(path: Path) -> str:
    return str(path)


def build(out: Path, captures: Path, modes: list[str], states: list[str], flows: list[str], gestures: bool):
    devices = sorted(d for d in out.iterdir() if d.is_dir())
    compare: dict[tuple[str, str], tuple[Path, dict]] = {}
    flow_results: dict[str, tuple[Path, dict]] = {}
    gesture_results: dict[str, tuple[Path, dict]] = {}
    for d in devices:
        if (d / "compare.log").exists():
            for k, v in parse_compare((d / "compare.log").read_text(errors="replace")).items():
                compare[k] = (d, v)
        if (d / "flows.log").exists():
            for k, v in parse_flows((d / "flows.log").read_text(errors="replace")).items():
                flow_results[k] = (d, v)
        if (d / "gestures.log").exists():
            for k, v in parse_gestures((d / "gestures.log").read_text(errors="replace")).items():
                gesture_results[k] = (d, v)

    rows = []
    for state in states:
        for mode in modes:
            found = compare.get((mode, state))
            dev = found[0] if found else next((d for d in devices if (d / "compare/rust" / mode / f"{state}.log").exists()), None)
            rust_png = dev / "compare/rust" / mode / f"{state}.png" if dev else None
            oracle_png = captures / mode / f"{state}.png"
            if found:
                result, deltas = found[1]["result"], found[1]["deltas"]
            elif dev and rust_png and not rust_png.exists():
                result, deltas = "fail", [f"Rust capture failed (see {dev / 'compare/rust' / mode / (state + '.log')})"]
            elif not oracle_png.exists():
                result, deltas = "fail", ["oracle capture missing"]
            else:
                result, deltas = "fail", ["not run"]
            rows.append({
                "kind": "state", "name": state, "mode": mode, "result": result, "deltas": deltas,
                "rust": str(rust_png) if rust_png and rust_png.exists() else "",
                "oracle": str(oracle_png) if oracle_png.exists() else "",
            })
    for flow in flows:
        found = flow_results.get(flow)
        if found:
            d, r = found
            rows.append({"kind": "flow", "name": flow, "result": r["result"], "detail": r["detail"],
                         "log": r["log"] or str(d / "flows" / f"{flow}.log")})
        else:
            rows.append({"kind": "flow", "name": flow, "result": "fail", "detail": "not run", "log": ""})
    if gestures:
        for screen in ("contacts", "destinations"):
            found = gesture_results.get(screen)
            if found:
                d, r = found
                rows.append({"kind": "gesture", "name": f"reorder {screen}", "result": r["result"],
                             "detail": r["detail"], "log": str(d / f"reorder-{screen}.log")})
            else:
                rows.append({"kind": "gesture", "name": f"reorder {screen}", "result": "fail", "detail": "not run", "log": ""})
    return rows


def markdown(rows, meta) -> str:
    fails = [r for r in rows if r["result"] != "pass"]
    mark = lambda r: "pass" if r["result"] == "pass" else "**FAIL**"
    lines = [
        "# Rust parity run",
        "",
        f"- when: {meta['when']}",
        f"- rust: {meta['rust']}; oracle: kmp @ {meta['oracle']}",
        f"- devices: {meta['serials']}",
        f"- **{len(rows) - len(fails)} pass, {len(fails)} fail** of {len(rows)}",
        "",
    ]
    if fails:
        lines += ["## Failing", ""]
        for r in fails:
            what = f"{r['name']} ({r['mode']})" if r["kind"] == "state" else f"{r['kind']} {r['name']}"
            why = "; ".join(r["deltas"]) if r["kind"] == "state" else r["detail"]
            lines.append(f"- {what}: {why}")
        lines.append("")
    states = [r for r in rows if r["kind"] == "state"]
    if states:
        lines += ["## States (layout vs the oracle)", "", "| state | mode | result | layout deltas | rust | oracle |", "|---|---|---|---|---|---|"]
        for r in states:
            deltas = "<br>".join(r["deltas"]).replace("|", "\\|") or "none"
            lines.append(f"| {r['name']} | {r['mode']} | {mark(r)} | {deltas} | {r['rust']} | {r['oracle']} |")
        lines.append("")
    for kind, title in (("flow", "Flows"), ("gesture", "Gestures")):
        part = [r for r in rows if r["kind"] == kind]
        if part:
            lines += [f"## {title}", "", "| name | result | detail | log |", "|---|---|---|---|"]
            for r in part:
                lines.append(f"| {r['name']} | {mark(r)} | {r['detail'].replace('|', chr(92) + '|')} | {r['log']} |")
            lines.append("")
    return "\n".join(lines)


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("out", type=Path)
    p.add_argument("--captures", type=Path, required=True)
    p.add_argument("--commit", default="unknown")
    p.add_argument("--modes", default="light dark large-text")
    p.add_argument("--states", default="")
    p.add_argument("--flows", default="")
    p.add_argument("--gestures", action="store_true")
    p.add_argument("--serials", default="")
    a = p.parse_args()

    rows = build(a.out, a.captures, a.modes.split(), a.states.split(), a.flows.split(), a.gestures)
    rust = subprocess.run(["git", "rev-parse", "--short", "HEAD"], capture_output=True, text=True).stdout.strip() or "unknown"
    meta = {"when": datetime.now().strftime("%Y-%m-%d %H:%M"), "rust": f"{rust}", "oracle": a.commit, "serials": a.serials}
    (a.out / "summary.md").write_text(markdown(rows, meta))
    (a.out / "results.json").write_text(json.dumps({"meta": meta, "rows": rows}, indent=2))
    failed = sum(r["result"] != "pass" for r in rows)
    print(f"{len(rows) - failed} pass, {failed} fail of {len(rows)}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
