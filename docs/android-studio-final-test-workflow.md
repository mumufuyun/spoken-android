# Spoken Android Device Regression

Use this workflow for a requested APK validation. Record the source commit, APK
hash, build type, package, Android version, and explicit ADB device serial. A
completed checklist describes only the tested build and device.

## Build and Install

1. Prepare [the local toolchain](development.md) and sync the project in Android Studio.
2. Build `:app:assembleDebug -PbuildABI=arm64-v8a` for a matching test device.
   Build only `:app`; no separate Rime plugin is needed.
3. Install the APK from `app/build/outputs/apk/debug/` onto the selected device.
   The debug package is `com.spoken.android.debug`. After installation, resolve
   its installed base APK with `adb -s SERIAL shell pm path com.spoken.android.debug`
   and compare that file's SHA-256 with the local APK. A successful transfer or
   install command is not sufficient if the installed bytes do not match. Stop
   validation and reinstall the intended artifact on a mismatch.
4. Enable and select Spoken Debug in Android settings. Reopen the host app and
   focus a fresh text field after replacing an APK; a retained input connection
   can display candidates without accepting committed text.
5. Let the bundled Rime configuration initialize. No manual data copy or plugin
   installation is part of normal setup. For provisioning changes, follow
   [the clean-install procedure](rime-clean-install-validation.md).

Use a local notes app or an unsent draft. Do not send real messages during tests.
An isolated native EditText and WebView test host can inspect composing ranges,
underline spans, selection direction, and composition events without accessing
personal drafts. This verifies those editor implementations; it does not establish
compatibility with every messaging app. Remove temporary test hosts afterward.

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
  tap to finish. Start between existing prefix/suffix text and repeat over a
  selection. Partials must update one underlined range in the host editor; the
  fixed keyboard dock supplies status and Cancel. Unsupported editors use the
  dock preview instead.
- Cancel while a partial is visible. Only the owned preview should disappear;
  replaced text and its original selection direction should be restored. Move
  the caret or manually edit the preview in separate runs: the visible text and
  user edit should remain, the underline should clear, and delayed writes should
  stop.
- Immediately after starting, hide the keyboard or switch to a different field.
  Repeat while the provider is connecting or microphone startup is retrying.
- Start a new recording immediately after cancellation, then finish it normally.
- Repeat with the other configured providers. Custom Whisper-compatible ASR
  uploads after stop and does not offer streaming partial text.

Expected: cancelled sessions publish no late text; the microphone indicator clears
when no recording is active; old cleanup does not stop the new recording. Permission,
empty-audio, and network failures leave ordinary typing usable.

## 3. AI Processing

1. Long-press the voice key and compare DIRECT/original with SMOOTH/original,
   then work communication with English output. Include a name such as 杰森,
   an uncertain deadline, and an explicit correction. DIRECT must preserve the
   recognized string; AI scenes must preserve meaning, conditions, and uncertainty.
   After Finish, the original stays underlined while AI runs. The final result
   replaces that exact range automatically, clears the underline, and preserves
   surrounding text without appending a second copy.
2. Speak a suggestion in meeting-notes mode and a request in AI-instruction mode.
   A suggestion must not become a decision, and an AI instruction must not become
   the completed task. Treat this as live model-quality acceptance, not something
   established by prompt unit tests.
3. While AI is processing, tap the voice key to input the original. Separately
   test an invalid AI key and an ASR disconnect after a partial appears. Expect
   one original insertion, a separate fallback/incomplete notice, and no late
   second insertion. Incomplete recognition must skip AI.
4. Without changing fields or hiding the keyboard, long-press and choose the
   previous-original action; it explicitly inserts the original at the current
   cursor/selection. Repeat during another recording: recovery must cancel that
   recording. The separate cancel action must insert nothing. Start typing again.
5. Repeat after two minutes, switching fields/apps, hiding the keyboard, and in
   a password/private field. No retained original may be offered or inserted in
   those cases. Check the long-press sheet on a small screen, landscape, and large
   font settings: all actions must remain reachable by scrolling.
6. Confirm the dock shows AI processing before testing Cancel and a field switch
   in separate recordings. Cancel restores the pre-recording content; switching
   fields preserves the visible original and never inserts into the new field.
   Wait beyond the normal provider response time and verify both fields remain
   unchanged. Do not count a run where AI already finalized before the action as
   coverage of this race.

Expected: scene/language selection persists, configuration changes affect the next
recording, cancellation blocks late text, notices never enter the editor, and
ordinary typing remains usable. There is no persistent or cross-app recovery.

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
