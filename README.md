<div align="center">
  <a href="https://abdownloadmanager.com" target="_blank">
    <img width="180" src="assets/logo/app_logo_with_background.svg" alt="AB Download Manager 图标">
  </a>
</div>
<h1 align="center">AB 下载管理器</h1>
<p align="center">
    <a href="https://github.com/amir1376/ab-download-manager/releases/latest"><img alt="GitHub 发布版本" src="https://img.shields.io/github/v/release/amir1376/ab-download-manager?color=greenlight&label=%E6%9C%80%E6%96%B0%E5%8F%91%E5%B8%83"></a>
    <a href="https://abdownloadmanager.com"><img alt="AB Download Manager 官网" src="https://img.shields.io/badge/%E9%A1%B9%E7%9B%AE-%E5%AE%98%E7%BD%91-purple?&labelColor=gray"></a>
    <a href="https://t.me/abdownloadmanager_discussion"><img alt="Telegram 群组" src="https://img.shields.io/badge/Telegram-%E7%BE%A4%E7%BB%84-blue?logo=telegram&labelColor=gray"></a>
    <a href="https://t.me/abdownloadmanager"><img alt="Telegram 频道" src="https://img.shields.io/badge/Telegram-%E9%A2%91%E9%81%93-blue?logo=telegram&labelColor=gray"></a>
    <a href="https://crowdin.com/project/ab-download-manager"><img alt="Crowdin" src="https://badges.crowdin.net/ab-download-manager/localized.svg"></a>
</p>

<a href="https://abdownloadmanager.com" target="_blank">
    <img alt="AB Download Manager 横幅" src="assets/banners/app_banner.png"/>
</a>


## 简介

