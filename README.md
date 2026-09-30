# DiPlay-Geely · 吉利车机社区适配

本仓库直接 fork 自 [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)，面向 **吉利星越 L 2025 款及吉利银河车机**开展社区适配，欢迎车主、开发者和测试者一起共建。

**当前阶段：适配准备中，尚未实车验证。** 目前仅建立 fork 和适配说明，尚未发布本分支的吉利适配 APK，也不声明任何车型已支持。上游主要支持 BYD；其兼容性结论不能直接套用于吉利。吉利银河不同车型、硬件和固件需要分别验证。

Community adaptation for Geely Xingyue L (2025) and Geely Galaxy head units. Experimental, not yet vehicle-tested; no verified Geely compatibility or adaptation release is claimed.

## 兼容性记录

| 目标车型 / 范围 | 车机硬件 / 固件 | 连接、音频及交互验证 | 状态 |
| --- | --- | --- | --- |
| 吉利星越 L 2025 款（揽星） | 待补充准确版本 | 尚未实测 | 适配准备中 / 未验证 |
| 吉利银河车机（具体车型待补充） | 待按车型与版本分别记录 | 尚未实测 | 适配准备中 / 未验证 |

不得以某一车型或固件的结果推断整个车系可用。仪表、HUD、方向盘按键和车辆数据等厂商专属功能均需单独验证。

## 欢迎共建

欢迎提交聚焦吉利适配的 Pull Request、文档修正和可复现测试记录。测试请在车辆安全停放时进行，不在驾驶过程中操作或排障。请记录：

- 准确车型、年款、配置，以及车机系统、固件、Android 版本和硬件信息
- 本仓库提交 SHA / 构建版本，以及 iPhone 型号与 iOS 版本
- 连接方式：USB、Wi-Fi Direct 或车机热点；首次连接、断线重连及休眠唤醒的结果
- 媒体播放、导航播报、Siri、通话及音频焦点切换的结果；注明每项通过、失败或未测
- 分辨率、触控、横竖屏、方向盘按键，以及复现步骤、预期 / 实际行为和脱敏日志

请勿提交密码、私钥、签名材料、VIN、精确位置或其他个人信息。测试失败和未测项目同样有价值。

### 测试记录模板

复制以下字段填写，并通过 Pull Request 补充脱敏记录：

- 测试日期：
- 车型 / 年款 / 配置：
- 车机硬件 / 系统 / 固件 / Android 版本：
- DiPlay-Geely 提交 SHA / 构建版本：
- iPhone / iOS：
- 连接方式：
- 首连 / 断线重连 / 休眠唤醒：通过 / 失败 / 未测
- 媒体 / 导航 / Siri / 通话 / 音频切换：通过 / 失败 / 未测
- 显示 / 触控 / 方向盘按键：通过 / 失败 / 未测
- 复现步骤、预期与实际结果、脱敏日志：

## 上游与参考分支

