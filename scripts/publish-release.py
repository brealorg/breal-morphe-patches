#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Any


ROOT = Path.cwd()

ALLOWED_PREPARED_METADATA_FILES = {
    "gradle.properties",
    "patches-bundle.json",
    "patches-list.json",
    "README.md",
}

REQUIRED_MPP_ENTRIES = {
    "classes.dex",
    "extensions/boostforreddit.mpe",
}


def run(
    cmd: list[str],
    *,
    check: bool = True,
    text: bool = True,
    capture: bool = False,
    input_text: str | None = None,
) -> subprocess.CompletedProcess[str]:
    print("+", " ".join(cmd))
    proc = subprocess.run(
        cmd,
        text=text,
        input=input_text,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
        check=False,
    )
    if check and proc.returncode != 0:
        if capture:
            if proc.stdout:
                print(proc.stdout, end="")
            if proc.stderr:
                print(proc.stderr, end="", file=sys.stderr)
        raise SystemExit(f"Command failed with exit {proc.returncode}: {' '.join(cmd)}")
    return proc


def git(args: list[str], *, check: bool = True, capture: bool = True) -> str:
    proc = run(["git", *args], check=check, capture=capture)
    return (proc.stdout or "").strip()


def gh(args: list[str], *, check: bool = True, capture: bool = True) -> str:
    proc = run(["gh", *args], check=check, capture=capture)
    return (proc.stdout or "").strip()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


def parse_gradle_version(path: Path) -> str:
    text = path.read_text(encoding="utf-8")
    match = re.search(r"(?m)^\s*version\s*=\s*([^\s#]+)\s*$", text)
    if not match:
        raise SystemExit("FAIL: Could not parse gradle.properties version")
    return match.group(1)


def default_asset_name(version: str) -> str:
    return f"patches-{version}.mpp"


def default_asset_path(version: str) -> Path:
    return ROOT / "patches" / "build" / "libs" / default_asset_name(version)


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def changed_files() -> list[str]:
    out = git(["ls-files", "--modified", "--others", "--exclude-standard"])
    return sorted(line.strip() for line in out.splitlines() if line.strip())


def changed_metadata_files(files: list[str]) -> list[str]:
    return [path for path in files if path in ALLOWED_PREPARED_METADATA_FILES]


def unexpected_changed_files(files: list[str]) -> list[str]:
    return [path for path in files if path not in ALLOWED_PREPARED_METADATA_FILES]


def local_tag_exists(tag: str) -> bool:
    proc = run(["git", "rev-parse", "-q", "--verify", f"refs/tags/{tag}"], check=False, capture=True)
    return proc.returncode == 0


def remote_tag_exists(remote: str, tag: str) -> bool:
    proc = run(["git", "ls-remote", "--exit-code", "--tags", remote, f"refs/tags/{tag}"], check=False, capture=True)
    return proc.returncode == 0


def github_release_exists(tag: str) -> bool:
    proc = run(["gh", "release", "view", tag, "--json", "tagName"], check=False, capture=True)
    return proc.returncode == 0


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def readme_current_release_section(readme: str) -> str:
    match = re.search(r"(?ms)^## Current release\n.*?(?=^##\s|\Z)", readme)
    return match.group(0) if match else ""


def parse_readme_current_release_table(readme: str) -> dict[str, str]:
    rows: dict[str, str] = {}
    for line in readme_current_release_section(readme).splitlines():
        match = re.match(r"^\|\s*([^|]+?)\s*\|\s*`?([^`|]+?)`?\s*\|$", line.strip())
        if match:
            label = match.group(1).strip()
            value = match.group(2).strip()
            if label.lower() not in {"field", "---"}:
                rows[label] = value
    return rows


def mpp_entries(path: Path) -> set[str]:
    with zipfile.ZipFile(path) as z:
        return set(z.namelist())


def verify_mpp(path: Path) -> str:
    require(path.exists(), f"MPP does not exist: {path}")
    require(path.is_file(), f"MPP is not a file: {path}")

    names = mpp_entries(path)
    for required in REQUIRED_MPP_ENTRIES:
        require(required in names, f"MPP missing required entry: {required}")

    sha = sha256_file(path)
    print(f"MPP: {path}")
    print(f"MPP SHA256: {sha}")
    print("MPP entries: classes.dex=OK extensions/boostforreddit.mpe=OK")
    return sha


