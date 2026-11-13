#!/usr/bin/env bash
# Emulator helpers for the bb-verify runbook. Source it from the repo root:
#   SERIAL=emulator-5556 . tools/verify/ui.sh
#
# dump / txt / tapt / rowtap / rowstate / waitfor run `uiautomator dump`, which suspends
# every accessibility service while it runs — ours included. Never call them between
# entering a blocked surface and the block firing; use top_activity and logcat there.

ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
A="$ADB -s ${SERIAL:-emulator-5556}"
P="${PKG:-com.djuki.blockbrainrot.debug}"
VERIFY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
A11Y="$P/com.example.stayfree.service.StayFreeAccessibilityService"

shot() { $A exec-out screencap -p > "$1"; }
top_activity() { $A shell dumpsys activity activities | grep -m1 topResumedActivity; }

# Current window as "text|resource-id|x1,y1,x2,y2|checked" lines.
dump() {
  MSYS_NO_PATHCONV=1 $A shell uiautomator dump /sdcard/w.xml >/dev/null 2>&1
  MSYS_NO_PATHCONV=1 $A shell cat /sdcard/w.xml | python -I "$VERIFY_DIR/nodes.py"
}
# txt "<regex>": the text of every matching node, ';'-separated.
txt() { dump | grep -E -- "$1" | cut -d'|' -f1 | tr '\n' ';'; }
# waitfor "<text>": polls until a node matches (8 tries).
waitfor() { local n=0; until dump | grep -qF -- "$1"; do n=$((n + 1)); [ $n -gt 8 ] && { echo "TIMEOUT: $1"; return 1; }; done; }

# tapt "<text or |id|>" [nth]: taps the centre of the nth matching node.
tapt() {
  local b x1 y1 x2 y2
  b=$(dump | grep -F -- "$1" | sed -n "${2:-1}p" | cut -d'|' -f3)
  [ -z "$b" ] && { echo "NO NODE: $1"; return 1; }
  IFS=, read -r x1 y1 x2 y2 <<<"$b"
  $A shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
}
# rowtap "<row label>" <x>: taps at x on that row (switches, steppers, delete buttons).
rowtap() {
  local b y1 y2
  b=$(dump | grep -F -- "$1" | head -1 | cut -d'|' -f3)
  [ -z "$b" ] && { echo "NO ROW: $1"; return 1; }
  y1=$(echo "$b" | cut -d, -f2); y2=$(echo "$b" | cut -d, -f4)
  $A shell input tap "$2" $(((y1 + y2) / 2))
}
# rowstate "<row label>": checked state of the first switch after that row's label.
rowstate() { dump | awk -F'|' -v t="$1" 'index($0,t)==1{f=1;next} f&&$2 ~ /switch/{print $4; exit}'; }

a11y_on() {
  $A shell settings put secure enabled_accessibility_services "$A11Y"
  $A shell settings put secure accessibility_enabled 1
  until $A shell dumpsys accessibility | grep -q 'Bound services:{Service\[label='; do :; done
}
a11y_off() { $A shell settings put secure enabled_accessibility_services none; }

# prefs "<key part>": DataStore entries whose key contains it (the emulator has no root or sqlite3).
prefs() { $A exec-out run-as "$P" cat files/datastore/stayfree_prefs.preferences_pb | python -I "$VERIFY_DIR/pb.py" "${1:-}"; }

# premium 0|1: debug-only override, then waits until the app has picked it up.
premium() {
  $A shell "run-as $P sh -c 'echo $1 > files/premium_override'"
  $A shell input keyevent HOME
  $A shell am start -n "$P/com.example.stayfree.ui.MainActivity" >/dev/null 2>&1
  local n=0
  until prefs premium_active | grep -q "(1, $1)" || [ $n -gt 40 ]; do n=$((n + 1)); done
}

# clock_shift <seconds>: moves the system clock relative to the host; clock_restore undoes it.
clock_shift() { $A shell settings put global auto_time 0; $A shell cmd alarm set-time $((($(date +%s) + $1) * 1000)); }
clock_restore() { $A shell cmd alarm set-time $(($(date +%s) * 1000)); $A shell settings put global auto_time 1; }
