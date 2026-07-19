#!/usr/bin/env python3
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / (
    "extensions/boostforreddit/src/main/java/app/morphe/extension/"
    "boostforreddit/gallery/GalleryAnimatedPreview.java"
)
PATCH = ROOT / (
    "patches/src/main/kotlin/app/morphe/patches/reddit/customclients/"
    "boostforreddit/fix/gallery/GalleryAnimatedPreviewPatch.kt"
)


class GalleryAnimatedPreviewSourceContractTests(unittest.TestCase):
    def test_gallery_preview_accepts_animated_video_sources(self):
        source = SOURCE.read_text(encoding="utf-8")

        self.assertIn(
            "MORPHE_BOOST_GALLERY_ANIMATED_PREVIEW_V8_MEDIA_SOURCES",
            source,
        )
        self.assertIn('callString(model, "getMp4")', source)
        self.assertIn('callBoolean(model, "isAnimated")', source)
        self.assertIn("isSupportedMediaUrl(page.animatedUrl)", source)
        self.assertNotIn("isDirectGif", source)

    def test_gallery_preview_selects_boost_media_source_types(self):
        source = SOURCE.read_text(encoding="utf-8")

        self.assertIn('path.endsWith(".mpd")', source)
        self.assertIn('path.endsWith(".m3u8")', source)
        self.assertIn(
            "com.google.android.exoplayer2.source.dash."
            "DashMediaSource$Factory",
            source,
        )
        self.assertIn(
            "com.google.android.exoplayer2.source.hls."
            "HlsMediaSource$Factory",
            source,
        )
        self.assertIn('Class.forName("s4.h0$b")', source)
        self.assertIn("buildMediaSource(context, page.animatedUrl)", source)

    def test_patch_description_covers_gif_and_video(self):
        patch = PATCH.read_text(encoding="utf-8")

        self.assertIn("gallery GIF and video media", patch)


if __name__ == "__main__":
    unittest.main()
