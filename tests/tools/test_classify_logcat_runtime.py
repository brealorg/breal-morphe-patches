#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools" / "classify-logcat-runtime.py"


def load_module():
    spec = importlib.util.spec_from_file_location("classify_logcat_runtime", TOOL)
    assert spec and spec.loader
    mod = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = mod
    spec.loader.exec_module(mod)
    return mod


def run_tool(text: str, *args: str) -> subprocess.CompletedProcess[str]:
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as tmp:
        tmp.write(text)
        path = tmp.name
    try:
        return subprocess.run(
            [sys.executable, str(TOOL), "--logcat", path, *args],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
    finally:
        Path(path).unlink(missing_ok=True)


def parse_kv(stdout: str) -> dict[str, str]:
    out = {}
    for line in stdout.splitlines():
        if "=" in line and line.split("=", 1)[0].isupper():
            k, v = line.split("=", 1)
            out[k] = v
    return out


def assert_classification(proc: subprocess.CompletedProcess[str], expected: str, expected_rc: int) -> dict[str, str]:
    if proc.returncode != expected_rc:
        raise AssertionError(f"rc={proc.returncode}, expected {expected_rc}\nSTDOUT:\n{proc.stdout}\nSTDERR:\n{proc.stderr}")
    kv = parse_kv(proc.stdout)
    if kv.get("CLASSIFICATION") != expected:
        raise AssertionError(f"classification={kv.get('CLASSIFICATION')}, expected {expected}\nSTDOUT:\n{proc.stdout}")
    return kv


def test_monkey_androidruntime_noise_is_pass():
    text = """
07-03 15:23:01.547 D/AndroidRuntime(10647): >>>>>> START com.android.internal.os.RuntimeInit uid 2000 <<<<<<
07-03 15:23:02.426 D/AndroidRuntime(10647): Calling main entry com.android.commands.monkey.Monkey
07-03 15:23:02.659 I/AndroidRuntime(10647): VM exiting with result code 0.
"""
    proc = run_tool(text, "--package", "com.rubenmayayo.reddit")
    kv = assert_classification(proc, "PASS", 0)
    assert kv["ANDROID_RUNTIME"] == "3"
    assert kv["FATAL_EXCEPTION"] == "0"


def test_target_fatal_exception_is_fail():
    text = """
07-03 10:00:00.000 E/AndroidRuntime(123): FATAL EXCEPTION: main
07-03 10:00:00.001 E/AndroidRuntime(123): Process: com.rubenmayayo.reddit, PID: 123
07-03 10:00:00.002 E/AndroidRuntime(123): java.lang.NullPointerException: boom
"""
    proc = run_tool(text, "--package", "com.rubenmayayo.reddit")
    kv = assert_classification(proc, "FAIL", 1)
    assert kv["APP_CRASH"] == "yes"
    assert kv["HARD_BLOCKERS"] == "1"


def test_foreign_fatal_exception_is_warn_with_package_filter():
    text = """
07-03 10:00:00.000 E/AndroidRuntime(123): FATAL EXCEPTION: main
07-03 10:00:00.001 E/AndroidRuntime(123): Process: com.other.app, PID: 123
"""
    proc = run_tool(text, "--package", "com.rubenmayayo.reddit")
    kv = assert_classification(proc, "WARN", 0)
    assert kv["APP_CRASH"] == "unknown"


def test_fatal_without_package_filter_is_fail():
    text = "E/AndroidRuntime: FATAL EXCEPTION: main\n"
    proc = run_tool(text)
    assert_classification(proc, "FAIL", 1)


def test_activity_not_found_is_fail():
    text = "E/AndroidRuntime: android.content.ActivityNotFoundException: Unable to find explicit activity class\n"
    proc = run_tool(text, "--package", "com.rubenmayayo.reddit.dev")
    assert_classification(proc, "FAIL", 1)


def test_install_failed_is_fail():
    proc = run_tool("Failure [INSTALL_FAILED_CONFLICTING_PROVIDER]\n")
    assert_classification(proc, "FAIL", 1)


def test_http_403_is_warning_not_fail():
    text = "W/System.err: net.dean.jraw.http.NetworkException: Request returned non-successful status code: 403 Blocked\n"
    proc = run_tool(text, "--package", "com.rubenmayayo.reddit.dev")
    kv = assert_classification(proc, "WARN", 0)
    assert kv["HARD_BLOCKERS"] == "0"


def test_benign_firebase_init_is_pass():
    text = """
I/FirebaseApp: Device unlocked: initializing all Firebase APIs for app [DEFAULT]
I/FA: App measurement initialized
"""
    proc = run_tool(text, "--package", "com.rubenmayayo.reddit")
    assert_classification(proc, "PASS", 0)


def test_crashlytics_settings_noise_is_warning():
    proc = run_tool("W/FirebaseCrashlytics: Settings request failed\n")
    assert_classification(proc, "WARN", 0)


def test_expected_activity_seen_and_missing():
    seen = "I ActivityTaskManager: START u0 {cmp=com.rubenmayayo.reddit/com.rubenmayayo.reddit.ui.activities.MediaImageActivity}\n"
    proc_seen = run_tool(seen, "--expect-activity", "MediaImageActivity")
    kv_seen = assert_classification(proc_seen, "PASS", 0)
    assert kv_seen["EXPECTED_ACTIVITY_SEEN"] == "yes"

    proc_missing = run_tool(seen, "--expect-activity", "MediaVideoActivity")
    kv_missing = assert_classification(proc_missing, "WARN", 0)
    assert kv_missing["EXPECTED_ACTIVITY_SEEN"] == "no"


def test_json_output():
    proc = run_tool("I/FA: App measurement initialized\n", "--format", "json")
    if proc.returncode != 0:
        raise AssertionError(proc.stderr)
    data = json.loads(proc.stdout)
    assert data["classification"] == "PASS"
    assert data["hard_blockers"] == 0


def test_direct_classifier_api():
    mod = load_module()
    result = mod.classify_text(
        "E/AndroidRuntime: FATAL EXCEPTION: main\nE/AndroidRuntime: Process: com.rubenmayayo.reddit\n",
        packages=["com.rubenmayayo.reddit"],
        expect_activities=[],
        hard_patterns=[],
        require_patterns=[],
    )
    assert result.classification == "FAIL"
    assert len(result.hard_blockers) == 1


def main() -> int:
    tests = [
        test_monkey_androidruntime_noise_is_pass,
        test_target_fatal_exception_is_fail,
        test_foreign_fatal_exception_is_warn_with_package_filter,
        test_fatal_without_package_filter_is_fail,
        test_activity_not_found_is_fail,
        test_install_failed_is_fail,
        test_http_403_is_warning_not_fail,
        test_benign_firebase_init_is_pass,
        test_crashlytics_settings_noise_is_warning,
        test_expected_activity_seen_and_missing,
        test_json_output,
        test_direct_classifier_api,
    ]

    for test in tests:
        print(f"RUN {test.__name__}")
        test()
        print(f"OK  {test.__name__}")

    print("RESULT=MORPHE_PL08B_LOGCAT_CLASSIFIER_TEST_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
