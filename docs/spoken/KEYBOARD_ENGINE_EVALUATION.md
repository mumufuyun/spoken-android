# Spoken Mobile Android 输入法内核选型

> 状态：Baseline reproduced v0.2  
> 日期：2026-08-24  
> 核心约束：中文拼音九键是首版硬门槛

## 1. 结论摘要

推荐路线已经通过源码构建得到进一步收敛：

> 先在已成功构建的 Fcitx5 Android 九键分支上增加最小触屏 3×3 九键；如果触摸适配牵动范围过大，再评估 Trime；只有完整开源底座都不适合时，才由 Spoken 自己维护 `InputMethodService` 和 JNI。

不建议直接把现有 Spoken 继续扩展成完整键盘，也不应在复现现有项目之前先写新的 IME 基础设施。

参考实现优先级：

1. Fcitx5 Android 九键手机分支：已构建成功，作为当前 Spike 底座；
2. Trime：若 GPL 策略可接受，尝试作为可直接 fork 的触屏可配置底座；
3. AOSP LatinIME：参考现代 Android 输入框适配、英文/符号和按键基础设施；
4. AOSP PinyinIME：只参考旧拼音解码接口，不作为可直接使用的产品底座。

最终决定必须经过实机技术原型，不应只凭仓库文档作出。

## 2. 选型硬门槛

候选方案必须支持或允许我们实现：

- 触屏中文拼音九键；
- 本地拼音解码、候选排序、用户词频和联想；
- 未提交拼音的增删、分段与候选选择；
- 中文、英文、数字、符号和特殊输入框；
- Spoken 自定义 UI 和语音/AI 状态；
- 每个输入会话严格隔离；
- Android 现代版本持续维护；
- 许可证允许未来公开或商业分发；
- 核心文字输入无需安装第二个 APK 或在线下载后才能首次使用。

## 3. 候选方案对比

| 方案 | 中文能力 | 触屏九键现状 | UI 可控性 | 许可证 | 结论 |
|---|---|---|---|---|---|
| Fcitx5 Android 九键分支 | 已有 Rime T9、组合态、候选、学习和测试 | 缺触屏 3×3 键区，需增加薄适配层 | 中 | LGPL-2.1-or-later；配置/部分数据含 GPL | **当前 Spike 底座** |
| 自有 IME + librime | 强，方案和词库可配置 | 需自行实现完整 IME/JNI/候选层 | 最高 | librime BSD-3-Clause；方案/词库需逐项核对 | 完整底座失败后的兜底 |
| Trime | 基于 librime，中文方案丰富 | 可通过配置实现九宫格，但现成配置兼容性不稳定 | 高 | GPL-3.0-or-later | 个人使用可行，产品分发约束较大 |
| AOSP PinyinIME | 有本地拼音解码与用户词库 | 未提供现代九键产品体验 | 中 | Apache-2.0 | 已约 12 年未维护，不作为底座 |
| AOSP LatinIME / HeliBoard | 英文键盘基础成熟 | 不解决中文拼音九键 | 中到高 | AOSP Apache-2.0；HeliBoard GPL-3.0 | 只参考英文与按键层 |
| 从零开发拼音引擎 | 完全自控 | 完全自控 | 最高 | 自有 | 工程量与质量风险不可接受 |

## 4. 当前推荐：Fcitx5 T9 基线 + Spoken 触摸层

主 APK、Rime 插件和 735 个单元测试已经在固定提交复现成功。现有 `T9Keyboard` 只包含一排屏幕功能键，核心数字输入来自实体键，因此当前只新增触屏 `3×3` 九键及其语义适配，复用既有 composing、preedit、候选、分页和词典能力。

构建证据、产物、许可证与明确改造面见 `OPEN_SOURCE_BASELINE_REPORT.md`。

### 4.1 兜底方案：Spoken Shell + librime

仅当 Fcitx5 基线的触摸适配被证明接近重写整个输入层，且 Trime 也不合适时，才采用自有壳 + librime。

#### 为什么保留 librime 作为兜底

