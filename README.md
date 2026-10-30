# Swell

An Android home screen that arranges itself.

No folders, no dragging. Home is one list, an app to a line, kept in the order you actually
open things: restless for the first few days, then still enough to learn with your thumb. Pure Kotlin + Compose. **No INTERNET permission**; your launch history never leaves the phone.

## How it decides

Swell's model is a *seat book*: every app holds a seat, and every launch is a vote for the app
you opened. Three rules, kept exactly as they were tuned in the design prototype the launcher
was ported from:

1. **Every launch is a vote.** Votes decay (0.4 % per launch of anything else, 3 % per day), so
   last month matters less than this week, but not enough to yank an icon overnight.
2. **An app may only climb one seat per launch** (four while the page is young), and only if it
   beats the occupant by a gap that grows as the page gathers evidence. A pinned seat is never
   passed. This hysteresis is what keeps the list stable enough for muscle memory.
3. **Every app has a line, in rank order.** The most-used app heads the list. The drawer holds
   the same apps alphabetically, with search.

The model is pure Kotlin (`seats/SeatBook.kt`), imports nothing from Android, and has 18 unit
tests that pin the arithmetic.

## Using it

- Tap a line to open its app. That is the vote.
- Drag or fling the list: the lines stay still and the apps step through them, one line at a time.
- Long-press a line: pin the app's place (so it cannot be taken), app info, uninstall.
- Pull the handle, swipe up, or long-press the handle / clock for the drawer.
- **Settings**: long-press the clock or the handle, or use the button at the bottom of the drawer.
  Three styles (Mono is the default; Atelier and Candy), five accent swatches plus a full picker,
  a role-by-role colour table, the clock's place, icon size, monochrome icons, and how readily
  the list reorders.
- **Widgets**, **"Add to Home screen"** pinned shortcuts from your browser, and Swell's backdrop as
  a **live wallpaper** (so the lock screen and app switcher match).
- Work and private profiles are listed alongside personal apps.

## Requirements

Android 8.0 (API 26) or newer. Developed on a OnePlus 12R and a Galaxy S22; other ROMs should
work, but please open an issue with the model and Android version if the home screen misbehaves.

## Install

Grab an APK from the Releases page, or build one:

```
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd package set-home-activity ca.keepitlit.swell/.MainActivity
```

Toolchain: AGP 8.9.1, Gradle 8.11.1 (wrapper included), Kotlin 2.1.20, JDK 17, compileSdk 36.

### Release signing

A release build is signed only if a keystore is supplied; otherwise Gradle produces an
**unsigned** release APK rather than failing. Set these as environment variables or Gradle
properties:

```
SWELL_STORE_FILE      path to the .jks
SWELL_STORE_PASSWORD
SWELL_KEY_ALIAS
SWELL_KEY_PASSWORD
```

## Privacy

- No `INTERNET` permission. Nothing is sent anywhere.
- `allowBackup="false"`: the seat book (what you open and when) stays on the phone and is not
  included in platform backups.
- Permissions used: `ACCESS_HIDDEN_PROFILES` (list Private Space apps), `REQUEST_DELETE_PACKAGES`
  (the long-press Uninstall), `SYSTEM_ALERT_WINDOW` (optional; lets Swell appear the instant you
  leave an app instead of after the system launcher's one-second stall). Each is explained in
  `AndroidManifest.xml`.

## Layout

- `seats/SeatBook.kt`: the model, pure Kotlin, tested in `app/src/test`.
- `theme/Palette.kt`: the three styles and the accent arithmetic.
- `theme/Appearance.kt`: what the user chose; persisted in SharedPreferences.
- `ui/`: Compose: home, drawer, long-press menu, appearance sheet, colour picker.
- `data/`, `helper/`: app enumeration across profiles, LauncherApps launch, icon cache.
- `wallpaper/`: the live-wallpaper service.
- `fastlane/metadata/`: store / F-Droid listing text.

## Tests

```
./gradlew testDebugUnitTest
```

21 tests: 18 on the seat book, 3 on the palette arithmetic. CI runs the same plus `assembleDebug`
on every push.

## License

MIT, see `LICENSE`. Copyright (c) 2026 Keep It Lit Software Inc.
