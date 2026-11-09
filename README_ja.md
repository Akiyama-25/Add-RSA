# Add RSA (Akari.Add.RSA)

> Duck ToolBox でローカル発行された RKP Keybox に AOSP RSA 証明書を高速補完し、Duck Detector の TEE 検査を完璧に通過。  
> Android TEE 認証シミュレーション向けの高速・セキュアな Keybox RSA ブレンダー。

[English](README.md) | [简体中文](README_zh-CN.md) | [繁體中文](README_zh-TW.md) | [粵語](README_zh-HK.md) | [日本語](README_ja.md)

[![Build Signed APK](https://github.com/Akiyama-25/Add-RSA/actions/workflows/build-signed-apk.yml/badge.svg)](https://github.com/Akiyama-25/Add-RSA/actions/workflows/build-signed-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![TargetSDK 37](https://img.shields.io/badge/TargetSDK-37-blue.svg)](https://developer.android.com)

---

## 📖 設計背景と動作原理 (Background & Mechanism)

近年の Android 12+ / 13+ KeyMint アーキテクチャにおいて、Google が推奨する **RKP (Remote Key Provisioning)** 仕様では、デフォルトで **ECDSA (NIST P-256)** アルゴリズムの証明書のみが発行され、RSA 証明書の発行は行われません。

**Duck ToolBox** の RKP ワークベンチを用いて端末上でローカル発行した `Local RKP.xml` には、構造上 `<Key algorithm="rsa">` ノードが含まれていません。ECDSA 自体は Google 公式のハードウェア保証チェーンを備えていますが、`teesim` で TEE をシミュレートする際：
1. Android Keystore および `Duck Detector` は包括的なハードウェア暗号機能プローブ（`Single-use EC`、`ECDH`、`RSA-PSS`、`RSA-OAEP` 等）を実行します。
2. RSA ノードが存在しない場合、Keystore 処理が例外で失敗し、TEE 異常検知アラートが発生します。

**Add RSA** は、Google 公式の正規 ECDSA 証明書チェーンとプリセットされた AOSP RSA ソフトウェア認証ルートを自動的に結合します：
- **ECDSA 証明書チェーン**: Google 公式ハードウェア認証（`Trust: Google | Security: TEE`）をそのまま維持。
- **RSA 鍵ブロック**: すべての RSA 暗号化・復号リクエストを確実に受け持ち、Keystore のデュアルアルゴリズム要件を満たします。
- **結合結果**: Duck Detector で完全な TEE グリーン（全パス）を達成します！

---

## ✨ 主な機能 (Key Features)

- **🎨 Material 3 Expressive (M3E) モダンデザイン**:
  最新の M3 Expressive 設計ガイドラインに準拠。ダイナミックカラー、スクワークル角丸カード、触覚アニメーションフィードバック、適応型ダークテーマをサポート。
- **📱 メイン画面の左右スワイプ切り替え**:
  メイン画面の 3 つのタブ（クイック補完、RSA 鍵、設定）間を指のスワイプでスムーズに切り替え可能。ボトムナビゲーションバーと双方向に同期。
- **🔙 予測型「戻る」ジェスチャー (Predictive Back Gesture)**:
  サブ画面への出入りに滑らかなトランジションアニメーションを採用。Android 14+ / AOSP 予測型戻るジェスチャーに対応し、指の動きに追従するカード縮小、角丸変形、背景パララックス表示、負値オーバーシュート防止クランプを完備。
- **📐 レスポンシブ適応型レイアウト (Adaptive Stacking)**:
  ダイアログのアクションボタンやステータス行が水平方向の画面幅をインテリジェントに検知。幅が狭い端末でもボタンが自動で上下スタック配置に切り替わり、文字の見切れやはみ出しを解消。
- **🌐 アプリ内言語切り替え**:
  システム設定に依存せず、アプリ内から**システム標準**、**简体中文**、**繁體中文**、**粵語**、**English**、**日本語**をリアルタイムに変更可能。
- **🔄 アップデート確認とチャンネル選択**:
  更新確認機能を内蔵し、**Release 正式版チャンネル**と **Action CI 自動ビルドチャンネル**を自由に選択可能。Action 版はビルド時のベース Release Tag をキャッシュし、新バージョンを的確に案内。
- **🔒 ECDSA 証明書の完全メモリ一時保持 (Zero-Disk Footprint)**:
  ユーザーの ECDSA 証明書は**一切ハードコードされず、ストレージにも保存されません**。揮発性メモリ上でのみ変換処理を行い、エクスポート完了時に直ちにメモリキャッシュを完全破棄。漏洩リスクを排除します。
- **🛡️ AES-256-GCM 静的暗号化ストレージ**:
  アプリ内に保存されるすべての RSA 証明書（内蔵 AOSP ルート証明書およびユーザーがインポートしたカスタム RSA 鍵）は、AES-256-GCM で強固に暗号化されて保管されます。
- **🚀 システム共有とファイル直接オープン**:
  `.xml` に対応した `ACTION_SEND` および `ACTION_VIEW` インテントフィルタを実装。Duck ToolBox からの共有やファイルマネージャーからのタップで即座にアプリが起動します。
- **⚙️ TargetSDK 37**:
  最新の Android 環境に完全適合し、エッジ・トゥ・エッジ（Edge-to-Edge）没入型フルスクリーン体験を提供。

---

## 📲 クイックスタート (Quick Start)

1. **Duck ToolBox** の RKP ワークベンチでローカル Keybox（`.xml`）を発行・取得します。
2. ファイルマネージャーで該当の `.xml` を選択し、**共有 -> Add RSA** をタップ（またはアプリを開いて「ECDSA Keybox を選択」）。
3. デバイス ID と証明書チェーン数を確認し、**【結合してエクスポート】** をタップします。
4. 適応型ダイアログでファイル保存、クリップボードへのコピー、またはシステム共有を選択し、MT マネージャー等で `/data/adb/teesim/keybox.xml` に上書きします。
5. 出力完了後、アプリは直ちに一時証明書キャッシュを破棄します。

---

## 🛠️ CI/CD ビルド構成 (GitHub Actions & InstaBox)

完全な自動化パイプラインを構築しており、すべてのビルドジョブはユーザーの **InstaBox** セルフホストランナー上で安全に実行されます：

- **通常ビルドワークフロー (`build-signed-apk.yml`)**:
  `main` ブランチへの push を検知して自動で InstaBox を起動し、JDK 21 と Platform SDK 37 をセットアップ。Actions Cache でキーストアを保護しながら、`Action-<実行番号>` 形式の署名済み Release APK アーティファクトを出力します。
- **Release 発行ワークフロー (`release-signed-apk.yml`)**:
  手動実行（`workflow_dispatch`）専用。実行時に指定されたタグをバージョン名に採用し、設定ファイル内の `versionCode` を自動で +1 インクリメントして署名済み APK を GitHub Releases に公開し、設定変更をリポジトリへ反映します。

---

## 📄 ライセンス (License)

本プロジェクトは [MIT ライセンス](LICENSE) に基づいて公開されています。
