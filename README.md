# 📸 PanoramaPro 本地环境配置指南

> **注意**：本项目的 Gradle 配置、CMake 脚本及依赖逻辑已通过 Git 同步。
> 你**不需要**修改任何代码，只需手动下载 OpenCV SDK 并放置在指定位置即可运行。

---

## 🛠 1. 必需环境 (Prerequisites)

在开始之前，请确保你的 Android Studio 已安装以下组件（可在 `SDK Manager` -> `SDK Tools` 中检查）：

* **NDK (Side by side)**: 版本 **27.2.12479018**。
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

---

## 📐 3. 手动导入 Eigen 库 (新增步骤)

本项目使用 **Eigen** 进行矩阵运算（APAP 算法核心依赖）。它是一个纯头文件库，配置非常简单。

### 3.1 下载
1.  访问 [Eigen 官网下载页](https://gitlab.com/libeigen/eigen/-/releases).
2.  找到 **5.0.0** 版本。
3.  点击 **Source code (zip)** 下载压缩包。

### 3.2 解压与重命名
1.  解压下载的压缩包。
2.  你会看到一个名为 `eigen-5.0.0` (或类似版本号) 的文件夹。
3.  将其**重命名**为：`eigen` (全小写)。

### 3.3 放置位置与关键检查
1.  将重命名后的 `eigen` 文件夹直接复制到项目的 **根目录** 下（与 `opencv` 文件夹并列）。
2.  **⚠️ 关键检查**：双击进入你刚才放进去的 `eigen` 文件夹，确认里面直接包含一个名为 **`Eigen`** (首字母大写) 的子文件夹。

**✅ 更新后的完整目录结构如下：**

```text
E:\PanoramaPro\ (你的项目根路径)
  ├── .git/
  ├── app/
  ├── gradle/
  ├── opencv/           <-- OpenCV 目录
  │     └── sdk/
  ├── eigen/            <-- 🆕 必须叫这个名字，放在这里
  │     ├── Eigen/      <-- ⚠️ 核心头文件目录 (注意首字母大写)
  │     ├── unsupported/
  │     └── ...
  ├── build.gradle.kts
  └── settings.gradle.kts
 ```

## 🧠 4. 手动配置 ONNX Runtime 与 AI 模型 (AI 补全依赖)

本项目使用 **LaMa** 模型进行图像智能补全，依赖 **ONNX Runtime** 的 C++ 推理库。由于我们需要进行 Native (C++) 开发，不能仅通过 Gradle 依赖引入，必须手动提取库文件以获取头文件。

### 4.1 准备 ONNX Runtime 库

1.  **下载**：访问 [Maven Central - ONNX Runtime Android](https://central.sonatype.com/artifact/com.microsoft.onnxruntime/onnxruntime-android/1.17.1)。
2.  **选择文件**：点击右侧 "Download" 按钮，选择下载 **`aar`** 文件 (版本 **1.18.0**)。
3.  **解压**：
    * 将下载的 `.aar` 文件后缀名改为 `.zip`。
    * 解压该压缩包。
4.  **整理文件夹**（⚠️ 关键步骤）：
    * 在项目根目录下新建一个文件夹，命名为 **`onnxruntime`**。
    * 将解压包中的 **`headers`** 文件夹复制到 `onnxruntime` 中。
    * 将解压包中的 **`jni`** 文件夹复制到 `onnxruntime` 中。

### 4.2 添加 AI 模型文件

1.  **获取模型**：[lama_fp32.onnx](https://huggingface.co/Carve/LaMa-ONNX/blob/main/lama_fp32.onnx)。
2.  **放置位置**：将 `lama_fp32.onnx` 文件放入以下目录（如果目录不存在请手动创建）：
    * `app/src/main/assets/`

---

## ✅ 最终目录结构核对

配置完成后，你的项目根目录应包含以下关键文件夹。请务必仔细核对 **ONNX** 和 **Eigen** 的内部文件夹名称，否则 CMake 编译会报错。

```text
E:\PanoramaPro\ (项目根目录)
  ├── .git/
  ├── app/
  │     └── src/
  │          └── main/
  │               └── assets/
  │                    └── lama_fp32.onnx  <-- 📄 确认模型文件在这里
  ├── gradle/
  ├── opencv/           <-- 📁 OpenCV SDK
  │     └── sdk/
  ├── eigen/            <-- 📁 Eigen 库
  │     └── Eigen/      <-- ⚠️ 确认首字母大写
  ├── onnxruntime/      <-- 🆕 ONNX Runtime 库
  │     ├── headers/    <-- ⚠️ 里面全是 .h 头文件 
  │     └── jni/        <-- ⚠️ 里面是 arm64-v8a 等架构文件夹 
  ├── build.gradle.kts
  └── settings.gradle.kts