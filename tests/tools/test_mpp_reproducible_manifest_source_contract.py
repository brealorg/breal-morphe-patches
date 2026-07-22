from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILD_FILE = ROOT / "patches" / "build.gradle.kts"
MARKER = "MORPHE_DETERMINISTIC_MPP_MANIFEST_TIMESTAMP_V1"


class MppReproducibleManifestSourceContractTest(unittest.TestCase):
    def test_timestamp_has_one_deterministic_override(self) -> None:
        text = BUILD_FILE.read_text(encoding="utf-8")

        self.assertEqual(1, text.count(MARKER))
        self.assertEqual(
            1,
            len(
                re.findall(
                    r'manifest\.attributes\["Timestamp"\]\s*=\s*"0"',
                    text,
                )
            ),
        )
        self.assertIn(
            "tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach",
            text,
        )

    def test_override_contains_no_active_wall_clock_expression(self) -> None:
        text = BUILD_FILE.read_text(encoding="utf-8")
        block = text.split(MARKER, 1)[1]

        # Remove Kotlin line comments before checking executable text.
        active_code = "\n".join(
            line.split("//", 1)[0]
            for line in block.splitlines()
        )

        self.assertNotIn("System.currentTimeMillis()", active_code)
        self.assertNotIn("Instant.now()", active_code)
        self.assertNotIn("LocalDateTime.now()", active_code)


if __name__ == "__main__":
    unittest.main()
