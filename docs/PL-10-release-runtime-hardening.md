# PL-10 release/runtime hardening

PL-10 codifies the hardening lessons from Issue #28.

## Runtime device selection

Runtime scripts that call ADB must not silently use ANDROID_SERIAL.

Use an explicit serial selected by the caller:

    env -u ANDROID_SERIAL adb -s "$SERIAL" ...

Scripts that install, launch, clear logcat, pull installed APKs, or otherwise touch device/app state must require an explicit --serial or equivalent.

## Logcat classification

Do not classify a runtime as failed just because AndroidRuntime appears in logcat. Shell tools, monkey, and RuntimeInit can emit benign AndroidRuntime lines.

Use the canonical classifier:

    tools/classify-runtime-logcat.sh \
      --logcat logcat.txt \
      --out-dir runtime-classification \
      --package com.rubenmayayo.reddit.dev

Hard crash classification should be app-specific, for example:

- FATAL EXCEPTION in target package context.
- Process: <package>.
- Application Error: <package>.
- ANR in <package>.
- Force finishing activity <package>.

## Release MPP apply-check

A release MPP apply-check must use the same known-good options file as the candidate build when baseline patches require options, especially Spoof client.

    tools/release-mpp-apply-check.sh \
      --base "$BASE_APK" \
      --mpp "patches/build/libs/patches-$VERSION.mpp" \
      --options-file "$OPTIONS_FILE" \
      --enable "Feature patch name" \
      --require-applied "Feature patch name" \
      --require-applied "Spoof client" \
      --require-marker "FEATURE_RUNTIME_MARKER"

## Final artifact SHA binding

MPP artifacts are not assumed byte-for-byte deterministic across separate local builds. README SHA must describe the exact artifact that will be published.

After the final build and before commit/tag/publish:

    scripts/bind-release-artifact-sha.py --version "$VERSION" --tag "$TAG"
    scripts/release-gate.py --version "$VERSION" --tag "$TAG" ...

Do not rebuild the MPP after SHA binding unless the SHA is rebound.

## Gradle clean and tracked build artifacts

After gradlew clean, restore accidental deletions of historical tracked release artifacts:

    tools/restore-tracked-build-artifacts.sh

Longer-term, tracked build artifacts should be minimized or removed from normal source-control workflows.

## High refresh-rate validation semantics

For adaptive-refresh patches, runtime validation proves that Boost requests high refresh via Android APIs such as preferredRefreshRate=120.0.

It does not prove the physical panel is running at 120 Hz. Android, ROM, power policy, display policy, and per-device heuristics can still choose the final refresh rate.

Release notes should say:

    Runtime validated that Boost requests preferredRefreshRate=120.0.
    Exact physical panel Hz was not independently measured.

## Future feature-release checklist

1. Build MPP with ./gradlew :patches:buildAndroid.
2. Static MPP marker gate.
3. Normal candidate apply gate using tools/build-boost-candidate.sh.
4. DEV-clone runtime gate where app/device state risk exists.
5. Runtime classification with canonical classifier/finalizer.
6. Release MPP apply-check with known-good options file.
7. Final artifact SHA bind.
8. Release gate.
9. Publish.
10. Remote release/asset/signature/feed verification.
