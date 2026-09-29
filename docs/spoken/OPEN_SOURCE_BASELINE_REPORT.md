# 拼音九键开源基线复现报告

> 状态：Basic device smoke passed / Interaction validation pending  
> 日期：2026-08-24  
> 当前范围：只验证 Android 触屏拼音九键技术路径，不讨论语音融合

## 1. 结论

`Rizumu85/fcitx5-android-t9-phone` 已在本机从源码成功构建，主输入法、Fcitx5、libime、中文扩展与 Rime 插件均可编译，仓库的 735 个基线单元测试全部通过。最小触屏九键 Spike 也已完成编译和定向测试。

因此可以确认：

- Android IME 生命周期、native/JNI 构建、Rime 插件、拼音九键状态机、preedit、候选、分页、候选选择、用户词典和输入框适配，无需从零开发；
- 该项目不能原样完成我们的验证，因为它面向实体九键手机，屏幕上的 `T9Keyboard` 只有一排功能键，没有触屏 `3×3` 拼音九键；
- 已在该开源基线上增加很薄的触摸输入层，`1–9` 触摸键复用已有九键会话和候选管线，没有新增拼音解码器；
- 当前结论是 **Conditional Go**，不是最终 Go。只有真机上的触屏九键完成 30–50 条真实语料测试后，才能确认产品级技术路径。

暂不进入 Trime 或“自有 IME 壳 + librime”。现有基线已经证明大量成熟能力可复用，先完成最小触屏适配更符合复用优先原则。

## 2. 固定基线

| 项目 | 固定值 |
|---|---|
| 主仓库 | `https://github.com/Rizumu85/fcitx5-android-t9-phone` |
| 主仓库提交 | `08624210ca3c97f019adfa358ceb6f81b38a3390` |
| Rime 配置仓库 | `https://github.com/Rizumu85/rime-ice-t9-phone` |
| Rime 配置版本/提交 | `v3.2.2` / `dd5e855c6616f6e86b1a4c1b0fc093b9444c8cd2` |
| 配置归档 SHA-256 | `2ab608dbc238ab3a0d8810eb39d9225b6b2ab755d6178ea0f88c8eb1057476d7` |
| 目标 ABI | `arm64-v8a` |
| 主项目许可证 | `LGPL-2.1-or-later` |

配置归档的实际 SHA-256 与主项目 `BuildConfig` 中锁定的校验值一致。

## 3. 复现结果

### 3.1 构建结果

成功执行：

```shell
./gradlew :app:assembleDebug :plugin:rime:assembleDebug \
  -PbuildABI=arm64-v8a \
  -PcmakeVersion=4.0.2
```

产物：

| 产物 | 大小 | SHA-256 |
|---|---:|---|
| `org.fcitx.fcitx5.android-4.6.6-arm64-v8a-debug.apk` | 71 MB | `44be063e63142333bd88b31dfc45706becc5b88371205d69cd3cc8b74004df7a` |
| `org.fcitx.fcitx5.android.plugin.rime-4.6.6-arm64-v8a-debug.apk` | 8.5 MB | `c7371c8ae00cbe7d443cb4a36ff9e51edafe6244374790e21a5e2afb5294fa70` |

本机研究副本产物位于：

```text
/Users/vincent/.codex/research/fcitx5-android-t9-phone/app/build/outputs/apk/debug/
/Users/vincent/.codex/research/fcitx5-android-t9-phone/plugin/rime/build/outputs/apk/debug/
```

### 3.2 测试结果

成功执行：

```shell
./gradlew :app:testDebugUnitTest \
  -PbuildABI=arm64-v8a \
  -PcmakeVersion=4.0.2
```

结果：

- 132 个测试套件；
- 735 个测试；
- 0 failure；
- 0 error；
- 0 skipped；
- 其中 95 个测试文件名称直接涉及 T9、拼音或候选。

已有覆盖包括中文九键 composing 生命周期、Rime bridge、候选管线与分页、拼音展示、候选新鲜度、词典持久化、删除、输入延迟追踪和物理九键行为。

