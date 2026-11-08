# Add RSA (Akari.Add.RSA)

> 为 Duck ToolBox 本地签发的 RKP Keybox 快速补全 AOSP RSA 证书，完美通过 Duck Detector TEE 检测。
> Fast, Secure Keybox RSA Blender for Android TEE Attestation Simulation.

[![Build Signed APK](https://github.com/Akiyama-25/Add-RSA/actions/workflows/build-signed-apk.yml/badge.svg)](https://github.com/Akiyama-25/Add-RSA/actions/workflows/build-signed-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![TargetSDK 37](https://img.shields.io/badge/TargetSDK-37-blue.svg)](https://developer.android.com)

---

## 📖 核心原理与设计背景 (Background & Mechanism)

在现代 Android 12+ / 13+ KeyMint 体系下，Google 推行的 **RKP（Remote Key Provisioning，远程密钥配置）** 规范默认仅签发 **ECDSA（NIST P-256）** 算法证书，不再签发 RSA 证书。

当用户通过 **Duck ToolBox** 的 RKP Workbench 在本地向 Google 申请签发 Keybox 时，导出的 `Local RKP.xml` 天生缺少 `<Key algorithm="rsa">` 节点。虽然 ECDSA 拥有合法的 Google 硬件级信任链，但在配合 `teesim` 模拟 TEE 时：
1. Android Keystore 及 `Duck Detector` 会进行全面的硬件密码学能力探针（包含 `Single-use EC`、`ECDH`、`RSA-PSS`、`RSA-OAEP` 等）；
2. 缺失 RSA 节点会导致 Keystore 操作失败并抛出异常，触发 TEE 异常报警。

**Add RSA** 将真实的 Google 官方 ECDSA 证书链与预置的 AOSP RSA 软件认证根自动混合：
- **ECDSA 证书链**：维持官方背书，提供真实的硬件 TEE 根与设备认证（`Trust: Google | Security: TEE`）；
- **RSA 密钥块**：垫底承接所有 RSA 加解密调用，满足 Keystore 双算法硬性要求；
- **混合结果**：在 Duck Detector 中完美点亮 TEE 绿灯！

---

## ✨ 核心特性 (Key Features)

- **🎨 Material 3 Expressive (M3E) 设计**：
  采用最新的 M3 Expressive 设计规范，支持动态色彩（Dynamic Color）、圆角 Squircle 容器、触觉动画反馈与自适应暗色模式。
- **🚀 系统分享即时补全**：
  支持通过系统分享（Share Sheet）直接向应用传入 `.xml` 文件，自动解析 ECDSA 凭证并一键补全导出。
- **🔒 ECDSA 证书零落盘保护 (Zero-Disk Footprint)**：
  用户的 ECDSA 证书**绝不硬编码、绝不写入持久化存储**。所有凭据仅在易失性内存中暂存，导出混合证书后立即自毁卸载，杜绝泄露风险。
- **🛡️ 全量 AES-256-GCM 静态加密**：
  所有保存在应用内的 RSA 证书（包括预置编码的 AOSP 默认证书及用户导入的自定义 RSA 密钥）均通过 AES-256-GCM 算法加密存储。
- **🌐 全面多语言国际化**：
  原生支持 **简体中文**、**繁体中文（台湾/香港）**、**英语 (English)**、**日语 (日本語)**。
- **⚙️ TargetSDK 37**：
  面向最新 Android 系统深度适配，提供边缘到边缘（Edge-to-Edge）全面屏体验。

---

## 📲 使用说明 (Quick Start)

1. 在 **Duck ToolBox** 的 RKP 工作台签发获取本地 Keybox（`.xml`）。
2. 在文件管理器中选中该 `.xml` 文件，点击 **分享 -> Add RSA**（或直接打开应用点击“选择 ECDSA Keybox”）。
3. 检查设备 ID 与证书层级无误后，点击 **【混合并导出】**。
4. 可选择直接保存为文件、复制到剪贴板，或通过系统分享发送至 MT 管理器 / 文件管理器并覆盖至 `/data/adb/teesim/keybox.xml`。
5. 导出完成后，应用自动清空临时证书缓存。

---

## 🛠️ CI/CD 构建架构 (GitHub Actions & InstaBox)

项目配置了完整的自动化构建流，所有编译任务均交由 **InstaBox** 自托管运行器执行：

- **普通构建工作流 (`build-signed-apk.yml`)**：
  监听 `main` 分支推送，自动唤醒 InstaBox 实例，配置 JDK 21 与 Platform SDK 37，通过 GitHub Actions Cache 持久化签名密钥，产出带签名的 Release APK。
- **Release 发布工作流 (`release-signed-apk.yml`)**：
  监听 Git Tag 触发，自动编译、签名并发布 GitHub Release，同时上传校验哈希值与密钥备份。

---

## 📄 开源许可 (License)

本项目遵循 [MIT 许可证](LICENSE) 开源。
