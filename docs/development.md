# Local Development

Open the repository root in Android Studio or use the Gradle wrapper. The product
is `:app`; `:plugin:rime` remains only as a performance fixture. Do not build or
install a separate Rime plugin for ordinary Spoken testing.

## Toolchain

The pinned versions are defined in `build-logic/convention/src/main/kotlin/Versions.kt`,
`gradle/libs.versions.toml`, and `gradle/wrapper/gradle-wrapper.properties`:

- JDK 21 (Gradle runtime; Android bytecode targets Java 11).
- Gradle 9.3.1, Android Gradle Plugin 9.1.0, Kotlin 2.3.10.
- Android platform 36 and Build Tools 36.1.0.
- NDK 28.0.13004108, CMake 3.31.6, Ninja, Extra CMake Modules, GNU Gettext.
- Minimum Android API 23; target API 36.

Install the tools from their official distributions. Keep machine-specific paths
in the ignored `local.properties`, environment variables, or an external shell
activation file; never commit credentials or a developer's absolute paths.

On macOS, for example:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```

Set `sdk.dir` in `local.properties` when the SDK is not in its default location.
Android Studio can use its bundled JBR if it meets the Gradle runtime requirement.

## Required Submodules

Initialize only the product dependencies. Unused language plugins in `.gitmodules`
are not required; some historical upstream pins may no longer be available.

```bash
git submodule update --init --recursive --jobs 4 -- \
  lib/fcitx5/src/main/cpp/fcitx5 \
  lib/fcitx5/src/main/cpp/prebuilt \
  lib/fcitx5-lua/src/main/cpp/fcitx5-lua \
  lib/libime/src/main/cpp/libime \
  lib/fcitx5-chinese-addons/src/main/cpp/fcitx5-chinese-addons \
  plugin/rime/src/main/cpp/fcitx5-rime \
  plugin/rime/src/main/cpp/rime-prelude \
  plugin/rime/src/main/cpp/rime-essay \
  plugin/rime/src/main/cpp/rime-luna-pinyin \
  plugin/rime/src/main/cpp/rime-stroke
```

Preserve the repository's pinned commits. Do not replace them with current upstream
heads to resolve a download failure.

## Focused Verification

Start with the smallest check relevant to the change:

```bash
./gradlew --version
git diff --check
./gradlew :app:testDebugUnitTest -PbuildABI=arm64-v8a \
  --tests 'org.fcitx.fcitx5.android.input.voice.VoiceAudioSessionTest' \
  --tests 'org.fcitx.fcitx5.android.input.voice.AiProcessorTest'
```

The first test invocation may still compile Android/native dependencies. Record a
failed dependency download separately from a test failure. A test source count,
old report, or syntax check is not a passing test run.

When an APK build is requested:

```bash
./gradlew :app:assembleDebug -PbuildABI=arm64-v8a
```

Outputs are under `app/build/outputs/apk/debug/`; the installed debug package is
`com.spoken.android.debug`. The code namespace remains `org.fcitx.fcitx5.android`,
so fully qualified activity and IME class names retain that prefix.

Use [the device workflow](android-studio-final-test-workflow.md) for regression
checks and [the release runbook](release-runbook.md) for signing. Rime provisioning
changes require [clean-install validation](rime-clean-install-validation.md).
