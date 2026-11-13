---
name: bb-verify
description: Step-by-step emulator verification runbook for Block Doomscroll. Use whenever a feature must be proven working — after any build/install, before declaring anything done, for blocking features (3/3 bar) and for usage-stats accuracy checks.
---

# Block Doomscroll — verification runbook

Follow the steps IN ORDER. Skipping step 4 is the #1 cause of false "it doesn't
work" conclusions. Never declare a feature done if any step was skipped — say
which step was skipped instead.

## 0. How much proof — pick the tier first
- **A — depends on the system or on timing** (a block firing, the a11y / tracking
  services, notifications, reboot, clock, anything OEM-sensitive): **3 passes in a
  row**, step 5.
- **B — deterministic in-app logic** (gates, calculations, formatting): a unit test
  for the rule + ONE emulator pass per path. Three identical passes prove nothing more.
- **C — looks**: one screenshot per theme (dark, light) and per language whose copy
  changed; confirm all three tabs still render.

A pass that failed because of the test itself (precondition expired, tap missed, app
not ready yet) is re-run and reported — it is not a product failure. A product failure
resets the count to 0. Evidence belongs to one build: after a code change re-run only
the tiers that change can affect, and say which build each result is from.

Verify in a fresh session, after the code is committed. In a long session every adb
call drags the whole conversation along, and verification is hundreds of calls.

## 1. Boot the emulator
Two AVDs on the same image, with different apps installed:

| AVD | port | installed | use for |
|---|---|---|---|
| `Pixel_9` | 5554 | Instagram, TikTok, YouTube | content targets; shared between sessions |
| `Pixel_9_nav` | 5556 | YouTube only | everything else, and any parallel session |

Run `adb devices` first — two sessions on one emulator trample each other (installs,
force-stops, taps). Then `emulator -avd <name> -port <port> -gpu host -no-snapshot` in the
background and poll `adb shell getprop sys.boot_completed` until it prints `1`.
`-no-snapshot` matters: a snapshot boot keeps the old monotonic clock while the wall clock
jumps, which strict mode reads as a clock change.

## 2. Build
`export JAVA_HOME="$HOME/.jdks/openjdk-22.0.2"` then
`./gradlew :app:assembleDebug`. Release APK: `assembleRelease`; Play bundle:
`bundleRelease` (→ `app/build/outputs/bundle/release/app-release.aab`).

## 3. Install
adb lives at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`.
`adb install -r` with a **Windows-style local path** (`C:\...\app-debug.apk`).
`MSYS_NO_PATHCONV=1` is only for device-side paths — it breaks local ones.

## 4. ⚠️ Gotcha #1 — re-enable the accessibility service (every reinstall AND every `am force-stop` disables it)
```
adb shell settings put secure enabled_accessibility_services com.djuki.blockbrainrot.debug/com.example.stayfree.service.StayFreeAccessibilityService
adb shell settings put secure accessibility_enabled 1
```
(`a11y_on` in the helpers does this and waits for the binding.)
Poll `adb shell dumpsys accessibility | grep 'Block Doomscroll Screen Monitor'`
until bound (a few seconds; re-set `accessibility_enabled 1` while waiting).
⚠️ Zombie binding: `Crashed services` non-empty, or "bound" under the app label
("Block Doomscroll Test") instead of the a11y label = events are NOT delivered.
Reset: `settings put secure enabled_accessibility_services none` → force-stop
the app → re-run step 4.

## 5. The 3/3 protocol (tier A)
Enter the target surface → block fires → clear the target app (`am force-stop` on the
TARGET is fine, never on ours) → repeat. **3 consecutive passes.** Confirm each fire
with `adb logcat -s MoreMoneyA11y` (`Content surface: <Name>`) and `top_activity`.
- A cold-started YouTube swallows taps for ~5 s: wait before tapping Shorts.
- TikTok on the emulator opens Google sign-in sheets over itself — press Back once they
  load, or use Instagram for the block test.
- Calendar with a "block now" rule is a reliable trigger for `BlockOverlayActivity`.
- Focus mode currently blocks the keyboard too (Gboard is not exempt): don't type while
  a Focus session is running.

## 6. Usage-stats features
Ground truth is `adb shell dumpsys usagestats`, NOT the Digital Wellbeing UI
(its dashboard lags by minutes and will fail a fresh comparison for reasons
that aren't your bug). Protocol: controlled usage of 2–3 apps for a known
duration → compare app's numbers against dumpsys (±5%) → 3/3. Avoid
split-screen during comparison runs (overlapping foreground inflates totals
by design, same as Digital Wellbeing). Multi-day history can't be faked on the
emulator (no root, and a clock change shifts the whole usage log) — prove it with a
unit test and on a real phone.

## 7. Seeing what is on screen
- ⚠️ `uiautomator dump` **suspends every accessibility service while it runs**, ours
  included (`onUnbind`, then a rebind). Never dump between "enter the surface" and
  "block fired" — nothing would fire. Dump only our own static screens, after the block
  screen is already up.
- It also fails outright on autoplay video (IG feed/Reels, TikTok, Shorts) and on
  `ContentBlockActivity` → use `screencap`, or a temporary `Log.d` tree-walk in
  `handleContentBlock` (viewIdResourceName + bounds; remove before committing).
- Take tap coordinates from dump bounds — they are exact. Scaling them off a
  screenshot (displayed ×≈1.21) is the fallback for screens that can't be dumped.

## 8. Helpers — `tools/verify/`
`SERIAL=emulator-5556 . tools/verify/ui.sh` gives `dump`, `txt`, `waitfor`, `tapt`,
`rowtap`, `rowstate`, `shot`, `top_activity`, `a11y_on` / `a11y_off`, `prefs <key>`
(reads DataStore — the emulator has no root or sqlite3), `premium 0|1`,
`clock_shift <seconds>` / `clock_restore`. Use them instead of rewriting them.

## 9. Things that cost time before
- `sed -i` in Git Bash rewrites CRLF files as LF (`strings.xml` and some `.kt` files are
  CRLF) and turns a two-line change into a whole-file diff. Edit with the Edit tool or a
  script that keeps line endings.
- After `pm clear` every permission has to be granted again.
- The emulator is short on space: `pm clear com.zhiliaoapp.musically` frees ~150 MB.
- If Play asks to re-authenticate and "Try again" loops, remove the Google account and
  add it again (the user types the password).
- Leave the emulator as you found it, or write down what you left on it (test PIN,
  blocks, `premium_override`).

## 10. Report honestly
State exactly what was exercised and what wasn't, per tier and per build. "Build green"
alone is NOT verification.