def verify_signature(asset: Path, signature: Path, *, skip_signature: bool) -> None:
    if skip_signature:
        print("WARN: signature verification skipped by --skip-signature")
        return

    require(signature.exists(), f"signature file does not exist: {signature}")
    require(signature.is_file(), f"signature path is not a file: {signature}")
    require(shutil.which("gpg") is not None, "gpg is required to verify signature")

    run(["gpg", "--batch", "--verify", str(signature), str(asset)], check=True, capture=False)
    print(f"Signature: {signature}")
    print("Signature verification: OK")


def verify_readme_bundle(version: str, tag: str, asset_name: str, asset_sha: str) -> None:
    bundle = read_json(ROOT / "patches-bundle.json")
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    rows = parse_readme_current_release_table(readme)

    expected_url = f"https://github.com/brealorg/breal-morphe-patches/releases/download/{tag}/{asset_name}"

    require(bundle.get("version") == version, f"patches-bundle.json version mismatch: {bundle.get('version')!r}")
    require(bundle.get("download_url") == expected_url, "patches-bundle.json download_url mismatch")
    require(bundle.get("signature_download_url") == f"{expected_url}.asc", "patches-bundle.json signature_download_url mismatch")

    require(rows.get("Version") == version, f"README Current release Version mismatch: {rows.get('Version')!r}")
    require(rows.get("Release tag") == tag, f"README Current release Release tag mismatch: {rows.get('Release tag')!r}")
    require(rows.get("Asset") == asset_name, f"README Current release Asset mismatch: {rows.get('Asset')!r}")
    require(rows.get("SHA256") == asset_sha, f"README Current release SHA256 mismatch: {rows.get('SHA256')!r}")
    require(rows.get("Download URL") == expected_url, "README Current release Download URL mismatch")

    print("README/bundle active release metadata: OK")


def verify_patches_list(version: str) -> None:
    data = read_json(ROOT / "patches-list.json")
    require(data.get("version") == f"v{version}", f"patches-list.json version mismatch: {data.get('version')!r}")
    patches = data.get("patches")
    require(isinstance(patches, list) and len(patches) > 0, "patches-list.json patches missing/empty")
    print(f"patches-list.json: OK ({len(patches)} patches)")


def verify_branch_alignment(branch: str, mirror_branches: list[str], remote: str) -> None:
    git(["fetch", remote, "--tags", "--prune"])
    head = git(["rev-parse", "HEAD"])
    origin_branch = git(["rev-parse", f"{remote}/{branch}"])

    print(f"HEAD: {head}")
    print(f"{remote}/{branch}: {origin_branch}")

    require(head == origin_branch, f"HEAD must equal {remote}/{branch} before publishing")

    for mirror in mirror_branches:
        mirror_ref = f"{remote}/{mirror}"
        proc = run(["git", "rev-parse", "--verify", mirror_ref], check=False, capture=True)
        require(proc.returncode == 0, f"mirror branch missing: {mirror_ref}")
        mirror_sha = (proc.stdout or "").strip()
        print(f"{mirror_ref}: {mirror_sha}")
        require(mirror_sha == head, f"{mirror_ref} must equal HEAD before publishing")


def verify_tooling() -> None:
    require(shutil.which("git") is not None, "git is required")
    require(shutil.which("gh") is not None, "gh is required")
    require((ROOT / "scripts" / "release-gate.py").exists(), "scripts/release-gate.py missing")
    require((ROOT / "scripts" / "verify-remote-release.sh").exists(), "scripts/verify-remote-release.sh missing")
    print("Tooling: git=OK gh=OK release-gate=OK verify-remote=OK")


def release_gate_cmd(args: argparse.Namespace, asset_path: Path) -> list[str]:
    cmd = [
        sys.executable,
        "scripts/release-gate.py",
        "--version",
        args.version,
        "--tag",
        args.tag,
        "--mpp",
        str(asset_path),
    ]

    for item in args.require_description:
        cmd.extend(["--require-description", item])
    for item in args.marker:
        cmd.extend(["--marker", item])
    for item in args.stale:
        cmd.extend(["--stale", item])

    return cmd


