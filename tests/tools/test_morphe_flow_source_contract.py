#!/usr/bin/env python3
from __future__ import annotations

import ast
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools" / "morphe-flow.py"


class MorpheFlowSourceContractTest(unittest.TestCase):
    def test_read_only_contract_markers_exist_once(self) -> None:
        text = SOURCE.read_text(encoding="utf-8")
        self.assertEqual(1, text.count("Hardening v22.1 deliberately performs no Git or GitHub mutation"))
        self.assertIn('mutations: str = "NONE"', text)
        self.assertIn("report_fingerprint: str", text)
        self.assertIn("refusing non-read-only Git operation", text)

    def test_all_subprocess_calls_are_centralized(self) -> None:
        tree = ast.parse(SOURCE.read_text(encoding="utf-8"))
        calls: list[tuple[str, int]] = []
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call):
                continue
            function = node.func
            if (
                isinstance(function, ast.Attribute)
                and isinstance(function.value, ast.Name)
                and function.value.id == "subprocess"
            ):
                calls.append((function.attr, node.lineno))
        self.assertEqual(1, len(calls))
        self.assertEqual("run", calls[0][0])

    def test_no_shell_execution(self) -> None:
        text = SOURCE.read_text(encoding="utf-8")
        self.assertNotIn("shell=True", text)
        self.assertNotIn("os.system", text)
        self.assertNotIn("subprocess.Popen", text)


if __name__ == "__main__":
    unittest.main()
