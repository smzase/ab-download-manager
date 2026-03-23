<div align="center">
  <a href="https://abdownloadmanager.com" target="_blank">
    <img width="180" src="assets/logo/app_logo_with_background.svg" alt="AB Download Manager Logo">
  </a>
</div>
<h1 align="center">AB Download Manager</h1>
<p align="center">
    <a href="https://github.com/amir1376/ab-download-manager/releases/latest"><img alt="GitHub Release" src="https://img.shields.io/github/v/release/amir1376/ab-download-manager?color=greenlight&label=latest%20release"></a>
    <a href="https://abdownloadmanager.com"><img alt="AB Download Manager Website" src="https://img.shields.io/badge/project-website-purple?&labelColor=gray"></a>
    <a href="https://t.me/abdownloadmanager_discussion"><img alt="Telegram Group" src="https://img.shields.io/badge/Telegram-Group-blue?logo=telegram&labelColor=gray"></a>
    <a href="https://t.me/abdownloadmanager"><img alt="Telegram Channel" src="https://img.shields.io/badge/Telegram-Channel-blue?logo=telegram&labelColor=gray"></a>
    <a href="https://crowdin.com/project/ab-download-manager"><img alt="Crowdin" src="https://badges.crowdin.net/ab-download-manager/localized.svg"></a>
</p>

<a href="https://abdownloadmanager.com" target="_blank">
    <img alt="AB Download Manager Banner" src="assets/banners/app_banner.png"/>
</a>


## 简介

