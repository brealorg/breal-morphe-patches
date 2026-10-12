#!/usr/bin/env bash

# Boost candidate static gate.
# Does not install APKs, does not touch adb/device state.

usage() {
  cat <<'USAGE'
Usage:
  tools/boost-check-candidate.sh --apk CANDIDATE.apk [options]

Options:
  --apk PATH                  Candidate APK to inspect. Required.
  --base PATH                 Optional base/original Boost APK for comparison.
  --mpp PATH                  Optional MPP bundle to inspect.
  --package NAME              Expected package. Default: com.rubenmayayo.reddit
  --expected-target-sdk SDK   Expected targetSdk. Default: 35
  --require-permission NAME   Required manifest permission. Repeatable.
  --require-marker TEXT       Required decompressed APK marker string. Repeatable.
  --forbid-marker TEXT        Forbidden decompressed APK marker string. Repeatable.
  --help                      Show this help.

Default required permission:
  android.permission.POST_NOTIFICATIONS

Example:
  tools/boost-check-candidate.sh \
    --apk candidate.apk \
    --mpp patches-1.4.26.mpp \
    --base ~/com.rubenmayayo.reddit_1.12.12.apk \
    --require-marker openStaticImageViaBoost \
    --forbid-marker CrashlyticsRegistrar
USAGE
}

APK=""
BASE_APK=""
MPP=""
PACKAGE="com.rubenmayayo.reddit"
EXPECTED_TARGET_SDK="35"
REQUIRED_PERMISSIONS=("android.permission.POST_NOTIFICATIONS")
REQUIRED_MARKERS=()
FORBIDDEN_MARKERS=()

need_value() {
  if [ -z "$2" ]; then
    echo "Missing value for $1"
    usage
    exit 2
  fi
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --apk|--candidate)
      need_value "$1" "${2:-}"
      APK="$2"
      shift 2
      ;;
    --base)
      need_value "$1" "${2:-}"
      BASE_APK="$2"
      shift 2
      ;;
    --mpp)
      need_value "$1" "${2:-}"
      MPP="$2"
      shift 2
      ;;
    --package)
      need_value "$1" "${2:-}"
      PACKAGE="$2"
      shift 2
      ;;
    --expected-target-sdk)
      need_value "$1" "${2:-}"
      EXPECTED_TARGET_SDK="$2"
      shift 2
      ;;
    --require-permission)
      need_value "$1" "${2:-}"
      REQUIRED_PERMISSIONS+=("$2")
      shift 2
      ;;
    --require-marker)
      need_value "$1" "${2:-}"
      REQUIRED_MARKERS+=("$2")
      shift 2
      ;;
    --forbid-marker)
      need_value "$1" "${2:-}"
      FORBIDDEN_MARKERS+=("$2")
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1"
      usage
      exit 2
      ;;
  esac
done

STAMP="$(date +%Y%m%d-%H%M%S)"
LOG="logs/boost-check-candidate-$STAMP.log"
exec > >(tee "$LOG") 2>&1

FAIL=0
PASS_N=0
WARN_N=0
FAIL_N=0

pass() {
  PASS_N=$((PASS_N + 1))
  printf '[PASS] %s\n' "$*"
}

warn() {
  WARN_N=$((WARN_N + 1))
  printf '[WARN] %s\n' "$*"
}

fail() {
  FAIL=1
  FAIL_N=$((FAIL_N + 1))
  printf '[FAIL] %s\n' "$*"
}

have() {
  command -v "$1" >/dev/null 2>&1
}