### 3.3 尚未完成

基础真机安装与单次输入冒烟已经完成，尚未完成：

- 30–50 条真实语料的连续输入测试；
- 候选选择、分页、删除、回退、英文、数字和符号的系统测试；
- 聊天、搜索、密码和数字等不同输入框兼容性验证；
- 长时间使用下的延迟、崩溃和内存观察；
- 与豆包的真实语料对比。

### 3.4 触屏九键 Spike 结果

独立分支：`codex/touch-t9-spike`

已实现：

- 三排 `1–9` 触屏按键，键帽为 `@# / ABC / DEF / GHI / JKL / MNO / PQRS / TUV / WXYZ`；
- 保留现有符号、Emoji、语言切换、空格、删除和回车功能栏；
- 新增单一 `VirtualT9KeyAction`，由 `PhysicalT9KeyHandler.handleVirtualShortPress` 接入现有状态机；
- 中文首个数字的外层转发例外由共享 handler 处理，避免触屏首键重复注入；
- 新安装默认键盘高度由实体键功能栏的 10%/15% 调整为触屏四排所需的 40%/45%。

定向测试：`PhysicalT9KeyHandlerTest` 共 42 个测试，0 failure、0 error，其中新增 3 个触屏短按测试。

新 APK：

| 产物 | 大小 | SHA-256 |
|---|---:|---|
| `org.fcitx.fcitx5.android-4.6.6-arm64-v8a-debug.apk` | 72 MB | `4a76aec78e252b98eb785c01b8d7b532f942e3f793dfaf3d27331720531fd0e1` |

当前实现有意不包含触屏长按、左/右侧操作栏和语音入口；这些属于真机确认基本输入链路可用后的下一轮交互设计。

### 3.5 真机基础冒烟（2026-08-25）

| 项目 | 结果 |
|---|---|
| 设备 | `2509FPN0BC`，`arm64-v8a` |
| 主 APK 安装 | 成功 |
| Rime 插件安装 | 成功 |
| 系统 IME 注册、启用和切换 | 成功 |
| Rime 配置 | `3.2.2`，SHA-256 与锁定值一致 |
| 键盘显示 | 3×3 九宫格与底部功能栏显示正常 |
| 基础输入 | 触屏 `6 → 4` 显示拼音 `ni` |
| 候选 | 首候选“你”，后续候选正常出现 |
| 崩溃 | 本次安装、部署和输入过程中未发现 FATAL EXCEPTION |

这次测试确认了最小闭环：

```text
触屏数字键 → VirtualT9KeyAction → PhysicalT9KeyHandler
→ ChineseT9 会话 → Rime → 拼音栏与中文候选
```

测试仅观察候选，没有向短信会话发送任何内容。当前输入法仍保持为系统默认，供后续手工体验。

真机截图：[`evidence/touch-t9-ni-device-2026-08-25.png`](evidence/touch-t9-ni-device-2026-08-25.png)

## 4. 能直接复用的部分

| 能力 | 判断 | 证据/说明 |
|---|---|---|
| Android `InputMethodService` | 直接复用 | 已有完整服务、输入连接和输入框生命周期 |
| Fcitx5 native/JNI | 直接复用 | arm64-v8a 本机构建成功 |
| librime/Rime 插件 | Spike 直接复用 | 插件 APK 构建成功，配置版本与哈希固定 |
| 中文 T9 会话 | 直接复用 | 已有 composition、分段、输入 receipt 与 engine operation queue |
| 拼音 preedit/reading row | 直接复用 | 已有 `T9PinyinRow*` 展示、窗口和布局策略 |
| 候选首屏、分页和选择 | 直接复用 | 已有完整候选管线及大量单测 |
| 删除与回退 | 直接复用并补触摸测试 | 已有 virtual T9 backspace 和已选分段 reopen 语义 |
| 用户词典/学习 | 直接复用并真机验证 | 已有持久化与学习测试 |
| 数字、符号、英文等辅助输入 | 大部分复用 | 已有布局和输入模式；首版只保留必要部分 |
| 设置、主题、手写、物理键扩展 | Spike 删除或隐藏 | 不属于当前验证目标 |