- librime 是跨平台 C++ 输入法核心，采用 BSD-3-Clause；
- 支持通过 YAML schema 定义拼音及变体；
- 社区已有九宫格/T9 schema，可证明数字键到拼音候选的技术路径存在；
- 中文解码与产品 UI 可以解耦；
- Spoken 可以独立控制 Compose/View UI、语音入口、场景交互和会话安全；
- 不需要继承完整开源输入法 App 的信息架构与视觉负担。

#### 主要风险

- 需要维护 Android NDK、JNI、ABI 和 native 崩溃诊断；
- 九键 schema、候选排序和词库质量必须实机验证；
- Rime 默认体验偏可配置输入平台，不等于主流手机九键体验；
- 用户词频、下一词预测、拼音容错和中英文混输可能需要额外插件或自研层；
- 优质词库常带 GPL、LGPL 或 CC 授权，必须逐个组件确认；
- 不能直接假设社区 T9 配置可用于商业分发或符合我们的交互。

#### 兜底架构

```text
Android InputMethodService
        │
        ├── Input Session Controller
        │     ├── InputConnection identity
        │     ├── composing / selection
        │     └── session cancellation guard
        │
        ├── Keyboard Presentation
        │     ├── T9 layout
        │     ├── candidate strip/grid
        │     ├── English / number / symbol layouts
        │     └── voice and scene controls
        │
        ├── Local Text Engine
        │     ├── JNI adapter
        │     ├── librime session
        │     ├── schema and dictionaries
        │     └── user dictionary store
        │
```

当前只验证文字输入。Rime session 与 Android input session 必须有明确 ID，不允许使用一个全局可变引擎承载多个输入框。

## 5. 其他方案判断

### 5.1 Fcitx5 Android

优点：

- 项目活跃；
- 已包含拼音、双拼、五笔、候选展开、剪贴板、符号与 Emoji；
- 插件体系中可接入 Rime；
- Android IME 工程化程度明显高于当前 Spoken 原型。

阻碍：

- 官方 README 明确写着虚拟键盘布局尚不可自定义；
- 现有九键分支主要面向物理九键手机，触屏全键盘不是其重点；
- 完整 fork 会把 Spoken 绑定到较大的 Kotlin/C++/插件构建体系；
- LGPL 合规、第三方插件和词库许可需要正式审查。

判断：作为架构和代码参考价值很高；只有技术原型证明能低成本加入触屏九键并隔离产品 UI 后，才考虑成为底座。

### 5.2 Trime

优点：

- Android 上长期使用 librime；
- 键盘布局和输入方案高度可配置；
- 可参考 JNI 生命周期、候选获取和 schema 部署。

阻碍：

- GPL-3.0-or-later 会显著影响整个衍生 App 的分发方式；
- 九宫格不是稳定的开箱即用主路径，社区 issue 记录过配置版本失效；
- 高可配置平台的交互与 Spoken 的单一、稳定个人体验目标不同。

判断：重点阅读和实验，不建议直接作为闭源或许可证策略未确定产品的代码底座。

### 5.3 AOSP PinyinIME

优点：Apache-2.0，包含本地拼音解码和用户词库的旧实现。

阻碍：主分支最后实质更新约在 12 年前，面向早期 Android 构建体系；迁移成本、兼容性和候选质量风险高。

判断：不作为底座，只参考算法接口和测试样本。

### 5.4 HeliBoard / OpenBoard 路线

优点：英文字母键盘、手势、符号、输入框适配和主题能力较成熟。

阻碍：当前不提供中文拼音/CJK 主路径，九键和中文候选仍需重新开发；HeliBoard 本身为 GPL-3.0。

判断：可参考按键与英文布局，不能解决核心选型。

## 6. 许可证策略

当前建议：

- 优先使用 BSD、Apache-2.0、MIT 等宽松许可证组件；
- 使用 LGPL 组件前确认动态链接、修改源码提供方式和应用商店分发要求；
- 在产品是否开源尚未决定前，不把 GPL App 代码直接合入 Spoken；
- Rime schema、词库、语言模型和代码分别建立 SPDX 清单；
- 技术原型可以使用候选配置验证体验，但进入产品仓库前必须完成许可替换或确认。

