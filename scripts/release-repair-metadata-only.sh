#!/usr/bin/env bash
set -euo pipefail

echo "FAIL: direct metadata repair publication is retired." >&2
echo "Prepare the metadata correction on a focused work/* branch and merge it through a pull request to main." >&2
echo "The immutable release tag and published assets must remain unchanged." >&2
echo "RESULT=MORPHE_RELEASE_REPAIR_METADATA_ONLY_RETIRED"
exit 2
