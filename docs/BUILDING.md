```powershell
.\native\build.ps1
```

默认执行单元测试、debug lint 和 debug 构建，随后检查 APK 对齐、签名、包名与版本；产物及 SHA-256 文件写入 `dist/`。

## 环境

使用 JDK 21，将 `JAVA_HOME` 指向 JDK，将 `ANDROID_HOME` 指向 Android SDK。安装 SDK 包并接受许可：

```text
sdkmanager "platforms;android-37.0" "build-tools;36.0.0"
sdkmanager --licenses
```

使用仓库自带的 Gradle 9.5.0 Wrapper，其下载校验和已固定。也可向脚本传入 `-JavaHome` 和 `-AndroidSdk`。

Linux / macOS：

```sh
cd native
bash ./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

测试报告在 `native/app/build/reports/`，截图在 `native/app/build/test-screenshots/`。当前包名为 `com.ysy.switcherfiveg.hyperos4`，版本为 `1.1.1` / `26102003`。

## 签名 release

先在当前进程设置以下环境变量，再运行 `./native/build.ps1 -Release`。直接运行 `:app:assembleRelease` 同样需要它们；缺少任意一项会失败，不会生成未签名替代产物。

| 环境变量 | 内容 |
| --- | --- |
| `FIVEG_STORE_FILE` | 仓库外的 keystore 路径，建议使用绝对路径 |
| `FIVEG_STORE_PASSWORD` | keystore 密码 |
| `FIVEG_KEY_ALIAS` | 密钥别名 |
| `FIVEG_KEY_PASSWORD` | 密钥密码 |

release 构建还检查 v2 签名。若需核对特定证书，可加 `-ExpectedCertificateSha256 <64位证书摘要>`。保持原有安装的覆盖升级，需要使用相同签名证书。

## 可选离线验证

Robolectric 默认从 Maven 在线下载测试运行时。已有 SDK 35 测试 JAR 的本地缓存时，可指定目录：

```powershell
.\native\build.ps1 -Offline -RobolectricDir 'D:\android-test-jars'
```

`-Offline` 控制 Gradle 依赖解析，`-RobolectricDir` 单独启用 Robolectric 离线解析。直接使用 Wrapper 时，可设置 `FIVEG_ROBOLECTRIC_OFFLINE=true` 和 `FIVEG_ROBOLECTRIC_DIR`；对应 Gradle 参数为 `-Pfiveg.robolectric.offline=true -Pfiveg.robolectric.dir=<目录>`，参数优先于环境变量。首次在线构建不需要这些配置。

## GitHub Actions

PR、`main` 提交和 `v*` 标签执行测试、lint、debug 构建并上传明确标注的 debug 产物，不需要签名 secret。工作流不会自动创建 GitHub Release。

手动运行工作流并勾选 `signed_release` 才会生成经过签名验证的 release 候选产物。仓库需配置 `FIVEG_KEYSTORE_BASE64`、`FIVEG_STORE_PASSWORD`、`FIVEG_KEY_ALIAS`、`FIVEG_KEY_PASSWORD` 四个 Actions secrets；第一个是 keystore 的 Base64 内容。临时密钥仅在签名步骤使用，随后删除。

参考：[Android SDK 工具](https://developer.android.com/tools/sdkmanager)、[GitHub Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)。
