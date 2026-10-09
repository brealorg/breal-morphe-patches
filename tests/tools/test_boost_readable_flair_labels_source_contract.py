from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PATCH = (
    ROOT
    / "patches/src/main/kotlin/app/morphe/patches/reddit/customclients"
    / "boostforreddit/fix/flair/ReadableFlairLabelsPatch.kt"
)
EXTENSION = (
    ROOT
    / "extensions/boostforreddit/src/main/java/app/morphe/extension"
    / "boostforreddit/utils/BoostFlairContrast.java"
)


class BoostReadableFlairLabelsSourceContractTest(unittest.TestCase):
    """Issue #197: flair text must stay readable on its background."""

    def test_patch_adjusts_flair_colors_text_field(self) -> None:
        source = PATCH.read_text(encoding="utf-8")
        self.assertIn('"Lcom/rubenmayayo/reddit/ui/customviews/m;"', source)
        self.assertIn('name = "<init>"', source)
        # b = text color, a = background color in Boost's FlairColors.
        self.assertIn("iget p1, p0, $FLAIR_COLORS->b:I", source)
        self.assertIn("iget p2, p0, $FLAIR_COLORS->a:I", source)
        self.assertIn("->readableTextColor(II)I", source)
        self.assertIn("iput p1, p0, $FLAIR_COLORS->b:I", source)

    def test_extension_only_overrides_unreadable_opaque_flair(self) -> None:
        source = EXTENSION.read_text(encoding="utf-8")
        self.assertIn("static final double MIN_CONTRAST = 3.0;", source)
        self.assertIn("Color.alpha(backgroundColor) < 0xF0", source)
        self.assertIn("contrast(textColor, backgroundColor) >= MIN_CONTRAST", source)


if __name__ == "__main__":
    unittest.main()
