import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CHECKER = ROOT / "tools" / "check_boost_settings_audit_gateway.py"
ACTIVITY = "com.rubenmayayo.reddit.ui.preferences.v2.SettingsActivityCompat"


class BoostSettingsAuditGatewayTests(unittest.TestCase):
    def run_checker(self, manifest: str, expected: str) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory(prefix="settings-audit-gateway-") as temp:
            path = Path(temp) / "manifest.txt"
            path.write_text(textwrap.dedent(manifest), encoding="utf-8")
            return subprocess.run(
                [
                    "python3",
                    str(CHECKER),
                    "--manifest",
                    str(path),
                    "--expect",
                    expected,
                ],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )

    @staticmethod
    def xmltree(settings_attributes: str) -> str:
        return f"""
        N: android=http://schemas.android.com/apk/res/android
          E: manifest
            E: application
              E: activity
                A: android:name(0x01010003)=\"{ACTIVITY}\" (Raw: \"{ACTIVITY}\")
                {settings_attributes}
              E: receiver
                A: android:name(0x01010003)=\"androidx.work.impl.diagnostics.DiagnosticsReceiver\"
                A: android:permission(0x01010006)=\"android.permission.DUMP\" (Raw: \"android.permission.DUMP\")
                A: android:exported(0x01010010)=(type 0x12)0xffffffff
        """

    def test_existing_dump_receiver_does_not_create_settings_gateway(self) -> None:
        completed = self.run_checker(self.xmltree(""), "absent")
        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertIn("SETTINGS_AUDIT_GATEWAY=ABSENT", completed.stdout)

    def test_exact_exported_dump_protected_activity_is_present(self) -> None:
        completed = self.run_checker(
            self.xmltree(
                """A: android:permission(0x01010006)=\"android.permission.DUMP\" (Raw: \"android.permission.DUMP\")
                A: android:exported(0x01010010)=(type 0x12)0xffffffff"""
            ),
            "present",
        )
        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertIn("SETTINGS_AUDIT_GATEWAY=PRESENT", completed.stdout)

    def test_partial_gateway_fails_closed(self) -> None:
        completed = self.run_checker(
            self.xmltree(
                'A: android:permission(0x01010006)="android.permission.DUMP"'
            ),
            "absent",
        )
        self.assertEqual(completed.returncode, 1)

    def test_decoded_xml_uses_same_exact_component_check(self) -> None:
        completed = self.run_checker(
            f"""<?xml version=\"1.0\" encoding=\"utf-8\"?>
            <manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">
              <application>
                <activity android:name=\"{ACTIVITY}\" />
                <receiver android:name=\"DiagnosticsReceiver\"
                  android:permission=\"android.permission.DUMP\"
                  android:exported=\"true\" />
              </application>
            </manifest>""",
            "absent",
        )
        self.assertEqual(completed.returncode, 0, completed.stderr)


if __name__ == "__main__":
    unittest.main()
