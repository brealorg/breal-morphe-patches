from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EXT = ROOT / "extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit"
KEY = "morphe_boost_search_show_active_subreddits"


class BoostSearchActiveSubredditsToggleSourceContractTest(unittest.TestCase):
    """Issue #201: the Search "Active subreddits" landing must be switchable."""

    def test_setting_is_on_the_suggestions_page_and_defaults_on(self) -> None:
        registry = (EXT / "settings/MorpheSettingsV5Registry.java").read_text(encoding="utf-8")
        suggestions = next(
            line for line in registry.splitlines()
            if '"v5/reading_and_interaction/search_and_filters/search/suggestions"' in line
            and "V5PageSpec" in line
        )
        self.assertIn(f'"{KEY}"', suggestions)

        metadata = (EXT / "settings/MorpheSettingsV5ReadingMetadata.java").read_text(encoding="utf-8")
        self.assertEqual(2, metadata.count(f'case "{KEY}":'))

        skeleton = (
            ROOT
            / "patches/src/main/kotlin/app/morphe/patches/reddit/customclients"
            / "boostforreddit/misc/settings/BoostMorpheSettingsSkeletonPatch.kt"
        ).read_text(encoding="utf-8")
        start = skeleton.index(f'android:key="{KEY}"')
        self.assertIn('android:defaultValue="true"', skeleton[start:start + 400])

    def test_search_rows_respect_the_setting(self) -> None:
        source = (EXT / "search/SearchExploreRows.java").read_text(encoding="utf-8")
        self.assertEqual(1, source.count("MORPHE_SEARCH_ACTIVE_SUBREDDITS_PREFERENCE_ISSUE201_V1"))
        self.assertIn(f'"{KEY}"', source)
        self.assertIn(".getBoolean(PREFERENCE_KEY, true)", source)
        # Both the instant path and the async refresh are gated.
        self.assertEqual(2, source.count("!isActiveSubredditsEnabled(activity)"))
        # Reddit-native filter labels are still normalized when disabled.
        gate = source.index("if (!isActiveSubredditsEnabled(activity)) {")
        self.assertLess(source.index("normalizeRedditSearchFilterLabels(activity);"), gate)


if __name__ == "__main__":
    unittest.main()
