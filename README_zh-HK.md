# Add RSA (Akari.Add.RSA)

> 幫 Duck ToolBox 本地出嘅 RKP Keybox 快速補齊 AOSP RSA 證書，完美過骨 Duck Detector TEE 檢測。  
> 專為 Android TEE 模擬打造，又快又穩陣嘅 Keybox 混合補全工具。

[English](README.md) | [简体中文](README_zh-CN.md) | [繁體中文](README_zh-TW.md) | [粵語](README_zh-HK.md) | [日本語](README_ja.md)

[![Build Signed APK](https://github.com/Akiyama-25/Add-RSA/actions/workflows/build-signed-apk.yml/badge.svg)](https://github.com/Akiyama-25/Add-RSA/actions/workflows/build-signed-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![TargetSDK 37](https://img.shields.io/badge/TargetSDK-37-blue.svg)](https://developer.android.com)

---

## 📖 背後原理同設計機制 (Background & Mechanism)

喺現代 Android 12+ / 13+ 嘅 KeyMint 架構下，Google 推行嘅 **RKP（Remote Key Provisioning，遠端密鑰配置）** 規範預設淨係會批 **ECDSA（NIST P-256）** 算法嘅證書，唔會再批 RSA 證書。

當你用 **Duck ToolBox** 嘅 RKP 工作台喺本地向 Google 申領 Keybox 個陣，導出嚟嘅 `Local RKP.xml` 天生就無咗個 `<Key algorithm="rsa">` 節點。雖然 ECDSA 本身有 Google 官方嘅硬件級信任鏈背書，但當你配合 `teesim` 模擬 TEE 嗰陣：
1. Android Keystore 同埋 `Duck Detector` 會做全套硬件密碼學能力測試（包埋 `Single-use EC`、`ECDH`、`RSA-PSS`、`RSA-OAEP` 等等）；
2. 一無咗 RSA 節點就會搞到 Keystore 調用出事拋 Exception，即刻響起 TEE 異常警報。

**Add RSA** 自動將 Google 官方真材實料嘅 ECDSA 證書鏈，同預先擺入去嘅 AOSP RSA 軟件認證根合體混合：
- **ECDSA 證書鏈**：保留官方背書，提供真實硬件 TEE 根同設備認證（`Trust: Google | Security: TEE`）；
- **RSA 密鑰塊**：包底處理所有 RSA 加解密請求，滿足 Keystore 雙算法嘅硬性要求；
- **合體結果**：喺 Duck Detector 入面完美過關，TEE 綠燈全開！

---

## ✨ 核心功能 (Key Features)

- **🎨 Material 3 Expressive (M3E) 現代化介面**：
  跟足最新 M3 Expressive 設計標準，支援動態取色（Dynamic Color）、圓角 Card、自動適應深色模式同埋震動回饋效果。
- **📱 主畫面左右掃滑順暢換頁**：
  主頁面三大分區（快脆補全、RSA 金鑰、設定）全面支援手指左右輕輕掃滑換頁，同底部 Navigation Bar 完美無縫同步。
- **🔙 二級頁面與預測性返回手勢 (Predictive Back Gesture)**：
  入去同出返二級頁面都有順滑轉場動畫，全套支援 Android 14+ / AOSP 預測性返回手勢：即時跟手縮放、動態圓角變形透出底層主畫面，絕無負數彈跳閃退問題。
- **📐 彈性自動排版 (Adaptive Stacking)**：
  彈窗掣同資訊欄全部內建自適應寬度感應；當畫面唔夠位嗰陣，操作掣會自動由橫排轉成上下疊埋一齊，字唔會再俾掣擠壓切斷。
- **🌐 獨立語言設定**：
  App 內置獨立語言選擇，隨時即刻轉**跟返系統**、**简体中文**、**繁體中文**、**粵語**、**English**、**日本語**，無須重啟手機。
- **🔄 更新檢查與渠道自由揀選**：
  內建更新檢測功能，可以自由揀**Release 正式版渠道**定係**Action CI 自動構建渠道**。Action 版會自動快取當時構建嘅 Release Tag，精準提醒新版推出。
- **🔒 ECDSA 零落盤保證 (Zero-Disk Footprint)**：
  你嘅 ECDSA 證書**絕對唔會寫死喺程式入面，更加唔會 Save 喺本地儲存**。證書淨係會喺 RAM 記憶體暫態處理，合體兼導出完即刻全自動自毀卸載，徹底杜絕洩漏風險。
- **🛡️ 全面 AES-256-GCM 靜態加密儲存**：
  App 入面所有儲存嘅 RSA 證書（包括內置 AOSP 軟件根同你自己入嘅自訂 RSA）全部都經過 AES-256-GCM 高強度密文加密存儲。
- **🚀 系統分享與點開檔案即開即用**：
  專門配置咗支援 `.xml` 嘅 `ACTION_SEND` 同 `ACTION_VIEW` Filter，無論由 Duck ToolBox 直接 Share 入嚟定喺檔案總管一撳，即刻自動打開準備補全。
- **⚙️ TargetSDK 37**：
  深度適配最新 Android 系統，享受全屏沉浸（Edge-to-Edge）極致體驗。

---

## 📲 點樣用 (Quick Start)

1. 喺 **Duck ToolBox** 嘅 RKP 工作台攞到你部機嘅本地 Keybox（`.xml`）。
2. 喺檔案管理員揀中個 `.xml`，撳 **分享 -> Add RSA**（或者直接開本 App 撳「揀 ECDSA Keybox」）。
3. 睇下 Device ID 同證書鏈層級啱唔啱，跟住撳 **【合體兼匯出】**。
4. 喺自適應彈窗度揀直接 Save 做檔案、Copy 到剪貼簿，或者經系統分享掉去 MT 管理器直接覆蓋 `/data/adb/teesim/keybox.xml`。
5. 導出完畢之後，App 會即刻清走所有暫存嘅 ECDSA 記憶體。

---

## 🛠️ CI/CD 自動構建架構 (GitHub Actions & InstaBox)

項目配齊自動化流水線，所有編譯任務都交畀用家自己嘅 **InstaBox** Self-hosted Runner 行：

- **普通構建工作流 (`build-signed-apk.yml`)**：
  監聽 `main` 分支 Push，自動叫醒 InstaBox 虛擬機，配置 JDK 21 同 Platform SDK 37，透過 GitHub Actions Cache 持久保存簽名 Key，版本號命名為 `Action-<工作流編號>`，產出有簽名嘅 Release APK Artifact。
- **Release 發布工作流 (`release-signed-apk.yml`)**：
  設定為淨係畀手動觸發（`workflow_dispatch`）。觸發嗰陣自動拎你輸入嘅 tag 做版本號，自動將設定檔入面嘅 `versionCode` 加 1，編譯簽名發布去 GitHub Releases 並將最新設定寫返入 Git 倉庫。

---

## 📄 開源許可 (License)

本項目採用 [MIT 許可證](LICENSE) 開源。
