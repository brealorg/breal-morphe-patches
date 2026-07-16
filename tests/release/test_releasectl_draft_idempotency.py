#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "releasectl.py"

SPEC = importlib.util.spec_from_file_location(
    "morphe_releasectl_draft_idempotency",
    SCRIPT,
)

assert SPEC is not None and SPEC.loader is not None

MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def identity():
    return MODULE.ReleaseIdentity(
        version="1.4.99",
        tag="morphe-patches-99",
        release_commit="a" * 40,
        mpp_asset_name="patches-1.4.99.mpp",
        signature_asset_name="patches-1.4.99.mpp.asc",
        mpp_sha256="b" * 64,
        signing_identity=MODULE.DEFAULT_SIGNING_IDENTITY,
    )


def draft(release_id: int, assets=None):
    return {
        "id": release_id,
        "tag_name": "morphe-patches-99",
        "draft": True,
        "prerelease": False,
        "target_commitish": "a" * 40,
        "created_at": "2026-07-16T21:33:33Z",
        "assets": [] if assets is None else assets,
    }


class Result:
    def __init__(
        self,
        args,
        returncode=0,
        stdout="",
        stderr="",
    ):
        self.args = tuple(args)
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = stderr


class FakeRunner:
    def __init__(self, listings):
        self.listings = list(listings)
        self.calls = []

    def run(self, repo_path, args):
        call = tuple(args)
        self.calls.append(call)

        if call[:4] == (
            "gh",
            "api",
            "--method",
            "DELETE",
        ):
            return Result(call)

        if call[:3] == (
            "gh",
            "api",
            "--paginate",
        ):
            listing = (
                self.listings.pop(0)
                if self.listings
                else []
            )

            return Result(
                call,
                stdout=json.dumps([listing]) + "\n",
            )

        raise AssertionError(f"unexpected call: {call}")


class DraftCreationIdempotencyTests(unittest.TestCase):
    def test_first_claim_does_not_query_or_create_twice(
        self,
    ):
        runner = FakeRunner([])

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)

            with MODULE._idempotent_draft_creation_guard(
                root,
                "owner/repo",
                identity(),
                runner,
            ) as should_create:
                self.assertTrue(should_create)

            self.assertEqual(runner.calls, [])

    def test_existing_claim_waits_for_delayed_observation(
        self,
    ):
        runner = FakeRunner(
            [
                [],
                [],
                [draft(101)],
            ]
        )

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)

            claim, _, _ = MODULE._draft_creation_paths(
                root,
                identity(),
            )

            claim.parent.mkdir(parents=True)
            claim.write_text("{}\n", encoding="utf-8")

            with MODULE._idempotent_draft_creation_guard(
                root,
                "owner/repo",
                identity(),
                runner,
            ) as should_create:
                self.assertFalse(should_create)

            list_calls = [
                call
                for call in runner.calls
                if call[:3] == (
                    "gh",
                    "api",
                    "--paginate",
                )
            ]

            self.assertEqual(len(list_calls), 3)

    def test_duplicate_empty_draft_is_deleted(
        self,
    ):
        runner = FakeRunner(
            [
                [
                    draft(200),
                    draft(201),
                ]
            ]
        )

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)

            claim, _, _ = MODULE._draft_creation_paths(
                root,
                identity(),
            )

            claim.parent.mkdir(parents=True)
            claim.write_text("{}\n", encoding="utf-8")

            with MODULE._idempotent_draft_creation_guard(
                root,
                "owner/repo",
                identity(),
                runner,
            ) as should_create:
                self.assertFalse(should_create)

            delete_calls = [
                call
                for call in runner.calls
                if call[:4] == (
                    "gh",
                    "api",
                    "--method",
                    "DELETE",
                )
            ]

            self.assertEqual(len(delete_calls), 1)

    def test_asset_bearing_duplicate_aborts(
        self,
    ):
        runner = FakeRunner(
            [
                [
                    draft(300, [{"name": "a.mpp"}]),
                    draft(301, [{"name": "a.mpp.asc"}]),
                ]
            ]
        )

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)

            claim, _, _ = MODULE._draft_creation_paths(
                root,
                identity(),
            )

            claim.parent.mkdir(parents=True)
            claim.write_text("{}\n", encoding="utf-8")

            with self.assertRaisesRegex(
                RuntimeError,
                "automatic deletion is unsafe",
            ):
                with MODULE._idempotent_draft_creation_guard(
                    root,
                    "owner/repo",
                    identity(),
                    runner,
                ):
                    pass


if __name__ == "__main__":
    unittest.main()
