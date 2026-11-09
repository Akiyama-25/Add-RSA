# Add RSA (Akari.Add.RSA)

> 为 Duck ToolBox 本地签发的 RKP Keybox 快速补全 AOSP RSA 证书，完美通过 Duck Detector TEE 检测。  
> 专为 Android TEE 模拟打造的高速、安全 Keybox 混合补全工具。

[English](README.md) | [简体中文](README_zh-CN.md) | [繁體中文](README_zh-TW.md) | [粵語](README_zh-HK.md) | [日本語](README_ja.md)

[![Build Signed APK](https://github.com/Akiyama-25/Add-RSA/actions/workflows/build-signed-apk.yml/badge.svg)](https://github.com/Akiyama-25/Add-RSA/actions/workflows/build-signed-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![TargetSDK 37](https://img.shields.io/badge/TargetSDK-37-blue.svg)](https://developer.android.com)

---

## 📖 核心原理与设计背景 (Background & Mechanism)

在现代 Android 12+ / 13+ KeyMint 体系下，Google 推行的 **RKP（Remote Key Provisioning，远程密钥配置）** 规范默认仅签发 **ECDSA（NIST P-256）** 算法证书，不再签发 RSA 证书。

当用户通过 **Duck ToolBox** 的 RKP 工作台在本地向 Google 申请签发 Keybox 时，导出的 `Local RKP.xml` 天生缺少 `<Key algorithm="rsa">` 节点。虽然 ECDSA 拥有合法的 Google 硬件级信任链，但在配合 `teesim` 模拟 TEE 时：
1. Android Keystore 及 `Duck Detector` 会进行全面的硬件密码学能力探针（包含 `Single-use EC`、`ECDH`、`RSA-PSS`、`RSA-OAEP` 等）；
2. 缺失 RSA 节点会导致 Keystore 操作失败并抛出异常，触发 TEE 异常报警。

**Add RSA** 将真实的 Google 官方 ECDSA 证书链与预置的 AOSP RSA 软件认证根自动混合：
- **ECDSA 证书链**：维持官方背书，提供真实的硬件 TEE 根与设备认证（`Trust: Google | Security: TEE`）；
- **RSA 密钥块**：垫底承接所有 RSA 加解密调用，满足 Keystore 双算法硬性要求；
- **混合结果**：在 Duck Detector 中完美点亮 TEE 绿灯！

---

## ✨ 核心特性 (Key Features)

- **🎨 Material 3 Expressive (M3E) 现代化界面**：
  采用最新 M3 Expressive 设计规范，支持动态色彩（Dynamic Color）、圆角卡片、自适应暗色模式与触觉动效反馈。
- **📱 主页面左右滑动切换**：
  主界面三大分区（快捷补全、RSA 密钥、偏好设置）全面支持手指左右滑动顺滑翻页，并与底部导航栏实时平滑联动。
- **🔙 二级页面与预见性返回手势 (Predictive Back Gesture)**：
  进出二级页面具备平滑转场动画，全链路支持 Android 14+ / AOSP 预见性返回手势：实时跟手缩放、动态圆角形变透出底层页面，防负向超调安全截断。
- **📐 响应式自适应布局 (Adaptive Stacking)**：
  所有弹窗操作按钮与状态行均集成自适应宽度检测；当水平宽度受限时，操作按钮自动由横向排布平滑切换为上下堆叠排布，告别文字挤压与溢出截断。
- **🌐 独立语言切换**：
  内置应用专属语言设置，支持**跟随系统**、**简体中文**、**繁體中文**、**粵語**、**English**、**日本語**即时热切换。
- **🔄 更新检测与更新渠道自由选择**：
  内置更新检测模块，用户可自由选择切换 **Release 正式版渠道** 或 **Action CI 自动构建渠道**。Action 构建版自动缓存编译时基准 Release Tag，智能提示新版发布。
- **🔒 ECDSA 凭证零落盘保护 (Zero-Disk Footprint)**：
  用户的 ECDSA 证书**绝不硬编码、绝不写入持久化存储**。凭证仅在运行内存中瞬态参与拼装，合成导出后内存立即自毁卸载，杜绝凭据泄漏风险。
- **🛡️ 全量 AES-256-GCM 静态存储加密**：
  应用内保存的所有 RSA 凭据（内置 AOSP 软件根与用户导入的自定义 RSA 密钥）在存储状态下强制采用 AES-256-GCM 高强度密文存储。
- **🚀 系统分享与文件直开**：
  配置了针对 `.xml` 的 `ACTION_SEND` 与 `ACTION_VIEW` 过滤器，无论是通过 Duck ToolBox 直接分享还是文件管理器点击打开，均可即刻调起混合处理。
- **⚙️ TargetSDK 37**：
  面向最新 Android 系统深度适配，提供边缘到边缘（Edge-to-Edge）全面屏体验。

---

## 📲 使用说明 (Quick Start)

1. 在 **Duck ToolBox** 的 RKP 工作台签发获取本地 Keybox（`.xml`）。
2. 在文件管理器中选中该 `.xml` 文件，点击 **分享 -> Add RSA**（或直接打开应用点击“选择 ECDSA Keybox”）。
3. 检查设备 ID 与证书层级无误后，点击 **【混合并导出】**。
4. 在自适应弹窗中选择直接保存为文件、复制到剪贴板，或通过系统分享发送至 MT 管理器 / 文件管理器并覆盖至 `/data/adb/teesim/keybox.xml`。
5. 导出完成后，应用自动清空临时证书缓存。

---

## 🛠️ CI/CD 构建架构 (GitHub Actions & InstaBox)

项目配置了完整的自动化流水线，所有编译任务均由用户的 **InstaBox** 自托管运行器执行：

- **普通构建工作流 (`build-signed-apk.yml`)**：
  监听 `main` 分支推送，自动唤醒 InstaBox 实例，配置 JDK 21 与 Platform SDK 37，通过 GitHub Actions Cache 持久化签名密钥，版本号格式化为 `Action-<工作流编号>` 并输出带签名的 Release APK Artifact。
- **Release 发布工作流 (`release-signed-apk.yml`)**：
  设定为仅限手动触发（`workflow_dispatch`）。触发时自动将用户指定的 tag 作为版本号，读取配置并自动将 `versionCode` 递增 1，编译签名 APK 发布至 GitHub Releases 并回写仓库。

---

## 📄 开源许可 (License)

本项目遵循 [MIT 许可证](LICENSE) 开源。
