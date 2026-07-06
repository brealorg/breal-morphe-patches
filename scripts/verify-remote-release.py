#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import zipfile
from pathlib import Path
from typing import Any


FORBIDDEN_MANAGER_DESCRIPTION_FRAGMENTS = (
    "Morphe patch bundle",
    "Latest in",
    "Also includes",
    "previous fixes",
    "Clean APKs",
    "Clean APKs must be supplied separately",
)

MAX_MANAGER_DESCRIPTION_LENGTH = 700


class Verdict:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.info: list[str] = []

    def fail(self, msg: str) -> None:
        self.errors.append(msg)

    def warn(self, msg: str) -> None:
        self.warnings.append(msg)

    def note(self, msg: str) -> None:
        self.info.append(msg)

    def require(self, condition: bool, msg: str) -> None:
        if not condition:
            self.fail(msg)


def run_git(args: list[str], *, check: bool = True) -> str:
    proc = subprocess.run(
        ["git", *args],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if check and proc.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed: {proc.stderr.strip()}")
    return proc.stdout.strip()


def run_cmd(args: list[str], *, input_bytes: bytes | None = None) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(
        args,
        input=input_bytes,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def parse_gradle_version(path: Path) -> str:
    text = path.read_text(encoding="utf-8")
    match = re.search(r"(?m)^\s*version\s*=\s*([^\s#]+)\s*$", text)
    if not match:
        raise RuntimeError("Could not find version = ... in gradle.properties")
    return match.group(1)


def parse_repo_slug(remote_url: str) -> tuple[str, str]:
    remote_url = remote_url.strip()

    ssh_match = re.match(r"^git@github\.com:([^/]+)/(.+?)(?:\.git)?$", remote_url)
    if ssh_match:
        return ssh_match.group(1), ssh_match.group(2)

    https_match = re.match(r"^https://github\.com/([^/]+)/(.+?)(?:\.git)?$", remote_url)
    if https_match:
        return https_match.group(1), https_match.group(2)

    gh_match = re.match(r"^gh:([^/]+)/(.+?)(?:\.git)?$", remote_url)
    if gh_match:
        return gh_match.group(1), gh_match.group(2)

    raise RuntimeError(f"Could not parse GitHub owner/repo from remote.origin.url: {remote_url!r}")


def github_token() -> str | None:
    for name in ("GH_TOKEN", "GITHUB_TOKEN"):
        token = os.environ.get(name)
        if token:
            return token.strip()

    if shutil.which("gh"):
        proc = subprocess.run(
            ["gh", "auth", "token"],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        if proc.returncode == 0 and proc.stdout.strip():
            return proc.stdout.strip()

    return None


def http_get(url: str, *, token: str | None = None, accept: str | None = None) -> bytes:
    headers = {
        "User-Agent": "morphe-verify-remote-release",
    }
    if accept:
        headers["Accept"] = accept
    if token:
        headers["Authorization"] = f"Bearer {token}"

    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=45) as response:
        return response.read()


def http_json(url: str, *, token: str | None = None) -> Any:
    return json.loads(http_get(url, token=token, accept="application/vnd.github+json").decode("utf-8"))


def read_json_from_git(ref: str, path: str) -> dict[str, Any]:
    text = run_git(["show", f"{ref}:{path}"])
    return json.loads(text)


def read_text_from_git(ref: str, path: str) -> str:
    return run_git(["show", f"{ref}:{path}"])


def read_github_contents_text(owner: str, repo: str, path: str, ref: str, *, token: str | None) -> str:
    url = f"https://api.github.com/repos/{owner}/{repo}/contents/{path}?ref={ref}"
    payload = http_json(url, token=token)
    if payload.get("encoding") != "base64" or "content" not in payload:
        raise RuntimeError(f"Unexpected GitHub contents response for {path}@{ref}")
    raw = base64.b64decode(payload["content"])
    return raw.decode("utf-8")


def readme_current_release_section(readme: str) -> str:
    match = re.search(r"(?ms)^## Current release\n.*?(?=^##\s|\Z)", readme)
    return match.group(0) if match else ""


def parse_readme_current_release_table(readme: str) -> dict[str, str]:
    section = readme_current_release_section(readme)
    rows: dict[str, str] = {}

    for line in section.splitlines():
        match = re.match(r"^\|\s*([^|]+?)\s*\|\s*`?([^`|]+?)`?\s*\|$", line.strip())
        if not match:
            continue
        label = match.group(1).strip()
        value = match.group(2).strip()
        if label and label.lower() not in {"field", "---"}:
            rows[label] = value

    return rows


def readme_standalone_sha(readme: str) -> str | None:
    match = re.search(r"(?m)^SHA256:\s*`?([0-9a-f]{64})`?\s*$", readme)
    return match.group(1) if match else None


def readme_expected_sha(readme: str) -> str | None:
    rows = parse_readme_current_release_table(readme)
    table_sha = rows.get("SHA256")
    if table_sha and re.fullmatch(r"[0-9a-f]{64}", table_sha):
        return table_sha
    return readme_standalone_sha(readme)


def peeled_remote_tag_commit(tag: str) -> str | None:
    out = run_git(["ls-remote", "--tags", "origin", f"refs/tags/{tag}", f"refs/tags/{tag}^{{}}"], check=False)
    direct: str | None = None
    peeled: str | None = None

    for line in out.splitlines():
        parts = line.split()
        if len(parts) != 2:
            continue
        sha, ref = parts
        if ref == f"refs/tags/{tag}^{{}}":
            peeled = sha
        elif ref == f"refs/tags/{tag}":
            direct = sha

    return peeled or direct


def git_is_ancestor(ancestor: str, descendant_ref: str) -> bool:
    proc = run_cmd(["git", "merge-base", "--is-ancestor", ancestor, descendant_ref])
    return proc.returncode == 0


def inspect_mpp(path: Path) -> tuple[list[str], bool]:
    with zipfile.ZipFile(path) as z:
        names = set(z.namelist())
        dex_entries = sorted(name for name in names if Path(name).name.endswith(".dex"))
        has_boost_ext = "extensions/boostforreddit.mpe" in names
    return dex_entries, has_boost_ext


def expected_asset_name(version: str) -> str:
    return f"patches-{version}.mpp"


def expected_download_url(owner: str, repo: str, tag: str, asset_name: str) -> str:
    return f"https://github.com/{owner}/{repo}/releases/download/{tag}/{asset_name}"


def check_bundle(
    verdict: Verdict,
    label: str,
    bundle: dict[str, Any],
    *,
    version: str,
    expected_url: str,
    expected_sig_url: str,
) -> None:
    verdict.require(bundle.get("version") == f"v{version}", f"{label}: version={bundle.get('version')!r}, expected {f'v{version}'!r}")
    verdict.require(bundle.get("download_url") == expected_url, f"{label}: download_url mismatch")
    verdict.require(bundle.get("signature_download_url") == expected_sig_url, f"{label}: signature_download_url mismatch")

    description = str(bundle.get("description", ""))
    if len(description) > MAX_MANAGER_DESCRIPTION_LENGTH:
        verdict.warn(f"{label}: Manager-facing description is long: {len(description)} chars")
    for fragment in FORBIDDEN_MANAGER_DESCRIPTION_FRAGMENTS:
        if fragment in description:
            verdict.warn(f"{label}: Manager-facing description contains discouraged boilerplate: {fragment!r}")


def check_patches_list(verdict: Verdict, label: str, data: dict[str, Any], *, version: str) -> None:
    expected = version
    verdict.require(data.get("version") == expected, f"{label}: version={data.get('version')!r}, expected {expected!r}")
    patches = data.get("patches")
    verdict.require(isinstance(patches, list) and len(patches) > 0, f"{label}: patches list missing or empty")


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify a published Morphe release from remote GitHub state and release assets.")
    parser.add_argument("--version", help="Expected version. Defaults to gradle.properties.")
    parser.add_argument("--tag", help="Expected tag. Defaults to README Current release tag, then v<version>.")
    parser.add_argument("--branch", default="main", help="Remote branch to verify. Defaults to main.")
    parser.add_argument("--repo", help="GitHub owner/repo. Defaults to remote.origin.url.")
    parser.add_argument("--asset", help="Expected MPP asset name. Defaults to patches-<version>.mpp.")
    parser.add_argument("--local-mpp", help="Optional local MPP path to compare with remote asset.")
    parser.add_argument("--require-local-mpp", action="store_true", help="Fail if local MPP is missing.")
    parser.add_argument("--strict-raw", action="store_true", help="Treat raw.githubusercontent.com mismatch as a hard failure.")
    parser.add_argument("--skip-signature", action="store_true", help="Do not require/check .asc signature asset.")
    parser.add_argument("--allow-dirty", action="store_true", help="Allow dirty working tree.")
    args = parser.parse_args()

    root = Path.cwd()
    verdict = Verdict()

    try:
        run_git(["fetch", "origin", "--tags", "--prune"])
    except Exception as e:
        verdict.fail(f"git fetch origin --tags --prune failed: {e}")

    dirty = run_git(["status", "--porcelain"], check=False)
    if dirty and not args.allow_dirty:
        verdict.fail("working tree is dirty; use --allow-dirty only for deliberate diagnostics")

    try:
        version = args.version or parse_gradle_version(root / "gradle.properties")
    except Exception as e:
        verdict.fail(str(e))
        version = args.version or "UNKNOWN"

    local_readme = (root / "README.md").read_text(encoding="utf-8") if (root / "README.md").exists() else ""
    readme_rows = parse_readme_current_release_table(local_readme)

    tag = args.tag or readme_rows.get("Release tag") or f"v{version}"
    asset_name = args.asset or expected_asset_name(version)

    try:
        if args.repo:
            owner, repo = args.repo.split("/", 1)
        else:
            owner, repo = parse_repo_slug(run_git(["config", "--get", "remote.origin.url"]))
    except Exception as e:
        verdict.fail(str(e))
        owner, repo = "UNKNOWN", "UNKNOWN"

    expected_url = expected_download_url(owner, repo, tag, asset_name)
    expected_sig_url = f"{expected_url}.asc"

    print("===== verify remote release plan =====")
    print(f"repo: {owner}/{repo}")
    print(f"branch: {args.branch}")
    print(f"version: {version}")
    print(f"tag: {tag}")
    print(f"asset: {asset_name}")
    print(f"expected_url: {expected_url}")
    print()

    try:
        head = run_git(["rev-parse", "HEAD"])
        origin_branch = run_git(["rev-parse", f"origin/{args.branch}"])
        print("===== local/origin refs =====")
        print(f"HEAD: {head}")
        print(f"origin/{args.branch}: {origin_branch}")
        verdict.require(head == origin_branch, f"HEAD does not match origin/{args.branch}")
    except Exception as e:
        verdict.fail(f"could not verify HEAD/origin alignment: {e}")

    try:
        local_tag_commit = run_git(["rev-list", "-n", "1", tag])
    except Exception as e:
        verdict.fail(f"local tag {tag!r} missing or unreadable: {e}")
        local_tag_commit = None

    try:
        remote_tag_commit = peeled_remote_tag_commit(tag)
        verdict.require(bool(remote_tag_commit), f"remote tag {tag!r} missing")
    except Exception as e:
        verdict.fail(f"could not inspect remote tag {tag!r}: {e}")
        remote_tag_commit = None

    origin_branch_commit_for_tag_check: str | None = None
    try:
        origin_branch_commit_for_tag_check = run_git(["rev-parse", f"origin/{args.branch}"])
    except Exception as e:
        verdict.fail(f"could not resolve origin/{args.branch} for tag ancestry check: {e}")

    if local_tag_commit and remote_tag_commit:
        verdict.require(local_tag_commit == remote_tag_commit, f"local tag commit {local_tag_commit} != remote tag commit {remote_tag_commit}")

        if origin_branch_commit_for_tag_check:
            verdict.require(
                git_is_ancestor(local_tag_commit, f"origin/{args.branch}"),
                f"tag {tag} commit {local_tag_commit} is not reachable from origin/{args.branch}",
            )
            if local_tag_commit != origin_branch_commit_for_tag_check:
                verdict.warn(
                    f"tag {tag} points to historical release commit {local_tag_commit}; "
                    f"origin/{args.branch} is now {origin_branch_commit_for_tag_check}"
                )

    print("===== tag refs =====")
    print(f"local peeled tag commit: {local_tag_commit or 'MISSING'}")
    print(f"remote peeled tag commit: {remote_tag_commit or 'MISSING'}")
    print(f"origin/{args.branch}: {origin_branch_commit_for_tag_check or 'MISSING'}")
    print()

    token = github_token()

    release: dict[str, Any] = {}
    try:
        release = http_json(f"https://api.github.com/repos/{owner}/{repo}/releases/tags/{tag}", token=token)
        verdict.require(release.get("tag_name") == tag, f"GitHub release tag_name={release.get('tag_name')!r}, expected {tag!r}")
    except Exception as e:
        verdict.fail(f"GitHub release for tag {tag!r} missing/unreadable: {e}")

    assets = release.get("assets") if isinstance(release, dict) else []
    if not isinstance(assets, list):
        assets = []

    asset = next((item for item in assets if item.get("name") == asset_name), None)
    sig_asset = next((item for item in assets if item.get("name") == f"{asset_name}.asc"), None)

    verdict.require(asset is not None, f"GitHub release asset missing: {asset_name}")
    if not args.skip_signature:
        verdict.require(sig_asset is not None, f"GitHub release signature asset missing: {asset_name}.asc")

    print("===== GitHub release assets =====")
    for item in assets:
        print(f"- {item.get('name')} size={item.get('size')} url={item.get('browser_download_url')}")
    print()

    local_bundle: dict[str, Any] = {}
    origin_bundle: dict[str, Any] = {}
    api_bundle: dict[str, Any] = {}
    raw_bundle: dict[str, Any] | None = None

    try:
        local_bundle = json.loads((root / "patches-bundle.json").read_text(encoding="utf-8"))
        check_bundle(verdict, "local patches-bundle.json", local_bundle, version=version, expected_url=expected_url, expected_sig_url=expected_sig_url)
    except Exception as e:
        verdict.fail(f"local patches-bundle.json unreadable/invalid: {e}")

    try:
        origin_bundle = read_json_from_git(f"origin/{args.branch}", "patches-bundle.json")
        check_bundle(verdict, f"origin/{args.branch}:patches-bundle.json", origin_bundle, version=version, expected_url=expected_url, expected_sig_url=expected_sig_url)
    except Exception as e:
        verdict.fail(f"origin/{args.branch}:patches-bundle.json unreadable/invalid: {e}")

    try:
        api_text = read_github_contents_text(owner, repo, "patches-bundle.json", args.branch, token=token)
        api_bundle = json.loads(api_text)
        check_bundle(verdict, "GitHub contents API patches-bundle.json", api_bundle, version=version, expected_url=expected_url, expected_sig_url=expected_sig_url)
    except Exception as e:
        verdict.fail(f"GitHub contents API patches-bundle.json unreadable/invalid: {e}")

    try:
        raw_url = f"https://raw.githubusercontent.com/{owner}/{repo}/{args.branch}/patches-bundle.json"
        raw_bundle = json.loads(http_get(raw_url, token=token).decode("utf-8"))
        try:
            check_bundle(verdict, "raw patches-bundle.json", raw_bundle, version=version, expected_url=expected_url, expected_sig_url=expected_sig_url)
        except Exception as e:
            verdict.warn(f"raw patches-bundle.json check raised unexpectedly: {e}")
        raw_mismatch = raw_bundle != api_bundle if api_bundle else False
        if raw_mismatch:
            message = "raw patches-bundle.json differs from GitHub contents API; likely CDN/cache if API/git object are correct"
            if args.strict_raw:
                verdict.fail(message)
            else:
                verdict.warn(message)
    except Exception as e:
        if args.strict_raw:
            verdict.fail(f"raw patches-bundle.json unreadable/invalid: {e}")
        else:
            verdict.warn(f"raw patches-bundle.json unreadable/invalid: {e}")

    print("===== bundle states =====")
    for label, bundle in (
        ("local", local_bundle),
        (f"origin/{args.branch}", origin_bundle),
        ("github_api", api_bundle),
        ("raw", raw_bundle or {}),
    ):
        print(f"{label}: version={bundle.get('version')!r} download_url={bundle.get('download_url')!r}")
    print()

    try:
        local_pl = json.loads((root / "patches-list.json").read_text(encoding="utf-8"))
        check_patches_list(verdict, "local patches-list.json", local_pl, version=version)
    except Exception as e:
        verdict.fail(f"local patches-list.json unreadable/invalid: {e}")

    try:
        origin_pl = read_json_from_git(f"origin/{args.branch}", "patches-list.json")
        check_patches_list(verdict, f"origin/{args.branch}:patches-list.json", origin_pl, version=version)
    except Exception as e:
        verdict.fail(f"origin/{args.branch}:patches-list.json unreadable/invalid: {e}")

    try:
        api_pl = json.loads(read_github_contents_text(owner, repo, "patches-list.json", args.branch, token=token))
        check_patches_list(verdict, "GitHub contents API patches-list.json", api_pl, version=version)
    except Exception as e:
        verdict.fail(f"GitHub contents API patches-list.json unreadable/invalid: {e}")

    try:
        origin_readme = read_text_from_git(f"origin/{args.branch}", "README.md")
        origin_rows = parse_readme_current_release_table(origin_readme)
        origin_sha = readme_expected_sha(origin_readme)
        verdict.require(origin_rows.get("Version") == version, f"README Current release Version={origin_rows.get('Version')!r}, expected {version!r}")
        verdict.require(origin_rows.get("Release tag") == tag, f"README Current release Release tag={origin_rows.get('Release tag')!r}, expected {tag!r}")
        verdict.require(origin_rows.get("Asset") == asset_name, f"README Current release Asset={origin_rows.get('Asset')!r}, expected {asset_name!r}")
        verdict.require(origin_rows.get("Download URL") == expected_url, "README Current release Download URL mismatch")
        verdict.require(bool(origin_sha), "README SHA256 missing")
    except Exception as e:
        verdict.fail(f"README Current release verification failed: {e}")
        origin_sha = None

    print("===== README current release =====")
    try:
        for key, value in parse_readme_current_release_table(read_text_from_git(f"origin/{args.branch}", "README.md")).items():
            print(f"{key}: {value}")
        print(f"SHA256 resolved: {origin_sha or 'MISSING'}")
    except Exception:
        print("README table unavailable")
    print()

    remote_asset_sha: str | None = None
    remote_mpp_path: Path | None = None
    remote_sig_path: Path | None = None

    with tempfile.TemporaryDirectory(prefix="morphe-remote-release-") as tmpdir:
        tmp = Path(tmpdir)

        if asset is not None:
            try:
                asset_url = asset.get("browser_download_url")
                verdict.require(asset_url == expected_url, f"GitHub asset browser_download_url mismatch: {asset_url!r}")
                remote_bytes = http_get(asset_url, token=token)
                remote_mpp_path = tmp / asset_name
                remote_mpp_path.write_bytes(remote_bytes)
                remote_asset_sha = sha256_bytes(remote_bytes)

                print("===== remote asset =====")
                print(f"downloaded: {remote_mpp_path}")
                print(f"sha256: {remote_asset_sha}")
                print(f"size: {len(remote_bytes)}")

                if origin_sha:
                    verdict.require(remote_asset_sha == origin_sha, f"remote asset SHA {remote_asset_sha} != README SHA {origin_sha}")

                local_mpp = Path(args.local_mpp) if args.local_mpp else root / "patches" / "build" / "libs" / asset_name
                if local_mpp.exists():
                    local_sha = sha256_file(local_mpp)
                    print(f"local_mpp: {local_mpp}")
                    print(f"local_mpp_sha256: {local_sha}")
                    verdict.require(remote_asset_sha == local_sha, f"remote asset SHA {remote_asset_sha} != local MPP SHA {local_sha}")
                elif args.require_local_mpp:
                    verdict.fail(f"local MPP missing: {local_mpp}")
                else:
                    verdict.warn(f"local MPP missing; skipped local-vs-remote asset comparison: {local_mpp}")

                dex_entries, has_boost_ext = inspect_mpp(remote_mpp_path)
                print(f"dex_entries: {', '.join(dex_entries) if dex_entries else 'MISSING'}")
                print(f"boost_extension: {'OK' if has_boost_ext else 'MISSING'}")
                verdict.require(any(Path(name).name == "classes.dex" for name in dex_entries), "remote MPP missing classes.dex")
                verdict.require(has_boost_ext, "remote MPP missing extensions/boostforreddit.mpe")
                print()
            except Exception as e:
                verdict.fail(f"remote asset download/inspection failed: {e}")

        if sig_asset is not None and not args.skip_signature and remote_mpp_path is not None:
            try:
                sig_url = sig_asset.get("browser_download_url")
                verdict.require(sig_url == expected_sig_url, f"GitHub signature asset browser_download_url mismatch: {sig_url!r}")
                sig_bytes = http_get(sig_url, token=token)
                remote_sig_path = tmp / f"{asset_name}.asc"
                remote_sig_path.write_bytes(sig_bytes)

                print("===== remote signature =====")
                print(f"downloaded: {remote_sig_path}")
                print(f"size: {len(sig_bytes)}")

                if shutil.which("gpg"):
                    proc = run_cmd(["gpg", "--verify", str(remote_sig_path), str(remote_mpp_path)])
                    gpg_out = (proc.stdout + proc.stderr).decode("utf-8", errors="replace")
                    print(gpg_out.strip())
                    verdict.require(proc.returncode == 0, "gpg --verify failed for remote MPP signature")
                else:
                    verdict.warn("gpg not found; signature asset exists but cryptographic verification was skipped")
                print()
            except Exception as e:
                verdict.fail(f"remote signature download/verification failed: {e}")

    print("===== warnings =====")
    if verdict.warnings:
        for warning in verdict.warnings:
            print(f" - {warning}")
    else:
        print("(none)")

    print()
    if verdict.errors:
        print("REMOTE RELEASE FAILED")
        for error in verdict.errors:
            print(f" - {error}")
        return 1

    print("REMOTE RELEASE OK")
    print(f"Version: {version}")
    print(f"Tag: {tag}")
    print(f"Asset: {asset_name}")
    if remote_asset_sha:
        print(f"Remote asset SHA256: {remote_asset_sha}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
