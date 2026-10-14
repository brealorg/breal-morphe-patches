#!/usr/bin/env bash
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)" || exit 1
python3 - "$ROOT" <<'PY'
import pathlib
import re
import sys

# Issue #191: static Activity -> View registries kept finished activities alive.
# They were reduced to weak values, then removed once the nav stopped caching
# views per activity. Keep them from coming back as strong references.
root = pathlib.Path(sys.argv[1])
source = (
    root
    / 'extensions/boostforreddit/src/main/java/app/morphe/extension/'
      'boostforreddit/utils/BoostSearchBottomNavigation.java'
).read_text()
for name in ('DECOR_NAVIGATION_CONTAINERS', 'DECOR_NAVIGATION_VIEWS'):
    if re.search(rf'\b{name}\b', source):
        raise SystemExit(f'FAIL: {name} registry is back')
if re.search(r'static\s+final\s+(?:java\.util\.)?(?:Hash)?Map<Activity,\s*(?:View|FrameLayout)>', source):
    raise SystemExit('FAIL: strong static Activity -> View map')
print('NAVIGATION_REGISTRY_RETENTION=PASS')
PY
