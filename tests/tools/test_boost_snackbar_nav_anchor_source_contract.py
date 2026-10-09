from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PATCH = (
    ROOT
    / "patches/src/main/kotlin/app/morphe/patches/reddit/customclients"
    / "boostforreddit/fix/homefab/AnchorSnackbarAboveNavigationPatch.kt"
)
UTILS = (
    ROOT
    / "extensions/boostforreddit/src/main/java/app/morphe/extension"
    / "boostforreddit/utils"
)


class BoostSnackbarNavAnchorSourceContractTest(unittest.TestCase):
    def test_patch_hooks_show_and_disarms_hidden_legacy_navigation(self) -> None:
        source = PATCH.read_text(encoding="utf-8")

        self.assertIn('name = "a0"', source)
        self.assertIn("->anchorFor(", source)
        self.assertIn("->V(Landroid/view/View;)", source)
        # The hidden AHBottomNavigation behavior zeroed Snackbar bottom
        # margins (dropping Material's anchor offset) on every layout.
        self.assertIn("AHBottomNavigationBehavior;", source)
        self.assertIn('name = "Q"', source)
        self.assertIn("->isShown()Z", source)

    def test_extension_keeps_native_anchor_and_tracks_foreign_snackbars(self) -> None:
        anchor = (UTILS / "BoostSnackbarAnchor.java").read_text(encoding="utf-8")
        clearance = (UTILS / "BoostFabClearance.java").read_text(encoding="utf-8")

        self.assertEqual(1, anchor.count("MORPHE_BOOST_SNACKBAR_NAV_ANCHOR_V1"))
        self.assertIn("currentAnchor != null && currentAnchor.isShown()", anchor)
        self.assertIn("BoostFabClearance.trackForeignObstruction(", anchor)
        self.assertNotIn("DEBUG", anchor)
        self.assertIn("public static void trackForeignObstruction(", clearance)
        self.assertIn("removeOnPreDrawListener", clearance)


if __name__ == "__main__":
    unittest.main()