[AB 下载管理器](https://abdownloadmanager.com) 是一款桌面应用程序，帮助您比以往更高效地管理和组织下载任务。

## 功能特性

- ⚡️ 更快的下载速度
- ⏰ 下载队列和计划任务
- 🌐 浏览器扩展插件
- 💻 多平台支持（Android / Windows / Linux / Mac）
- 🌙 多种主题（深色/浅色/黑色等），现代化界面
- ☁️ Cloudflare Worker 中转支持（绕过网络限制）
- ❤️ 免费开源

请访问[项目官网](https://abdownloadmanager.com)了解更多信息。

## 安装指南

### 下载并安装应用

<a href="https://abdownloadmanager.com"><img src="https://img.shields.io/badge/%E5%AE%98%E6%96%B9%E7%BD%91%E7%AB%99-897BFF?logo=abdownloadmanager&logoColor=fff&style=flat-square" alt="官方网站" height="32" /></a>
<a href="https://github.com/amir1376/ab-download-manager/releases/latest"><img src="https://img.shields.io/badge/GitHub%20%E5%8F%91%E5%B8%83-2a2f36?logo=github&logoColor=fff&style=flat-square" alt="GitHub 发布" height="32" /></a>

#### Linux 安装脚本

```bash
bash <(curl -fsSL https://raw.githubusercontent.com/amir1376/ab-download-manager/master/scripts/install.sh)
```

#### Windows 安装（Winget 或 Scoop）

**winget**：

```bash
winget install amir1376.ABDownloadManager
```

**scoop**：

```bash
scoop install extras/abdownloadmanager
```

#### macOS 和 Linux 安装（Homebrew）

```bash
brew tap amir1376/tap && brew install --cask ab-download-manager
```

> ⚠️ **警告：** 本软件不在 Google Play 或其他应用商店上架，除非在此列出的渠道。任何**声称与本项目相关**的版本都应被视为**诈骗和不安全**。

有关其他安装方法、卸载说明和更多详细信息，请参阅 [wiki](https://github.com/amir1376/ab-download-manager/wiki/) 页面。

### 浏览器扩展

您可以下载浏览器扩展来将应用与浏览器集成。

<p align="left">
<a href="https://addons.mozilla.org/firefox/addon/ab-download-manager/">
    <picture>
        <img alt="Chrome 扩展" src="./assets/banners/firefox-extension.png" height="48">
    </picture>
</a>
<a href="https://chromewebstore.google.com/detail/bbobopahenonfdgjgaleledndnnfhooj">
    <picture>
        <source media="(prefers-color-scheme: dark)" srcset="./assets/banners/chrome-extension_dark.png" height="48">
        <source media="(prefers-color-scheme: light)" srcset="./assets/banners/chrome-extension_light.png" height="48">
        <img alt="Chrome 扩展" src="./assets/banners/chrome-extension_light.png" height="48">
    </picture>
</a>
</p>

## 截图

<div align="center">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./assets/screenshots/app-home_dark.png">
  <source media="(prefers-color-scheme: light)" srcset="./assets/screenshots/app-home_light.png">
  <img alt="应用主页" src="./assets/screenshots/app-home_dark.png">
</picture>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./assets/screenshots/app-download_dark.png">
  <source media="(prefers-color-scheme: light)" srcset="./assets/screenshots/app-download_light.png">
  <img alt="应用下载页面" src="./assets/screenshots/app-download_dark.png">
</picture>
</div>

## Cloudflare Worker 中转功能

AB 下载管理器现在支持通过 Cloudflare Worker 中转下载，这可以：

- 🚀 绕过某些网络限制
- 🔒 隐藏真实下载源
- 🌍 提高在某些地区的下载稳定性

### 使用方法

1. 在 Cloudflare 上部署 Worker 脚本（位于项目根目录的 `cloudflare-worker.js`）
2. 在 AB 下载管理器的**设置 → 下载引擎 → 使用代理**中选择 **Cloudflare Worker**
3. 输入您的 Worker URL 和可选的 API Token

详细配置说明请参考 [cloudflare-worker.js](cloudflare-worker.js) 文件中的注释。

## 项目状态与反馈

请注意，本项目正处于起步阶段，**许多功能**正在开发中！

**但是**，在此期间您可能会遇到**错误或问题**。如果遇到，请通过[社区聊天](#社区)或 `GitHub Issues` 向我报告，我会尽快修复。

## 社区

您可以加入我们的 [Telegram 群组](https://t.me/abdownloadmanager_discussion) 来：

- 报告问题
- 建议新功能
- 获取使用帮助

## 相关仓库和源代码

AB 下载管理器项目包含多个相关仓库：

| 仓库                                                                                       | 描述                                                                          |
|--------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| [主应用](https://github.com/amir1376/ab-download-manager)（您在这里）                       | 运行在您**设备**上的**应用程序**                                              |
| [浏览器集成](https://github.com/amir1376/ab-download-manager-browser-integration)           | 安装在您**浏览器**上的**浏览器扩展**                                          |
| [网站](https://github.com/amir1376/ab-download-manager-website)                             | **AB 下载管理器**[官网](https://abdownloadmanager.com) 的源代码               |

我花费了大量时间创建这个项目。

如果您喜欢我的工作，请考虑给它一个 ⭐ — 谢谢！❤️

## 错误报告

如果您在源代码中发现任何错误，请通过 `GitHub Issues` 部分报告。

## 从源代码构建

要在本地机器上编译和测试桌面应用程序，请按照以下步骤操作：

1. 克隆项目。
2. 下载并解压 [JBR](https://github.com/JetBrains/JetBrainsRuntime/releases)，并通过以下方式之一使其可用：
    
    - 将其添加到您的 `PATH`，或
    - 将 `JAVA_HOME` 环境变量设置为其安装路径。
  
3. 导航到项目目录，打开终端并执行以下命令：

    ```bash
    ./gradlew createReleaseFolderForCi
    ```

4. 输出将位于：

    ```
    <项目目录>/build/ci-release
    ```

> **注意**。本项目由 GitHub Actions 编译和发布，配置文件在[这里](./.github/workflows/publish.yml)，所以如果您遇到任何问题，也可以参考该文件。

## 翻译

如果您想帮助将 AB 下载管理器翻译成其他语言，或改进现有翻译，可以在 Crowdin 上进行。方法如下：

- 在 [Crowdin](https://crowdin.com/project/ab-download-manager) 上访问该项目
- 请**不要**通过 Pull Request 提交翻译
- 如果您想添加新语言，请参阅[此说明](https://github.com/amir1376/ab-download-manager/issues/144)

## 贡献

如果您想为这个项目做出贡献，请先阅读[贡献指南](CONTRIBUTING.md)。

## 支持项目

如果您想支持这个项目，可以在 [DONATE.md](DONATE.md) 文件中找到捐赠详情。
