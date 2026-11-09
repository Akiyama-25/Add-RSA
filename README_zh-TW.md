# Add RSA (Akari.Add.RSA)

> 為 Duck ToolBox 本機簽發的 RKP Keybox 快速補全 AOSP RSA 證書，完美通過 Duck Detector TEE 檢測。  
> 專為 Android TEE 模擬打造的高速、安全 Keybox 混合補全工具。

[English](README.md) | [简体中文](README_zh-CN.md) | [繁體中文](README_zh-TW.md) | [粵語](README_zh-HK.md) | [日本語](README_ja.md)

[![Build Signed APK](https://github.com/Akiyama-25/Add-RSA/actions/workflows/build-signed-apk.yml/badge.svg)](https://github.com/Akiyama-25/Add-RSA/actions/workflows/build-signed-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![TargetSDK 37](https://img.shields.io/badge/TargetSDK-37-blue.svg)](https://developer.android.com)

---

## 📖 核心原理與設計背景 (Background & Mechanism)

在現代 Android 12+ / 13+ KeyMint 架構下，Google 推行的 **RKP（Remote Key Provisioning，遠端金鑰配置）** 規範預設僅簽發 **ECDSA（NIST P-256）** 演算法證書，不再簽發 RSA 證書。

當使用者透過 **Duck ToolBox** 的 RKP 工作台在本機向 Google 申請簽發 Keybox 時，導出的 `Local RKP.xml` 天生缺少 `<Key algorithm="rsa">` 節點。雖然 ECDSA 擁有合法的 Google 硬體級信任鏈，但在配合 `teesim` 模擬 TEE 時：
1. Android Keystore 及 `Duck Detector` 會進行全方位的硬體密碼學能力探針（包含 `Single-use EC`、`ECDH`、`RSA-PSS`、`RSA-OAEP` 等）；
2. 缺失 RSA 節點會導致 Keystore 操作失敗並擲出例外，觸發 TEE 異常警報。

**Add RSA** 將真實的 Google 官方 ECDSA 證書鏈與預置的 AOSP RSA 軟體認證根自動混合：
- **ECDSA 證書鏈**：維持官方背書，提供真實的硬體 TEE 根與裝置認證（`Trust: Google | Security: TEE`）；
- **RSA 金鑰塊**：墊底承接所有 RSA 加解密呼叫，滿足 Keystore 雙演算法硬性要求；
- **混合結果**：在 Duck Detector 中完美點亮 TEE 綠燈！

---

## ✨ 核心特性 (Key Features)

- **🎨 Material 3 Expressive (M3E) 現代化介面**：
  採用最新 M3 Expressive 設計規範，支援動態色彩（Dynamic Color）、圓角卡片、自適應深色模式與觸覺動效回饋。
- **📱 主頁面左右滑動切換**：
  主介面三大分區（快捷補全、RSA 金鑰、偏好設定）全面支援手指左右滑動順暢翻頁，並與底部導覽列即時平滑連動。
- **🔙 二級頁面與預見性返回手勢 (Predictive Back Gesture)**：
  進出二級頁面具備平滑轉場動畫，全鏈路支援 Android 14+ / AOSP 預見性返回手勢：即時跟手縮放、動態圓角形變透出底層頁面，防負向超調安全截斷。
- **📐 響應式自適應佈局 (Adaptive Stacking)**：
  所有彈窗操作按鈕與狀態行均整合自適應寬度檢測；當水平寬度受限時，操作按鈕自動由橫向排布平滑切換為上下堆疊排布，告別文字擠壓與溢出截斷。
- **🌐 獨立語言切換**：
  內建應用專屬語言設定，支援**跟隨系統**、**简体中文**、**繁體中文**、**粵語**、**English**、**日本語**即時熱切換。
- **🔄 更新檢測與更新渠道自由選擇**：
  內建更新檢測模組，使用者可自由選擇切換 **Release 正式版渠道** 或 **Action CI 自動構建渠道**。Action 構建版自動快取編譯時基準 Release Tag，智慧提示新版發布。
- **🔒 ECDSA 憑證零落盤保護 (Zero-Disk Footprint)**：
  使用者的 ECDSA 證書**絕不硬編碼、絕不寫入持久化儲存**。憑證僅在執行記憶體中暫態參與拼裝，合成導出後記憶體立即自毀卸載，杜絕憑證外洩風險。
- **🛡️ 全量 AES-256-GCM 靜態儲存加密**：
  應用內保存的所有 RSA 憑證（內建 AOSP 軟體根與使用者匯入的自訂 RSA 金鑰）在儲存狀態下強制採用 AES-256-GCM 高強度密文儲存。
- **🚀 系統分享與檔案直開**：
  配置了針對 `.xml` 的 `ACTION_SEND` 與 `ACTION_VIEW` 過濾器，無論是透過 Duck ToolBox 直接分享還是檔案管理器點擊開啟，均可即刻調起混合處理。
- **⚙️ TargetSDK 37**：
  面向最新 Android 系統深度適配，提供邊緣到邊緣（Edge-to-Edge）全螢幕體驗。

---

## 📲 使用說明 (Quick Start)

1. 在 **Duck ToolBox** 的 RKP 工作台簽發獲取本機 Keybox（`.xml`）。
2. 在檔案管理器中選取該 `.xml` 檔案，點擊 **分享 -> Add RSA**（或直接開啟應用點擊「選擇 ECDSA Keybox」）。
3. 檢查裝置 ID 與證書層級無誤後，點擊 **【混合並導出】**。
4. 在自適應彈窗中選擇直接儲存為檔案、複製至剪貼簿，或透過系統分享發送至 MT 管理器 / 檔案管理器並覆蓋至 `/data/adb/teesim/keybox.xml`。
5. 導出完成後，應用自動清空臨時證書快取。

---

## 🛠️ CI/CD 構建架構 (GitHub Actions & InstaBox)

專案配置了完整的自動化流水線，所有編譯任務均由使用者的 **InstaBox** 自託管執行器執行：

- **普通構建工作流 (`build-signed-apk.yml`)**：
  監聽 `main` 分支推送，自動喚醒 InstaBox 實例，配置 JDK 21 與 Platform SDK 37，透過 GitHub Actions Cache 持久化簽名金鑰，版本號格式化為 `Action-<工作流編號>` 並輸出帶簽名的 Release APK Artifact。
- **Release 發布工作流 (`release-signed-apk.yml`)**：
  設定為僅限手動觸發（`workflow_dispatch`）。觸發時自動將使用者指定的 tag 作為版本號，讀取設定並自動將 `versionCode` 遞增 1，編譯簽名 APK 發布至 GitHub Releases 並回寫倉庫。

---

## 📄 開源許可 (License)

本專案遵循 [MIT 許可證](LICENSE) 開源。
