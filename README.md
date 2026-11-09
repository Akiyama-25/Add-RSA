# Add RSA (Akari.Add.RSA)

> Quickly blend AOSP RSA certificates into locally provisioned RKP Keyboxes from Duck ToolBox to pass Duck Detector TEE attestation simulation.  
> Fast, Secure Keybox RSA Blender for Android TEE Attestation Simulation.

[English](README.md) | [简体中文](README_zh-CN.md) | [繁體中文](README_zh-TW.md) | [粵語](README_zh-HK.md) | [日本語](README_ja.md)

[![Build Signed APK](https://github.com/Akiyama-25/Add-RSA/actions/workflows/build-signed-apk.yml/badge.svg)](https://github.com/Akiyama-25/Add-RSA/actions/workflows/build-signed-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![TargetSDK 37](https://img.shields.io/badge/TargetSDK-37-blue.svg)](https://developer.android.com)

---

## 📖 Background & Mechanism

Under modern Android 12+ / 13+ KeyMint architecture, Google's **RKP (Remote Key Provisioning)** standard provisions only **ECDSA (NIST P-256)** algorithm certificates by default and deprecates provisioning of RSA certificates.

When users request a local Keybox via the RKP Workbench in **Duck ToolBox**, the exported `Local RKP.xml` naturally lacks a `<Key algorithm="rsa">` block. Although ECDSA provides a legitimate Google hardware root of trust, when combined with `teesim` for TEE simulation:
1. Android Keystore and `Duck Detector` perform comprehensive hardware cryptographic probes (including `Single-use EC`, `ECDH`, `RSA-PSS`, `RSA-OAEP`, etc.);
2. The absence of an RSA node causes Keystore operations to fail and raise exceptions, triggering TEE anomaly alerts.

**Add RSA** automatically blends official Google ECDSA certificate chains with the embedded AOSP RSA software attestation root:
- **ECDSA Certificate Chain**: Preserves official Google hardware backing and device attestation (`Trust: Google | Security: TEE`);
- **RSA Key Block**: Serves as a baseline fallback for all RSA encryption/decryption requests, satisfying Keystore dual-algorithm requirements;
- **Result**: Perfectly achieves green TEE pass status in Duck Detector!

---

## ✨ Key Features

- **🎨 Material 3 Expressive (M3E) Modern UI**:
  Built strictly with M3 Expressive design principles: dynamic color, rounded cards, tactile animation feedback, and adaptive dark mode.
- **📱 Horizontal Swipe Page Transitions**:
  Seamlessly swipe left and right across all primary tabs (Quick Blend, RSA Keys, Settings) with bidirectional synchronisation to the bottom navigation bar.
- **🔙 Predictive Back Gestures**:
  Fluid secondary screen transitions featuring full Android 14+ / AOSP predictive back gestures: real-time finger-following card scaling, corner morphing, parallax background reveal, and zero-overshoot safety clamping.
- **📐 Responsive Adaptive Layout (Adaptive Stacking)**:
  Dialog action buttons and card details intelligently sense horizontal space constraints; when width is restricted, action rows smoothly stack vertically to prevent text clipping.
- **🌐 In-App Per-App Language Switcher**:
  Dedicated in-app language settings supporting immediate runtime switching between **System Default**, **Simplified Chinese**, **Traditional Chinese**, **Cantonese (粵語)**, **English**, and **Japanese (日本語)**.
- **🔄 Update Checker & Channel Selection**:
  Check for updates with flexible channel selection: **Release (Stable)** or **Action (CI Build)**. Action builds automatically cache the baseline Release tag to detect newer releases.
- **🔒 Zero-Disk Footprint for ECDSA**:
  Your personal ECDSA certificates are **never hardcoded and never persisted to storage**. ECDSA data exists solely in volatile RAM during assembly and is purged immediately upon export.
- **🛡️ AES-256-GCM Storage Encryption**:
  All stored RSA credentials (the built-in AOSP software root and imported custom RSA keys) are encrypted with AES-256-GCM at rest.
- **🚀 System Share Sheet & Direct File Open**:
  Equipped with intent filters for `.xml` (`ACTION_SEND` and `ACTION_VIEW`), supporting instant import directly from Duck ToolBox or file managers.
- **⚙️ TargetSDK 37**:
  Fully optimized for modern Android versions with edge-to-edge system bar integration.

---

## 📲 Quick Start

1. Provision a local Keybox (`.xml`) using **Duck ToolBox**'s RKP Workbench.
2. In your file manager, select the `.xml` file and tap **Share -> Add RSA** (or launch the app and tap "Select ECDSA Keybox").
3. Verify the device ID and certificate chain levels, then tap **[Blend & Export]**.
4. In the responsive dialog, save directly to storage, copy to clipboard, or share to MT Manager / file manager and overwrite `/data/adb/teesim/keybox.xml`.
5. Upon export completion, transient memory cache is automatically purged.

---

## 🛠️ CI/CD Build Architecture (GitHub Actions & InstaBox)

The project includes an automated pipeline where all build operations run on the user's **InstaBox** self-hosted runner:

- **Continuous Integration Workflow (`build-signed-apk.yml`)**:
  Triggers on pushes to `main`, wakes the InstaBox runner, sets up JDK 21 and Platform SDK 37, builds a signed APK versioned as `Action-<run_number>`, and uploads signed APK artifacts.
- **Release Publishing Workflow (`release-signed-apk.yml`)**:
  Configured strictly for manual invocation (`workflow_dispatch`). Automatically adopts the input tag as the release version, increments `versionCode` in the project configuration by 1, builds and publishes the signed release APK to GitHub Releases, and commits the updated `versionCode` back to the repository.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
