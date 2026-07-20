from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
USES_PATTERN = re.compile(r"^\s*-?\s*uses:\s*([^\s#]+)", re.MULTILINE)
FULL_SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")


class GitHubActionsShaPinningSourceContractTest(unittest.TestCase):
    def test_remote_actions_are_pinned_to_full_commit_shas(self) -> None:
        workflow_root = ROOT / ".github" / "workflows"
        remote_uses = 0

        for path in sorted(workflow_root.glob("*.yml")):
            text = path.read_text(encoding="utf-8")
            for action in USES_PATTERN.findall(text):
                if action.startswith("./") or action.startswith("docker://"):
                    continue

                remote_uses += 1
                self.assertIn("@", action, f"{path}: {action}")
                reference = action.rsplit("@", 1)[1]
                self.assertRegex(
                    reference,
                    FULL_SHA_PATTERN,
                    f"{path}: mutable action reference {action}",
                )

        self.assertGreater(remote_uses, 0)


if __name__ == "__main__":
    unittest.main()
