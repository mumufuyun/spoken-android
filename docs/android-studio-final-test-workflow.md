# Spoken Android Device Regression

Use this workflow for a requested APK validation. Record the source commit, APK
hash, build type, package, Android version, and explicit ADB device serial. A
completed checklist describes only the tested build and device.

## Build and Install

1. Prepare [the local toolchain](development.md) and sync the project in Android Studio.
2. Build `:app:assembleDebug -PbuildABI=arm64-v8a` for a matching test device.
   Build only `:app`; no separate Rime plugin is needed.
3. Install the APK from `app/build/outputs/apk/debug/` onto the selected device.
   The debug package is `com.spoken.android.debug`.
4. Enable and select Spoken Debug in Android settings. Reopen the host app and
   focus a fresh text field after replacing an APK; a retained input connection
   can display candidates without accepting committed text.
5. Let the bundled Rime configuration initialize. No manual data copy or plugin
   installation is part of normal setup. For provisioning changes, follow
   [the clean-install procedure](rime-clean-install-validation.md).

Use a local notes app or an unsent draft. Do not send real messages during tests.

## 1. Text Input

- Enter a Chinese sentence, disambiguate pinyin, expand candidates, select a word,
  delete remaining composition, and then delete committed text.
- Switch among Chinese, English QWERTY, numbers, and both symbol pages. The voice
  entry and side controls must remain aligned.
- Check chat/send, browser/search, URL/email, phone/number, and password fields.
  Password fields must not start voice input or learn typed content.
- Hide/reopen the keyboard and switch apps; old composition must not leak into a
  different editor. For physical keys, use [the dedicated notes](t9-debugging.md).

Expected: no lost/duplicate text, stale candidates, crash, or incorrect editor action.

## 2. Voice Start, Stop, and Cancellation

- Grant microphone permission and configure one ASR provider. Tap to record, then
  tap to finish; the recognized text must be committed once to the same editor.
- Immediately after starting, hide the keyboard or switch to a different field.
  Repeat while the provider is connecting or microphone startup is retrying.
- Start a new recording immediately after cancellation, then finish it normally.
- Repeat with the other configured providers. Custom Whisper-compatible ASR
  uploads after stop and does not offer streaming partial text.

Expected: cancelled sessions publish no late text; the microphone indicator clears
when no recording is active; old cleanup does not stop the new recording. Permission,
empty-audio, and network failures leave ordinary typing usable.

## 3. AI Processing

- Test original transcription, work communication, and a translated language.
  Original scene + original language bypasses AI; original scene + another
  language performs translation.
- During AI processing, tap the voice key to use the recognized original text.
  Wait for the old request to finish and verify it does not insert a second result.
- Switch fields or hide the keyboard during AI processing; no result may enter
  the new field. A failed AI request falls back to the recognized text.

Expected: scene/language selection persists and each accepted result commits once.

## 4. Offline Setup and Lifecycle

- On a clean debug install, turn off networking and complete first Chinese input.
- Restore networking, configure voice, and repeat text -> voice -> text editing.
- Cold-start, rotate, and reopen the keyboard. Check for crashes, repeated Rime
  provisioning, stuck recording, and credentials unexpectedly disappearing.

Expected: core text input remains local; voice failure does not block it. Never
clear release data for this test.

## 5. Release and Updates

Use the signed minified APK and [the release runbook](release-runbook.md). Verify
package/signature/version/ABI, install/upgrade behavior, app updates from
`mumufuyun/spoken-android`, and configuration updates from the Rime upstream.
Debug builds offer only configuration updates because release APKs have a separate
package ID. No published Spoken release means there is no app update to install.

## Failure Report

Include the exact steps, expected/actual result, source commit/APK hash, device and
Android version, input field/app, ASR provider, scene/language, and relevant logs.
Remove API keys and personal transcript text before sharing. Keep untested cases
marked as pending instead of treating them as passed.
