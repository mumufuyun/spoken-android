# Spoken Android Release Runbook

Spoken ships one APK per architecture. The Rime engine and the verified baseline
configuration archive are bundled in `:app`. The standalone Rime module is a
performance fixture, not a product download.

## Release Inputs

- Confirm the source commit, version, supported ABIs, signing key, and device
  regression results before publishing.
- Update `baseVersionCode` and `baseVersionName` in
  `build-logic/convention/src/main/kotlin/Versions.kt`, plus `buildVersionName` in
  `gradle.properties`. Preserve increasing version codes for installed users.
- Keep `README.md` as the current installation/usage manual.
- Write version-specific changes and validation limitations in
  `release-notes-vX.Y.Z.md`. Historical upstream 4.x notes and `release-baidu/`
  materials are not Spoken release instructions.
- If changing the Rime baseline, update version, URL, SHA-256, byte size, and the
  bundled `app/src/main/assets/rime/rime-ice-t9-phone-main.zip` together. Verify
  archive integrity and required schemas/prediction data. Run clean-install
  validation; an already deployed workspace is insufficient.

App updates use `mumufuyun/spoken-android`. Configuration updates continue to use
`Rizumu85/rime-ice-t9-phone`. Both channels must remain independently versioned.

## Verification and Signing

Follow [local development](development.md) for prerequisites. Run relevant tests
and the [device regression workflow](android-studio-final-test-workflow.md).
Record failures and untested cases; never infer a pass from an old baseline report.

Provide `SIGN_KEY_FILE`, `SIGN_KEY_ALIAS`, and `SIGN_KEY_PWD` through a secure local
environment. Do not put passwords in committed files or command history. Keep the
same release signing identity for upgrade compatibility.

```bash
./gradlew :app:assembleRelease -PbuildABI=arm64-v8a,armeabi-v7a
```

Expected product outputs for a version `X.Y.Z`:

```text
app/build/outputs/apk/release/com.spoken.android-X.Y.Z-arm64-v8a-release.apk
app/build/outputs/apk/release/com.spoken.android-X.Y.Z-armeabi-v7a-release.apk
```

Do not publish debug, unsigned, benchmark, baseline-profile, or Rime-plugin APKs.
Verify each product APK using the installed Build Tools:

```bash
"$ANDROID_HOME/build-tools/36.1.0/apksigner" verify --print-certs "$APK"
"$ANDROID_HOME/build-tools/36.1.0/aapt" dump badging "$APK"
```

Confirm `com.spoken.android`, version name/code, expected ABI, and signing
certificate. Preserve the certificate fingerprint and APK SHA-256 with the
release evidence.

Install and cold-start the signed, minified release on the selected test device.
Debug validation cannot establish R8 or upgrade compatibility. Use the full class
name because the code namespace differs from the application ID:

```bash
adb -s "$SERIAL" shell am force-stop com.spoken.android
adb -s "$SERIAL" shell am start \
  -n com.spoken.android/org.fcitx.fcitx5.android.ui.main.MainActivity
adb -s "$SERIAL" logcat -d | rg \
  'FATAL EXCEPTION|Invalid component registrar|InvalidRegistrarException|NoSuchMethodException'
```

Also verify upgrade installation with the previous Spoken release when one exists.
Never clear release data to make an upgrade test pass.

## Publication

Publishing is a separate action from local preparation. Review the signed APKs,
release notes, validation results, and target commit before creating the public
release.

Use `mumufuyun/spoken-android`, tag `vX.Y.Z`, and the verified source commit. Attach
only supported architecture APKs with the names above: the updater filters for
`com.spoken.android-<version>-<abi>-release.apk`. Each user installs one APK.

After publication:

1. Read back the release tag, assets, sizes, and download links.
2. Download the public APKs and compare their SHA-256 with the verified local files.
3. From an older signed Spoken release, confirm the application update points to
   this repository and selects the device's ABI.
4. Confirm offline first-install Chinese input using the bundled baseline, plus
   the independent configuration update path.

If distributing through another channel, stage the same signed APKs and a short
single-APK installation note. Do not reuse legacy four-APK Baidu packages or
remove historical remote files as part of an ordinary release.
