#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / (
    "extensions/boostforreddit/src/main/java/app/morphe/extension/"
    "boostforreddit/gallery/GalleryAnimatedPreview.java"
)
PATCH = ROOT / (
    "patches/src/main/kotlin/app/morphe/patches/reddit/customclients/"
    "boostforreddit/fix/gallery/GalleryAnimatedPreviewPatch.kt"
)


def test_gallery_preview_accepts_animated_video_sources():
    source = SOURCE.read_text(encoding="utf-8")

    assert "MORPHE_BOOST_GALLERY_ANIMATED_PREVIEW_V8_MEDIA_SOURCES" in source
    assert 'callString(model, "getMp4")' in source
    assert 'callBoolean(model, "isAnimated")' in source
    assert "isSupportedMediaUrl(page.animatedUrl)" in source
    assert "isDirectGif" not in source


def test_gallery_preview_selects_boost_media_source_types():
    source = SOURCE.read_text(encoding="utf-8")

    assert 'path.endsWith(".mpd")' in source
    assert 'path.endsWith(".m3u8")' in source
    assert "com.google.android.exoplayer2.source.dash.DashMediaSource$Factory" in source
    assert "com.google.android.exoplayer2.source.hls.HlsMediaSource$Factory" in source
    assert 'Class.forName("s4.h0$b")' in source
    assert "buildMediaSource(context, page.animatedUrl)" in source


def test_patch_description_covers_gif_and_video():
    patch = PATCH.read_text(encoding="utf-8")

    assert "gallery GIF and video media" in patch


if __name__ == "__main__":
    test_gallery_preview_accepts_animated_video_sources()
    test_gallery_preview_selects_boost_media_source_types()
    test_patch_description_covers_gif_and_video()
