# Yep

**"Did I lock the door?" — answered by your phone instead of a drive home.**

Yep is a tiny check log for the things people second-guess: front door locked, stove off, meds taken,
car locked. When you do the thing, you **press and hold** its card until it fills. Later, one glance
tells you exactly when you did it ("Today, 8:42 PM · 2 hr ago").

Holding instead of tapping is the point. The extra second of deliberate attention is what makes the
moment stick in memory, and it means pocket taps can't log anything by accident.

## Features (POC)

- Hold-to-confirm cards with haptic feedback; a card turns green for 12 hours after a confirmation
- Undo banner right after confirming, in case you held the wrong card
- Tap a card for its history (last 50 confirmations), rename, clear or delete
- Add your own checks with an emoji icon
- "x of y confirmed in the last 12 hours" summary, refreshed every 30 seconds
- Light and dark theme, edge-to-edge on every Android version, TalkBack "Confirm" action

## Why it's low risk

- **No permissions** at all, and no `INTERNET` permission, so nothing can leave the phone
- **No third-party libraries**: Kotlin and the Android framework only
- Data is a small JSON document in `SharedPreferences`; Android Auto Backup carries it to a new phone
- The hold timer runs on frame time, not animator time, so "Remove animations" can't turn it into a tap

## Run it

**Android Studio (easiest):** File → Open → this `yep` folder → wait for Gradle sync → create an
emulator in Device Manager (any phone, API 36 image) → Run ▶.

**Command line:** with `ANDROID_HOME` set (or a `local.properties` containing `sdk.dir=...`):

```powershell
.\gradlew.bat assembleDebug      # APK lands in app\build\outputs\apk\debug\
.\gradlew.bat installDebug       # installs on a running emulator
.\gradlew.bat test               # JVM unit tests for the logic package
```

## Project layout

```
app/src/main/java/com/georgewitte/yep/
  MainActivity.kt          check list, add dialog, undo banner
  HistoryActivity.kt       history, rename, clear, delete
  ui/HoldToConfirmCard.kt  the press-and-hold card (custom view)
  ui/Ui.kt                 edge-to-edge insets + small drawing helpers
  data/CheckStore.kt       SharedPreferences + JSON persistence
  logic/Checks.kt          pure list operations (unit tested)
  logic/TimeText.kt        "Today, 8:42 PM · 2 hr ago" formatting (unit tested)
app/src/test/...           JUnit tests
```

## Build setup

AGP 9.2.1 (built-in Kotlin) · Gradle 9.4.1 · compileSdk/targetSdk 36 · minSdk 26 (Android 8.0+) · JDK 17+.

## Before publishing to Google Play

- Pick the final `applicationId` in `app/build.gradle.kts`; it can't change after the first upload
- Create an upload key and enable Play App Signing; build with `.\gradlew.bat bundleRelease`
- Data safety form: **no data collected or shared**
- Check the name is free on Play; "Yep" may need a suffix (for example "Yep — Did I lock it?")

## Next ideas

- Home-screen widget showing the last confirmation time
- Optional photo proof (camera intent, still no permissions needed)
- Per-check "fresh for" window (for example 1 hour for the stove, 24 hours for meds)
