# 构建指南（BUILDING）

Eta 的构建环境要求与已知坑位记录，适用于本地（含 Android 上的 aarch64 Linux 容器）与 CI 构建。

## 版本要求

- **JDK 25**：`toolchain { languageVersion = JavaLanguageVersion.of(25) }`，AGP 9.3.1 的 `targetCompatibility = VERSION_25` 强制要求
- **Android SDK**：`compileSdk = 37`（`sdkmanager --channel=3 "platforms;android-37.0"`）
- **Gradle**：wrapper 9.6.1（AGP 9.3.1 要求）
- **Kotlin**：2.4.0（buildscript 显式提升，覆盖 AGP 内置 KGP 2.2.10；miuix 0.9.3 与 Compose Multiplatform 1.11.1 依赖）

## 构建命令

```bash
./gradlew :app:assembleDebug     # Debug APK
./gradlew :app:assembleRelease   # Release APK（需要签名环境变量）
```

产物：`app/build/outputs/apk/debug/app-debug.apk`、`app/build/outputs/apk/release/app-release.apk`

## Release 签名

`app/build.gradle.kts` 检测以下环境变量**全部非空**才启用 release 签名：

| 变量 | 说明 |
|---|---|
| `ETA_RELEASE_STORE_FILE` | keystore 文件路径 |
| `ETA_RELEASE_STORE_PASSWORD` | store 密码 |
| `ETA_RELEASE_KEY_ALIAS` | key 别名 |
| `ETA_RELEASE_KEY_PASSWORD` | key 密码 |

缺失时 `assembleRelease` 仍可构建，但产物为**未签名** APK（signingConfig 不生效）。

## 本地 aarch64（ARM64）环境的两个坑

Eta 在 ARM64 设备（如 Android 手机的 Linux 容器）上可以完整构建，但有两个已知问题：

1. **aapt2 需要 qemu**：SDK 内置 aapt2 是 x86_64 二进制，在 aarch64 上直接运行会失败，导致资源处理阶段 `BUILD FAILED`（与代码无关）。需要安装 `qemu-user`（提供 `qemu-x86_64`）才能完成构建。
2. **Robolectric 不支持 aarch64**：`org.robolectric:robolectric:4.16.1` 的 native runtime 不支持 `Linux aarch64`。全量 638 个单测中有 171 个必然以同一错误失败：

   ```
   java.lang.AssertionError: The Robolectric native runtime is not supported on Linux (aarch64)
   ```

   这是架构限制，不是代码问题；这些测试只能在 x86_64 上运行。

## 单元测试策略

- 全量单测（638 个，含 Robolectric）只在 x86_64 环境有意义：本地 aarch64 无法运行 Robolectric，CI 上全量执行约需 90 分钟，对发布没有增量价值。
- 因此 **CI 不跑单测**，只构建 Debug 与 Release APK；需要跑测试时在 x86_64 环境手动执行 `./gradlew :app:testDebugUnitTest`。
- 非 Robolectric 的纯 JVM 测试（如 UI 状态映射、协议校验）在本地 aarch64 可直接运行验证。

## 常见错误速查

| 现象 | 原因 | 处理 |
|---|---|---|
| `BUILD FAILED` 于资源处理阶段 | aapt2（x86_64 二进制）无法运行 | 安装 qemu-user，或改用 x86_64 环境 |
| `The Robolectric native runtime is not supported...` | aarch64 架构限制 | 换 x86_64 环境跑测试 |
| `error: invalid source release: 25` | JDK 低于 25 | 安装 JDK 25 |
| `assembleRelease` 产物未签名 | 缺少 `ETA_RELEASE_*` 环境变量 | 补齐四个签名变量 |
