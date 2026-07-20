#!/usr/bin/env python3
"""Canonical app/package metadata used by feed normalization and README generation.

SUPPORTED_APP_META mirrors the Compatibility declarations in patch source. Legacy
display names are retained only so older feed shapes normalize without exposing raw
package identifiers.
"""

from __future__ import annotations

from typing import Any


def _meta(name: str, color: str = "#607D8B") -> dict[str, Any]:
    return {
        "name": name,
        "description": None,
        "apkFileType": "APK_REQUIRED",
        "appIconColor": color,
        "signatures": None,
        "minSdk": 21,
    }


SUPPORTED_APP_META: dict[str, dict[str, Any]] = {
    "com.andrewshu.android.reddit": _meta("rif is fun"),
    "com.andrewshu.android.redditdonation": _meta("rif is fun golden platinum"),
    "com.imgur.mobile": _meta("Imgur", "#1BB76E"),
    "com.laurencedawson.reddit_sync": _meta("Sync for Reddit"),
    "com.laurencedawson.reddit_sync.dev": _meta("Sync for Reddit Dev"),
    "com.laurencedawson.reddit_sync.pro": _meta("Sync for Reddit Pro"),
    "com.onelouder.baconreader": _meta("BaconReader"),
    "com.onelouder.baconreader.premium": _meta("BaconReader Premium"),
    "com.rubenmayayo.reddit": _meta("Boost for Reddit", "#FF4500"),
    "free.reddit.news": _meta("Relay for Reddit"),
    "io.syncapps.lemmy_sync": _meta("Sync for Lemmy"),
    "me.edgan.redditslide": _meta("Slide (fork)"),
    "ml.docilealligator.infinityforreddit.patreon": _meta("Infinity for Reddit (Patreon)"),
    "ml.docilealligator.infinityforreddit.plus": _meta("Infinity for Reddit+"),
    "o.o.joey": _meta("Joey for Reddit"),
    "o.o.joey.dev": _meta("Joey for Reddit Dev"),
    "o.o.joey.pro": _meta("Joey for Reddit Pro"),
    "org.cygnusx1.continuum": _meta("Continuum"),
    "reddit.news": _meta("Relay for Reddit Pro"),
}

LEGACY_APP_META: dict[str, dict[str, Any]] = {
    "app.morphe.android.youtube": _meta("Morphe YouTube"),
    "com.google.android.youtube": _meta("YouTube"),
    "com.rubenmayayo.lemmy": _meta("Boost for Lemmy", "#00AEEF"),
    "com.rubenmayayo.reddit.dev": _meta("Boost for Reddit Dev", "#FF4500"),
}

APP_META: dict[str, dict[str, Any]] = {
    **SUPPORTED_APP_META,
    **LEGACY_APP_META,
}

KNOWN_PACKAGE_LABELS = {
    package_name: str(meta["name"])
    for package_name, meta in APP_META.items()
}


def package_label(package_name: str, explicit: str = "") -> str:
    return explicit or KNOWN_PACKAGE_LABELS.get(
        package_name,
        package_name or "Unknown package",
    )
