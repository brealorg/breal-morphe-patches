.RECIPEPREFIX := >

VERSION ?=
TAG ?=
EXTRA_GATE_ARGS ?=

.PHONY: help status prepare-release release-build release-gate verify-remote release-publish

help:
> @echo "Targets:"
> @echo "  make status"
> @echo "  make release-build VERSION=1.4.93"
> @echo "  make prepare-release VERSION=1.4.93 TAG=morphe-patches-93 CHANGELOG_FILE=/tmp/changelog.txt"
> @echo "  make release-gate VERSION=1.4.93 TAG=morphe-patches-93 EXTRA_GATE_ARGS='...'"
> @echo "  make verify-remote VERSION=1.4.93 TAG=morphe-patches-93"
> @echo "  make release-publish VERSION=1.4.93 TAG=morphe-patches-93 EXTRA_PUBLISH_ARGS='--dry-run'"

status:
> git --no-pager status -sb
> git --no-pager log --oneline --decorate -6


prepare-release:
> @test -n "$(VERSION)" || (echo "Usage: make prepare-release VERSION=1.4.26 TAG=morphe-patches-26 CHANGELOG=... or CHANGELOG_FILE=/tmp/changelog.txt"; exit 1)
> @test -n "$(TAG)" || (echo "Usage: make prepare-release VERSION=1.4.26 TAG=morphe-patches-26 CHANGELOG=... or CHANGELOG_FILE=/tmp/changelog.txt"; exit 1)
> @test -n "$(CHANGELOG)$(CHANGELOG_FILE)" || (echo "Usage: make prepare-release VERSION=1.4.26 TAG=morphe-patches-26 CHANGELOG=... or CHANGELOG_FILE=/tmp/changelog.txt"; exit 1)
> @if [ -n "$(CHANGELOG_FILE)" ]; then \
>   ./scripts/prepare-release.py --version "$(VERSION)" --tag "$(TAG)" --changelog "$$(cat "$(CHANGELOG_FILE)")" $(EXTRA_PREPARE_ARGS); \
> else \
>   ./scripts/prepare-release.py --version "$(VERSION)" --tag "$(TAG)" --changelog "$(CHANGELOG)" $(EXTRA_PREPARE_ARGS); \
> fi

release-build:
> @test -n "$(VERSION)" || (echo "Usage: make release-build VERSION=1.4.22"; exit 1)
> ./gradlew clean :patches:buildAndroid --no-daemon
> @echo
> @echo "===== release artifact ====="
> ls -lh "patches/build/libs/patches-$(VERSION).mpp"
> sha256sum "patches/build/libs/patches-$(VERSION).mpp"
> @echo
> @echo "===== required entries ====="
> unzip -l "patches/build/libs/patches-$(VERSION).mpp" | grep -Ei 'classes.*\.dex|META-INF/MANIFEST.MF|extensions/boostforreddit\.mpe'

release-gate:
> @test -n "$(VERSION)" || (echo "Usage: make release-gate VERSION=1.4.22 TAG=morphe-patches-22"; exit 1)
> @test -n "$(TAG)" || (echo "Usage: make release-gate VERSION=1.4.22 TAG=morphe-patches-22"; exit 1)
> ./scripts/release-gate.py --version "$(VERSION)" --tag "$(TAG)" $(EXTRA_GATE_ARGS)

verify-remote:
> @test -n "$(VERSION)" || (echo "Usage: make verify-remote VERSION=1.4.22 TAG=morphe-patches-22"; exit 1)
> @test -n "$(TAG)" || (echo "Usage: make verify-remote VERSION=1.4.22 TAG=morphe-patches-22"; exit 1)
> ./scripts/verify-remote-release.sh "$(VERSION)" "$(TAG)"

release-publish:
> @test -n "$(VERSION)" || (echo "Usage: make release-publish VERSION=1.4.93 TAG=morphe-patches-93 EXTRA_PUBLISH_ARGS='--dry-run'"; exit 1)
> @test -n "$(TAG)" || (echo "Usage: make release-publish VERSION=1.4.93 TAG=morphe-patches-93 EXTRA_PUBLISH_ARGS='--dry-run'"; exit 1)
> ./scripts/publish-release.py --version "$(VERSION)" --tag "$(TAG)" $(EXTRA_PUBLISH_ARGS)

update-readme-sha:
> @test -n "$(VERSION)" || (echo "Usage: make update-readme-sha VERSION=1.4.22"; exit 1)
> ./scripts/update-readme-sha.py --version "$(VERSION)" $(EXTRA_SHA_ARGS)

release-local-final:
> @test -n "$(VERSION)" || (echo "Usage: make release-local-final VERSION=1.4.22 TAG=morphe-patches-22"; exit 1)
> @test -n "$(TAG)" || (echo "Usage: make release-local-final VERSION=1.4.22 TAG=morphe-patches-22"; exit 1)
> $(MAKE) release-build VERSION="$(VERSION)"
> $(MAKE) update-readme-sha VERSION="$(VERSION)" EXTRA_SHA_ARGS="$(EXTRA_SHA_ARGS)"
> $(MAKE) release-gate VERSION="$(VERSION)" TAG="$(TAG)" EXTRA_GATE_ARGS="$(EXTRA_GATE_ARGS)"

release-hold-gate:
> @test -n "$(VERSION)" || (echo "Usage: make release-hold-gate VERSION=1.4.22 TAG=morphe-patches-22"; exit 1)
> @test -n "$(TAG)" || (echo "Usage: make release-hold-gate VERSION=1.4.22 TAG=morphe-patches-22"; exit 1)
> ./scripts/release-hold-gate.py --version "$(VERSION)" --tag "$(TAG)" $(EXTRA_HOLD_ARGS)

boost-runtime-media-marker-gate:
> @test -n "$(VERSION)" || (echo "Usage: make boost-runtime-media-marker-gate VERSION=1.4.22"; exit 1)
> ./scripts/check-boost-runtime-media-markers.py --version "$(VERSION)" $(EXTRA_MEDIA_MARKER_ARGS)

boost-reddit-gallery-undelete-marker-gate:
>./scripts/check-boost-reddit-gallery-undelete-markers.py --version "$(VERSION)" $(EXTRA_GALLERY_UNDELETE_MARKER_ARGS)
