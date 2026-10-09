from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE = (
    ROOT
    / "extensions/boostforreddit/src/main/java/app/morphe/extension"
    / "boostforreddit/utils/BoostSystemBarInsetsFix.java"
)


class BoostImageViewerToolbarInsetSourceContractTest(unittest.TestCase):
    """Issue #189: the image viewer toolbar must not get the top inset twice."""

    def test_safe_area_listener_consumes_insets_after_padding(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        self.assertEqual(
            1, source.count('"MORPHE_BOOST_IMAGE_VIEWER_SAFE_AREA_ISSUE189_V2"')
        )

        match = re.search(
            r"private static void applySafeAreaPadding\(final View view\) \{.*?\n    }\n",
            source,
            flags=re.S,
        )
        self.assertIsNotNone(match)
        method = match.group(0)
        # Pad first (keeps #171 safe-area behavior), then consume so the
        # fitsSystemWindows toolbar does not pad itself again.
        self.assertRegex(
            method,
            r"(?s)applySafeAreaPaddingNow\(v, insets\);\s*.*?return consumeSafeAreaInsets\(insets\);",
        )

    def test_consume_helper_covers_system_bars_and_cutout(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn("return WindowInsets.CONSUMED;", source)
        self.assertIn("insets.consumeSystemWindowInsets()", source)
        self.assertIn("consumed.consumeDisplayCutout()", source)


if __name__ == "__main__":
    unittest.main()
