from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
EXT = ROOT / "extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/sidebar/SidebarTrendingPosts.java"
PATCH = ROOT / "patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/sidebartrending/BoostSidebarTrendingTodayPatch.kt"
FP = ROOT / "patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/sidebartrending/Fingerprints.kt"


class BoostSidebarTrendingContractTest(unittest.TestCase):
    def test_extension_uses_native_first(self):
        text = EXT.read_text()
        self.assertIn("loadNativeRows()", text)
        self.assertIn("filterRenderableRows(nativeRows)", text)
        self.assertIn("source=native", text)

    def test_fallback_uses_hot_public_listing(self):
        text = EXT.read_text()
        self.assertIn('fetchHotListing(client, "popular")', text)
        self.assertIn('fetchHotListing(client, "all")', text)
        self.assertIn('applySorting(paginator, "HOT")', text)

    def test_fallback_builds_native_models(self):
        text = EXT.read_text()
        self.assertIn("com.rubenmayayo.reddit.models.reddit.SubmissionModel", text)
        self.assertIn("com.rubenmayayo.reddit.models.reddit.m", text)
        self.assertIn('rowClass.getMethod("d", List.class)', text)

    def test_global_limit_control_is_removed(self):
        text = EXT.read_text()
        self.assertIn('findView(root, "limit_group")', text)
        self.assertIn("removeView(limitGroup)", text)
        self.assertIn('limit_control=removed context=global', text)

    def test_subreddit_context_gets_explicit_label(self):
        text = EXT.read_text()
        self.assertIn('findNoArgMethod(activity.getClass(), "W1")', text)
        self.assertIn('setText("Limit to r/" + community)', text)
        self.assertIn("setChecked(false)", text)

    def test_patch_targets_sidebar_loader_not_search_generic(self):
        patch = PATCH.read_text()
        fingerprints = FP.read_text()
        self.assertIn("trendingTodayAsyncLoaderFingerprint", patch)
        self.assertIn("TrendingTodayFragment", fingerprints)
        self.assertIn('classDef.type == "Lod/a;"', fingerprints)
        self.assertNotIn("SearchGenericActivity", patch + fingerprints)

    def test_load_is_triggered_immediately(self):
        extension = EXT.read_text()
        patch = PATCH.read_text()
        self.assertIn("public static void triggerLoad", extension)
        self.assertIn("load_trigger=immediate", extension)
        self.assertIn("->triggerLoad(Ljava/lang/Object;)V", patch)

    def test_front_page_sentinel_is_global(self):
        text = EXT.read_text()
        self.assertIn('_load_front_page_this_is_not_a_subreddit', text)
        self.assertIn('name.contains("not_a_subreddit")', text)

    def test_fallback_rows_are_identity_marked(self):
        text = EXT.read_text()
        self.assertIn("IdentityHashMap<Object, Boolean>", text)
        self.assertIn("fallbackRows.addAll(rows)", text)
        self.assertIn("fallbackRows.contains(row)", text)

    def test_bind_entry_records_row_before_parameter_reuse(self):
        extension = EXT.read_text()
        patch = PATCH.read_text()
        self.assertIn("public static void recordBoundRow", extension)
        self.assertIn("invoke-static {p0, p1}", patch)
        self.assertIn("->recordBoundRow(Ljava/lang/Object;Ljava/lang/Object;)V", patch)
        self.assertLess(
            patch.index("->recordBoundRow(Ljava/lang/Object;Ljava/lang/Object;)V"),
            patch.index("val returnIndices"),
        )

    def test_fallback_description_policy_is_identity_based(self):
        extension = EXT.read_text()
        patch = PATCH.read_text()
        fingerprints = FP.read_text()
        self.assertIn("public static void applyBoundTextPolicy", extension)
        self.assertIn("fallback ? View.GONE : View.VISIBLE", extension)
        self.assertIn("holderFallbackState", extension)
        self.assertNotIn("normalizeComparableText", extension)
        self.assertIn("trendingTodayViewHolderBindFingerprint", patch)
        self.assertIn("->applyBoundTextPolicy(Ljava/lang/Object;)V", patch)
        self.assertIn('TrendingTodayAdapter\\$TrendingTodayViewHolder', fingerprints)

    def test_marker_is_versioned(self):
        self.assertIn("MORPHE_SIDEBAR_TRENDING_POSTS_V3", EXT.read_text())


if __name__ == "__main__":
    unittest.main()
