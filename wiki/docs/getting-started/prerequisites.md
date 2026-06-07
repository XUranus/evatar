---
sidebar_position: 2
title: 环境准备
description: 开发前需要安装的工具和环境配置
---

# 环境准备

本文详细介绍搭建 Evatar 开发环境所需的全部工具及其安装方法。

---

## Python 3.11+

后端使用 Python 3.11 开发，推荐使用 3.11 或更高版本。

### 安装

```bash
# Ubuntu/Debian
sudo apt update && sudo apt install python3.11 python3.11-venv python3-pip

# macOS (Homebrew)
brew install python@3.11

# Arch Linux
sudo pacman -S python python-pip
```

### 验证

```bash
python3 --version
# 期望输出: Python 3.11.x 或更高

python3 -m venv --help
# 确认 venv 模块可用
```

### 创建虚拟环境

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
python --version  # 确认激活后版本正确
```

:::tip
强烈推荐使用虚拟环境隔离项目依赖，避免与系统 Python 包冲突。
:::

---

## Node.js 22+ 与 pnpm

前端使用 Vite 8 + React 19 + TypeScript 6 构建，需要 Node.js 22 或更高版本。

### 安装 Node.js

```bash
# 使用 nvm (推荐)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.0/install.sh | bash
source ~/.bashrc
nvm install 22
nvm use 22

# 或使用系统包管理器
# Ubuntu: curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash - && sudo apt install nodejs
# Arch: sudo pacman -S nodejs npm
```

### 安装 pnpm

```bash
# 通过 corepack (Node.js 内置)
corepack enable
corepack prepare pnpm@latest --activate

# 或直接安装
npm install -g pnpm
```

### 验证

```bash
node --version
# 期望输出: v22.x.x 或更高

pnpm --version
# 期望输出: 9.x.x 或更高
```

---

## Android Studio 与 SDK

Android 客户端使用 Jetpack Compose 构建，需要 Android Studio 和 SDK API 34。

### 安装 Android Studio

1. 从 [developer.android.com/studio](https://developer.android.com/studio) 下载最新稳定版
2. 安装后打开，进入 **SDK Manager**（Settings -> Languages & Frameworks -> Android SDK）
3. 安装以下组件：
   - **SDK Platforms**: Android 14 (API 34)
   - **SDK Tools**: Android SDK Build-Tools, Android SDK Platform-Tools

### 验证

```bash
# 确认 SDK 路径
echo $ANDROID_HOME
# 期望输出: /Users/xxx/Library/Android/sdk (macOS)
#          或 /home/xxx/Android/Sdk (Linux)

# 查看已安装的 SDK 版本
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --list_installed
```

---

## JDK 17

Android Gradle 插件和 Kotlin 编译需要 JDK 17。`build.gradle.kts` 中明确指定了 `jvmTarget = "17"`。

### 安装

```bash
# Ubuntu/Debian (Eclipse Temurin)
sudo apt install openjdk-17-jdk

# macOS (Homebrew)
brew install --cask temurin@17

# Arch Linux
sudo pacman -S jdk17-openjdk
```

### 验证

```bash
java -version
# 期望输出: openjdk version "17.x.x"

javac -version
# 期望输出: javac 17.x.x
```

:::note
Android Studio 自带 JDK，但命令行构建 (`./gradlew`) 需要系统 JDK 17。如果使用 Android Studio 内置 JDK，需设置 `JAVA_HOME` 指向 Studio 自带的 JDK 路径。
:::

### 设置 JAVA_HOME

```bash
# Linux (Temurin)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# macOS (Temurin via Homebrew)
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home

# 写入 shell 配置文件持久化
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk' >> ~/.zshrc
```

---

## ADB (Android Debug Bridge)

用于将 APK 安装到实体设备、查看日志等。

### 安装

ADB 随 Android SDK Platform-Tools 一起安装。如果已安装 Android Studio，则 ADB 已包含在内。

```bash
# 单独安装
# Ubuntu: sudo apt install android-tools-adb
# macOS: brew install android-platform-tools
# Arch: sudo pacman -S android-tools
```

### 验证

```bash
adb version
# 期望输出: Android Debug Bridge version 1.0.xx

# 连接设备后查看
adb devices
# 期望输出:
# List of devices attached
# XXXXXXXX    device
```

### 启用 USB 调试

在 Android 设备上：
1. 进入 **设置 -> 关于手机**，连续点击 **版本号** 7 次启用开发者模式
2. 进入 **设置 -> 系统 -> 开发者选项**
3. 开启 **USB 调试**
4. 通过 USB 连接电脑，设备上弹出授权对话框时点击"允许"

---

## Git

### 安装

```bash
# Ubuntu/Debian
sudo apt install git

# macOS
xcode-select --install

# Arch Linux
sudo pacman -S git
```

### 验证

```bash
git --version
# 期望输出: git version 2.x.x

# 配置用户信息（如未配置）
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

---

## 环境检查清单

在开始开发前，运行以下命令确认所有工具已就绪：

```bash
echo "=== 环境检查 ==="
python3 --version          # Python 3.11+
node --version             # v22+
pnpm --version             # 9+
java -version              # 17.x
git --version              # 2.x+
adb version                # 1.0.xx
```

所有版本满足要求后，即可继续 **[第一次运行](./first-run.md)**。
