#!/usr/bin/env python3
"""Phase 2 Navigation contract for Morphe Issue #121."""
from __future__ import annotations
import hashlib, json, re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SETTINGS = ROOT / "extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings"
NATIVE_PATH = SETTINGS / "MorpheSettingsV4NativePages.java"
CATALOG_PATH = SETTINGS / "MorpheSettingsV4Catalog.java"
HUB_PATH = SETTINGS / "MorpheSettingsV4NavigationDrawerHubFragment.java"
CONTRACT_PATH = ROOT / "tools/contracts/boost-settings-navigation-phase2-v1.json"
EXPECTED_CONTRACT_SHA256 = "0a1be51e15f3e914d78ebe04fd832895f5d0fe8f326b78048c8474fce282e624"

def class_block(text: str, declaration: str) -> str:
    start=text.find(declaration)
    if start<0: raise AssertionError(f"missing declaration: {declaration}")
    brace=text.find("{",start); depth=0
    for index in range(brace,len(text)):
        if text[index]=="{": depth+=1
        elif text[index]=="}":
            depth-=1
            if depth==0: return text[start:index+1]
    raise AssertionError(f"unclosed declaration: {declaration}")

def switch_case_map(method: str) -> dict[str,str]:
    result={}
    pattern=re.compile(r'((?:\s*case "(pref_[a-z0-9_]+)":)+)\s*return ([A-Z0-9_]+);',re.S)
    for m in pattern.finditer(method):
        for key in re.findall(r'case "(pref_[a-z0-9_]+)":',m.group(1)):
            result[key]=m.group(3)
    return result

raw=CONTRACT_PATH.read_bytes()
actual_sha=hashlib.sha256(raw).hexdigest()
assert actual_sha==EXPECTED_CONTRACT_SHA256,actual_sha
contract=json.loads(raw.decode())
native=NATIVE_PATH.read_text()
catalog=CATALOG_PATH.read_text()
hub=HUB_PATH.read_text()

assert contract["task_page"]["children"]==['Toolbar', 'Bottom navigation', 'Navigation drawer', 'Back & exit']
assert contract["drawer_hub"]["children"]==['Feeds & library', 'Account & tools', 'Go-to shortcuts', 'Quick toggles', 'Subscriptions', 'Account switcher', 'Drawer behavior']
assert contract["exposed_key_count"]==30
assert contract["withheld_keys"]==["pref_drawer_show_friends"]
assert contract["max_controls_per_leaf"]==6
assert len(contract["leaf_pages"])==10

expected_keys=set(); expected_fragments={}
for page in contract["leaf_pages"]:
    keys=page["keys"]; assert 1<=len(keys)<=6
    expected_keys.update(keys)
    for key in keys: expected_fragments[key]=page["fragment_constant"]
    declaration="public static final class "+page["fragment_class"]+"\n            extends NavigationSubsetPage"
    block=class_block(native,declaration)
    literals=set(re.findall(r'"(pref_[a-z0-9_]+)"',block))
    literals.discard(page["resource"])
    assert literals==set(keys),(page["id"],sorted(literals),sorted(keys))
    assert f'"{page["title"]}"' in block
    assert f'"{page["resource"]}"' in block

assert len(expected_keys)==30
assert "pref_drawer_show_friends" not in expected_keys
assert "MORPHE_BOOST_SETTINGS_NAVIGATION_PHASE2_LEAF_SPLIT_ISSUE121_V1" in native
assert "private abstract static class NavigationSubsetPage" in native
assert "protected String pageIntro()" in native

nstart=catalog.index('new Category(\n                    "navigation"')
nend=catalog.index("new Category(",nstart+1)
ncat=catalog[nstart:nend]
for title in contract["task_page"]["children"]: assert f'"{title}"' in ncat
for constant in ("V4_NAVIGATION_TOOLBAR_FRAGMENT","V4_NAVIGATION_BOTTOM_FRAGMENT",
                 "V4_NAVIGATION_DRAWER_HUB_FRAGMENT","V4_NAVIGATION_BACK_EXIT_FRAGMENT"):
    assert constant in ncat
assert "V4_NAVIGATION_FRAGMENT" not in catalog
assert '"Navigation controls"' not in ncat
assert '"navigation".equals(category.id)' not in catalog

frag_method=class_block(catalog,"private static String navigationFragmentForKey(String key)")
assert switch_case_map(frag_method)==expected_fragments
exposed=class_block(catalog,"private static boolean isNavigationExposedKey(String key)")
assert set(re.findall(r'case "(pref_[a-z0-9_]+)":',exposed))==expected_keys
assert "pref_drawer_show_friends" not in exposed

general=class_block(native,"public static final class General extends NativePage")
assert '"pref_ask_exit".equals(key)' in general and '"pref_double_exit".equals(key)' in general

assert "MORPHE_BOOST_SETTINGS_NAVIGATION_DRAWER_HUB_ISSUE121_V1" in hub
astart=hub.index("private static final Destination[] DESTINATIONS")
aend=hub.index("\n    };",astart)+len("\n    };")
arr=hub[astart:aend]
assert arr.count("new Destination(")==7
for page in contract["leaf_pages"]:
    if page["parent"]=="Navigation drawer":
        assert f'"{page["title"]}"' in arr
        assert "MorpheSettingsV4Catalog."+page["fragment_constant"] in arr
assert "MorpheSettingsV4Search.prepareMenu(this, menu, true);" in hub
assert "MorpheSettingsV4Search.handleMenuItem(this, item)" in hub
assert "public static final class BottomNavigation extends NativePage" in native
assert "public static final class Drawer extends NativePage" in native

print(f"CONTRACT={CONTRACT_PATH}")
print(f"CONTRACT_SHA256={actual_sha}")
print("NAVIGATION_TASK_CHILD_COUNT=4")
print("NAVIGATION_DRAWER_CHILD_COUNT=7")
print("NAVIGATION_LEAF_PAGE_COUNT=10")
print("NAVIGATION_EXPOSED_KEY_COUNT=30")
print("NAVIGATION_MAX_CONTROLS_PER_LEAF=6")
print("WITHHELD_FRIENDS_KEY=PASS")
print("SEARCH_ROUTE_CONTRACT=PASS")
print("GLOBAL_SEARCH_COVERAGE=PASS")
print("CLASSIC_FALLBACK_PRESERVED=PASS")
print("RESULT=MORPHE_ISSUE121_NAVIGATION_PHASE2_CONTRACT_PASS")
