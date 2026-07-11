#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
DOC = ROOT / "docs" / "release-state.md"


class ReleaseStateDocumentationTests(unittest.TestCase):
    def test_d01_documentation_exists_and_has_no_trailing_whitespace(self) -> None:
        self.assertTrue(DOC.is_file())
        text = DOC.read_text(encoding="utf-8")
        self.assertFalse(
            [
                number
                for number, line in enumerate(text.splitlines(), start=1)
                if line.endswith((" ", "\t"))
            ]
        )

    def test_d02_all_read_only_commands_are_documented(self) -> None:
        text = DOC.read_text(encoding="utf-8")
        for command in (
            "scripts/releasectl.py inspect",
            "scripts/releasectl.py plan",
            "scripts/releasectl.py verify",
        ):
            self.assertIn(command, text)

    def test_d03_every_mutating_phase_requires_reinspection(self) -> None:
        text = DOC.read_text(encoding="utf-8")
        for phrase in (
            "before local finalization",
            "before the atomic release-ref push",
            "before draft release creation",
            "before each asset upload",
            "before draft publication",
            "Re-run the command after every mutation",
        ):
            self.assertIn(phrase, text)

    def test_d04_state_and_exit_contract_is_complete(self) -> None:
        text = DOC.read_text(encoding="utf-8")
        for state in (
            "NOT_FINALIZED",
            "LOCAL_FINALIZED",
            "READY_TO_PUBLISH",
            "PARTIALLY_PUBLISHED",
            "PUBLISHED_NOT_VERIFIED",
            "PUBLISHED_AND_VERIFIED",
            "INCONSISTENT_ABORT",
        ):
            self.assertIn(state, text)
        for code in ("`0`", "`2`", "`3`", "`4`", "`5`"):
            self.assertIn(code, text)


    def test_d05_historical_release_branch_advance_is_documented(self) -> None:
        text = DOC.read_text(encoding="utf-8")
        for phrase in (
            "annotated tag is the exact immutable commit anchor",
            "advance after publication",
            "must not invalidate an otherwise verified historical release",
            "divergent branch remains a concrete conflict",
        ):
            self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
