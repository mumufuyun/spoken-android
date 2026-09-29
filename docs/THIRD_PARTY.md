# Third-Party Components and Licenses

This document is the SPDX-style inventory of third-party components shipped in
the Spoken Mobile single APK. Per-library metadata for the in-app About screen
lives in `app/licenses/libraries/` (AboutLibraries format); this file records
the distribution-level view, including data packages that AboutLibraries does
not cover.

## Product status

Spoken Mobile is a derivative work of fcitx5-android (LGPL-2.1-or-later). All
modifications are distributed in source form under the same license, as
required.

Note that some linked components carry GPL licenses (librime-octagram is
GPL-3.0-only and is statically linked into the prebuilt librime; the bundled
rime-ice-t9-phone configuration is GPL-3.0-only data; tt9 English wordlist
contains GPL-3.0-only parts). As a result the distributed APK as a whole is
effectively subject to GPL-3.0 terms. This is acceptable while the product is
open source. If a proprietary distribution channel is ever considered,
octagram must be excluded from the librime build and the configuration and
wordlist must be replaced with permissively licensed alternatives.

## Native input method stack

| Component | Source | License |
|---|---|---|
| fcitx5-android (this fork) | github.com/Rizumu85/fcitx5-android-t9-phone | LGPL-2.1-or-later |
| fcitx5 | github.com/fcitx/fcitx5 | LGPL-2.1-or-later |
| fcitx5-lua | github.com/fcitx/fcitx5-lua | LGPL-2.1-or-later |
| fcitx5-chinese-addons | github.com/fcitx/fcitx5-chinese-addons | GPL-2.0-or-later AND LGPL-2.1-or-later |
| libime | github.com/fcitx/libime | LGPL-2.1-or-later |
| fcitx5-rime | github.com/Rizumu85/fcitx5-rime (android-t9 branch) | LGPL-2.1-or-later |
| librime | github.com/rime/librime (prebuilt) | BSD-3-Clause |

## Rime data packages (compiled into the engine workspace)

| Component | License |
|---|---|
| rime-prelude | LGPL-3.0-only |
| rime-essay | LGPL-3.0-only |
| rime-luna-pinyin | LGPL-3.0-or-later |
| rime-stroke | LGPL-3.0-or-later |

## Bundled configuration archive

| Component | Source | License |
|---|---|---|
| rime-ice-t9-phone (T9 schemas, dictionaries, predict.db) | github.com/Rizumu85/rime-ice-t9-phone v3.2.2 | GPL-3.0-only |

The archive ships in `app/src/main/assets/rime/` and is installed on first run
by `RimeConfigProvisioner`; the online release channel remains the upgrade
path. See the GPL note in "Product status" above.

## Prebuilt native libraries (fcitx5-android prebuilt repo)

| Component | License |
|---|---|
| boost (headers, iostreams) | BSL-1.0 |
| glog | BSD-3-Clause |
| leveldb | BSD-3-Clause |
| libintl-lite | BSL-1.0 |
| lua | MIT |
| marisa-trie | BSD-2-Clause AND LGPL-2.1-or-later |
| opencc | Apache-2.0 |
| yaml-cpp | MIT |
| libuv | MIT |
| zstd | BSD-3-Clause AND GPL-2.0-only |

## Rime addon plugins linked into librime

| Component | License |
|---|---|
| librime-lua | BSD-3-Clause |
| librime-octagram | GPL-3.0-only |
| librime-predict | BSD-3-Clause |
| librime-predict data | BSD-3-Clause AND Apache-2.0 |

## Other bundled data

| Component | License |
|---|---|
| TT9 English wordlist (assets/t9) | MIT AND Apache-2.0 AND GPL-3.0-only |
| Hanzi handwriting data (assets/handwriting) | LGPL-3.0-or-later AND Arphic-1999 |

## JVM / Android dependencies

AndroidX, Kotlin, OkHttp, kotlinx-serialization, splitties, Material
Components, Room, AboutLibraries, timber, arrow, flexbox, imagecropper and
other Maven dependencies are listed with their licenses in
`app/licenses/libraries/` and rendered in the in-app About page. Google ML
Kit Digital Ink is proprietary and subject to Google's ML Kit Terms of
Service.
