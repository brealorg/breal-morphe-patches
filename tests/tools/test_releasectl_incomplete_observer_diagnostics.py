#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "scripts" / "releasectl.py"

SPEC = importlib.util.spec_from_file_location(
    "morphe_releasectl",
    SOURCE,
)
assert SPEC is not None
assert SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def complete_result() -> SimpleNamespace:
    return SimpleNamespace(
        observations_complete=True,
        errors=(),
        warnings=(),
    )


class IncompleteObserverDiagnosticsTest(unittest.TestCase):
    def test_reports_incomplete_github_release_error(self) -> None:
        local_git = SimpleNamespace(
            safety=SimpleNamespace(
                observations_complete=True,
                current_branch_is_main=True,
                worktree_clean=True,
                index_clean=True,
                required_tools_available=True,
            ),
            errors=(),
            warnings=(),
        )
        github_release = SimpleNamespace(
            observations_complete=False,
            errors=(
                "GitHub repository metadata is missing "
                "the authenticated permissions object",
            ),
            warnings=(),
        )
        inspection = SimpleNamespace(
            local_git=local_git,
            remote_git=complete_result(),
            github_release=github_release,
            local_artifacts=complete_result(),
            local_metadata=SimpleNamespace(
                observations_complete=True,
                errors=(),
                warnings=(),
                mismatches=(),
            ),
            remote_assets=complete_result(),
            errors=github_release.errors,
            warnings=(),
        )

        result = MODULE._incomplete_observation_diagnostics(
            inspection
        )

        self.assertIn("github_release=INCOMPLETE", result)
        self.assertIn(
            "missing the authenticated permissions object",
            result,
        )

    def test_reports_dirty_local_git_safety_state(self) -> None:
        local_git = SimpleNamespace(
            safety=SimpleNamespace(
                observations_complete=False,
                current_branch_is_main=True,
                worktree_clean=False,
                index_clean=True,
                required_tools_available=True,
            ),
            errors=(),
            warnings=(),
        )
        inspection = SimpleNamespace(
            local_git=local_git,
            remote_git=complete_result(),
            github_release=complete_result(),
            local_artifacts=complete_result(),
            local_metadata=SimpleNamespace(
                observations_complete=True,
                errors=(),
                warnings=(),
                mismatches=(),
            ),
            remote_assets=complete_result(),
            errors=(),
            warnings=(),
        )

        result = MODULE._incomplete_observation_diagnostics(
            inspection
        )

        self.assertIn("local_git=INCOMPLETE", result)
        self.assertIn("worktree_clean=False", result)
        self.assertIn("index_clean=True", result)


if __name__ == "__main__":
    unittest.main()
