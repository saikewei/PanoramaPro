# 📸 PanoramaPro 本地环境配置指南

> **注意**：本项目的 Gradle 配置、CMake 脚本及依赖逻辑已通过 Git 同步。
> 你**不需要**修改任何代码，只需手动下载 OpenCV SDK 并放置在指定位置即可运行。

---

## 🛠 1. 必需环境 (Prerequisites)

在开始之前，请确保你的 Android Studio 已安装以下组件（可在 `SDK Manager` -> `SDK Tools` 中检查）：

* **NDK (Side by side)**: 版本 **25.1.8937393** (r25c) 或 **26.1.10909125** (r26b)。
* **CMake**: 版本 **3.22.1**。

---

## 📂 2. 手动导入 OpenCV SDK (核心步骤)

由于 OpenCV SDK 体积过大（>200MB），我们**没有**将其上传到 Git 仓库。请按照以下步骤在本地配置：

### 2.1 下载
1.  访问 [OpenCV 官网下载页](https://opencv.org/releases/)。
2.  下载 **Android** 版本 4.12.0。
3.  你会得到一个 `.zip` 压缩包。

### 2.2 解压与重命名
1.  解压压缩包。
2.  找到解压后的 **`OpenCV-android-sdk`** 文件夹。
3.  将其**重命名**为：`opencv` (全小写)。

### 2.3 放置位置
将重命名后的 `opencv` 文件夹直接复制到项目的 **根目录** 下。

**✅ 正确的目录结构如下：**

```text
E:\PanoramaPro\ (你的项目根路径)
  ├── .git/
  ├── app/
  ├── gradle/
  ├── opencv/           <-- 🔴 必须叫这个名字，且放在这里
  │     └── sdk/
  │          ├── native/
  │          ├── java/
  │          └── ...
  ├── build.gradle.kts
  └── settings.gradle.kts
```
**提示: 项目已配置 .gitignore 忽略 opencv/ 目录，请不要强制将其添加到版本控制中。**

## ✅ 3. 验证与同步
1. 打开 Android Studio。

2. 点击右上角的 Sync Project with Gradle Files (大象图标)。

3. Sync 成功：如果底部 Build 面板显示 BUILD SUCCESSFUL，说明配置完成。