本报告是工程选型建议，不替代正式法律意见。

## 7. 两阶段技术原型

### Spike A：九键输入质量（首要）

目标：证明 `librime` 能在 Spoken 自有 UI 中提供可接受的触屏拼音九键体验。

本阶段只回答一个问题：

> 我们能否找到一条确定、可维护、许可证可接受的 Android 拼音九键实现路径？

本阶段不讨论或验证语音入口、语音与文字融合、AI 场景、最终视觉和完整 Spoken 产品架构。

范围：

- 单独建立实验模块，不改造当前语音原型；
- 编译 arm64-v8a 的 librime；
- 最小 JNI：创建/销毁 session、发送按键、读取 preedit、读取候选、选择候选；
- 固定 3×3 数字键和必要操作键；
- 验证能否读取音节边界、当前拼音分支及组成“左侧消歧栏”所需的状态；
- 验证显式分词、删除当前编码、清空重输和确认候选；
- 加载许可清晰的最小简体拼音词库；
- 验证删除、分词、连续输入、候选翻页和用户词频；
- 在用户真实手机上与当前常用九键完成同一组输入任务。

通过条件：

- 30 条日常句子均可完成；
- 没有阻断性的拼音组合或删改缺陷；
- 能实现可理解的拼音 preedit、音节分隔和九键消歧，不只能展示黑盒候选列表；
- 候选首屏延迟和按键反馈无明显卡顿；
- 核心九键交互无需 fork 整个 Trime/Fcitx5 App；
- 词库与代码许可证路径可接受。

否决条件：

- 数字歧义导致候选质量无法达到日常使用要求；
- 必须依赖高风险 GPL 词库或大量私有补丁才能工作；
- JNI/native 生命周期在 IME 场景下频繁崩溃；
- 为实现基本九键必须重写 librime 主要解码路径。

### Spike B：文字与语音同会话

只有 Spike A 通过，并完成下一轮产品定义后，才决定是否以及如何开展本阶段。以下内容不属于当前计划的承诺范围：

- 九键 composing 中启动和取消语音；
- 语音结果进入当前光标/选区；
- 语音完成后继续九键修改；
- 切换输入框后旧 ASR/AI 回调不能提交；
- 网络失败不影响本地九键；
- 密码框正确禁用语音和学习。

## 8. 推荐推进顺序

1. 收集当前常用九键键盘的全套截图和操作习惯；
2. 建立独立 `keyboard-spike`，验证 librime JNI 与 T9 schema；
3. 用真实输入语料对比当前常用键盘和原型候选质量；
4. 完成许可证清单；
5. Spike A 评审后再决定正式底座；
6. 重新建立生产级 Spoken Mobile 工程，不在现有语音原型上直接堆叠。

## 9. 主要资料

- librime：https://github.com/rime/librime
- Rime 官网与方案机制：https://rime.im/
- Fcitx5 Android：https://github.com/fcitx5-android/fcitx5-android
- Fcitx5 Android 九键手机分支：https://github.com/Rizumu85/fcitx5-android-t9-phone
- Trime：https://github.com/osfans/trime
- AOSP PinyinIME：https://android.googlesource.com/platform/packages/inputmethods/PinyinIME/
- AOSP LatinIME：https://android.googlesource.com/platform/packages/inputmethods/LatinIME/
- HeliBoard：https://github.com/HeliBorg/HeliBoard

## 10. 当前决策记录

- **已决定**：移动端必须同时具备完整文字输入和语音输入；
- **已决定**：首版主文字布局为中文拼音九键；
- **已决定**：不再把系统输入法切换体验作为核心解法；
- **已决定**：当前只验证拼音九键技术实现，不预设语音与文字的融合形态；
- **已验证**：Fcitx5 Android 九键分支可从源码构建，单测通过，可复用引擎、会话和候选层；
- **当前实施选择**：在该分支增加最小触屏 3×3 九键；
- **兜底**：Trime，其后才是 Spoken 自有 IME 壳 + librime；
- **尚未决定**：最终词库、T9 schema、英文键盘实现以及产品开源策略。
