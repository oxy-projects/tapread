# TapRead · 点读

<p align="center">
  <strong>English</strong> | <a href="./README.zh-CN.md">简体中文</a>
</p>

<p align="center">
  <a href="https://github.com/DLWangSan/tapread/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/DLWangSan/tapread?style=flat-square"></a>
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/github/license/DLWangSan/tapread?style=flat-square"></a>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%208%2B-green?style=flat-square">
  <img alt="API" src="https://img.shields.io/badge/minSdk-26-blue?style=flat-square">
</p>

<p align="center">
  <b>Tap anywhere you can copy — TapRead speaks it aloud.</b><br/>
  A lightweight Android reader for elders and anyone who finds on-screen text hard to read.
  No OCR. Uses the system Text-to-Speech engine.
</p>

<p align="center">
  <img src="./images/screenshot-home.jpg" alt="TapRead home" width="280">
  &nbsp;
  <img src="./images/screenshot-voice.jpg" alt="TapRead voice settings" width="280">
</p>

## Why TapRead

Android 10+ blocks background apps from reading the clipboard. Care Mode in WeChat can read chats but not Moments. TapRead keeps one simple habit:

> If you cannot read it — copy it, then listen.

## Features

- **Android 9 and below** — copy → auto read
- **Android 10+** — copy → tap the floating ball, or enable Accessibility for auto read
- System TTS with speech rate / pitch and a shortcut into system voice settings
- Skips very short text and simple verification-code patterns
- TapRead does not upload clipboard text; speech is delegated to a trusted system TTS engine using an offline voice by default (explicit system compatibility mode may use network voices)

## Modes

| Android version | Recommended mode |
| --- | --- |
| ≤ 9 | Clipboard monitor — copy and listen |
| ≥ 10 | Floating ball (default) + optional Accessibility auto-read |

## Install

1. Download the latest APK from [Releases](https://github.com/DLWangSan/tapread/releases/latest)
2. Install on Android 8.0+ (API 26+)
3. Grant **Display over other apps**, then start the floating ball
4. (Optional) Enable the **TapRead** Accessibility service for copy-to-read

> After updating Accessibility-related builds, turn the TapRead service **off and on again** in system settings.

## Quick start

1. Copy text in WeChat / browser / any app  
2. Tap the green speaker ball → hear it  
3. Tap again while speaking to stop  

## Build

```bash
./gradlew :app:assembleRelease
# or debug build
./gradlew :app:assembleDebug
```

Requirements: JDK 17, Android SDK Platform 37 (37.0), SDK Build Tools 36.0.0. Gradle 9.3.1 + AGP 9.1.1; Kotlin support is built into AGP.

## Privacy

- Clipboard content is used only for on-device speech
- No account or analytics SDK; speech is delegated to the configured system TTS engine
- Accessibility mode only watches copy-related events / clipboard changes

## Star History

<!-- star-history:start -->
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="assets/star-history/star-history-dark.svg">
  <img alt="Star history" src="assets/star-history/star-history-light.svg">
</picture>
<!-- star-history:end -->

## License

[MIT](./LICENSE) © DLWangSan


## 本地优化说明

- 不再记录剪贴板正文或复制通知正文；标记为敏感的剪贴板默认不朗读；可在「隐私与朗读」开启手动朗读例外，自动朗读始终跳过。
- 默认只选择标记为离线的中文声音。系统能试听但应用无法确认离线元数据时，可手动开启「系统兼容模式（可能联网）」；该模式使用系统声音，是否联网由独立语音引擎决定。
- 长文本按句分段，并保护 emoji 的 UTF-16 字符边界。
- 悬浮按钮扩大为 72dp，等待窗口获得焦点后读取，拖动位置限制在屏幕内。
- 主界面新增「朗读复制的文字」，无需悬浮窗或无障碍权限即可手动朗读。
- Release 使用本机 `.signing/release.properties` 配置的独立密钥签名；该目录已忽略，需妥善备份其中的密钥和配置。未配置时生成未签名 Release，测试也可用 `assembleDebug`。

文本分段回归测试（需 Kotlin 编译器和 JDK）：

```sh
kotlinc app/src/main/java/com/dlwangsan/tapread/TextCleaner.kt tests/TextCleanerTest.kt -include-runtime -d /tmp/tapread-text-test.jar
java -jar /tmp/tapread-text-test.jar
```

仍需在目标手机验证：离线中文声音、微信复制触发、悬浮窗焦点、锁屏及服务关闭后行为。离线标记由引擎自行声明，不能代替对引擎自身的安全审查。

目标系统：ColorOS 16.0.8 / Android 16，`targetSdk = 36`；编译使用 `compileSdk = 37`，最低支持版本保持 API 26。仍需目标手机真机验证。
