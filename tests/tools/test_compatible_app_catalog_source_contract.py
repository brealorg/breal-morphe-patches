from __future__ import annotations

import json
import re
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from compatible_app_catalog import SUPPORTED_APP_META


COMPATIBILITY_PATTERN = re.compile(
    r"Compatibility\(\s*"
    r'name\s*=\s*"([^"]+)"\s*,\s*'
    r'packageName\s*=\s*"([^"]+)"',
    re.DOTALL,
)


class CompatibleAppCatalogSourceContractTest(unittest.TestCase):
    def test_catalog_matches_patch_compatibility_declarations(self) -> None:
        source_apps: dict[str, str] = {}
        source_root = ROOT / "patches" / "src" / "main" / "kotlin"

        for path in source_root.rglob("*.kt"):
            for name, package_name in COMPATIBILITY_PATTERN.findall(
                path.read_text(encoding="utf-8")
            ):
                previous = source_apps.setdefault(package_name, name)
                self.assertEqual(previous, name, package_name)

        catalog_apps = {
            package_name: str(meta["name"])
            for package_name, meta in SUPPORTED_APP_META.items()
        }

        self.assertEqual(19, len(source_apps))
        self.assertEqual(source_apps, catalog_apps)

    def test_catalog_matches_published_feed_names(self) -> None:
        data = json.loads((ROOT / "patches-list.json").read_text(encoding="utf-8"))
        feed_apps: dict[str, str] = {}

        for patch in data.get("patches", []):
            for app in patch.get("compatiblePackages") or []:
                package_name = str(app["packageName"])
                name = str(app["name"])
                previous = feed_apps.setdefault(package_name, name)
                self.assertEqual(previous, name, package_name)

        catalog_apps = {
            package_name: str(meta["name"])
            for package_name, meta in SUPPORTED_APP_META.items()
        }

        self.assertEqual(19, len(feed_apps))
        self.assertEqual(feed_apps, catalog_apps)

    def test_supported_metadata_is_manager_complete(self) -> None:
        required = {
            "name",
            "description",
            "apkFileType",
            "appIconColor",
            "signatures",
            "minSdk",
        }
        for package_name, meta in SUPPORTED_APP_META.items():
            self.assertEqual(required, set(meta), package_name)
            self.assertTrue(str(meta["name"]).strip(), package_name)
            self.assertEqual("APK_REQUIRED", meta["apkFileType"], package_name)
            self.assertEqual(21, meta["minSdk"], package_name)

    def test_documented_catalog_matches_tooling_catalog(self) -> None:
        table_pattern = re.compile(r"^\| `([^`]+)` \| ([^|]+?) \|$", re.MULTILINE)
        documented_apps = dict(
            table_pattern.findall(
                (ROOT / "docs" / "supported-app-catalog.md").read_text(
                    encoding="utf-8"
                )
            )
        )
        catalog_apps = {
            package_name: str(meta["name"])
            for package_name, meta in SUPPORTED_APP_META.items()
        }

        self.assertEqual(19, len(documented_apps))
        self.assertEqual(catalog_apps, documented_apps)


if __name__ == "__main__":
    unittest.main()
