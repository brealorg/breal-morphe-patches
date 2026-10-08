from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
HOMEFAB = (
    ROOT
    / "patches/src/main/kotlin/app/morphe/patches/reddit/customclients"
    / "boostforreddit/fix/homefab"
)
EXTENSION = (
    ROOT
    / "extensions/boostforreddit/src/main/java/app/morphe/extension"
    / "boostforreddit/utils/BoostFabClearance.java"
)


class BoostFabDependencyClearanceSourceContractTest(unittest.TestCase):
    """Issue #179: Snackbar removal must not drop FABs behind navigation."""

    def test_static_fab_margin_patch_depends_on_clearance(self) -> None:
        source = (HOMEFAB / "FixHomeFloatingActionMenuOverlapPatch.kt").read_text(
            encoding="utf-8"
        )
        # The resource patch removes the static bottom offset, so it must
        # always ship together with the dependency-aware translation.
        self.assertIn('INCLUDED_FAB_BOTTOM_MARGIN =\n    "0.0dp"', source)
        self.assertIn("dependsOn(fixFabDependencyClearancePatch)", source)
        # Sizing the include would drop fab_random's native layout_behavior.
        self.assertNotIn('"@layout/fab_random"', source)

    def test_both_scroll_aware_behaviors_are_hooked(self) -> None:
        fingerprints = (HOMEFAB / "Fingerprints.kt").read_text(encoding="utf-8")
        patch = (HOMEFAB / "FixFabDependencyClearancePatch.kt").read_text(
            encoding="utf-8"
        )

        for name in (
            "fabMenuDependentViewChangedFingerprint",
            "fabMenuDependentViewRemovedFingerprint",
            "fabBottomNavigationDependentViewChangedFingerprint",
            "fabBottomNavigationDependentViewRemovedFingerprint",
        ):
            self.assertIn(f"internal val {name}", fingerprints)
            self.assertIn(name, patch)

        self.assertIn("->onDependentViewChanged(", patch)
        self.assertIn("->onDependentViewRemoved(", patch)

    def test_extension_uses_every_visible_dependency(self) -> None:
        source = EXTENSION.read_text(encoding="utf-8")

        self.assertEqual(
            1, source.count("MORPHE_BOOST_FAB_DEPENDENCY_CLEARANCE_ISSUE179_V2")
        )
        for dependency in (
            "com.google.android.material.snackbar.Snackbar$SnackbarLayout",
            "com.google.android.material.bottomnavigation.BottomNavigationView",
            "com.aurelhubert.ahbottomnavigation.AHBottomNavigation",
            "com.rubenmayayo.reddit.ui.customviews.NavigationArrowsView",
        ):
            self.assertIn(f'"{dependency}"', source)

        # The removed Snackbar must be excluded and the highest dependency wins.
        self.assertIn("dependency == excluded", source)
        self.assertIn("Math.min(", source)
        self.assertNotIn("coordinator.getBottom() - child.getBottom()", source)
        self.assertIn("visibleTop - anchorBottom", source)


if __name__ == "__main__":
    unittest.main()