def write_release_notes_file(args: argparse.Namespace) -> Path:
    if args.notes_file:
        path = Path(args.notes_file)
        require(path.exists(), f"notes file does not exist: {path}")
        return path

    bundle = read_json(ROOT / "patches-bundle.json")
    description = str(bundle.get("description", "")).strip()
    if not description:
        description = f"Release {args.version}"

    tmp = Path(tempfile.mkdtemp(prefix="morphe-release-notes-")) / f"release-notes-{args.version}.md"
    tmp.write_text(description + "\n", encoding="utf-8")
    return tmp


def commit_prepared_metadata(version: str, files: list[str], *, dry_run: bool) -> None:
    if not files:
        print("Metadata commit: skipped; no prepared metadata changes")
        return

    print("Prepared metadata changes:")
    for path in files:
        print(f" - {path}")

    message = f"Prepare Morphe patch bundle {version} [skip ci]"

    if dry_run:
        print(f"DRY RUN: would git add metadata and commit: {message}")
        return

    run(["git", "add", *files])
    run(["git", "commit", "-m", message])


def create_tag(tag: str, *, dry_run: bool) -> None:
    if dry_run:
        print(f"DRY RUN: would create annotated tag {tag}")
        return
    run(["git", "tag", "-a", tag, "-m", tag])


def push_refs(remote: str, branch: str, mirror_branches: list[str], tag: str, *, dry_run: bool) -> None:
    refs = [branch, *mirror_branches]
    if dry_run:
        for ref in refs:
            print(f"DRY RUN: would push HEAD:{ref}")
        print(f"DRY RUN: would push tag {tag}")
        return

    for ref in refs:
        run(["git", "push", remote, f"HEAD:{ref}"])
    run(["git", "push", remote, tag])


def create_github_release(args: argparse.Namespace, asset_path: Path, signature_path: Path) -> None:
    notes_file = write_release_notes_file(args)
    title = args.title or f"Morphe patch bundle {args.version}"

    cmd = [
        "gh",
        "release",
        "create",
        args.tag,
        str(asset_path),
    ]

    if not args.skip_signature:
        cmd.append(str(signature_path))

    cmd.extend([
        "--title",
        title,
        "--notes-file",
        str(notes_file),
    ])

    if args.dry_run:
        print("DRY RUN: would create GitHub release:")
        print("+", " ".join(cmd))
        return

    run(cmd)


def run_remote_verify(args: argparse.Namespace, asset_path: Path) -> None:
    cmd = [
        "scripts/verify-remote-release.sh",
        "--version",
        args.version,
        "--tag",
        args.tag,
        "--branch",
        args.branch,
        "--local-mpp",
        str(asset_path),
        "--require-local-mpp",
    ]

    if args.skip_signature:
        cmd.append("--skip-signature")

    if args.dry_run:
        print("DRY RUN: would run remote verifier:")
        print("+", " ".join(cmd))
        return

    run(cmd)


def print_recovery_hint(args: argparse.Namespace) -> None:
    print()
    print("===== recovery hints if publish failed after local mutation =====")
    print(f"- inspect: git --no-pager status -sb && git --no-pager log --oneline --decorate -8")
    print(f"- local tag cleanup if not pushed: git tag -d {args.tag}")
    print(f"- remote tag cleanup only if intentionally rolling back: git push origin :refs/tags/{args.tag}")
    print(f"- GitHub release cleanup only after inspection: gh release delete {args.tag} --cleanup-tag")
    print("Do not delete remote state automatically; inspect first.")


