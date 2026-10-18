from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PATCH = (
    ROOT
    / "patches/src/main/kotlin/app/morphe/patches/reddit/customclients"
    / "boostforreddit/fix/feed/FeedEndFooterPatch.kt"
)
EXTENSION = (
    ROOT
    / "extensions/boostforreddit/src/main/java/app/morphe/extension"
    / "boostforreddit/utils/BoostFeedEndFooter.java"
)
CLEARANCE = (
    ROOT
    / "extensions/boostforreddit/src/main/java/app/morphe/extension"
    / "boostforreddit/utils/BoostNavContentClearance.java"
)


class BoostFeedEndFooterSourceContractTest(unittest.TestCase):
    """Issue #244: an ended feed must say so instead of silently stopping."""

    def test_patch_hooks_load_append_and_replace(self) -> None:
        source = PATCH.read_text(encoding="utf-8")

        self.assertIn('definingClass = "Lxb/l;"', source)
        self.assertIn('name = "s0"', source)
        self.assertIn('name = "W"', source)
        self.assertIn('name = "o0"', source)
        self.assertIn("->onLoadPage(Lnet/dean/jraw/paginators/Paginator;)V", source)
        self.assertIn("->onAppendPage(", source)
        self.assertIn("->onReplacePage(", source)

    def test_footer_only_for_an_ended_listing(self) -> None:
        source = EXTENSION.read_text(encoding="utf-8")

        self.assertEqual(1, source.count("MORPHE_BOOST_FEED_END_FOOTER_ISSUE244_V1"))
        # Only an empty page after Reddit stopped sending a cursor ends the feed.
        self.assertIn("if (paginator == null || paginator.hasNext()) {", source)
        self.assertIn("paginator.getSorting() == Sorting.BEST", source)
        self.assertIn('getMethod("onRefresh")', source)

    def test_nav_clearance_keeps_room_for_the_footer(self) -> None:
        source = CLEARANCE.read_text(encoding="utf-8")

        self.assertEqual(2, source.count("BoostFeedEndFooter.reservedHeight(list)"))


if __name__ == "__main__":
    unittest.main()
