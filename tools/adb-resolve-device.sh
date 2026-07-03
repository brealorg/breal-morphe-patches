#!/usr/bin/env bash

# Resolve one adb device deterministically.
# Read-only helper: does not install, uninstall, clear data, force-stop, or
# otherwise mutate device/app state.

FORMAT="shell"
SERIAL_SELECTOR=""
EXPLICIT_SERIAL=0
PREFER=""
ALLOW_OFFLINE=0
DEVICES_FILE=""

usage() {
  cat <<'USAGE'
Usage:
  tools/adb-resolve-device.sh [options]

Options:
  --serial SERIAL       Select exact adb serial. Overrides ANDROID_SERIAL.
  --prefer-usb          Select the single USB device if exactly one is usable.
  --prefer-tcp          Select the single TCP/IP device if exactly one is usable.
  --allow-offline       Allow non-"device" states for diagnostics.
  --format FORMAT       shell, plain, or json. Default: shell.
  --devices-file PATH   Test helper: read adb devices -l output from file.
  -h, --help            Show this help.

Default shell output is source/eval safe:

  eval "$(tools/adb-resolve-device.sh --format shell)"
  adb -s "$ADB_SERIAL" shell getprop ro.product.model

Exit codes:
  0  exactly one matching device resolved
  1  invalid options or no unique usable device
  2  adb missing or adb devices failed
USAGE
}

die() {
  echo "FAIL: $*" >&2
  exit 1
}

die_tool() {
  echo "FAIL: $*" >&2
  exit 2
}

shell_quote() {
  # Single-quote a string for POSIX shell eval.
  local s="${1-}"
  printf "'%s'" "$(printf "%s" "$s" | sed "s/'/'\\\\''/g")"
}

json_escape() {
  python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' "${1-}"
}

transport_for_serial() {
  local serial="$1"
  case "$serial" in
    *._adb-tls-connect._tcp|*:* )
      echo "tcp"
      ;;
    *)
      echo "usb"
      ;;
  esac
}

kv_from_rest() {
  local key="$1"
  shift
  local token value
  for token in "$@"; do
    case "$token" in
      "$key":*)
        value="${token#"$key":}"
        echo "$value"
        return 0
        ;;
    esac
  done
  echo ""
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --serial)
      [ "$#" -ge 2 ] || die "--serial requires a value"
      SERIAL_SELECTOR="$2"
      EXPLICIT_SERIAL=1
      shift 2
      ;;
    --prefer-usb)
      [ -z "$PREFER" ] || die "only one prefer option can be used"
      PREFER="usb"
      shift
      ;;
    --prefer-tcp)
      [ -z "$PREFER" ] || die "only one prefer option can be used"
      PREFER="tcp"
      shift
      ;;
    --allow-offline)
      ALLOW_OFFLINE=1
      shift
      ;;
    --format)
      [ "$#" -ge 2 ] || die "--format requires a value"
      FORMAT="$2"
      case "$FORMAT" in
        shell|plain|json) ;;
        *) die "unsupported format: $FORMAT" ;;
      esac
      shift 2
      ;;
    --devices-file)
      [ "$#" -ge 2 ] || die "--devices-file requires a path"
      DEVICES_FILE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown argument: $1"
      ;;
  esac
done

# Respect ANDROID_SERIAL for live adb resolution, but keep --devices-file
# deterministic for tests unless --serial was explicitly provided.
if [ "$EXPLICIT_SERIAL" -eq 0 ] && [ -z "$DEVICES_FILE" ]; then
  SERIAL_SELECTOR="${ANDROID_SERIAL:-}"
fi

if [ -n "$DEVICES_FILE" ]; then
  [ -f "$DEVICES_FILE" ] || die "devices file missing: $DEVICES_FILE"
  DEVICES_TEXT="$(cat "$DEVICES_FILE")" || die "could not read devices file: $DEVICES_FILE"
else
  command -v adb >/dev/null 2>&1 || die_tool "adb not found in PATH"
  DEVICES_TEXT="$(adb devices -l 2>&1)" || die_tool "adb devices -l failed"
fi

ROWS="$(
  printf "%s\n" "$DEVICES_TEXT" |
    awk '
      NR == 1 && /^List of devices attached/ { next }
      NF < 2 { next }
      /^adb server/ { next }
      {
        serial=$1
        state=$2
        rest=""
        for (i=3; i<=NF; i++) {
          rest = rest " " $i
        }
        print serial "\t" state "\t" rest
      }
    '
)"

ALL_COUNT="$(printf "%s\n" "$ROWS" | sed '/^$/d' | wc -l | tr -d ' ')"
if [ "$ALL_COUNT" -eq 0 ]; then
  die "no adb devices listed"
