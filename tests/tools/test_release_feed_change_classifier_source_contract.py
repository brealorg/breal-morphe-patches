from __future__ import annotations

import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def load_classifier():
    path = ROOT / "scripts/classify-release-feed-change.py"
    spec = importlib.util.spec_from_file_location(
        "morphe_release_feed_change_classifier",
        path,
    )
    if spec is None or spec.loader is None:
        raise RuntimeError(f"could not load {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def run_git(repo: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(repo), *args],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    return result.stdout.strip()


class ReleaseFeedChangeClassifierSourceContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.classifier = load_classifier()

    def test_canonical_release_metadata_scope_is_exact(self) -> None:
        self.assertEqual(
            self.classifier.RELEASE_METADATA_PATHS,
            (
                "CHANGELOG.md",
                "README.md",
                "gradle.properties",
                "patches-bundle.json",
                "patches-list.json",
            ),
        )

    def test_code_only_paths_use_code_only_mode(self) -> None:
        mode, changed, metadata = (
            self.classifier.classify_changed_paths(
                [
                    "extensions/boostforreddit/src/main/java/Fix.java",
                    "tools/check-example-contract.sh",
                ]
            )
        )
        self.assertEqual(mode, self.classifier.MODE_CODE_ONLY)
        self.assertEqual(
            changed,
            (
                "extensions/boostforreddit/src/main/java/Fix.java",
                "tools/check-example-contract.sh",
            ),
        )
        self.assertEqual(metadata, ())

    def test_every_release_metadata_path_requires_full_gate(self) -> None:
        for path in self.classifier.RELEASE_METADATA_PATHS:
            with self.subTest(path=path):
                mode, _changed, metadata = (
                    self.classifier.classify_changed_paths([path])
                )
                self.assertEqual(
                    mode,
                    self.classifier.MODE_FULL_RELEASE,
                )
                self.assertEqual(metadata, (path,))

    def test_adjacent_docs_do_not_trigger_release_sha_gate(self) -> None:
        mode, _changed, metadata = (
            self.classifier.classify_changed_paths(
                ["docs/release-validation-policy.md"]
            )
        )
        self.assertEqual(mode, self.classifier.MODE_CODE_ONLY)
        self.assertEqual(metadata, ())

    def test_real_git_diff_classifies_code_then_metadata(self) -> None:
        with tempfile.TemporaryDirectory(
            prefix="morphe-release-feed-classifier-"
        ) as tmp:
            repo = Path(tmp)
            run_git(repo, "init")
            run_git(repo, "config", "user.name", "Contract")
            run_git(repo, "config", "user.email", "contract@example.invalid")

            for path in self.classifier.RELEASE_METADATA_PATHS:
                target = repo / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(f"{path}\n", encoding="utf-8")

            code = repo / "src/Fix.java"
            code.parent.mkdir(parents=True, exist_ok=True)
            code.write_text("class Fix {}\n", encoding="utf-8")

            run_git(repo, "add", ".")
            run_git(repo, "commit", "-m", "base")
            base = run_git(repo, "rev-parse", "HEAD")

            code.write_text("class Fix { int value; }\n", encoding="utf-8")
            run_git(repo, "add", "src/Fix.java")
            run_git(repo, "commit", "-m", "code")

            code_changed = self.classifier.git_changed_paths(
                repo,
                base,
                "HEAD",
            )
            code_mode, _changed, metadata = (
                self.classifier.classify_changed_paths(code_changed)
            )
            self.assertEqual(
                code_mode,
                self.classifier.MODE_CODE_ONLY,
            )
            self.assertEqual(metadata, ())

            readme = repo / "README.md"
            readme.write_text("new release sha\n", encoding="utf-8")
            run_git(repo, "add", "README.md")
            run_git(repo, "commit", "-m", "metadata")

            metadata_changed = self.classifier.git_changed_paths(
                repo,
                base,
                "HEAD",
            )
            metadata_mode, _changed, metadata = (
                self.classifier.classify_changed_paths(metadata_changed)
            )
            self.assertEqual(
                metadata_mode,
                self.classifier.MODE_FULL_RELEASE,
            )
            self.assertEqual(metadata, ("README.md",))

    def test_ci_and_release_gate_source_contract(self) -> None:
        workflow = (
            ROOT / ".github/workflows/release-feed-smoke.yml"
        ).read_text(encoding="utf-8")
        feed = (ROOT / "tools/release-feed-smoke.sh").read_text(
            encoding="utf-8"
        )
        gate = (ROOT / "scripts/release-gate.py").read_text(
            encoding="utf-8"
        )
        release_workflow = (
            ROOT / ".github/workflows/release.yml"
        ).read_text(encoding="utf-8")

        self.assertEqual(workflow.count("fetch-depth: 0"), 1)
        self.assertEqual(
            workflow.count(
                "RELEASE_FEED_BASE_SHA: "
                "${{ github.event.pull_request.base.sha || github.event.before }}"
            ),
            1,
        )
        self.assertIn(
            "scripts/classify-release-feed-change.py",
            feed,
        )
        self.assertIn(
            "RELEASE_GATE_ARGS+=(--skip-readme-sha)",
            feed,
        )
        self.assertIn('"--skip-readme-sha"', gate)
        self.assertIn(
            "README_SHA_GATE=SKIPPED_CODE_ONLY_CHANGE",
            gate,
        )
        self.assertNotIn("--skip-readme-sha", release_workflow)


if __name__ == "__main__":
    unittest.main()