## 5. 必须单独开发的部分

### 5.1 触屏九键面板

现有 `T9Keyboard.Layout` 只有一行：符号、Emoji、语言、空格、删除和回车。数字 `2–9` 的核心 T9 输入由实体 `KeyEvent` 驱动。

最小 Spike 已验证文字输入路径可用。根据第一轮真机体验和后续 UI 决策，第二轮改为统一四行网格：

- 中间保留 `ABC / DEF / GHI / JKL / MNO / PQRS / TUV / WXYZ` 的 `3×3` 触摸区；
- 左侧为加宽的逗号、句号、问号、感叹号高频标点栏；
- 右侧为删除、跨两行的常驻语音入口、回车；
- 底行为符号、数字、空格、中英文模式；
- 左、中、右控制区共享行列边界，避免侧键和数字键视觉错位。

语音入口采用点击而不是按住。当前里程碑只实现键盘内的布局占位，不切换外部语音输入法，也不声称已接通录音；后续必须由 Spoken 在输入法进程内实现点击开始、点击结束、识别和注入状态。

2026-08-25 已在 `2509FPN0BC` 真机覆盖安装并完成第二轮验收：强制键帽后不再受 Fcitx 主题的“无边框”开关影响；点击 `6 → 4` 能得到 `ni`，首候选为“你”；删除可回退 composing；符号入口可打开现有 Picker。主键盘证据见 `docs/evidence/touch-t9-right-rail-device-2026-08-25.png` 与 `docs/evidence/touch-t9-right-rail-ni-device-2026-08-25.png`。

现有符号 Picker 仍是上游的大面积、无键帽面板，和已确认的主键盘视觉及豆包式分类操作不一致。它被明确记录为下一项独立 UI 改造，不计入本轮主键盘通过范围。

候选区第二轮改造也于 2026-08-25 完成真机验证。触屏九键第一次数字点击会进入独立 touch session：原光标跟随浮层改为键盘上沿固定的 `68 dp` 整宽两行候选条，上行为九键拼音消歧，下行为汉字候选；重复的独立 preedit 小气泡和常驻工具栏均被移除。实体按键输入不进入 touch session，继续保留原浮动候选行为。实机验证 `64 → ni → 你` 可直接点选并写入编辑框，未触发发送。证据见 `docs/evidence/touch-t9-docked-candidates-device-2026-08-25.png` 与 `docs/evidence/touch-t9-docked-selection-device-2026-08-25.png`。

右侧语音入口同时移除“点击说话”文字，改为复用 Spoken 的九柱静态波形；录音开始后再由后续语音状态机驱动动画和颜色，不在文字键盘里提前伪造录音状态。

### 5.2 触摸到现有 T9 会话的适配

不新写解码器，也不复制物理键状态机。触摸动作应进入已有的：

```text
触摸 KeyDef
    -> semantic T9 action adapter
    -> ChineseT9CompositionCoordinator
    -> ChineseT9KeyInputSession
    -> Fcitx5 / Rime
    -> 已有 preedit 与 candidate pipeline
```

当前 Spike 已把一次点击转换为共享 handler 的短按语义，没有向 Android 输入系统伪造硬件事件。正式实现仍应继续把可复用语义动作下沉，使触屏长按也能共享后半段逻辑。

### 5.3 产品化封装

当前安装链路是：

```text
主输入法 APK + Rime 插件 APK + 首次下载约 20 MB Rime 配置
```

这可用于开源基线 Spike，但不满足我们先前设定的“核心文字输入开箱可用”门槛。进入正式产品前需要决定：

- 将必要 Rime 能力与配置随单 APK 打包；或
- 保留插件架构，但提供可靠的一体化安装和离线恢复机制。

首选前者，但需在触屏 Spike 通过后单独评估改造量。

## 6. 构建与上游问题

