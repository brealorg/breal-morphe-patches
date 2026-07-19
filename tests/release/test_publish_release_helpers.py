#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "publish-release.py"
WORKFLOW = ROOT / ".github" / "workflows" / "release.yml"
PREPARE = ROOT / "scripts" / "prepare-release.py"
CONTROLLER = ROOT / "scripts" / "releasectl.py"


def test_compatibility_publisher_is_thin_controller_wrapper():
    text = SCRIPT.read_text(encoding="utf-8")

    assert "scripts/releasectl.py" in text
    assert '"publish"' in text
    assert '"--protected-main-commit"' in text
    assert '"git", "push"' not in text
    assert '"gh", "release"' not in text
    assert '"./gradlew"' not in text


def test_workflow_is_thin_protected_main_wrapper():
    workflow = WORKFLOW.read_text(encoding="utf-8")

    assert "python3 scripts/releasectl.py publish" in workflow
    assert '--protected-main-commit "$RELEASE_COMMIT"' in workflow
    assert 'test "$GITHUB_REF_NAME" = "main"' in workflow
    assert 'test "$CONFIRM" = "MORPHE_RELEASE_FROM_PROTECTED_MAIN"' in workflow
    assert "git push" not in workflow
    assert "git commit" not in workflow
    assert "git tag" not in workflow
    assert "gh release" not in workflow
    assert "scripts/prepare-release.py" not in workflow


def test_controller_never_pushes_main_during_publication():
    controller = CONTROLLER.read_text(encoding="utf-8")

    assert '"refs/heads/dev:refs/heads/dev"' in controller
    assert 'f"refs/tags/{identity.tag}:refs/tags/{identity.tag}"' in controller
    assert '"refs/heads/main:refs/heads/main"' not in controller
    assert "remote main must already equal the release commit" in controller


def test_prepare_handoff_requires_pull_request_before_release_dispatch():
    prepare = PREPARE.read_text(encoding="utf-8")

    assert "open and merge a pull request into main" in prepare
    assert "dispatch .github/workflows/release.yml" in prepare
    assert "HEAD:refs/heads/main" not in prepare


if __name__ == "__main__":
    test_compatibility_publisher_is_thin_controller_wrapper()
    test_workflow_is_thin_protected_main_wrapper()
    test_controller_never_pushes_main_during_publication()
    test_prepare_handoff_requires_pull_request_before_release_dispatch()
