#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "releasectl.py"
SPEC = importlib.util.spec_from_file_location("morphe_releasectl_github_release", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


GITHUB_API_VERSION = MODULE.GITHUB_API_VERSION
GhCommandResult = MODULE.GhCommandResult
ReleaseIdentity = MODULE.ReleaseIdentity
SubprocessGhRunner = MODULE.SubprocessGhRunner
observe_github_release = MODULE.observe_github_release


SHA256 = "d" * 64
WRONG_SHA256 = "e" * 64
REPOSITORY = "brealorg/breal-morphe-patches"


def identity() -> ReleaseIdentity:
    return ReleaseIdentity(
        version="1.4.67",
        tag="morphe-patches-67",
        release_commit="a" * 40,
        mpp_asset_name="patches-1.4.67.mpp",
        signature_asset_name="patches-1.4.67.mpp.asc",
        mpp_sha256=SHA256,
        signing_identity="0123456789ABCDEF",
    )


def asset(
    asset_id: int,
    name: str,
    *,
    digest: object = None,
    state: object = "uploaded",
) -> dict[str, object]:
    return {
        "id": asset_id,
        "name": name,
        "state": state,
        "digest": digest,
    }


def release(
    *,
    release_id: object = 67,
    tag: object = "morphe-patches-67",
    draft: object = False,
    prerelease: object = False,
    immutable: object = False,
    target_commitish: object = "a" * 40,
    html_url: object = (
        "https://github.com/brealorg/breal-morphe-patches/"
        "releases/tag/morphe-patches-67"
    ),
    assets: object | None = None,
) -> dict[str, object]:
    if assets is None:
        assets = [
            asset(101, "patches-1.4.67.mpp", digest=f"sha256:{SHA256}"),
            asset(102, "patches-1.4.67.mpp.asc"),
        ]
    return {
        "id": release_id,
        "tag_name": tag,
        "draft": draft,
        "prerelease": prerelease,
        "immutable": immutable,
        "target_commitish": target_commitish,
        "html_url": html_url,
        "assets": assets,
    }


def pages(*release_pages: list[dict[str, object]]) -> str:
    return json.dumps(list(release_pages))


class StaticGhRunner:
    def __init__(
        self,
        listing_stdout: str,
        *,
        listing_returncode: int = 0,
        listing_stderr: str = "",
        repository_stdout: str | None = None,
        repository_returncode: int = 0,
        repository_stderr: str = "",
    ) -> None:
        if repository_stdout is None:
            repository_stdout = json.dumps(
                {
                    "full_name": REPOSITORY,
                    "permissions": {"pull": True, "push": True, "admin": True},
                }
            )
        self.responses = [
            GhCommandResult(
                args=(),
                returncode=repository_returncode,
                stdout=repository_stdout,
                stderr=repository_stderr,
            ),
            GhCommandResult(
                args=(),
                returncode=listing_returncode,
                stdout=listing_stdout,
                stderr=listing_stderr,
            ),
        ]
        self.calls: list[tuple[str, ...]] = []

    def run(self, args: tuple[str, ...]) -> GhCommandResult:
        self.calls.append(tuple(args))
        index = len(self.calls) - 1
        if index >= len(self.responses):
            raise AssertionError(f"unexpected extra gh call: {args!r}")
        response = self.responses[index]
        return GhCommandResult(
            args=("gh", *tuple(args)),
            returncode=response.returncode,
            stdout=response.stdout,
            stderr=response.stderr,
        )


class RejectingRunner:
    def run(self, args: tuple[str, ...]) -> GhCommandResult:
        raise AssertionError(f"runner must not be called: {args!r}")


class GitHubReleaseObserverTests(unittest.TestCase):
    def observe(self, runner: StaticGhRunner):
        return observe_github_release(REPOSITORY, identity(), runner=runner)

    def test_h01_published_release_metadata_and_digest(self) -> None:
        result = self.observe(StaticGhRunner(pages([release()])))

        self.assertTrue(result.github.present)
        self.assertEqual(result.github.tag, "morphe-patches-67")
        self.assertFalse(result.github.is_draft)
        self.assertFalse(result.github.is_prerelease)
        self.assertEqual(result.github.mpp_asset_count, 1)
        self.assertEqual(result.github.signature_asset_count, 1)
        self.assertEqual(result.github.mpp_asset_digest, SHA256)
        self.assertEqual(result.release_id, 67)
        self.assertEqual(result.mpp_asset_ids, (101,))
        self.assertEqual(result.signature_asset_ids, (102,))
        self.assertTrue(result.observations_complete)
        self.assertEqual(result.errors, ())

    def test_h02_draft_release_is_observed(self) -> None:
        result = self.observe(
            StaticGhRunner(pages([release(draft=True, immutable=False)]))
        )

        self.assertTrue(result.github.present)
        self.assertTrue(result.github.is_draft)
        self.assertFalse(result.github.is_prerelease)
        self.assertFalse(result.is_immutable)
        self.assertTrue(result.observations_complete)

    def test_h03_absent_release_is_complete_absence(self) -> None:
        result = self.observe(StaticGhRunner(pages([])))

        self.assertFalse(result.github.present)
        self.assertEqual(result.github.mpp_asset_count, 0)
        self.assertIsNone(result.release_id)
        self.assertTrue(result.observations_complete)
        self.assertEqual(result.errors, ())

    def test_h04_unrelated_release_is_ignored(self) -> None:
        unrelated = release(tag="morphe-patches-66", release_id=66)
        result = self.observe(StaticGhRunner(pages([unrelated])))

        self.assertFalse(result.github.present)
        self.assertTrue(result.observations_complete)

    def test_h05_paginated_listing_finds_target(self) -> None:
        unrelated = release(tag="morphe-patches-66", release_id=66)
        result = self.observe(
            StaticGhRunner(pages([unrelated], [release()]))
        )

        self.assertTrue(result.github.present)
        self.assertEqual(result.release_id, 67)

    def test_h06_duplicate_matching_releases_are_rejected(self) -> None:
        result = self.observe(
            StaticGhRunner(pages([release(), release(release_id=68)]))
        )

        self.assertFalse(result.observations_complete)
        self.assertFalse(result.github.present)
        self.assertTrue(any("multiple releases" in error for error in result.errors))

    def test_h07_duplicate_mpp_assets_preserve_count(self) -> None:
        assets = [
            asset(101, "patches-1.4.67.mpp", digest=f"sha256:{SHA256}"),
            asset(103, "patches-1.4.67.mpp", digest=f"sha256:{SHA256}"),
            asset(102, "patches-1.4.67.mpp.asc"),
        ]
        result = self.observe(StaticGhRunner(pages([release(assets=assets)])))

        self.assertEqual(result.github.mpp_asset_count, 2)
        self.assertEqual(result.mpp_asset_ids, (101, 103))
        self.assertIsNone(result.github.mpp_asset_digest)
        self.assertTrue(result.observations_complete)

    def test_h08_duplicate_signature_assets_preserve_count(self) -> None:
        assets = [
            asset(101, "patches-1.4.67.mpp", digest=f"sha256:{SHA256}"),
            asset(102, "patches-1.4.67.mpp.asc"),
            asset(104, "patches-1.4.67.mpp.asc"),
        ]
        result = self.observe(StaticGhRunner(pages([release(assets=assets)])))

        self.assertEqual(result.github.signature_asset_count, 2)
        self.assertEqual(result.signature_asset_ids, (102, 104))
        self.assertTrue(result.observations_complete)

    def test_h09_missing_api_digest_is_warning_not_failure(self) -> None:
        assets = [
            asset(101, "patches-1.4.67.mpp", digest=None),
            asset(102, "patches-1.4.67.mpp.asc"),
        ]
        result = self.observe(StaticGhRunner(pages([release(assets=assets)])))

        self.assertIsNone(result.github.mpp_asset_digest)
        self.assertTrue(result.observations_complete)
        self.assertTrue(any("does not expose" in warning for warning in result.warnings))

    def test_h10_invalid_api_digest_is_observation_failure(self) -> None:
        assets = [
            asset(101, "patches-1.4.67.mpp", digest=f"sha256:{WRONG_SHA256[:-1]}x"),
            asset(102, "patches-1.4.67.mpp.asc"),
        ]
        result = self.observe(StaticGhRunner(pages([release(assets=assets)])))

        self.assertFalse(result.observations_complete)
        self.assertTrue(any("invalid SHA256" in error for error in result.errors))

    def test_h11_non_uploaded_expected_asset_is_incomplete(self) -> None:
        assets = [
            asset(
                101,
                "patches-1.4.67.mpp",
                digest=f"sha256:{SHA256}",
                state="open",
            ),
            asset(102, "patches-1.4.67.mpp.asc"),
        ]
        result = self.observe(StaticGhRunner(pages([release(assets=assets)])))

        self.assertFalse(result.observations_complete)
        self.assertEqual(result.github.mpp_asset_count, 1)
        self.assertTrue(any("state 'open'" in warning for warning in result.warnings))

    def test_h12_unrelated_assets_are_ignored(self) -> None:
        assets = [
            asset(90, "checksums.txt", digest=None),
            asset(101, "patches-1.4.67.mpp", digest=f"sha256:{SHA256}"),
            asset(102, "patches-1.4.67.mpp.asc"),
        ]
        result = self.observe(StaticGhRunner(pages([release(assets=assets)])))

        self.assertEqual(result.github.mpp_asset_count, 1)
        self.assertEqual(result.github.signature_asset_count, 1)
        self.assertEqual(result.warnings, ())

    def test_h13_invalid_json_is_rejected(self) -> None:
        result = self.observe(StaticGhRunner("not-json"))

        self.assertFalse(result.observations_complete)
        self.assertTrue(any("invalid JSON" in error for error in result.errors))

    def test_h14_non_array_page_is_rejected(self) -> None:
        result = self.observe(StaticGhRunner(json.dumps([{"bad": "page"}])))

        self.assertFalse(result.observations_complete)
        self.assertTrue(any("page 1" in error for error in result.errors))

    def test_h15_invalid_release_tag_name_is_rejected(self) -> None:
        bad = release(tag=None)
        result = self.observe(StaticGhRunner(pages([bad])))

        self.assertFalse(result.observations_complete)
        self.assertTrue(any("invalid tag_name" in error for error in result.errors))

    def test_h16_invalid_matching_release_fields_are_rejected(self) -> None:
        bad = release(
            release_id=True,
            draft="false",
            prerelease=None,
            immutable="false",
            target_commitish="",
            html_url=None,
        )
        result = self.observe(StaticGhRunner(pages([bad])))

        self.assertFalse(result.observations_complete)
        self.assertGreaterEqual(len(result.errors), 6)

    def test_h17_malformed_asset_is_rejected(self) -> None:
        result = self.observe(
            StaticGhRunner(pages([release(assets=[{"id": 1, "name": None}])]))
        )

        self.assertFalse(result.observations_complete)
        self.assertTrue(any("invalid name" in error for error in result.errors))

    def test_h18_gh_command_failure_is_reported(self) -> None:
        runner = StaticGhRunner(
            "",
            listing_returncode=1,
            listing_stderr="authentication failed",
        )
        result = self.observe(runner)

        self.assertFalse(result.observations_complete)
        self.assertTrue(any("authentication failed" in error for error in result.errors))

    def test_h19_missing_gh_executable_is_reported(self) -> None:
        with patch.object(
            MODULE.subprocess,
            "run",
            side_effect=FileNotFoundError("gh not found"),
        ):
            result = observe_github_release(
                REPOSITORY,
                identity(),
                runner=SubprocessGhRunner(),
            )

        self.assertFalse(result.observations_complete)
        self.assertTrue(any("exit 127" in error for error in result.errors))

    def test_h20_invalid_repository_is_rejected_without_runner(self) -> None:
        result = observe_github_release(
            "--repo=evil",
            identity(),
            runner=RejectingRunner(),
        )

        self.assertFalse(result.observations_complete)
        self.assertTrue(any("OWNER/REPO" in error for error in result.errors))

    def test_h21_no_push_permission_blocks_draft_sensitive_observation(self) -> None:
        metadata = json.dumps(
            {
                "full_name": REPOSITORY,
                "permissions": {"pull": True, "push": False, "admin": False},
            }
        )
        runner = StaticGhRunner(pages([]), repository_stdout=metadata)
        result = self.observe(runner)

        self.assertFalse(result.observations_complete)
        self.assertFalse(result.viewer_can_push)
        self.assertEqual(len(runner.calls), 1)
        self.assertTrue(any("draft release visibility" in error for error in result.errors))

    def test_h22_missing_permission_metadata_is_rejected(self) -> None:
        metadata = json.dumps({"full_name": REPOSITORY})
        runner = StaticGhRunner(pages([]), repository_stdout=metadata)
        result = self.observe(runner)

        self.assertFalse(result.observations_complete)
        self.assertIsNone(result.viewer_can_push)
        self.assertEqual(len(runner.calls), 1)
        self.assertTrue(any("permissions object" in error for error in result.errors))

    def test_h23_repository_metadata_failure_is_reported(self) -> None:
        runner = StaticGhRunner(
            pages([]),
            repository_returncode=1,
            repository_stderr="repository lookup failed",
        )
        result = self.observe(runner)

        self.assertFalse(result.observations_complete)
        self.assertEqual(len(runner.calls), 1)
        self.assertTrue(any("repository lookup failed" in error for error in result.errors))

    def test_h24_command_is_explicit_read_only_versioned_and_paginated(self) -> None:
        runner = StaticGhRunner(pages([]))
        result = self.observe(runner)

        self.assertTrue(result.observations_complete)
        self.assertEqual(len(runner.calls), 2)
        self.assertEqual(
            runner.calls[0],
            (
                "api",
                "--method",
                "GET",
                "--header",
                "Accept: application/vnd.github+json",
                "--header",
                f"X-GitHub-Api-Version: {GITHUB_API_VERSION}",
                f"repos/{REPOSITORY}",
            ),
        )
        self.assertEqual(
            runner.calls[1],
            (
                "api",
                "--method",
                "GET",
                "--paginate",
                "--slurp",
                "--header",
                "Accept: application/vnd.github+json",
                "--header",
                f"X-GitHub-Api-Version: {GITHUB_API_VERSION}",
                f"repos/{REPOSITORY}/releases?per_page=100",
            ),
        )

    def test_h25_result_serializes_with_stable_values(self) -> None:
        result = self.observe(StaticGhRunner(pages([release()])))
        payload = result.as_dict()

        self.assertEqual(payload["repository"], REPOSITORY)
        self.assertTrue(payload["viewer_can_push"])
        self.assertEqual(payload["github"]["tag"], "morphe-patches-67")
        self.assertEqual(payload["github"]["mpp_asset_digest"], SHA256)
        self.assertEqual(payload["mpp_asset_ids"], [101])
        self.assertTrue(payload["observations_complete"])

    def test_h26_subprocess_runner_disables_prompts_and_paging(self) -> None:
        completed = subprocess.CompletedProcess(
            args=("gh", "api"),
            returncode=0,
            stdout="[]",
            stderr="",
        )
        with patch.object(MODULE.subprocess, "run", return_value=completed) as mocked:
            result = SubprocessGhRunner().run(("api", "endpoint"))

        self.assertEqual(result.returncode, 0)
        kwargs = mocked.call_args.kwargs
        env = kwargs["env"]
        self.assertEqual(env["GH_PROMPT_DISABLED"], "1")
        self.assertEqual(env["GH_PAGER"], "cat")
        self.assertEqual(env["NO_COLOR"], "1")
        self.assertEqual(env["LC_ALL"], "C")
        self.assertFalse(kwargs["check"])
        self.assertNotIn("shell", kwargs)


if __name__ == "__main__":
    unittest.main()
