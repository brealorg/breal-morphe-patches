.RECIPEPREFIX := >

VERSION ?=
TAG ?=
EXTRA_GATE_ARGS ?=

.PHONY: help status release-build release-gate verify-remote

help:
> @echo "Targets:"
> @echo "  make status"
> @echo "  make release-build VERSION=1.4.22"
> @echo "  make release-gate VERSION=1.4.22 TAG=morphe-patches-22 EXTRA_GATE_ARGS='...'"
> @echo "  make verify-remote VERSION=1.4.22 TAG=morphe-patches-22"

status:
> git --no-pager status -sb
> git --no-pager log --oneline --decorate -6

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
