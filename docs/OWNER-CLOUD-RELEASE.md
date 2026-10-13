# Owner 手动云端构建：与 DiPlay-CN 相同的资产输入方式

## 当前交付状态

这是待审查的构建准备方案，不是已经生成的 APK。`.github/workflows/owner-release.yml` 是仅 owner 手动启动的入口；合并到默认分支后 GitHub 才会注册它。`docs/templates/owner-release.yml.example` 保留为内容一致的审查模板。本 PR 和合并动作不会下载官方 APK、提取真实身份、生成或导入真实签名密钥、配置 Secrets、触发签名构建或创建 Release。

源码基线固定为 [`2c0de42807458e80482bfff719936b957c069819`](https://github.com/xiaoshengwpp/DiPlay-Geely/commit/2c0de42807458e80482bfff719936b957c069819)。它集成官方 0.2.18，仍需本方案代码审查、owner 的签名输入和亲自运行才能产生候选完整 APK。普通 Android checks 只证明源码测试、lint 和无身份 debug 构建，不能证明完整 CarPlay 认证或实车可用。

## 为什么参考 CN，但不直接复制 workflow

[DiPlay-CN 的 release.yml](https://github.com/serein-morii/DiPlay-CN/blob/63b208f2e32ad3ea6b52abfc9ff1cb7169e4facc/.github/workflows/release.yml) 从官方 APK 选取 `assets/offline-mfi/identity.pk8` 与 `certificate.p7b`，再注入构建。其源码已到 0.2.18，资产来源仍固定在 0.2.14；这不等于身份来自同版本源码。

这里采用相同的“官方 APK → 外部资产目录 → 包含身份的 APK”数据流，但固定使用官方 0.2.18 的下载地址、长度和 SHA-256，拒绝其他来源或自动回退。目标分支实际任务是 `:mobile:assembleRelease`，没有 CN 的 `assembleStandaloneRelease`。不复制 CN 仓库中的公开 Android 签名库、不自动生成新签名、不沿用 tag 自动发布或 Gitee 推送。

上游说明该 APK 包含从公开 Carlinkit 固件恢复的实验性配件身份，私钥可从 APK 提取；不是为本应用新签发的 MFi 身份。后续 iOS 是否继续接受、广泛分发适用性和各车机稳定性仍未解决。这里没有作出新的许可或法律结论，也没有证明星越 L 2025 揽星实车兼容。

## 锁定的公开输入

权威锁位于 `scripts/owner_release.py` 的 `LOCK`，workflow 中的 source ref 与 URL 必须保持一致。

- 吉利构建源码：`2c0de42807458e80482bfff719936b957c069819`（PR #12 的已验证源码提交；不使用可变分支名）。
- 官方源码：`e8d23603ccf5aa9c32ad5c999656c5fb492c83e6`
- 官方 APK：[DiPlay-0.2.18.apk](https://github.com/shihabal3amri/DiPlay/releases/download/v0.2.18/DiPlay-0.2.18.apk)
- 字节数：`18170831`
- SHA-256：`e241457b36b21fe75b06b5682b4dcfc4fbc25cf75b1855c7a4d055129635ce8e`
- 吉利应用包名：`io.github.xiaoshengwpp.diplay.geely`
- 版本：`0.2.18-geely.2`；versionCode：`37002`
- 版本仍以已审查的 0.2.18 为基线；吉利后续适配与部分 0.2.19 音频移植不等于全量同步 0.2.19。源码版本必须在处理任何身份或签名输入前与发布锁一致，否则立即失败。
- ABI：`arm64-v8a`、`armeabi-v7a`、`x86_64`

如官方替换资源或上述检查不一致，构建必须失败。不要删掉校验或改用“最新 APK”；先另行审查来源与锁定值。

## 我们准备和审查的部分

1. 本 PR 提供脚本、惰性 fixture 测试、仅手动 workflow 及一致的模板。测试材料是明显的普通文本，不是生成的私钥或证书。
2. 审查和源码 CI 通过后，由维护者合并到默认分支并核对 GitHub 已注册入口；不需要 owner 手工设计流水线。合并只注册入口，不启动这个 workflow。
3. 启用后的流水线只允许仓库 `xiaoshengwpp/DiPlay-Geely` 的 owner `xiaoshengwpp` 在 `main` 分支亲自 workflow_dispatch；重跑者也必须是该 owner。没有 push、tag 或 PR 敏感构建触发器。
4. 默认 token 只有 `contents: read`，不创建 Release。构建源码 checkout 固定提交，与 workflow 工具 checkout 分开；不接受任意源码、任意下载地址或任意文件路径输入。

## Owner 必须亲自完成的最少步骤

### 1. 自行配置既有的稳定 Android 签名输入

进入[本仓库 Actions Secrets](https://github.com/xiaoshengwpp/DiPlay-Geely/settings/secrets/actions)：Settings → Secrets and variables → Actions → New repository secret。由你本人填入：

- `OWNER_ANDROID_KEYSTORE_BASE64`：你已经持有、愿意用于这个包名的稳定签名库文件的 Base64 文本。Base64 只是编码，不是加密。请在自己的可信工具中转换，不要贴到聊天、issue 或在线编码站。GitHub secret 有大小限制；过大时停下并重新设计安全输入方式。
- `ANDROID_KEYSTORE_PASSWORD`：该签名库密码
- `ANDROID_KEY_ALIAS`：既有签名 alias
- `ANDROID_KEY_PASSWORD`：该 alias 的密码

流水线将 Base64 还原到临时文件，然后设置 Gradle 要求的 `ANDROID_KEYSTORE_PATH`，因此无需你填写 runner 上不存在的路径。另三项直接对应 Gradle 环境变量。运行时目录由流程设置为 `DIPLAY_AUTH_ASSETS_DIR`，严格位于源码之外。

如果你已有这个包名使用过的稳定签名库，应继续用原来的；不要为了这次构建重新生成。不要使用 CN 公共签名库或旧的 HUD/debug 测试签名。

#### 首次发布且确实没有稳定签名库时

下面只是供你本人操作的命令说明，助手不会执行。Android 发布签名只用于 APK 的开发者身份与后续升级连续性，**它不是 CarPlay 的实验性配件身份**，生成它也不能替代 APK 中的那两个运行时资产。

你需要一个由你自己控制、已安装 JDK 的可信终端；已有的私有云终端也可以，不需要助手访问你的 Mac。这里没有替你开通任何云服务，也不假定你已有可用环境。若没有这样的终端，请先确定一个你愿意使用的环境；不要为赶进度把密钥交给公共在线生成器或随意新开付费环境。

如果没有现成云终端，可由你本人选择 GitHub Codespaces 的浏览器终端：先查看自己账号的免费额度和计费提示，再在仓库 **Code → Codespaces → Create codespace** 中创建。若页面要求开通付费、额度用尽或你不愿让 GitHub 托管签名，停在这里，不自动开通。创建后打开 Terminal，先运行 `keytool -help` 确认 JDK 可用；若不存在，先补充可信 JDK 环境再生成。不要只按 `.` 打开 github.dev，那不是可执行命令的云终端。此步骤和密钥输入均由你本人完成。

参考 GitHub 的[创建 Codespace 指南](https://docs.github.com/en/codespaces/developing-in-a-codespace/creating-a-codespace-for-a-repository?tool=webui)与[计费说明](https://docs.github.com/en/billing/concepts/product-billing/github-codespaces)。本方案未核查你的剩余额度，也未创建任何 Codespace。

仅首次、由你本人在该可信终端运行（示例使用 Linux shell，材料放在 HOME 私有目录，源码外）：

```sh
umask 077
mkdir -m 700 "$HOME/diplay-signing-private"
cd "$HOME/diplay-signing-private"
keytool -genkeypair -keystore diplay-geely-release.p12 -storetype PKCS12 \
  -alias diplay-geely-release -keyalg RSA -keysize 3072 -validity 10000
```

`keytool` 会交互询问密码与证书信息。你本人输入，不要把密码作为命令行参数，也不要发送到聊天或 CI 日志。该示例使用 PKCS12，alias 密码与库密码设为同一个；在 Secrets 中 `ANDROID_KEYSTORE_PASSWORD` 与 `ANDROID_KEY_PASSWORD` 填相同的自选密码，`ANDROID_KEY_ALIAS` 填 `diplay-geely-release`。不要再次运行命令覆盖或替换已有签名。

随后仍由你本人运行：

```sh
base64 -w 0 diplay-geely-release.p12 > owner-keystore.b64
keytool -list -v -keystore diplay-geely-release.p12 -alias diplay-geely-release
```

第一行适用于 GNU/Linux，将文件编码到当前私有目录，不会在终端打印私钥。用你可信环境的安全文件查看/复制功能，把 `owner-keystore.b64` 的完整内容填入 `OWNER_ANDROID_KEYSTORE_BASE64`；不要上传它到 GitHub 文件、Actions artifact、gist 或 issue。第二行会询问库密码并显示公开证书 SHA-256 指纹，请自行保存该公指纹，未来对照构建 manifest。

你本人将原 `.p12` 和密码安全备份到自己控制的安全存储，验证备份可用后清理临时编码副本与不再使用的临时环境。不要仅依赖 GitHub Secrets 作备份：它不是可随时取回原密钥的文件保险箱。丢失签名将影响同包名后续升级。若终端或安全备份尚不可用，签名准备仍未完成，先不要 Run workflow。

### 2. 启用并审查后，你本人点击 Run workflow

本 PR 尚未合并时没有已确认可用的专属 Run workflow 入口。只有本 PR 已合并、GitHub 确认注册 workflow 后，才可在[本仓库 Actions](https://github.com/xiaoshengwpp/DiPlay-Geely/actions) 选择 `Owner experimental release build`。

亲自勾选两项确认：

- 你选择处理官方实验性身份，并将它打包到自己的 APK。
- 你选择将这个含身份的 APK 上传到本仓库的 GitHub Actions artifact 存储。

分支选择 `main`，然后由你本人点击 Run workflow。助手不代点，不代填 Secrets。GitHub runner 会实际处理原始身份和 Android 签名库；含身份 APK 中的身份仍可提取。Artifact 存储不是“密钥仍保密”的保证，它不是私人保险箱；公开仓库中有 artifact 下载权限的其他用户可能取得此身份 APK。保留期设为 1 天，请及时取回。

### 3. 下载并核对结果，再决定后续发布

成功运行只上传三个文件：`DiPlay-0.2.18-geely.2.apk`、`SHA256SUMS.txt`、`build-manifest.json`。不上传松散身份文件、签名库、构建目录或原始日志包。

验证器先用 Android `apksigner verify` 验证 APK 签名，再记录公开签名证书 SHA-256 指纹；还核对包名、版本、固定源码提交、干净源码 checkout、恰好三 ABI、恰好两个预期身份条目、与本次提取的两文件逐字节一致及没有额外密钥文件。请把 manifest 中签名公指纹与你原有稳定签名的指纹比较；首次使用时自行保留公指纹作为以后升级的比较依据。它不证明签名库权属，也不证明身份获授权或车机认证成功。

APK 仅作为 owner 下载的候选产物。本流程没有任何 Release 上传步骤。后续公开发布含认证材料的 APK 必须由你本人另行决定并执行，同时保留适用许可证、对应源码与尚未实车验证的说明。不要重用已发布版本号或替换同标签 APK。所有安装、连接与音频/交互测试仅在安全停车后进行。

## 安全与失败行为

- ZIP 整体元数据拒绝重复路径、绝对/穿越路径、Windows 风格路径与 symlink；只读取两项白名单，大小限为每项 1 MiB。长度/摘要或条目不符时失败，绝不自动换源。
- 资产目录不得预先存在、不得在源码内、不得含 symlink、额外文件或额外目录；目录 0700，文件 0600。
- 不使用 Gradle 缓存 action、build cache、configuration cache、build scan 或调试输出；不 echo Secrets、不 dump 环境、不上传 build logs。
- 临时身份、签名库及整个构建源码 checkout（包括各模块 build/.cxx 中间输出）在 shell EXIT 和 workflow always 清理；runner 被强制终止时依赖 GitHub 临时 runner 的销毁。不能承诺平台层面安全擦除或零残留。
- 只有完整构建与验证成功才上传三个明确列出的产物；没有通配上传。
- 依赖从 Gradle/Android 正常官方仓库获取；身份下载仅固定官方 GitHub Release URL。脚本本身没有网络或自动发布功能。

## 仅合成测试

```sh
python3 -m unittest discover -s scripts -p test_owner_release.py -v
```

原有 `.github/workflows/android.yml` 会在 PR 上运行源码检查与该测试，再运行 `AGENTS.md` 要求的聚合 Gradle 检查。源 CI 通过也不意味着这个手动敏感工作流已跑过。本方案从未进行真实身份提取或签名构建，最终 APK 仍待 owner 步骤完成。

