# DiPlay-Geely · 吉利车机社区适配

本分支仅面向星越 L 2025 款（揽星），仍处于源码适配 / 未实车验证阶段。“银河车机”描述该目标车辆的系统背景，不代表支持银河品牌其他车型。上游支持范围不代表本 fork 的兼容性结论。

版本沿用原始上游并增加适配序号：当前源码为 `0.2.7-geely.1`，标签为 `v0.2.7-geely.1`，Android versionCode 为 `26001`。可安装 APK 将统一通过[本仓库普通 GitHub Releases](https://github.com/xiaoshengwpp/DiPlay-Geely/releases)发布，不另设测试包渠道。**目前尚无可安装发布**，仍需由所有者安全提供既有、获授权的运行时认证资产和稳定签名输入；源码 CI 产物不能当作可安装包。

安装名称为“DiPlay 星越 L”，独立包名为 `io.github.xiaoshengwpp.diplay.geely`；首次安装不会覆盖或自动迁移上游应用数据。后续本分支升级保持同包名、同签名并递增 versionCode。详见[版本、下载与安装说明](README.md#版本与下载)及[发布流程](docs/RELEASE.md)。发布本身不代表星越 L 兼容性已通过验证。

请先阅读 [吉利问题反馈、日志导出与隐私指南](README.md#问题反馈与兼容性共建)，再到 [本仓库提交故障或兼容性议题](https://github.com/xiaoshengwpp/DiPlay-Geely/issues/new/choose)。安全停车后测试；公开前人工审查并脱敏日志、截图及录屏。

以下保留上游中文说明及其原始链接，内容描述上游项目：

---

# DiPlay

为兼容的比亚迪安卓车机提供有线及无线 CarPlay，采用 DiAuto 风格界面。

> 这些项目专注于比亚迪汽车。它们可能在其他品牌上运行，但其他品牌不在支持范围内，也没有增加支持或修复其品牌特定兼容性问题的计划。

[下载与中文网站](https://shihabal3amri.github.io/DiPlay/zh-Hans/) · [完整说明](README.md) · [报告问题](https://github.com/shihabal3amri/DiPlay/issues/new/choose)

0.2.8 为公开预览版，未经 Apple 认证。请安装在车机上，而非 iPhone。无需越狱、转接盒或认证服务器。无线连接支持车载热点或 Wi-Fi Direct（后者需要 Android 10 或更高版本）。

本版本改进了无线 CarPlay 从蓝牙切换到 Wi-Fi 时以及 USB 连接下的位置上报，更新频率限制为每秒最多一次。可选的 ADB 车轮速度功能会在没有 GPS 时向 iPhone 发送比亚迪车轮速度和挡位，以支持位置推算；隧道效果尚未验证。新增可选的 iOS 27 驻车视频功能，可在车辆处于 P 挡时于车机屏幕播放受支持的视频，并使用 iPhone、触屏和方向盘控制。离开 P 挡后播放器会关闭。Apple TV+ 等受 DRM 保护的视频暂不支持，因为 DiPlay 不是获得 FairPlay 授权的接收器。

认证使用从公开固件中提取的实验性配件身份，无法保证未来持续可用。部分车机仍可能卡顿或无法应用图标大小设置。应用界面支持英语、简体中文、阿拉伯语、俄语和西班牙语。源代码、构建说明及许可证随版本提供。
