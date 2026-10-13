# 吉利能力适配审查分支

本次是 0.2.18-geely.2（versionCode 37002）源码候选，不替换已发布的 v0.2.18-geely.1，也不是 2025 星越 L 的实车兼容认证。基于已合并的 SYSTEM 语言默认值与部分上游 0.2.19 音频修复继续适配，并非全量 0.2.19 升级。

## 改动与默认行为

- 语言恢复上游“跟随系统”。保留用户已保存的语言，以及 Android 13+ 已存在的应用语言。旧版本自动写入的中文无法与主动选择区分，不会擅自清空。
- 恢复官方 0.2.18 的吉利 InputService 方向盘桥接，并增加服务访问检查、会话代数、取消重试和晚到回调拒绝。自动选择范围收窄到上游已报告的 `geely_fx11`、`CS11_A4`、DesaySV、Android 11 组合，仍须服务可用。未知硬件默认不绑定；高级设置中的实验开关可明确选择，下次 CarPlay 连接生效。
- 蓝牙音频模式只订阅 Siri 私有码，避免与 AVRCP 重复。标准前台媒体键与私有事件需要当前 CarPlay 会话，通话期间让给通话处理；重复来源的同一动作去重。
- Siri 麦源独立为自动、标准 VOICE_RECOGNITION、兼容 VOICE_COMMUNICATION。自动仅对已知硬件使用兼容源，未知硬件使用标准源。下次 Siri 请求读取设置。普通电话上行、音频模式和回声消除路径保留。
- OEM 电话音频状态不是普通 CarPlay 电话语音。原上游的额外 Telecom 后端面向 BYD，尚无经验证的吉利实现，本次不重新启用。诊断明确报告缺少后端。纯控制器仅在需要的通话真实获得活动后端时，才允许交出应用侧回声消除责任。
- 屏幕诊断只读取应用可见显示器的 ID、尺寸、density、rotation、flags 和状态，不把“三屏”拼成超宽画布，也不从名称推断原厂角色。仪表、中控、副驾与 AR-HUD 是不同对象。
- 高级设置可临时手动选择 Android 对应用公开的副显示器，尝试普通 Presentation 导航提示。不获取额外悬浮权限，不切换原厂仪表模式，不输出整幅地图。没有候选、创建失败、断开或显示器移除均安全关闭；导航过期清除。屏幕可见不代表它是物理 HUD，选择成功不代表车辆适配完成。

## ADB 的边界

ADB 是 Android 的通用调试工具，不是 BYD 专属。当前应用内传输尚未实现；这不代表车辆或车主电脑不支持 ADB。诊断只读取系统 ADB 设置（无权限时显示未知），不会把“设置开启”当成“已连接/已授权”。不生成或读取 ADB 私钥、不连接、不执行 shell、不自动授予权限或修改系统设置。

车主在明确授权的环境可自行收集以下只读证据；本次迭代没有执行这些命令：

```sh
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.product.device
adb shell getprop ro.build.version.release
adb shell dumpsys display
adb shell dumpsys window displays
```

请在分享结果前移除设备序列号、账号和其他私人信息。不要为排查问题公开 ADB 到网络。

## 仍需实车证据

1. 完整固件与硬件标识是否匹配已知组合；InputService 是否存在、导出、允许绑定、注册成功。
2. 播放/暂停/上下曲/Siri 长按、短按原车语音竞争、蓝牙音频双触发、通话让键、停用后无回调。媒体去重不覆盖所有 Siri 入口；如固件同时发送私有长按回调与前台语音键，仍须单独确认是否重复唤起 Siri，不能把媒体键去重测试视为此项通过。
3. Siri 与电话分别采集的来源、错误和帧统计；不需要上传录音。安静环境不会被自动判定为麦源故障。
4. 当前窗口尺寸、安全区、density、触摸对齐；副屏是否应用可见及是否允许 Presentation。
5. 若物理 HUD 不公开为 Android 副屏，需要厂商导航服务、权限、签名及协议证据。开启 BYD 协议不能代替吉利适配。

## 参考源码

- 官方 0.2.18：[GeelyWheelKeys](https://github.com/shihabal3amri/DiPlay/blob/e8d23603ccf5aa9c32ad5c999656c5fb492c83e6/common/src/main/java/com/shilapi/xcertplay/GeelyWheelKeys.kt)、[吉利贡献 PR #626](https://github.com/shihabal3amri/DiPlay/pull/626)
- 可见副屏导航的研究参考：[carlito GeelyHudProjection](https://github.com/carlito12345/DiPlay/blob/505d34a2d6995e7194d645e96bbd1a5769135b7a/common/src/main/java/com/shilapi/xcertplay/GeelyHudProjection.kt)。本候选采用普通 Presentation，不采用其 GD 车辆桥或 OEM 控制。
- [Android ADB 文档](https://developer.android.com/tools/adb)
