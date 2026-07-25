#!/usr/bin/env python3
from __future__ import annotations

import ast
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools" / "morphe_flow_operations.py"
LAUNCHER = ROOT / "tools" / "morphe-flow.py"


class OperationSourceContractTest(unittest.TestCase):
    def test_operation_scoped_markers_exist(self) -> None:
        text = SOURCE.read_text(encoding="utf-8")
        self.assertIn("REPORT_ONLY_NEVER_GATE_SYNC_LOCAL_MAIN", text)
        self.assertIn("Verify only the postconditions causally relevant", text)
        self.assertIn('mutations: str = "NONE"', text)
        self.assertIn("CHECK={check.field}", text)
        self.assertNotIn("worktree_registry_sha256", text)
        self.assertNotIn("target_index_mtime_ns", text)

    def test_launcher_bootstraps_sibling_module_before_import(self) -> None:
        text = LAUNCHER.read_text(encoding="utf-8")
        tools_dir = '_TOOLS_DIR = str(Path(__file__).resolve().parent)'
        path_insert = 'sys.path.insert(0, _TOOLS_DIR)'
        sibling_import = 'from morphe_flow_operations import ('
        self.assertIn(tools_dir, text)
        self.assertIn(path_insert, text)
        self.assertIn(sibling_import, text)
        self.assertLess(text.index(path_insert), text.index(sibling_import))

    def test_subprocess_execution_is_centralized(self) -> None:
        tree = ast.parse(SOURCE.read_text(encoding="utf-8"))
        calls = []
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call):
                continue
            func = node.func
            if isinstance(func, ast.Attribute) and func.attr == "run":
                if isinstance(func.value, ast.Name) and func.value.id == "subprocess":
                    calls.append(node.lineno)
        self.assertEqual(1, len(calls), calls)

    def test_mutating_git_commands_are_rejected(self) -> None:
        text = SOURCE.read_text(encoding="utf-8")
        tree = ast.parse(text)
        functions = {
            node.name: node
            for node in ast.walk(tree)
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
        }
        self.assertIn("_validate", functions)
        validator = ast.get_source_segment(text, functions["_validate"])
        assert validator is not None
        for command in ("fetch", "merge", "push", "checkout", "commit", "branch"):
            self.assertNotIn(f'command == "{command}"', validator)
        self.assertIn("refusing non-read-only operation Git command", validator)


if __name__ == "__main__":
    unittest.main()
