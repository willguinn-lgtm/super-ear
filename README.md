# Super Ear – Sound Amplifier

A native Android (Kotlin + Jetpack Compose) app that amplifies ambient sound
picked up by the phone's microphone and plays it back, louder and in real
time, through the user's **own** headphones — a personal "bionic ear" /
sound-amplifier tool, similar in spirit to apps like "Super Hearing" or
"Sound Amplifier."

**Important — what this app is, and is not:** this is an openly-used,
on-device hearing-assist tool. It is *not* a covert surveillance or
"spy" app. It shows a persistent, un-swipeable notification the entire time
the microphone is active, requires the user to grant microphone permission
themselves, and never uploads, records, or transmits audio anywhere. Keep it
that way — see [Play Store & legal compliance](#play-store--legal-compliance)
below before you publish.

## Features (v1)

- Real-time mic → headphone amplification (low-latency `AudioTrack` +
  `AudioRecord`, `AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY`)
- Adjustable volume boost, 100%–600% (`GainControl` slider)
- Optional background-noise smoothing, plus platform `NoiseSuppressor` /
  `AcousticEchoCanceler` when available on the device
- Headphone-required safety check (warns + blocks Start if no wired/BT
  headset is connected, to prevent feedback squeal through the speaker)
- Mandatory first-run disclaimer (not a medical device; don't use to listen
  to other people without consent)
- Foreground service with a visible, dismiss-proof notification + Stop action
  any time the mic is active
- AdMob banner ad for monetization (currently wired to Google's **test** ad
  unit ID — see below)
- Dark theme UI built to WCAG 2.2 AA: all text/background pairs verified
  ≥ 4.5:1 contrast, 48dp+ touch targets, `contentDescription`s and live
  regions for TalkBack, text sized in `sp` so it respects the system font
  scale

## Project layout

```
app/src/main/java/com/willg/superear/
  SuperEarApp.kt            – Application class, AdMob init, notif channel
  MainActivity.kt            – Compose UI (single screen)
  AudioEngine.kt              – Core mic→speaker amplification loop
  AudioAmplifierService.kt    – Foreground service wrapping AudioEngine
  HeadphoneUtil.kt            – Detects wired/Bluetooth headset connection
  PreferencesManager.kt       – Tiny SharedPreferences wrapper
  BannerAd.kt                 – AdMob banner Compose wrapper
  ui/theme/                   – Material3 dark theme, colors, type
```

## Building

Requires JDK 17+ and the Android SDK (compileSdk 34 / build-tools 34.0.0).
Easiest path: open the project folder in **Android Studio** (Koala/Ladybug
or newer) and let it sync — it will prompt to install any missing SDK
components automatically.

From the command line (after setting `ANDROID_HOME`/`ANDROID_SDK_ROOT` and
`JAVA_HOME`):

```powershell
.\gradlew.bat assembleDebug      # unsigned debug APK, for testing on a device
.\gradlew.bat assembleRelease    # minified release APK (unsigned)
.\gradlew.bat lintDebug          # static analysis
```

This project was built and verified end-to-end in this environment:
`assembleDebug`, `lintDebug`, and `assembleRelease` (R8 minification) all
complete successfully.

### Testing on a real device

1. Enable Developer Options + USB debugging on an Android phone.
2. `adb install app\build\outputs\apk\debug\app-debug.apk`
3. Plug in wired or Bluetooth headphones *before* tapping "Start listening"
   (the app will otherwise warn you and refuse to start, since speaker
   playback of the amplified mic feed causes a loud feedback squeal).

## Before you publish: required changes

1. **Set a real `applicationId`.** Pick something unique to you, e.g.
   `com.yourname.superear`, in [app/build.gradle.kts](app/build.gradle.kts)
   and [AndroidManifest.xml](app/src/main/AndroidManifest.xml) usages.
2. **Replace the AdMob test IDs** in
   [AndroidManifest.xml](app/src/main/AndroidManifest.xml) (`APPLICATION_ID`
   meta-data) and [BannerAd.kt](app/src/main/java/com/willg/superear/BannerAd.kt)
   (`TEST_BANNER_AD_UNIT_ID`) with your own IDs from the
   [AdMob console](https://apps.admob.com/). Shipping with test IDs means
   you earn $0 and Google will eventually flag the app.
3. **Sign the release build.** Generate a keystore and configure signing in
   `app/build.gradle.kts`:
   ```powershell
   keytool -genkeypair -v -keystore superear-release.jks -alias superear -keyalg RSA -keysize 2048 -validity 10000
   ```
   Then add a `signingConfigs { release { ... } }` block referencing it (keep
   the keystore and its passwords out of git — see `.gitignore`).
4. **Replace the placeholder launcher icon** (`ic_launcher_foreground.xml`,
   a simple vector ear/sound mark) with real branding.
5. Bump `versionCode`/`versionName` for each release you upload.

## Play Store & legal compliance

Google Play has a specific, strict policy against **stalkerware / covert
surveillance apps**. This app is designed to stay clearly on the right side
of that line — keep it that way:

- Never hide or remove the foreground-service notification while the mic is
  active; Play requires this for any app with background microphone access.
- Never market or describe the app as a way to listen in on other people
  (e.g. "spy on conversations," "listen through walls secretly"). Market it
  honestly: a personal sound amplifier / hearing-assist tool, used openly by
  its owner with their own headphones.
- Keep the disclaimer dialog (medical-device disclaimer + consent reminder)
  in the first-run flow.
- Add a privacy policy URL in your Play Console listing describing that
  audio is processed on-device only and never leaves the phone (true for
  this implementation — there is no network/audio-upload code at all).
- Complete Play Console's **Data Safety** form accurately: declare
  microphone access, and state audio is not collected/shared.
- If you ever add a "save a clip" feature, make it opt-in only and disclose
  it prominently, since on-device recording changes the privacy story.

## Known limitations / ideas for v2

- No EQ presets or directional/beamforming focus yet (stereo mic arrays
  would require native DSP work).
- No automated tests included (unit tests for `AudioEngine`'s gain/clamp
  math would be the first thing to add).
- AdMob free tier alone is unlikely to produce significant revenue; an
  optional one-time IAP to remove ads / unlock extra EQ presets is the more
  common monetization path for this app category.
