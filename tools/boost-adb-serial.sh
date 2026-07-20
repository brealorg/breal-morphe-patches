#!/usr/bin/env bash
FORMAT="plain"
HINT="${MORPHE_ADB_HINT:-}"
EXPECTED_MODEL="${MORPHE_ADB_EXPECT_MODEL:-}"
EXPECTED_DEVICE="${MORPHE_ADB_EXPECT_DEVICE:-}"

usage() {
  cat <<'USAGE'
Usage:
  tools/boost-adb-serial.sh [options]

Options:
  --format plain|shell   Output serial only or shell assignment. Default: plain.
  --hint TEXT            Stable endpoint hint, normally the reserved device IP.
  --expect-model MODEL   Require exact adb model metadata, e.g. Pixel_6.
  --expect-device DEVICE Require exact adb device metadata, e.g. oriole.
  -h, --help             Show this help.

Policy:
  - ANDROID_SERIAL is ignored because wireless-debugging ports rotate.
  - MORPHE_ADB_SERIAL is only a one-run override if already connected.
  - MORPHE_ADB_HINT is the preferred stable selector, e.g. 192.168.1.248.
  - A supplied hint is strict; a non-matching device is never selected.
  - MORPHE_ADB_EXPECT_MODEL and MORPHE_ADB_EXPECT_DEVICE fail closed.
  - mDNS _adb-tls-connect._tcp is used to discover/reconnect the current port.
  - TCP serials are preferred over adb-mdns aliases for runtime commands.
USAGE
}

die() {
  echo "FAIL: $*" >&2
  exit 1
}

emit() {
  if [ "$FORMAT" = "shell" ]; then
    printf 'MORPHE_ADB_SERIAL=%q\n' "$1"
  else
    printf '%s\n' "$1"
  fi
}

adb_lines() {
  adb devices -l | awk '$2=="device"{print}'
}

tcp_from_lines() {
  awk '$1 ~ /^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+/ {print $1}'
}

identity_from_lines() {
  awk -v expected_model="$EXPECTED_MODEL" -v expected_device="$EXPECTED_DEVICE" '
    {
      model_ok = (expected_model == "")
      device_ok = (expected_device == "")
      for (i = 3; i <= NF; i++) {
        if ($i == "model:" expected_model) model_ok = 1
        if ($i == "device:" expected_device) device_ok = 1
      }
      if (model_ok && device_ok) print
    }
  '
}

pick_active() {
  ALL_LINES="$(adb_lines)"

  [ -n "$ALL_LINES" ] || return 1

  LINES="$(printf '%s\n' "$ALL_LINES" | identity_from_lines)"

  [ -n "$LINES" ] || return 1

  if [ -n "${MORPHE_ADB_SERIAL:-}" ]; then
    if printf '%s\n' "$LINES" | awk '{print $1}' | grep -Fx "$MORPHE_ADB_SERIAL" >/dev/null; then
      emit "$MORPHE_ADB_SERIAL"
      return 0
    fi
    echo "WARN: MORPHE_ADB_SERIAL not connected, ignoring: $MORPHE_ADB_SERIAL" >&2
  fi

  if [ -n "$HINT" ]; then
    MATCHES="$(printf '%s\n' "$LINES" | grep -F "$HINT" || true)"
    TCP_MATCHES="$(printf '%s\n' "$MATCHES" | tcp_from_lines || true)"
    TCP_COUNT="$(printf '%s\n' "$TCP_MATCHES" | sed '/^$/d' | wc -l | tr -d ' ')"
    MATCH_COUNT="$(printf '%s\n' "$MATCHES" | sed '/^$/d' | wc -l | tr -d ' ')"

    if [ "$TCP_COUNT" -eq 1 ]; then
      emit "$(printf '%s\n' "$TCP_MATCHES" | sed -n '1p')"
      return 0
    fi
    if [ "$MATCH_COUNT" -eq 1 ]; then
      emit "$(printf '%s\n' "$MATCHES" | awk '{print $1}')"
      return 0
    fi

    # A stable hint is a strict identity boundary. If it does not match the
    # connected device rows, reconnect through mDNS instead of selecting an
    # unrelated single TCP target such as Android TV.
    return 1
  fi

  TCP_ALL="$(printf '%s\n' "$LINES" | tcp_from_lines || true)"
  TCP_COUNT="$(printf '%s\n' "$TCP_ALL" | sed '/^$/d' | wc -l | tr -d ' ')"
  TOTAL_COUNT="$(printf '%s\n' "$LINES" | sed '/^$/d' | wc -l | tr -d ' ')"

  if [ "$TCP_COUNT" -eq 1 ]; then
    emit "$(printf '%s\n' "$TCP_ALL" | sed -n '1p')"
    return 0
  fi

  if [ "$TOTAL_COUNT" -eq 1 ]; then
    emit "$(printf '%s\n' "$LINES" | awk '{print $1}')"
    return 0
  fi

  return 1
}

mdns_endpoints() {
  adb mdns services 2>/dev/null \
    | grep '_adb-tls-connect\._tcp' \
    | grep -Eo '([0-9]{1,3}\.){3}[0-9]{1,3}:[0-9]+' \
    | sort -u
}

connect_from_mdns() {
  ENDPOINTS="$(mdns_endpoints || true)"

  if [ -n "$HINT" ]; then
    HINTED="$(printf '%s\n' "$ENDPOINTS" | grep -F "$HINT" || true)"
    HINT_COUNT="$(printf '%s\n' "$HINTED" | sed '/^$/d' | wc -l | tr -d ' ')"
    if [ "$HINT_COUNT" -eq 1 ]; then
      adb connect "$(printf '%s\n' "$HINTED" | sed -n '1p')" >&2 || true
      sleep 1
      return 0
    fi
  fi

  COUNT="$(printf '%s\n' "$ENDPOINTS" | sed '/^$/d' | wc -l | tr -d ' ')"
  if [ "$COUNT" -eq 1 ]; then
    adb connect "$(printf '%s\n' "$ENDPOINTS" | sed -n '1p')" >&2 || true
    sleep 1
    return 0
  fi

  return 1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --format) FORMAT="${2:-}"; shift 2 ;;
    --hint) HINT="${2:-}"; shift 2 ;;
    --expect-model) EXPECTED_MODEL="${2:-}"; shift 2 ;;
    --expect-device) EXPECTED_DEVICE="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "FAIL: unknown arg: $1"; exit 2 ;;
  esac
done

case "$FORMAT" in plain|shell) ;; *) echo "FAIL: invalid --format: $FORMAT"; exit 2 ;; esac
command -v adb >/dev/null 2>&1 || die "adb not found"

if [ -n "${ANDROID_SERIAL:-}" ]; then
  echo "WARN: ignoring stale-prone ANDROID_SERIAL=$ANDROID_SERIAL" >&2
fi

if pick_active; then
  exit 0
fi

connect_from_mdns || true

if pick_active; then
  exit 0
fi

echo "Connected devices:" >&2
adb devices -l >&2 || true
echo >&2
echo "mDNS services:" >&2
adb mdns services >&2 || true
echo >&2
echo "Hint examples:" >&2
echo "  export MORPHE_ADB_HINT=192.168.1.248" >&2
echo "  tools/boost-adb-serial.sh --hint 192.168.1.248 --expect-model Pixel_6 --expect-device oriole" >&2
die "could not resolve a unique current adb target"