def main() -> int:
    parser = argparse.ArgumentParser(description="Publish an already prepared Morphe patch bundle release with fail-fast gates.")
    parser.add_argument("--version", required=True, help="Release version, for example 1.4.47")
    parser.add_argument("--tag", required=True, help="Release tag, for example v1.4.47")
    parser.add_argument("--branch", default="main", help="Primary branch to publish to. Default: main")
    parser.add_argument("--mirror-branch", action="append", default=["dev"], help="Additional branch to push HEAD to. Can be repeated. Default: dev")
    parser.add_argument("--remote", default="origin", help="Git remote. Default: origin")
    parser.add_argument("--asset", help="MPP asset path. Defaults to patches/build/libs/patches-<version>.mpp")
    parser.add_argument("--signature", help="Detached signature path. Defaults to <asset>.asc")
    parser.add_argument("--notes-file", help="Release notes file. Defaults to patches-bundle.json description.")
    parser.add_argument("--title", help="GitHub release title. Defaults to Morphe patch bundle <version>.")
    parser.add_argument("--marker", action="append", default=[], help="Marker passed through to release-gate.py")
    parser.add_argument("--stale", action="append", default=[], help="Stale value passed through to release-gate.py")
    parser.add_argument("--require-description", action="append", default=[], help="Required description text passed through to release-gate.py")
    parser.add_argument("--dry-run", action="store_true", help="Run all possible gates but do not commit, tag, push, or create release.")
    parser.add_argument("--allow-dirty", action="store_true", help="Diagnostic only: allow dirty files outside prepared metadata scope.")
    parser.add_argument("--allow-existing-tag", action="store_true", help="Diagnostic/dry-run only: do not fail when local or remote tag exists.")
    parser.add_argument("--allow-existing-release", action="store_true", help="Diagnostic/dry-run only: do not fail when GitHub release exists.")
    parser.add_argument("--skip-signature", action="store_true", help="Diagnostic only: skip local .asc requirement/upload/verification.")
    args = parser.parse_args()

    if args.version != parse_gradle_version(ROOT / "gradle.properties"):
        raise SystemExit(f"FAIL: gradle.properties version does not match --version {args.version}")

    asset_path = Path(args.asset) if args.asset else default_asset_path(args.version)
    signature_path = Path(args.signature) if args.signature else Path(str(asset_path) + ".asc")
    asset_name = asset_path.name

    print("===== publish release plan =====")
    print(f"version: {args.version}")
    print(f"tag: {args.tag}")
    print(f"branch: {args.branch}")
    print(f"mirror branches: {', '.join(args.mirror_branch) if args.mirror_branch else '(none)'}")
    print(f"asset: {asset_path}")
    print(f"signature: {signature_path}")
    print(f"dry_run: {args.dry_run}")
    print()

    verify_tooling()

    files = changed_files()
    bad_files = unexpected_changed_files(files)
    metadata_files = changed_metadata_files(files)

    if files:
        print("Changed files:")
        for path in files:
            print(f" - {path}")
    else:
        print("Changed files: (none)")

    if bad_files and not args.allow_dirty:
        raise SystemExit(f"FAIL: unexpected dirty files: {bad_files}")

    if args.allow_dirty and bad_files:
        print(f"WARN: allowing unexpected dirty files because --allow-dirty was passed: {bad_files}")

    verify_branch_alignment(args.branch, args.mirror_branch, args.remote)

    if local_tag_exists(args.tag):
        require(args.allow_existing_tag, f"local tag already exists: {args.tag}")
        print(f"WARN: local tag already exists and is allowed: {args.tag}")

    if remote_tag_exists(args.remote, args.tag):
        require(args.allow_existing_tag, f"remote tag already exists: {args.tag}")
        print(f"WARN: remote tag already exists and is allowed: {args.tag}")

    if github_release_exists(args.tag):
        require(args.allow_existing_release, f"GitHub release already exists: {args.tag}")
        print(f"WARN: GitHub release already exists and is allowed: {args.tag}")

    asset_sha = verify_mpp(asset_path)
    verify_signature(asset_path, signature_path, skip_signature=args.skip_signature)
    verify_readme_bundle(args.version, args.tag, asset_name, asset_sha)
    verify_patches_list(args.version)

    run(release_gate_cmd(args, asset_path))

    print()
    print("===== mutation boundary =====")
    print("All fail-fast checks before tag/release passed.")

    try:
        commit_prepared_metadata(args.version, metadata_files, dry_run=args.dry_run)
        create_tag(args.tag, dry_run=args.dry_run)
        push_refs(args.remote, args.branch, args.mirror_branch, args.tag, dry_run=args.dry_run)
        create_github_release(args, asset_path, signature_path)
        run_remote_verify(args, asset_path)
    except BaseException:
        print_recovery_hint(args)
        raise

    print()
    if args.dry_run:
        print("PUBLISH DRY RUN OK")
    else:
        print("PUBLISH RELEASE OK")
    print(f"Version: {args.version}")
    print(f"Tag: {args.tag}")
    print(f"Asset: {asset_name}")
    print(f"SHA256: {asset_sha}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
