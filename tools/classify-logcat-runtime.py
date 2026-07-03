#!/usr/bin/env python3
"""Classify Android logcat runtime evidence.

Read-only parser. It does not call adb, install APKs, mutate app data, or touch
device state. Feed it a captured logcat file or stdin.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable


@dataclass(frozen=True)
class Evidence:
    level: str
    category: str
    line_no: int
    message: str
    line: str


@dataclass
class Result:
    classification: str = "UNKNOWN"
    hard_blockers: list[Evidence] = field(default_factory=list)
    warnings: list[Evidence] = field(default_factory=list)
    noise: list[Evidence] = field(default_factory=list)
    relevant: list[Evidence] = field(default_factory=list)
    android_runtime_count: int = 0
    fatal_exception_count: int = 0
    expected_activity_seen: bool | None = None

    @property
    def app_crash(self) -> str:
        if any(ev.category in {"target_fatal_exception", "target_android_runtime"} for ev in self.hard_blockers):
            return "yes"
        if self.fatal_exception_count:
            return "unknown"
        return "no"

    def finalize(self) -> None:
        if self.hard_blockers:
            self.classification = "FAIL"
        elif self.warnings:
            self.classification = "WARN"
        elif self.relevant or self.noise:
            self.classification = "PASS"
        else:
            self.classification = "UNKNOWN"


def add_unique(items: list[Evidence], ev: Evidence) -> None:
    key = (ev.level, ev.category, ev.line_no, ev.message, ev.line)
    if all((x.level, x.category, x.line_no, x.message, x.line) != key for x in items):
        items.append(ev)


def make_ev(level: str, category: str, line_no: int, message: str, line: str) -> Evidence:
    return Evidence(level, category, line_no, message, line.rstrip("\n"))


def context(lines: list[str], index: int, before: int = 4, after: int = 12) -> list[str]:
    return lines[max(0, index - before): min(len(lines), index + after + 1)]


def any_package_in(lines: Iterable[str], packages: list[str]) -> bool:
    return bool(packages) and any(pkg in line for pkg in packages for line in lines)


def is_shell_androidruntime(ctx: list[str]) -> bool:
    text = "\n".join(ctx).lower()
    markers = [
        "com.android.commands.monkey.monkey",
        "com.android.commands.uiautomator",
        ">>>>>> start com.android.internal.os.runtimeinit",
        "calling main entry com.android.commands.monkey",
        "calling main entry com.android.commands.uiautomator",
        "vm exiting with result code 0",
        "shutting down vm",
    ]
    return "androidruntime" in text and any(m in text for m in markers)


def is_connectivity_probe(line: str) -> bool:
    low = line.lower()
    return "networkmonitor" in low and ("generate_204" in low or "connectivitycheck" in low)


def is_benign_firebase(line: str) -> bool:
    low = line.lower()
    return any(m in low for m in [
        "firebaseapp",
        "firebaseinitprovider",
        " i/fa",
        " d/fa",
        " v/fa",
        "i/fa:",
        "d/fa:",
        "v/fa:",
    ])


def classify_text(
    text: str,
    *,
    packages: list[str],
    expect_activities: list[str],
    hard_patterns: list[str],
    require_patterns: list[str],
) -> Result:
    result = Result()
    lines = text.splitlines()

    if not lines:
        result.finalize()
        return result

    required_seen = {pattern: False for pattern in require_patterns}

    for i, line in enumerate(lines):
        line_no = i + 1
        low = line.lower()
        ctx = context(lines, i)
        target_context = any_package_in(ctx, packages)

        if "androidruntime" in low:
            result.android_runtime_count += 1

        for pattern in hard_patterns:
            if pattern and pattern.lower() in low:
                ev = make_ev("FAIL", "hard_pattern", line_no, f"hard pattern matched: {pattern}", line)
                add_unique(result.hard_blockers, ev)
                add_unique(result.relevant, ev)

        for pattern in require_patterns:
            if pattern and pattern.lower() in low:
                required_seen[pattern] = True

        if "fatal exception" in low:
            result.fatal_exception_count += 1
            if not packages or target_context:
                ev = make_ev("FAIL", "target_fatal_exception", line_no, "FATAL EXCEPTION with target or unspecified package context", line)
                add_unique(result.hard_blockers, ev)
                add_unique(result.relevant, ev)
            else:
                ev = make_ev("WARN", "foreign_fatal_exception", line_no, "FATAL EXCEPTION outside requested package context", line)
                add_unique(result.warnings, ev)
                add_unique(result.relevant, ev)
            continue

        if "install_failed" in low:
            ev = make_ev("FAIL", "install_failed", line_no, "INSTALL_FAILED marker", line)
            add_unique(result.hard_blockers, ev)
            add_unique(result.relevant, ev)
            continue

        if "activitynotfoundexception" in low:
            ev = make_ev("FAIL", "activity_not_found", line_no, "ActivityNotFoundException runtime blocker", line)
            add_unique(result.hard_blockers, ev)
            add_unique(result.relevant, ev)
            continue

        if "androidruntime" in low:
            if is_shell_androidruntime(ctx):
                ev = make_ev("NOISE", "android_runtime_shell_tool", line_no, "AndroidRuntime from shell/monkey/uiautomator", line)
                add_unique(result.noise, ev)
            elif not packages or target_context:
                ev = make_ev("WARN", "android_runtime_unclassified", line_no, "AndroidRuntime line without FATAL EXCEPTION; review context", line)
                add_unique(result.warnings, ev)
                add_unique(result.relevant, ev)
            else:
                ev = make_ev("NOISE", "foreign_android_runtime", line_no, "AndroidRuntime outside requested package context", line)
                add_unique(result.noise, ev)

        if "anr in" in low or "application not responding" in low:
            level = "WARN" if not packages or target_context else "NOISE"
            category = "anr_target_context" if level == "WARN" else "foreign_anr"
            ev = make_ev(level, category, line_no, "ANR marker", line)
            add_unique(result.warnings if level == "WARN" else result.noise, ev)
            if level == "WARN":
                add_unique(result.relevant, ev)

        if "firebasecrashlytics" in low or "firebase-settings" in low or "settings request failed" in low:
            ev = make_ev("WARN", "firebase_crashlytics_noise_watch", line_no, "Firebase/Crashlytics signal; review acceptance policy", line)
            add_unique(result.warnings, ev)
            add_unique(result.relevant, ev)
            continue

        if is_benign_firebase(line):
            ev = make_ev("NOISE", "firebase_benign", line_no, "Benign Firebase/FA initialization", line)
            add_unique(result.noise, ev)

        if is_connectivity_probe(line):
            ev = make_ev("NOISE", "connectivity_probe", line_no, "Generic connectivity probe", line)
            add_unique(result.noise, ev)
            continue

        if re.search(r"\b(403|404)\b", line) or "filenotfoundexception" in low:
            if any(x in low for x in ["i.redd.it", ".mp4", "giphy", "jraw"]) or target_context:
                ev = make_ev("WARN", "http_or_media_error", line_no, "HTTP/media/API error; needs flow context", line)
                add_unique(result.warnings, ev)
                add_unique(result.relevant, ev)
            else:
                ev = make_ev("NOISE", "generic_http_error", line_no, "Generic HTTP error without target context", line)
                add_unique(result.noise, ev)

        if any(x.lower() in low for x in ["mediaimageactivity", "mediavideoactivity", "inlinegiphy", "exoplayer", "gifioexception"]):
            if "gifioexception" in low:
                ev = make_ev("WARN", "gif_io_exception", line_no, "GIF decoder exception", line)
                add_unique(result.warnings, ev)
                add_unique(result.relevant, ev)
            else:
                ev = make_ev("NOISE", "media_routing_evidence", line_no, "Media routing/player evidence", line)
                add_unique(result.noise, ev)
                add_unique(result.relevant, ev)

        if packages and any_package_in([line], packages) and any(x in low for x in ["exception", "crash", "error"]):
            if not any(ev.line_no == line_no for ev in result.hard_blockers + result.warnings):
                ev = make_ev("WARN", "target_exception_or_error", line_no, "Target package exception/error marker", line)
                add_unique(result.warnings, ev)
                add_unique(result.relevant, ev)

    if expect_activities:
        seen = any(any(activity in line for activity in expect_activities) for line in lines)
        result.expected_activity_seen = seen
        if seen:
            ev = make_ev("NOISE", "expected_activity_seen", 0, "Expected activity observed: " + ", ".join(expect_activities), "")
            add_unique(result.noise, ev)
        else:
            ev = make_ev("WARN", "expected_activity_missing", 0, "Expected activity missing: " + ", ".join(expect_activities), "")
            add_unique(result.warnings, ev)

    for pattern, seen in required_seen.items():
        if not seen:
            ev = make_ev("WARN", "required_pattern_missing", 0, f"required pattern missing: {pattern}", "")
            add_unique(result.warnings, ev)

    result.finalize()
    return result


def result_to_dict(result: Result) -> dict[str, object]:
    def ev_dict(ev: Evidence) -> dict[str, object]:
        return {
            "level": ev.level,
            "category": ev.category,
            "line_no": ev.line_no,
            "message": ev.message,
            "line": ev.line,
        }

    return {
        "classification": result.classification,
        "hard_blockers": len(result.hard_blockers),
        "warnings": len(result.warnings),
        "noise": len(result.noise),
        "app_crash": result.app_crash,
        "android_runtime": result.android_runtime_count,
        "fatal_exception": result.fatal_exception_count,
        "relevant_lines": len(result.relevant),
        "expected_activity_seen": result.expected_activity_seen,
        "hard_blocker_evidence": [ev_dict(ev) for ev in result.hard_blockers],
        "warning_evidence": [ev_dict(ev) for ev in result.warnings],
        "noise_evidence": [ev_dict(ev) for ev in result.noise[:25]],
    }


def print_text(result: Result) -> None:
    print(f"CLASSIFICATION={result.classification}")
    print(f"HARD_BLOCKERS={len(result.hard_blockers)}")
    print(f"WARNINGS={len(result.warnings)}")
    print(f"NOISE={len(result.noise)}")
    print(f"APP_CRASH={result.app_crash}")
    print(f"ANDROID_RUNTIME={result.android_runtime_count}")
    print(f"FATAL_EXCEPTION={result.fatal_exception_count}")
    print(f"RELEVANT_LINES={len(result.relevant)}")
    if result.expected_activity_seen is not None:
        print(f"EXPECTED_ACTIVITY_SEEN={'yes' if result.expected_activity_seen else 'no'}")

    for title, items in [
        ("hard blockers", result.hard_blockers),
        ("warnings", result.warnings),
        ("noise sample", result.noise[:20]),
    ]:
        if not items:
            continue
        print()
        print(f"===== {title} =====")
        for ev in items[:40]:
            loc = f"line {ev.line_no}" if ev.line_no else "global"
            print(f"- {ev.level} {ev.category} ({loc}): {ev.message}")
            if ev.line:
                print(f"  {ev.line[:260]}")


def read_logcat(path: str | None) -> str:
    if not path or path == "-":
        return sys.stdin.read()
    p = Path(path)
    if not p.exists():
        raise FileNotFoundError(f"logcat file does not exist: {p}")
    return p.read_text(encoding="utf-8", errors="replace")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Classify Android logcat runtime evidence.")
    parser.add_argument("--logcat", default="-", help="Logcat path, or '-' / omitted for stdin.")
    parser.add_argument("--package", action="append", default=[], help="Target package/process context. Repeatable.")
    parser.add_argument("--activity", action="append", default=[], help="Expected activity marker. Alias for --expect-activity.")
    parser.add_argument("--expect-activity", action="append", default=[], help="Expected activity marker. Repeatable.")
    parser.add_argument("--pattern", action="append", default=[], help="Hard-blocker substring pattern. Repeatable.")
    parser.add_argument("--hard-pattern", action="append", default=[], help="Hard-blocker substring pattern. Repeatable.")
    parser.add_argument("--require-pattern", action="append", default=[], help="Warn if substring pattern is not found. Repeatable.")
    parser.add_argument("--format", choices=["text", "json"], default="text")
    parser.add_argument("--fail-on-warn", action="store_true", help="Exit 1 for WARN as well as FAIL.")
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    try:
        text = read_logcat(args.logcat)
    except OSError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 2

    result = classify_text(
        text,
        packages=args.package,
        expect_activities=[*args.activity, *args.expect_activity],
        hard_patterns=[*args.pattern, *args.hard_pattern],
        require_patterns=args.require_pattern,
    )

    if args.format == "json":
        print(json.dumps(result_to_dict(result), indent=2, sort_keys=True))
    else:
        print_text(result)

    if result.classification == "FAIL":
        return 1
    if result.classification == "WARN" and args.fail_on_warn:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