sha_file() {
  if have sha256sum; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

abs_path() {
  if have realpath; then
    realpath "$1" 2>/dev/null || printf '%s\n' "$1"
  else
    case "$1" in
      /*) printf '%s\n' "$1" ;;
      *) printf '%s/%s\n' "$PWD" "$1" ;;
    esac
  fi
}

dump_badging() {
  apk_path="$1"
  out_file="$2"

  : > "$out_file"

  if have aapt2; then
    if aapt2 dump badging "$apk_path" > "$out_file" 2>"$out_file.err"; then
      echo "aapt2"
      return 0
    fi
  fi

  if have aapt; then
    if aapt dump badging "$apk_path" > "$out_file" 2>"$out_file.err"; then
      echo "aapt"
      return 0
    fi
  fi

  if have apkanalyzer; then
    {
      app_id="$(apkanalyzer manifest application-id "$apk_path" 2>/dev/null || true)"
      target="$(apkanalyzer manifest target-sdk "$apk_path" 2>/dev/null || true)"
      if [ -n "$app_id" ]; then
        echo "package: name='$app_id'"
      fi
      if [ -n "$target" ]; then
        echo "targetSdkVersion:'$target'"
      fi
      apkanalyzer manifest permissions "$apk_path" 2>/dev/null | sed "s/^/uses-permission: name='/; s/$/'/"
    } > "$out_file"
    if [ -s "$out_file" ]; then
      echo "apkanalyzer"
      return 0
    fi
  fi

  return 1
}

extract_package() {
  sed -n "s/^package: name='\([^']*\)'.*/\1/p" "$1" | head -n 1
}

extract_target_sdk() {
  sed -n "s/^targetSdkVersion:'\([^']*\)'.*/\1/p" "$1" | head -n 1
}

check_permission() {
  perm="$1"
  file="$2"
  if grep -F "uses-permission: name='$perm'" "$file" >/dev/null 2>&1 || grep -F "$perm" "$file" >/dev/null 2>&1; then
    pass "permission declared: $perm"
  else
    fail "missing permission: $perm"
  fi
}

echo "BOOST CANDIDATE STATIC GATE"
echo "Started: $(date -Is)"
echo "Repo: $PWD"
echo

if [ -z "$APK" ]; then
  fail "candidate APK not provided; use --apk PATH"
fi

if [ -n "$APK" ]; then
  APK="$(abs_path "$APK")"
fi
if [ -n "$BASE_APK" ]; then
  BASE_APK="$(abs_path "$BASE_APK")"
fi
if [ -n "$MPP" ]; then
  MPP="$(abs_path "$MPP")"
fi

TMP="$(mktemp -d /tmp/boost-check-candidate.XXXXXX 2>/dev/null || mktemp -d)"
cleanup() {
  rm -rf "$TMP"
}
trap cleanup EXIT

echo "Inputs:"
echo "  APK: $APK"
echo "  BASE_APK: ${BASE_APK:-<not provided>}"
echo "  MPP: ${MPP:-<not provided>}"
echo "  expected package: $PACKAGE"
echo "  expected targetSdk: $EXPECTED_TARGET_SDK"
echo

if [ -d .git ]; then
  pass "running inside git repo"
  echo
  echo "Git status:"
  git --no-pager status -sb 2>/dev/null || warn "could not read git status"
  echo
else
  warn "not running inside a git repo"
fi

if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  fail "candidate APK missing: ${APK:-<empty>}"
else
  pass "candidate APK exists"
  echo "candidate APK sha256: $(sha_file "$APK")"

  if have unzip; then
    if unzip -tq "$APK" >/dev/null 2>&1; then
      pass "candidate APK zip structure is valid"
    else
      fail "candidate APK zip structure failed unzip test"
    fi
  else
    fail "unzip is not available"
  fi
fi

if [ -n "$MPP" ]; then
  echo
  echo "MPP checks:"
  if [ ! -f "$MPP" ]; then
    fail "MPP missing: $MPP"
  else
    pass "MPP exists"
    echo "MPP sha256: $(sha_file "$MPP")"

    if have unzip; then
      if unzip -tq "$MPP" >/dev/null 2>&1; then
        pass "MPP zip structure is valid"
      else
        fail "MPP zip structure failed unzip test"
      fi

      unzip -l "$MPP" > "$TMP/mpp-list.txt" 2>/dev/null || true

      if awk '{print $4}' "$TMP/mpp-list.txt" | grep -Fx "classes.dex" >/dev/null 2>&1; then
        pass "MPP contains classes.dex"
      else
        fail "MPP missing classes.dex"
      fi

      if awk '{print $4}' "$TMP/mpp-list.txt" | grep -Fx "extensions/boostforreddit.mpe" >/dev/null 2>&1; then
        pass "MPP contains extensions/boostforreddit.mpe"
      else
        fail "MPP missing extensions/boostforreddit.mpe"
      fi
    else
      fail "cannot inspect MPP because unzip is unavailable"
    fi
  fi
fi

BADGING="$TMP/candidate-badging.txt"
if [ -f "$APK" ]; then
  echo
  echo "APK manifest checks:"
  BADGING_TOOL="$(dump_badging "$APK" "$BADGING" || true)"
  if [ -n "$BADGING_TOOL" ] && [ -s "$BADGING" ]; then
    pass "manifest readable with $BADGING_TOOL"

    ACTUAL_PACKAGE="$(extract_package "$BADGING")"
    ACTUAL_TARGET="$(extract_target_sdk "$BADGING")"

    if [ "$ACTUAL_PACKAGE" = "$PACKAGE" ]; then
      pass "package matches: $ACTUAL_PACKAGE"
    else
      fail "package mismatch: expected $PACKAGE, got ${ACTUAL_PACKAGE:-<unknown>}"
    fi

    if [ -n "$EXPECTED_TARGET_SDK" ]; then
      if [ "$ACTUAL_TARGET" = "$EXPECTED_TARGET_SDK" ]; then
        pass "targetSdk matches: $ACTUAL_TARGET"
      else
        fail "targetSdk mismatch: expected $EXPECTED_TARGET_SDK, got ${ACTUAL_TARGET:-<unknown>}"
      fi
    fi

    for perm in "${REQUIRED_PERMISSIONS[@]}"; do
      check_permission "$perm" "$BADGING"
    done
  else
    warn "could not read manifest via aapt2/aapt/apkanalyzer"
    warn "install Android build-tools or ensure aapt2/apksigner are in PATH"
  fi
fi

if [ -n "$BASE_APK" ]; then
  echo
  echo "Base APK comparison:"
  if [ ! -f "$BASE_APK" ]; then
    fail "base APK missing: $BASE_APK"
  else
    pass "base APK exists"
    echo "base APK sha256: $(sha_file "$BASE_APK")"

    BASE_BADGING="$TMP/base-badging.txt"
    BASE_TOOL="$(dump_badging "$BASE_APK" "$BASE_BADGING" || true)"
    if [ -n "$BASE_TOOL" ] && [ -s "$BASE_BADGING" ]; then
      BASE_PACKAGE="$(extract_package "$BASE_BADGING")"
      CAND_PACKAGE="$(extract_package "$BADGING")"

      if [ -n "$BASE_PACKAGE" ] && [ "$BASE_PACKAGE" = "$CAND_PACKAGE" ]; then
        pass "base and candidate package match: $BASE_PACKAGE"
      else
        fail "base/candidate package mismatch: base=${BASE_PACKAGE:-<unknown>} candidate=${CAND_PACKAGE:-<unknown>}"
      fi
    else
      warn "could not read base APK manifest"
    fi

    # Extension code is compiled against stock libraries; Boost ships stripped and obfuscated copies.
    LINKAGE_LOG="$TMP/linkage.txt"
    if python3 -I "$(dirname "$0")/boost_extension_linkage.py" \
        --base-apk "$BASE_APK" --candidate-apk "$APK" > "$LINKAGE_LOG" 2>&1; then
      pass "all member and class references resolve in the candidate APK"
    else
      fail "candidate references members or classes Boost does not ship"
      grep -E '^(MISSING|LINKAGE_GATE=FAIL)' "$LINKAGE_LOG" | sed -n '1,40p'
    fi
  fi
fi

if [ -f "$APK" ]; then
  echo
  echo "Signature checks:"
  if have apksigner; then
    if apksigner verify --print-certs "$APK" > "$TMP/apksigner.txt" 2>&1; then
      pass "candidate APK signature verifies"

      grep -E "Signer #1 certificate (DN|SHA-256 digest):" "$TMP/apksigner.txt" | sed -n '1,4p' || true

      SIG_WARNINGS="$(grep -c '^WARNING:' "$TMP/apksigner.txt" 2>/dev/null || true)"
      if [ "${SIG_WARNINGS:-0}" -gt 0 ]; then
        echo "[INFO] apksigner reported $SIG_WARNINGS non-fatal metadata warnings; full details are in the log"
      fi
    else
      fail "candidate APK signature verification failed"
      sed -n '1,80p' "$TMP/apksigner.txt"
    fi
  else
    warn "apksigner is not available; signature not verified"
  fi
fi

if [ -f "$APK" ] && have unzip; then
  echo
  echo "APK marker checks:"
  APK_DIR="$TMP/apk"
  mkdir -p "$APK_DIR"
  if unzip -qq "$APK" -d "$APK_DIR" >/dev/null 2>&1; then
    pass "candidate APK extracted for marker scan"

    for marker in "${REQUIRED_MARKERS[@]}"; do
      if grep -RasF -- "$marker" "$APK_DIR" >/dev/null 2>&1; then
        pass "required marker present: $marker"
      else
        fail "required marker missing: $marker"
      fi
    done

    for marker in "${FORBIDDEN_MARKERS[@]}"; do
      if grep -RasF -- "$marker" "$APK_DIR" >/dev/null 2>&1; then
        fail "forbidden marker present: $marker"
      else
        pass "forbidden marker absent: $marker"
      fi
    done

    if [ "${#REQUIRED_MARKERS[@]}" -eq 0 ] && [ "${#FORBIDDEN_MARKERS[@]}" -eq 0 ]; then
      warn "no marker checks requested"
    fi
  else
    fail "could not extract candidate APK for marker scan"
  fi
fi

echo
echo "SUMMARY"
echo "  PASS: $PASS_N"
echo "  WARN: $WARN_N"
echo "  FAIL: $FAIL_N"
echo "  LOG:  $LOG"

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT: PASS"
else
  echo "RESULT: FAIL"
fi

exit "$FAIL"
