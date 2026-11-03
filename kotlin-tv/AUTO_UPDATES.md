# GitHub 自动发布与电视应用内更新

公开渠道：本仓库（`ThugKd/hongguoTV`）的 GitHub Release。首次需要手动把带更新功能的正式 APK 安装到电视，此后不用逐次传文件。

## 电视上的行为

- 前台浏览时每 6 小时检查一次最新正式版；首次进入应用延迟 8 秒，避免抢首屏。播放或离开前台会取消更新请求，返回浏览页后可继续下载。
- 默认自动检查并下载，设置 → 版本与更新可以关闭，也可以手动检查。下载失败保留当前应用；部分文件清理，自动下载重试至少间隔 30 分钟。
- 下载完显示版本信息和更新日志，选择「安装新版」。Android 6/7 需允许系统的未知来源安装，Android 8 起需为本应用开启安装未知应用授权；之后均调用系统安装确认。
- 选择「稍后」推迟该版本提醒一天；不会中断正在播放的视频。同版本、旧版本和要求更高 Android 版本的安装包不会自动安装。
- APK 写入应用私有缓存，仅临时授权系统安装程序读取。下载使用 HTTPS、文件长度和 SHA-256 校验，并验证 APK 实际包名、版本号、最低 SDK 和当前安装应用的签名（Android 6 从 APK 清单读取最低 SDK，API 24 起读取 ApplicationInfo）；不在 APK 中保存 GitHub 令牌。

## 从推送到发布（GitHub Actions）

1. 向 `main` 推送客户端改动。当前工作流仅支持手动触发，推送代码或标签不会自动发布 APK。
2. 在自己的仓库配置下述签名 Secrets；Fork 不会继承上游仓库的 Secrets，后续构建须沿用相同签名。
3. 打开 **Actions → Build and publish TV update → Run workflow**，选择 `main`。工作流恢复签名并执行 JVM 测试、Release lint 和 release 构建。
4. 构建成功后 `scripts/publish-tv-update.py` 创建 Release，上传 APK、`update.json` 和 `SHA256SUMS`，并设为 latest；GitHub 同时提供对应标签的源码下载。
5. 应用通过本仓库 Release 的 `latest/download/update.json` 获取新版本，无需 GitHub 登录。

## 本地打包后手动上传

1. 配置本机 `kotlin-tv/keystore.properties` 或签名环境变量，使用长期保留且与已安装包一致的签名。不要提交私钥或密码。
2. 例如构建本次版本：`LVGUOTV_VERSION_NAME=1.0.0 LVGUOTV_VERSION_CODE=14 ./scripts/build-kotlin.sh`。以后每个新构建都递增 versionCode。
3. 把代码提交到 GitHub，并创建、推送对应标签，例如 `v1.0.0`。
4. 打开 **Releases → Draft a new release**，选择已推送的标签，填写标题和说明，上传 APK、`update.json`、`SHA256SUMS`，确认附件全部上传成功后发布并设为 latest。
5. `update.json` 的 `tag` 必须与 Release 标签一致，包名、版本号、文件名、大小和 SHA-256 必须对应实际 APK。附件 APK 使用 `lvguotv-kotlin-1.0.0-android6.apk` 这类命名，以便应用内更新器识别。

本次首次公开的 1.0.0（versionCode 14）沿用此前本机测试签名。早期 13 及以下测试包的更新器仅识别 `kotlin-v…` 标签，首次请手动覆盖安装这个新包；同包名、同签名可保留数据。新包支持 `1.0.0`、`v1.0.0` 和旧 `kotlin-v…` 标签，后续可正常使用应用内更新。

## 签名配置

配置动作需仓库所有者授权。本仓库的 Actions Secrets：

- `LVGUOTV_KEYSTORE_B64`：自己长期维护的签名 keystore 的 Base64 编码；不能使用上游私钥，也不要在后续版本随意更换。
- `LVGUOTV_STORE_PASSWORD`、`LVGUOTV_KEY_ALIAS`、`LVGUOTV_KEY_PASSWORD`：签名配置。

CI 优先读取上述 `LVGUOTV_*` Secrets；也兼容已有的 `HONGGUOTV_KEYSTORE_B64`、`HONGGUOTV_STORE_PASSWORD`、`HONGGUOTV_KEY_ALIAS`、`HONGGUOTV_KEY_PASSWORD` Secrets，并映射到新的构建变量。该映射不会更换签名密钥，也不要求重新上传已有密钥。

签名私钥只在运行签名构建的临时目录中恢复，任务结束删除；源码和 APK 均不包含这些凭据。不要在 APK 中嵌入 PAT。

## 版本规则与本地构建

- 当前手动发布：显示版本 `1.0.0`、versionCode `14`、标签 `v1.0.0`。云端工作流仍使用 `versionCode = 10200 + release workflow run_number` 和 `kotlin-v1.0.0-build.run_number` 标签，保持后续构建编号递增。
- 发布新版本前，将 `.github/workflows/release.yml` 中的版本号（当前 1.0.0）递增，并同步 `kotlin-tv/app/build.gradle.kts` 的默认 `versionName` 与 `kotlin-tv/README.md` 构建输出示例；调整版本显示名称时仍维持 versionCode 单调递增规则。
- 已发布构建重跑会因标签已存在而失败，不会覆盖原 APK；旧构建重跑也不能取代更新的 latest。
- 本地未传环境变量时生成 1.0.0 / versionCode 1，供首次验证。复现云端源码时，应从该 Release 的 `update.json` 读取版本，传入 `LVGUOTV_VERSION_CODE` 和 `LVGUOTV_VERSION_NAME`；本地低版本号的 APK 无法覆盖较高云端版本。正式分发以云端签名包为准。

电视系统没有安装器、禁止未知来源或无法访问 GitHub 时，自动更新不能完成；旧版仍可继续使用。最低 API 23 不等于 Android 6.0 实体电视已验收。

最低版本由 APK 的 `sdkVersion` 生成到 `update.json`，避免与 Gradle 配置脱节。新包使用 `-android6.apk`，更新器同时接受历史 `-android8.apk`。旧版更新器只接受最低 API 26；首次升级到本兼容版需要手动覆盖安装同包名、同签名且 versionCode 不低于已安装版本的 APK，之后可正常应用内更新。