[AB Download Manager](https://abdownloadmanager.com) 是一款桌面应用程序，可以帮助您更高效地管理和组织下载任务。

## 功能特点

- ⚡️ 更快的下载速度
- ⏰ 下载队列和定时任务
- 🌐 浏览器扩展集成
- 💻 多平台支持（Android / Windows / Linux / Mac）
- 🌙 多种主题（深色/浅色/纯黑等）以及现代化界面
- ❤️ 免费且开源

更多功能请访问[项目官网](https://abdownloadmanager.com)。

## 安装

### 下载并安装应用

<a href="https://abdownloadmanager.com"><img src="https://img.shields.io/badge/Official%20Website-897BFF?logo=abdownloadmanager&logoColor=fff&style=flat-square" alt="官方网站" height="32" /></a>
<a href="https://github.com/amir1376/ab-download-manager/releases/latest"><img src="https://img.shields.io/badge/GitHub%20Releases-2a2f36?logo=github&logoColor=fff&style=flat-square" alt="GitHub Releases" height="32" /></a>

#### Linux 安装脚本

```bash
bash <(curl -fsSL https://raw.githubusercontent.com/amir1376/ab-download-manager/master/scripts/install.sh)
```

#### Winget 或 Scoop（Windows）

**winget**:

```bash
winget install amir1376.ABDownloadManager
```

**scoop**:

```bash
scoop install extras/abdownloadmanager
```

#### Homebrew（macOS 和 Linux）

```bash
brew tap amir1376/tap && brew install --cask ab-download-manager
```

> ⚠️ **警告：** 本软件不在 Google Play 或其他应用商店上架，除非在此列出。任何**声称与此项目相关或隶属于本项目**的版本都应被视为**诈骗**，存在安全风险。

有关其他安装方法、卸载说明及更多详情，请参阅 [wiki](https://github.com/amir1376/ab-download-manager/wiki/) 页面。

### 浏览器扩展

您可以下载浏览器扩展来将应用与浏览器集成。

<p align="left">
<a href="https://addons.mozilla.org/firefox/addon/ab-download-manager/">
    <picture>
        <img alt="Chrome Extension" src="./assets/banners/firefox-extension.png" height="48">
    </picture>
</a>
<a href="https://chromewebstore.google.com/detail/bbobopahenonfdgjgaleledndnnfhooj">
    <picture>
        <source media="(prefers-color-scheme: dark)" srcset="./assets/banners/chrome-extension_dark.png" height="48">
        <source media="(prefers-color-scheme: light)" srcset="./assets/banners/chrome-extension_light.png" height="48">
        <img alt="Chrome Extension" src="./assets/banners/chrome-extension_light.png" height="48">
    </picture>
</a>
</p>

## Cloudflare Worker 代理支持

AB Download Manager 支持通过 Cloudflare Worker 作为代理进行下载，可以帮助您：

- 绕过网络限制
- 隐藏真实下载来源
- 获得更稳定的下载速度

### 工作原理

```
原下载链接 → Cloudflare Worker → 您的设备
```

### 部署 Cloudflare Worker

1. 访问 [Cloudflare Workers](https://dash.cloudflare.com/workers)
2. 创建新的 Worker
3. 将 `cloudflare-worker-proxy.js` 文件的内容粘贴到 Worker 编辑器中
4. 在 Worker 设置中配置环境变量 `AUTHORIZATION_KEY`（设置您的授权密钥）
5. 部署 Worker

### 在应用中配置

1. 打开应用设置 → 下载引擎 → 代理
2. 选择 "Cloudflare Worker"
3. 填写您的 Worker 域名（例如：`my-worker.my-subdomain.workers.dev`）
4. 填写您设置的授权密钥

## 截图

<div align="center">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./assets/screenshots/app-home_dark.png">
  <source media="(prefers-color-scheme: light)" srcset="./assets/screenshots/app-home_light.png">
  <img alt="App Home Section" src="./assets/screenshots/app-home_dark.png">
</picture>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./assets/screenshots/app-download_dark.png">
  <source media="(prefers-color-scheme: light)" srcset="./assets/screenshots/app-download_light.png">
  <img alt="App Download Section" src="./assets/screenshots/app-download_dark.png">
</picture>
</div>

## 项目状态与反馈

请注意，这个项目正处于发展初期。
**更多功能**即将到来！

**但是**，在此期间您可能会遇到一些 **Bug 或问题**。如果遇到问题，请通过[社区聊天](#community)或 `GitHub Issues` 向我反馈，我会尽快解决。

## 社区

您可以加入我们的 [Telegram 群组](https://t.me/abdownloadmanager_discussion)：

- 报告问题
- 提出功能建议
- 获取应用帮助

## 代码仓库

**AB Download Manager** 项目包含多个代码仓库：

| 仓库                                                                                   | 描述                                           |
|--------------------------------------------------------------------------------------|----------------------------------------------|
| [主应用程序](https://github.com/amir1376/ab-download-manager)（您在这里）              | 包含在您的 **设备** 上运行的 **应用程序**         |
| [浏览器集成](https://github.com/amir1376/ab-download-manager-browser-integration)    | 包含要安装在 **浏览器** 上的 **浏览器扩展**       |
| [网站](https://github.com/amir1376/ab-download-manager-website)                      | 包含 **AB Download Manager** [官网](https://abdownloadmanager.com) |

我为这个项目投入了大量时间和精力。

如果您喜欢我的工作，请考虑给我一个 ⭐ — 谢谢！❤️

## 问题反馈

如果您在源代码中发现任何 bug，请通过 `GitHub Issues` 报告。

## 从源码构建

要在本地编译和测试桌面应用，请按以下步骤操作：

1. 克隆项目。
2. 下载并解压 [JBR](https://github.com/JetBrains/JetBrainsRuntime/releases)，并通过以下方式使其可用：

    - 将其添加到 `PATH`，或
    - 将 `JAVA_HOME` 环境变量设置为其安装路径。

3. 进入项目目录，打开终端并执行以下命令：

    ```bash
    ./gradlew createReleaseFolderForCi
    ```

4. 编译输出位于：

    ```
    <project_dir>/build/ci-release
    ```

> **注意**：本项目通过 GitHub Actions 进行编译和发布，详情请参阅[此处](./.github/workflows/publish.yml)。

## 翻译

如果您想帮助将 AB Download Manager 翻译成其他语言，或改进现有翻译，可以通过以下方式进行：

- 访问 [Crowdin](https://crowdin.com/project/ab-download-manager) 上的项目
- 请**不要**通过拉取请求提交翻译
- 如果您想添加新语言，请参阅[此 Issue](https://github.com/amir1376/ab-download-manager/issues/144)

## 贡献

如果您想为本项目做出贡献，请先阅读[贡献指南](CONTRIBUTING.md)。

## 支持项目

如果您想支持这个项目，可以在 [DONATE.md](DONATE.md) 文件中找到捐款方式。
