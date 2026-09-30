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

## 问题反馈与兼容性共建

吉利相关问题请提交到 **[本仓库 Issues](https://github.com/xiaoshengwpp/DiPlay-Geely/issues)**，先搜索是否已有同车型、同固件问题，再选择 [故障反馈或兼容性记录](https://github.com/xiaoshengwpp/DiPlay-Geely/issues/new/choose)。已有议题可补充新版本与复现结果；不同问题请分开提交。文档修正和代码改进也欢迎 Pull Request。

**所有连接测试、录屏、日志导出和设置变更都应在车辆安全停放时完成，驾驶中不要操作或排障。** 星越 L 2025 款（揽星）及银河车型仍处于适配准备 / 未验证阶段；个人测试通过只代表该车型、硬件、固件和应用版本的组合，不等于整个车系正式支持。上游 APK 和其他 fork 的结果请明确标注来源，不要当成本仓库已发布的吉利版本；本仓库尚无吉利适配 APK，默认源码构建不包含运行时 CarPlay 协议认证资产（与 APK 签名不同），也不能据此承诺可直接连接车辆。自行构建若提示认证不可用 / 无法开始连接，可能是构建缺少该组件，不能直接归因于吉利固件；请报告构建来源与提示，不要上传认证资产或密钥。

### 1. 先记录现象与版本

- 车型、年款、配置（例如星越 L 2025 款揽星；银河请写具体车型）
- 车机硬件 / 芯片、系统名称、完整固件版本、Android 版本；不知道的写“未知”，截图须遮盖个人信息
- 应用版本、下载来源 / 发布页链接；自行构建请附仓库、分支和提交 SHA
- iPhone 型号、完整 iOS 版本、测试日期、问题发生时间及时区
- 连接方式：USB / Wi-Fi Direct / 车机热点 / LocalOnlyHotspot / 未知；无线可补充频段和信道，不要公开热点名称或密码
- 复现步骤、预期结果、实际结果、出现频率（例如 5 次中 3 次），以及此前是否正常、最近是否升级或改过设置

**将连接与声音分开描述：** 是否能进入 CarPlay、是否黑屏 / 断线；媒体、导航播报、Siri、通话是否分别有声，声音从哪里输出，切换后能否恢复。注明原车蓝牙是否同时连接、是否有其他投屏应用运行。兼容性记录逐项标“通过 / 失败 / 未测”，不要把“未测”填成“支持”。显示、触控、休眠唤醒、方向盘按键、仪表 / HUD 也需各自记录。

### 2. 导出应用内诊断报告（优先）

以下步骤按当前源码核对；不同来源或旧版 APK 界面可能不同，吉利车机上的保存与分享功能仍需实际验证。

1. 安全停车后，记录时间并复现一次问题，尽快返回 **DiPlay 首页 → 设置 → 诊断 → 保存诊断报告**（英文：Settings → Diagnostics → Save diagnostic report）。无需先开启“在屏幕上显示调试日志”。
2. **Android 10 及以上：** 默认保存到下载目录的 `DiPlay` 子目录，应用成功提示显示为 `Downloads/DiPlay/DiPlay-时间戳.txt`；文件管理器可能显示“下载”或 `Download`。
3. **Android 9：** 系统会要求选择保存位置。所有版本也可点 **选择保存位置**（Choose save location）自行选择目录，依赖车机提供文件选择器。
4. 看到 **诊断报告已保存** 后，先用文件管理器查看，或通过 **分享** 转到自己的设备审查。分享入口不可用时，从保存目录取出文件；报告不会自动上传。
5. 保存失败请记录完整提示和发生步骤；Android 10+ 的文件选择器打不开时可试默认“保存诊断报告”，默认保存失败可试“选择保存位置”。Android 9 若没有文件选择器，请在议题注明“无法导出”，附版本和错误截图即可，不要为此 root 或绕过车机权限限制。

**日志保留范围：** 应用在会话运行时写入本地有限日志，导出会包含仍存在的当前文件及最多 7 份轮转文件。新会话或文件超过约 512 KiB 会触发轮转，旧记录可能被覆盖；不是按天保存，不能保证找回几天前的问题。报告还含最近一次显示协商信息，请用时间戳区分当前设置与旧记录。问题后尽快导出；导出前不要清除应用数据或卸载，反复重连也可能覆盖所需记录。

### 3. 审查隐私后提交议题

**Issues 和附件对外公开。** 源码含过滤逻辑，但不能保证识别全部车机厂商字段，不能代替人工审查。上传前检查日志、截图和录屏，遮盖 VIN、车牌、手机号、账号 / 设备标识、SSID、MAC / IP、位置与路线，以及令牌、密码和其他凭据。不要上传 MFi 配件身份材料、证书私钥、签名密钥、配对记录或未经审查的完整系统日志。

优先提交**问题发生前后最少必要的脱敏片段**，保留时间、错误信息和相关状态；确认整份报告安全且确有必要时才附文件。无法安全脱敏时先交文字现象，不要公开原始日志，也不要自行假设存在私人收件渠道。保存导出文件在你自己的设备上，并在议题标出对应时间段。

应用内导出不可用时，**仅对已获授权且已经可用的 ADB 连接**，可选用 `adb logcat -v threadtime`，在电脑本地记录一次短复现后停止，再筛选并人工脱敏；它可能包含其他应用和系统信息，不要直接上传完整输出。不需要为反馈开启调试、root 或绕过车机限制；不会使用 ADB 也可以提交问题。

提交后如固件、iOS 或应用版本变化，请在原议题补充新版本与结果。测试成功、失败和未测都欢迎，但兼容性结论需要可复现证据。[隐私与诊断说明](docs/PRIVACY.md)介绍上游的日志处理边界。

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
