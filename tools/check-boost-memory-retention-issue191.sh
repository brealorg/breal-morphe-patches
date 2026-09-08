#!/usr/bin/env bash
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)" || exit 1
python3 - "$ROOT" <<'PY'
import pathlib
import re
import subprocess
import sys
import tempfile

root = pathlib.Path(sys.argv[1])
base = root / 'extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/utils'
source = (base / 'BoostSearchBottomNavigation.java').read_text()
for name, value in [('DECOR_NAVIGATION_CONTAINERS', 'FrameLayout'), ('DECOR_NAVIGATION_VIEWS', 'View')]:
    pattern = rf'private\s+static\s+final\s+WeakValueRegistry<Activity,\s*{value}>\s+{name}\s*=\s*new\s+WeakValueRegistry<>\(\);'
    if len(re.findall(pattern, source)) != 1:
        raise SystemExit(f'FAIL: {name} is not wired to the non-owning registry')
print('NAVIGATION_REGISTRY_WIRING=PASS', flush=True)
with tempfile.TemporaryDirectory(prefix='boost-issue191-jvm-') as output:
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', output,
                    str(base / 'WeakValueRegistry.java'),
                    str(root / 'tools/tests/BoostWeakValueRegistryTest.java')], check=True)
    subprocess.run(['java', '-Xmx64m', '-XX:+UseSerialGC', '-cp', output,
                    'app.morphe.extension.boostforreddit.utils.BoostWeakValueRegistryTest'],
                   check=True, timeout=25)
PY
