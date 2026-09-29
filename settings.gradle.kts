pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "fcitx5-android"

include(":lib:common")
include(":lib:fcitx5")
include(":lib:fcitx5-lua")
include(":lib:libime")
include(":lib:fcitx5-chinese-addons")
include(":codegen")
include(":app")
include(":performance")
include(":lib:plugin-base")
// Spoken ships a single APK: rime is compiled into :app. The plugin module is kept only
// because :performance uses its APK as a benchmark fixture. Other language plugins
// (anthy/chewing/hangul/jyutping/sayura/thai/unikey/clipboard-filter) are dropped.
include(":plugin:rime")