- **原始上游：** [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)。建仓基线为 `11dc9581df5323170e8033efb6f6b318857a5c81`。
- **吉利适配参考：** [carlito12345/DiPlay](https://github.com/carlito12345/DiPlay)（仓库描述为 “for geely”，当前分支为 `main`）；它是社区参考 fork，不是原始上游。引用或移植前需审查差异、来源和许可，本次初始化未移植其代码。
- 持续关注上游提交与发布，优先评估连接、音频、稳定性和安全修复。同步采用可审查的改动，先检查冲突与吉利回归风险，再合并；上游发布不等于本分支已验证可用。

## 许可与边界

保留上游 `LICENSE`、`docs/licenses`、`docs/THIRD_PARTY_NOTICES.md` 及原有版权声明；分发修改时须遵守适用许可。此项目为独立社区适配，与吉利、Apple 或 BYD 无官方关联或背书。

下方为保留的上游 README。其下载、版本和测试描述属于上游，**不代表本 fork 的吉利适配状态**。特别注意上游关于实验性配件身份、构建资产及分发风险的说明。

---

## 上游 README（原文保留）

# DiPlay

**CarPlay for compatible BYD Android head units.** Wired and wireless, with the familiar DiAuto interface. Independent app: `com.shihab.diplay`.

> **BYD support scope:** These projects focus on BYD cars. They may work on other brands, but other brands are unsupported and there are no plans to add support or fix brand-specific incompatibilities.

[Download & website](https://shihabal3amri.github.io/DiPlay/) · [Release](https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.7) · [Report a problem](https://github.com/shihabal3amri/DiPlay/issues/new/choose)

![DiPlay home](site/assets/home.png)

## 0.2.7 — public preview

Install on the **car**, not the iPhone. No jailbreak, dongle, Mac, account or authentication server is required for use. Core CarPlay does not require ADB; the optional dashboard-mode and battery features do. Your head unit must permit APK installation. Wireless supports Wi-Fi Direct or the car’s existing hotspot; Wi-Fi Direct requires Android 10+; the APK supports Android 9+ for wired use.

- Wired USB and wireless CarPlay with local authentication.
- BYD HUD navigation with arrows, distance and street names on verified firmware.
- Car hotspot support, improved audio buffering and saved receive diagnostics.
- Automatic address discovery, fixed-channel Wi-Fi fallbacks and successful-configuration memory.
- Icon/text size, resolution and frame rate; applying a display change reconnects CarPlay.
- Local diagnostic export. Reports are sent only if you choose to share them.
- Separate installation alongside DiAuto. Run one projection app at a time.

This is **not an Apple-certified product**. The APK bundles an experimental accessory identity recovered from public Carlinkit firmware, not a newly provisioned MFi identity for DiPlay. A bundled private key is extractable. Acceptance after future iOS updates, reliability across head units and suitability of that identity for general distribution are unresolved. This release invites community testing; it is not a guarantee of universal compatibility.

Earlier releases were tested on the development DiLink5.1 car: live windshield guidance and street names work, Car hotspot now starts CarPlay, and Wi-Fi Direct performance is substantially improved. Occasional audio cutouts remain and are deferred to a later update. The newly packaged 0.2.7 APK has not had a separate on-car test. Broader head-unit and iOS compatibility is not guaranteed. The HUD firmware scope and cleanup limits are documented in [BYD navigation](docs/BYD_NAVIGATION.md).

## What’s new in 0.2.7

- App interface in English, Simplified Chinese, Arabic, Russian and Spanish; synchronized Android app-language settings.
- Steering-wheel media controls and long-press Siri on supported BYD firmware while CarPlay is on screen.
- Dashboard display choices: map, turn card, or both; corrected dashboard keyframe recovery.
- Optional ADB feature on supported DiLink 5.0: pause the dashboard map stream when its display mode hides the map.
- Optional ADB battery reporting for Apple Maps, with warning threshold, charging-connector selection and a checked reconnect action.
- Audio playback reliability fixes and clearer dashboard settings.

## Documentation

- [Install and connect](docs/INSTALL.md)
- [Compatibility and troubleshooting](docs/COMPATIBILITY.md)
- [Privacy and diagnostic reports](docs/PRIVACY.md)
- [Build from source](docs/BUILD.md)
- [Validation](docs/VALIDATION.md)
- [Release notes](CHANGELOG.md)
- [Credits and licenses](docs/THIRD_PARTY_NOTICES.md)

The website is available in English, Arabic, Russian, Spanish and Simplified Chinese. The app interface supports those same five languages. Choose the app language in Settings; on Android 13+, it stays synchronized with Android’s per-app language setting.

## Source and credits

Based on [xcertplay](https://github.com/shilapi/xcertplay), GPL-3.0. The home/settings UI and website adapt [DiAuto](https://github.com/shihabal3amri/DiAuto), AGPL-3.0; that license is included in `docs/licenses`. Preserve those notices when distributing modifications. CarPlay and its icon belong to Apple Inc.; no Apple or BYD affiliation or endorsement is implied.

This repository starts with a clean public source snapshot. Local research, tester reports and release-signing secrets are excluded. The complete source corresponding to the APK is provided with every release; experimental runtime identity assets are described separately in the build instructions and notices.

## Local release packaging

The release APK intentionally contains the experimental accessory identity. The Git repository and source archive exclude all accessory and Android signing keys; tests generate synthetic identities at runtime. Source/CI builds omit runtime identity assets by default. Local release builds explicitly select an external asset directory. Publishing the APK makes its bundled identity extractable; building locally does not preserve that identity's confidentiality.