### 6.1 可接受的本机差异

- 上游锁定 CMake `3.31.6`；本机 CMake `4.0.2` 构建成功；
- 需要 Android NDK `28.0.13004108`、Ninja、Extra CMake Modules 和 GNU Gettext；
- 为避免 GitHub API 限流导致许可证生成任务联网等待，研究副本启用了 AboutLibraries `offlineMode`。

`offlineMode` 只用于本次可复现构建。正式 fork 应补齐本地许可证正文，而不是仅保留离线缺文警告。

### 6.2 上游子模块异常

递归 submodule 更新时，泰语插件 `fcitx5-libthai` 锁定的提交已无法从远端获取。该模块不在本次 `app + plugin:rime` 目标依赖中，因此没有阻止构建。

正式 fork 应从 `settings.gradle.kts` 移除与 Spike 无关的输入法插件，缩小克隆、构建与许可证表面，而不是修复不需要的泰语模块。

## 7. 许可证边界

### 7.1 当前已确认

- 主项目：`LGPL-2.1-or-later`；
- Fcitx5、libime、fcitx5-rime 等主要组件含 LGPL；
- librime：BSD-3-Clause；
- OpenCC：Apache-2.0；
- `rime-ice-t9-phone v3.2.2` 仓库根许可证：GPL-3.0；
- Rime 插件清单还包含 GPL/LGPL 数据或组件，例如 `librime-octagram`、`rime-essay`、`rime-luna-pinyin`；
- 英文 T9 词表许可证清单混合 MIT、Apache-2.0 和 GPL-3.0-only。

### 7.2 工程判断

- 作为个人使用和技术 Spike，可以继续复用这套基线；
- 若未来分发 Spoken，需要完成组件级法律审查，并履行 LGPL/GPL 对应义务，或替换 GPL 配置/数据；
- 不能把“librime 本身是 BSD”误当成整个 APK 与词库都是宽松许可证；
- 许可证并不阻塞下一步触屏技术验证，但会成为正式底座决策闸门。

本节是工程风险识别，不替代法律意见。

## 8. 下一步执行方案

### Step A：在开源基线上做最小触摸 Spike（已完成）

只实现：

1. 固定中文拼音 `3×3` 九键；
2. 将触摸数字键接入现有 Chinese T9 会话；
3. 复用现有 preedit、拼音 reading row、候选和删除；
4. 提供最小标点、清除、确认、英文、数字返回路径；
5. 不接真实语音、不改完整设置，只建立已确认的键盘统一网格。

第一轮完成 `1–9` 和共享会话接入并通过个人真机体验。第二轮按已确认方案实现左侧标点栏、中央九键、右侧删除/语音入口/回车，以及符号/数字/空格/中英文底栏；真实语音行为仍是下一阶段工作。

### Step B：真机验收

1. 安装两个基线 APK；
2. 部署固定的 `v3.2.2` 配置；
3. 验证普通文本、聊天、搜索、密码和数字输入框；
4. 使用 30–50 条脱敏真实语料对比豆包；
5. 记录候选质量、拼音消歧、删除、延迟和崩溃。

### Step C：决策

- 触摸适配小且候选质量可用：继续以该 fork 作为文字键盘 Spike 底座；
- 触摸适配牵动大量物理键专用代码：再评估 Trime 的可配置触屏层；
- 两者都不适合：最后才建立自有壳 + librime。

## 9. 当前决策

**选择：在 Fcitx5 Android 九键分支上继续做最小触屏拼音九键 Spike。**

理由不是它已经提供触屏九键，而是它已经提供了最昂贵、最难稳定的后半段能力。我们只维护触摸 UI 与语义适配层，能最大化复用并最小化开发量。

在真机语料验证通过前：

- 不把该 fork 合并进现有 Spoken 语音原型；
- 不决定最终产品架构；
- 已确定右侧常驻、点击式语音入口的布局方向，但不提前锁定录音中的完整交互细节；
- 不承诺该 GPL/LGPL 组合可直接用于未来分发。
