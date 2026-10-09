from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PATCH = (
    ROOT
    / "patches/src/main/kotlin/app/morphe/patches/reddit/customclients"
    / "boostforreddit/fix/feed/AutoLoadFilteredPagePatch.kt"
)
EXTENSION = (
    ROOT
    / "extensions/boostforreddit/src/main/java/app/morphe/extension"
    / "boostforreddit/utils/BoostFilteredPageAutoLoad.java"
)


class BoostFilteredPageAutoLoadSourceContractTest(unittest.TestCase):
    """Issue #179: a fully filtered page must not leave the feed stuck."""

    def test_patch_reuses_native_load_next_action(self) -> None:
        source = PATCH.read_text(encoding="utf-8")

        self.assertIn('name = "E1"', source)
        self.assertIn('"Lcom/rubenmayayo/reddit/ui/fragments/l\\$b;"', source)
        self.assertIn("->onPageFiltered(", source)
        # Falls through to the native Snackbar when the extension declines.
        self.assertIn('ExternalLabel("morphe_show_native_snackbar", getInstruction(0))', source)

    def test_extension_is_bounded_and_deferred(self) -> None:
        source = EXTENSION.read_text(encoding="utf-8")

        self.assertEqual(
            1, source.count("MORPHE_BOOST_FILTERED_PAGE_AUTOLOAD_ISSUE179_V1")
        )
        self.assertIn("static final int MAX_CONSECUTIVE_AUTO_LOADS = 5;", source)
        self.assertIn("static final long STREAK_WINDOW_MS = 20_000L;", source)
        self.assertIn("view.post(() -> loadNext.onClick(view));", source)
        self.assertIn("return false;", source)


if __name__ == "__main__":
    unittest.main()
