---
sidebar_position: 2
title: Environment Setup
description: Tools and environment configuration needed before development
---

# Environment Setup

This document details all tools and their installation methods required to set up the Evatar development environment.

---

## Python 3.11+

The backend is developed with Python 3.11. Version 3.11 or higher is recommended.

### Installation

```bash
# Ubuntu/Debian
sudo apt update && sudo apt install python3.11 python3.11-venv python3-pip

# macOS (Homebrew)
brew install python@3.11

# Arch Linux
sudo pacman -S python python-pip
```

### Verify

```bash
python3 --version
# Expected: Python 3.11.x or higher

python3 -m venv --help
# Confirm venv module is available
```

### Create Virtual Environment

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
python --version  # Confirm version is correct after activation
```

:::tip
It is strongly recommended to use a virtual environment to isolate project dependencies and avoid conflicts with system Python packages.
:::

---

## Node.js 22+ and pnpm

The frontend is built with Vite 8 + React 19 + TypeScript 6, requiring Node.js 22 or higher.

### Install Node.js

```bash
# Using nvm (recommended)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.0/install.sh | bash
source ~/.bashrc
nvm install 22
nvm use 22

# Or using system package manager
# Ubuntu: curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash - && sudo apt install nodejs
# Arch: sudo pacman -S nodejs npm
```

### Install pnpm

```bash
# Via corepack (built into Node.js)
corepack enable
corepack prepare pnpm@latest --activate

# Or install directly
npm install -g pnpm
```

### Verify

```bash
node --version
# Expected: v22.x.x or higher

pnpm --version
# Expected: 9.x.x or higher
```

---

## Android Studio and SDK

The Android client is built with Jetpack Compose, requiring Android Studio and SDK API 34.

### Install Android Studio

1. Download the latest stable version from [developer.android.com/studio](https://developer.android.com/studio)
2. After installation, open it and go to **SDK Manager** (Settings -> Languages & Frameworks -> Android SDK)
3. Install the following components:
   - **SDK Platforms**: Android 14 (API 34)
   - **SDK Tools**: Android SDK Build-Tools, Android SDK Platform-Tools

### Verify

```bash
# Confirm SDK path
echo $ANDROID_HOME
# Expected: /Users/xxx/Library/Android/sdk (macOS)
#          or /home/xxx/Android/Sdk (Linux)

# List installed SDK versions
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --list_installed
```

---

## JDK 17

Android Gradle Plugin and Kotlin compilation require JDK 17. `build.gradle.kts` explicitly specifies `jvmTarget = "17"`.

### Installation

```bash
# Ubuntu/Debian (Eclipse Temurin)
sudo apt install openjdk-17-jdk

# macOS (Homebrew)
brew install --cask temurin@17

# Arch Linux
sudo pacman -S jdk17-openjdk
```

### Verify

```bash
java -version
# Expected: openjdk version "17.x.x"

javac -version
# Expected: javac 17.x.x
```

:::note
Android Studio comes with its own JDK, but command-line builds (`./gradlew`) require system JDK 17. If using Android Studio's built-in JDK, set `JAVA_HOME` to point to Studio's JDK path.
:::

### Set JAVA_HOME

```bash
# Linux (Temurin)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# macOS (Temurin via Homebrew)
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home

# Write to shell config file for persistence
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk' >> ~/.zshrc
```

---

## ADB (Android Debug Bridge)

Used to install APKs to physical devices, view logs, etc.

### Installation

ADB is installed with Android SDK Platform-Tools. If Android Studio is already installed, ADB is included.

```bash
# Standalone installation
# Ubuntu: sudo apt install android-tools-adb
# macOS: brew install android-platform-tools
# Arch: sudo pacman -S android-tools
```

### Verify

```bash
adb version
# Expected: Android Debug Bridge version 1.0.xx

# Check after connecting device
adb devices
# Expected:
# List of devices attached
# XXXXXXXX    device
```

### Enable USB Debugging

On the Android device:
1. Go to **Settings -> About Phone**, tap **Build Number** 7 times to enable developer mode
2. Go to **Settings -> System -> Developer Options**
3. Enable **USB Debugging**
4. Connect via USB to computer, tap "Allow" when the authorization dialog appears

---

## Git

### Installation

```bash
# Ubuntu/Debian
sudo apt install git

# macOS
xcode-select --install

# Arch Linux
sudo pacman -S git
```

### Verify

```bash
git --version
# Expected: git version 2.x.x

# Configure user info (if not configured)
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

---

## Environment Checklist

Before starting development, run the following commands to confirm all tools are ready:

```bash
echo "=== Environment Check ==="
python3 --version          # Python 3.11+
node --version             # v22+
pnpm --version             # 9+
java -version              # 17.x
git --version              # 2.x+
adb version                # 1.0.xx
```

Once all versions meet requirements, proceed to **[First Run](./first-run.md)**.
