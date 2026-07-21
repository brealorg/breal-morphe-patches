#!/usr/bin/env python3
"""Write a deterministic JSON report for the Boost DEV settings audit."""

import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path


ITEM_PATTERN = re.compile(
    r"MORPHE_AUDIT_ITEM_OK "
    r"key=(?P<key>\S+) "
    r"domain_count=(?P<domain_count>\d+) "
    r"consumer=(?P<consumer>\S+) "
    r"effect=(?P<effect>\S+) "
    r"render=(?P<render>\S+)"
)
RENDER_PATTERN = re.compile(
    r"MORPHE_RENDER_AUDIT_ITEM_OK "
    r"probe=(?P<probe>\S+) keys=(?P<keys>\S+)"
)
PASS_MARKERS = {
    "write": "MORPHE_BINDING_AUDIT_WRITE_OK",
    "domain_actions": "MORPHE_DOMAIN_AUDIT_OK items=26 actions=196",
    "rendered_effects": "MORPHE_RENDER_AUDIT_OK count=6",
    "cold_reload": "MORPHE_BINDING_AUDIT_RELOAD_OK",
    "native_consumers_after_reload": "MORPHE_DOMAIN_AUDIT_RELOAD_OK count=26",
    "restore": "MORPHE_BINDING_AUDIT_RESTORE_OK",
    "app_result": (
        "RESULT=MORPHE_BOOST_SETTINGS_"
        "APPEARANCE_LAYOUT_AUDIT_V56_APP_PASS"
    ),
}


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--log", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--status", required=True, choices=("PASS", "FAIL"))
    parser.add_argument("--failure-reason", default="")
    parser.add_argument("--package", required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True)
    parser.add_argument("--apk-sha256", required=True)
    parser.add_argument("--android-release", required=True)
    parser.add_argument("--android-sdk", required=True)
    parser.add_argument("--device", required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--normal-boost-untouched", required=True)
    return parser.parse_args()


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def parse_items(log_text):
    items = []
    seen = set()
    for match in ITEM_PATTERN.finditer(log_text):
        key = match.group("key")
        if key in seen:
            raise ValueError(f"duplicate audit item: {key}")
        seen.add(key)
        items.append(
            {
                "key": key,
                "status": "PASS",
                "domain_count": int(match.group("domain_count")),
                "consumer": match.group("consumer"),
                "side_effect": match.group("effect"),
                "render_probe": match.group("render"),
            }
        )
    return items


def parse_render_probes(log_text):
    probes = []
    seen = set()
    for match in RENDER_PATTERN.finditer(log_text):
        name = match.group("probe")
        if name in seen:
            raise ValueError(f"duplicate render probe: {name}")
        seen.add(name)
        probes.append(
            {
                "probe": name,
                "status": "PASS",
                "keys": (
                    []
                    if match.group("keys") == "-"
                    else match.group("keys").split(",")
                ),
            }
        )
    return probes


def manifest_domain_count(spec, manifest):
    if spec["type"] == "boolean":
        return 2
    if spec["type"] == "integer":
        domain = spec["domain"]
        return domain["maximum"] - domain["minimum"] + 1
    if "domain" in spec:
        return len(spec["domain"])
    if spec["writer"] == "font":
        return len(manifest["font_domain"])
    return len(manifest["font_size_domain"])


def manifest_expectations(manifest):
    specs = manifest["appearance"] + manifest["post_views"] + manifest["fonts"]
    item_domains = {
        spec["key"]: manifest_domain_count(spec, manifest) for spec in specs
    }
    item_domains["saved_views"] = len(manifest["saved_views"]["domain"])
    item_domains["app_icon"] = len(manifest["app_icons"])
    render_probes = {
        probe["name"]: probe["keys"] for probe in manifest["render_probes"]
    }
    return item_domains, render_probes


def main():
    args = parse_args()
    log_text = args.log.read_text(encoding="utf-8", errors="replace")
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    errors = []
    try:
        items = parse_items(log_text)
        render_probes = parse_render_probes(log_text)
    except ValueError as error:
        errors.append(str(error))
        items = []
        render_probes = []

    phases = {
        name: marker in log_text for name, marker in PASS_MARKERS.items()
    }
    domain_actions = sum(item["domain_count"] for item in items)
    expected_items, expected_render_probes = manifest_expectations(manifest)
    if args.status == "PASS":
        if manifest.get("schema") != 2:
            errors.append("manifest schema is not 2")
        if manifest.get("scope") != "appearance-layout":
            errors.append("manifest scope mismatch")
        if manifest.get("harness_version") != 56:
            errors.append("manifest harness version mismatch")
        actual_items = {item["key"]: item["domain_count"] for item in items}
        if actual_items != expected_items:
            errors.append("audit item/domain manifest mismatch")
        expected_actions = sum(expected_items.values())
        if domain_actions != expected_actions:
            errors.append(
                f"expected {expected_actions} domain actions, got {domain_actions}"
            )
        actual_render_probes = {
            probe["probe"]: probe["keys"] for probe in render_probes
        }
        if actual_render_probes != expected_render_probes:
            errors.append("render-probe manifest mismatch")
        missing_phases = [name for name, passed in phases.items() if not passed]
        if missing_phases:
            errors.append("missing phases: " + ",".join(missing_phases))
        if args.normal_boost_untouched != "true":
            errors.append("Normal Boost untouched proof missing")

    status = "FAIL" if errors or args.status == "FAIL" else "PASS"
    reasons = [reason for reason in (args.failure_reason, *errors) if reason]
    report = {
        "schema": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "scope": manifest.get("scope"),
        "harness": {
            "version": manifest.get("harness_version"),
            "manifest_schema": manifest.get("schema"),
            "manifest_sha256": sha256(args.manifest),
        },
        "safety": manifest.get("safety"),
        "target": {
            "package": args.package,
            "version_name": args.version_name,
            "version_code": args.version_code,
            "apk_sha256": args.apk_sha256,
            "android_release": args.android_release,
            "android_sdk": args.android_sdk,
            "device": args.device,
            "model": args.model,
        },
        "result": {
            "status": status,
            "failure_reason": "; ".join(reasons) or None,
            "normal_boost_untouched": (
                args.normal_boost_untouched == "true"
            ),
        },
        "summary": {
            "audit_items": len(items),
            "domain_actions": domain_actions,
            "render_probes": len(render_probes),
        },
        "phases": phases,
        "items": items,
        "render_probes": render_probes,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"AUDIT_REPORT_STATUS={status}")
    print(f"AUDIT_REPORT_JSON={args.output}")
    return 0 if status == args.status else 1


if __name__ == "__main__":
    raise SystemExit(main())
