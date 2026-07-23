import shutil
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class BoostMppResolverTests(unittest.TestCase):
    def make_repo(self, version: str = "9.9.9") -> Path:
        temp = Path(tempfile.mkdtemp(prefix="boost-mpp-test-"))
        self.addCleanup(shutil.rmtree, temp)
        (temp / "tools").mkdir()
        (temp / "patches" / "build" / "libs").mkdir(parents=True)
        (temp / "gradle.properties").write_text(
            f"version = {version}\n", encoding="utf-8"
        )
        for name in ("boost-resolve-mpp.sh", "check-mpp-release-asset.sh"):
            target = temp / "tools" / name
            shutil.copy2(ROOT / "tools" / name, target)
            target.chmod(0o755)
        return temp

    @staticmethod
    def write_mpp(path: Path, *, android: bool) -> None:
        with zipfile.ZipFile(path, "w") as archive:
            if android:
                archive.writestr("classes.dex", b"dex\n")
            archive.writestr("extensions/boostforreddit.mpe", b"extension")

    def test_resolves_exact_version_and_ignores_newer_documentation_archives(self) -> None:
        repo = self.make_repo()
        expected = repo / "patches" / "build" / "libs" / "patches-9.9.9.mpp"
        self.write_mpp(expected, android=True)
        self.write_mpp(expected.with_name("patches-9.9.9-javadoc.mpp"), android=False)

        completed = subprocess.run(
            [repo / "tools" / "boost-resolve-mpp.sh"],
            cwd=repo,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertEqual(completed.stdout.strip(), str(expected))

    def test_rejects_assemble_mpp_without_android_dex(self) -> None:
        repo = self.make_repo()
        mpp = repo / "patches" / "build" / "libs" / "patches-9.9.9.mpp"
        self.write_mpp(mpp, android=False)

        completed = subprocess.run(
            [repo / "tools" / "boost-resolve-mpp.sh"],
            cwd=repo,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

        self.assertEqual(completed.returncode, 1)
        self.assertIn("missing required MPP entries: classes.dex", completed.stderr)
        self.assertIn(":patches:buildAndroid", completed.stderr)


class BoostMppWorkflowContractTests(unittest.TestCase):
    def test_dev_runtime_builds_android_mpp_and_uses_resolver(self) -> None:
        text = (ROOT / "tools" / "boost-dev-issue-runtime.sh").read_text(
            encoding="utf-8"
        )
        self.assertIn(":patches:buildAndroid", text)
        self.assertIn("tools/boost-resolve-mpp.sh", text)
        self.assertNotIn("-name 'patches-*.mpp'", text)

    def test_dev_from_mpp_checks_structure_before_candidate_build(self) -> None:
        text = (ROOT / "tools" / "boost-dev-from-mpp.sh").read_text(
            encoding="utf-8"
        )
        check = text.index('tools/check-mpp-release-asset.sh "$MPP"')
        build = text.index("===== build patched normal candidate =====")
        self.assertLess(check, build)

    def test_debuggable_dev_is_opt_in_and_forwarded_only_to_clone(self) -> None:
        text = (ROOT / "tools" / "boost-dev-from-mpp.sh").read_text(
            encoding="utf-8"
        )
        self.assertIn("--debuggable-dev", text)
        self.assertIn("DEBUGGABLE_DEV=0", text)
        self.assertIn("DEVCLONE_ARGS+=(--debuggable)", text)
        self.assertIn("normal candidate must remain non-debuggable", text)

    def test_devclone_sets_and_verifies_debuggable_manifest_on_request(self) -> None:
        text = (ROOT / "tools" / "build-boost-devclone-candidate.sh").read_text(
            encoding="utf-8"
        )
        self.assertIn("DEBUGGABLE=0", text)
        self.assertIn('node.set(android_debuggable, "true")', text)
        self.assertIn("signed DEV debuggable state matches request", text)

    def test_debuggable_dev_gateway_is_exact_dump_protected_and_opt_in(self) -> None:
        text = (ROOT / "tools" / "build-boost-devclone-candidate.sh").read_text(
            encoding="utf-8"
        )
        self.assertIn(
            'AUDIT_SETTINGS_ACTIVITY="com.rubenmayayo.reddit.ui.preferences.v2.SettingsActivityCompat"',
            text,
        )
        self.assertIn('AUDIT_GATEWAY_PERMISSION="android.permission.DUMP"', text)
        self.assertIn('node.set(android_exported, "true")', text)
        self.assertIn(
            "node.set(android_permission, audit_gateway_permission)", text
        )
        self.assertIn("if debuggable and audit_gateway_rewrites != 1", text)
        self.assertIn(
            "signed DEV settings audit gateway is exported and DUMP-protected",
            text,
        )
        self.assertIn("check_boost_settings_audit_gateway.py", text)
        self.assertIn(
            "decoded DEV settings audit gateway state matches request", text
        )

    def test_normal_candidate_builder_does_not_enable_android_debugging(self) -> None:
        text = (ROOT / "tools" / "build-boost-candidate.sh").read_text(
            encoding="utf-8"
        )
        self.assertNotIn('key == "Enable Android debugging"', text)

    def test_outer_workflow_rejects_audit_gateway_in_normal_candidate(self) -> None:
        text = (ROOT / "tools" / "boost-dev-from-mpp.sh").read_text(
            encoding="utf-8"
        )
        self.assertIn(
            "normal candidate must not expose the settings audit gateway", text
        )
        self.assertIn("check_boost_settings_audit_gateway.py", text)
        self.assertNotIn(
            "grep -Fq 'android.permission.DUMP' \"$NORMAL_MANIFEST_XMLTREE\"",
            text,
        )


if __name__ == "__main__":
    unittest.main()