fi

USABLE_ROWS=""
while IFS="$(printf '\t')" read -r serial state rest; do
  [ -n "$serial" ] || continue
  if [ "$ALLOW_OFFLINE" -eq 1 ] || [ "$state" = "device" ]; then
    USABLE_ROWS="${USABLE_ROWS}${serial}	${state}	${rest}"$'\n'
  fi
done <<EOF
$ROWS
EOF

USABLE_COUNT="$(printf "%s\n" "$USABLE_ROWS" | sed '/^$/d' | wc -l | tr -d ' ')"
if [ "$USABLE_COUNT" -eq 0 ]; then
  die "no usable adb devices; use --allow-offline for diagnostics"
fi

MATCH_ROWS=""

if [ -n "$SERIAL_SELECTOR" ]; then
  while IFS="$(printf '\t')" read -r serial state rest; do
    [ -n "$serial" ] || continue
    if [ "$serial" = "$SERIAL_SELECTOR" ]; then
      MATCH_ROWS="${MATCH_ROWS}${serial}	${state}	${rest}"$'\n'
    fi
  done <<EOF
$USABLE_ROWS
EOF
else
  while IFS="$(printf '\t')" read -r serial state rest; do
    [ -n "$serial" ] || continue
    transport="$(transport_for_serial "$serial")"
    if [ -z "$PREFER" ] || [ "$transport" = "$PREFER" ]; then
      MATCH_ROWS="${MATCH_ROWS}${serial}	${state}	${rest}"$'\n'
    fi
  done <<EOF
$USABLE_ROWS
EOF
fi

MATCH_COUNT="$(printf "%s\n" "$MATCH_ROWS" | sed '/^$/d' | wc -l | tr -d ' ')"

if [ "$MATCH_COUNT" -eq 0 ]; then
  if [ -n "$SERIAL_SELECTOR" ]; then
    die "selector matched no usable device: $SERIAL_SELECTOR"
  fi
  die "no usable adb device matched selector"
fi

if [ "$MATCH_COUNT" -gt 1 ]; then
  echo "FAIL: more than one adb device matched; pass --serial, --prefer-usb, or --prefer-tcp" >&2
  printf "%s\n" "$MATCH_ROWS" | sed '/^$/d' >&2
  exit 1
fi

SELECTED_LINE="$(printf "%s\n" "$MATCH_ROWS" | sed '/^$/d' | head -n 1)"
IFS="$(printf '\t')" read -r ADB_SERIAL ADB_STATE ADB_REST <<EOF
$SELECTED_LINE
EOF

ADB_TRANSPORT="$(transport_for_serial "$ADB_SERIAL")"

# shellcheck disable=SC2086
set -- $ADB_REST
ADB_PRODUCT="$(kv_from_rest product "$@")"
ADB_MODEL="$(kv_from_rest model "$@")"
ADB_DEVICE="$(kv_from_rest device "$@")"

case "$FORMAT" in
  shell)
    echo "ADB_SERIAL=$(shell_quote "$ADB_SERIAL")"
    echo "ADB_STATE=$(shell_quote "$ADB_STATE")"
    echo "ADB_TRANSPORT=$(shell_quote "$ADB_TRANSPORT")"
    echo "ADB_MODEL=$(shell_quote "$ADB_MODEL")"
    echo "ADB_PRODUCT=$(shell_quote "$ADB_PRODUCT")"
    echo "ADB_DEVICE=$(shell_quote "$ADB_DEVICE")"
    ;;
  plain)
    printf "ADB_SERIAL=%s\n" "$ADB_SERIAL"
    printf "ADB_STATE=%s\n" "$ADB_STATE"
    printf "ADB_TRANSPORT=%s\n" "$ADB_TRANSPORT"
    printf "ADB_MODEL=%s\n" "$ADB_MODEL"
    printf "ADB_PRODUCT=%s\n" "$ADB_PRODUCT"
    printf "ADB_DEVICE=%s\n" "$ADB_DEVICE"
    ;;
  json)
    printf '{'
    printf '"serial":%s,' "$(json_escape "$ADB_SERIAL")"
    printf '"state":%s,' "$(json_escape "$ADB_STATE")"
    printf '"transport":%s,' "$(json_escape "$ADB_TRANSPORT")"
    printf '"model":%s,' "$(json_escape "$ADB_MODEL")"
    printf '"product":%s,' "$(json_escape "$ADB_PRODUCT")"
    printf '"device":%s' "$(json_escape "$ADB_DEVICE")"
    printf '}\n'
    ;;
esac
