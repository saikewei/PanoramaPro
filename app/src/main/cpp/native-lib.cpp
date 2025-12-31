#include <jni.h>
#include <string>
#include <vector>
#include <opencv2/opencv.hpp>
#include <android/bitmap.h>
#include "APAP.h"
#include "Utils.h"
#include "ImageCompleter.h"
#include "onnxruntime_cxx_api.h"
#include "LaMaInpainter.h"
#include "SIFT.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_panoramapro_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_panoramapro_core_APAPStitcher_nativeStitchImages(
        JNIEnv* env,
        jobject /* this */,
        jobjectArray bitmaps,
        jboolean enable_linear_blending) {

    // --- 1. 循环读取图片 ---
    int count = env->GetArrayLength(bitmaps);
    if (count < 2) return nullptr;

    std::vector<cv::Mat> images;
    images.reserve(count);

    for (int i = 0; i < count; i++) {
        // A. 获取数组元素 (这会创建一个 Local Reference)
        jobject bitmap = env->GetObjectArrayElement(bitmaps, i);

        // B. 调用刚才封装的单张转换函数
        cv::Mat img = Utils::bitmapToMat(env, bitmap);

        if (!img.empty()) {
            images.push_back(img);
        }

        // C. 手动释放局部引用
        env->DeleteLocalRef(bitmap);
    }

    if (images.size() < 2) return nullptr;

    // --- 2. 执行算法 ---
    APAP apap;
    if (!apap.Load_image(std::move(images))) {
        return nullptr;
    }

    cv::Mat result = apap.Stitching(enable_linear_blending == JNI_TRUE);

    // --- 3. 输出转换 ---
    return Utils::matToBitmap(env, result);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_panoramapro_core_OpencvCompleter_nativeCompleteImage(
        JNIEnv* env,
        jobject /* this */,
        jobject bitmap) {
    // 1. Bitmap -> Mat
    cv::Mat src = Utils::bitmapToMat(env, bitmap);
    if (src.empty()) {
        return nullptr;
    }

    // 2. 调用补全逻辑
    cv::Mat completed = ImageCompleter::process(src);
    if (completed.empty()) {
        return nullptr;
    }
    // 3. Mat -> Bitmap
    return Utils::matToBitmap(env, completed);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_panoramapro_OnnxEnvironment_checkRuntime(
        JNIEnv* env,
        jobject /* this */) {

    std::string result;

    try {
        // 1. 验证链接：调用静态方法获取版本号
        // 如果链接失败，这里甚至进不来，会直接报 UnsatisfiedLinkError
        std::string version = Ort::GetVersionString();

        // 2. 验证运行：尝试创建一个 ONNX 环境 (Env)
        // 这一步验证 .so 是否能正常初始化
        Ort::Env ort_env(ORT_LOGGING_LEVEL_WARNING, "CheckEnv");

        result = "Success! ONNX Version: " + version;

    } catch (const std::exception& e) {
        // 捕获 C++ 异常 (比如架构不匹配导致的初始化失败)
        result = "Error: " + std::string(e.what());
    } catch (...) {
        result = "Error: Unknown exception";
    }

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_panoramapro_core_LaMaCompleter_nativeInit(
        JNIEnv* env, jobject, jstring modelPath) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    auto* inpainter = new LaMaInpainter();
    bool success = inpainter->init(path, LaMaInpainter::EP_NNAPI);

    env->ReleaseStringUTFChars(modelPath, path);

    if (success) {
        return reinterpret_cast<jlong>(inpainter);
    } else {
        delete inpainter;
        return 0;
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_panoramapro_core_LaMaCompleter_nativeProcess(
        JNIEnv* env, jobject, jlong handle, jobject bitmap) {

    auto* inpainter = reinterpret_cast<LaMaInpainter*>(handle);
    if (!inpainter) return nullptr;

    // 1. Bitmap -> Mat (BGR)
    cv::Mat src = Utils::bitmapToMat(env, bitmap);

    // 2. Run LaMa
    cv::Mat dst = inpainter->process(src);

    // 3. Mat -> Bitmap
    return Utils::matToBitmap(env, dst);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_panoramapro_core_LaMaCompleter_nativeRelease(
        JNIEnv* env, jobject, jlong handle) {

    auto* inpainter = reinterpret_cast<LaMaInpainter*>(handle);
    delete inpainter;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_panoramapro_core_SIFTStitcher_nativeStitchImages(
        JNIEnv* env,
        jobject /* this */,
        jobjectArray bitmaps,
        jboolean enable_linear_blending) {

    // --- 1. 循环读取图片 ---
    int count = env->GetArrayLength(bitmaps);
    if (count < 2) return nullptr;

    std::vector<cv::Mat> images;
    images.reserve(count);

    for (int i = 0; i < count; i++) {
        // A. 获取数组元素 (这会创建一个 Local Reference)
        jobject bitmap = env->GetObjectArrayElement(bitmaps, i);

        // B. 调用刚才封装的单张转换函数
        cv::Mat img = Utils::bitmapToMat(env, bitmap);

        if (!img.empty()) {
            images.push_back(img);
        }

        // C. 手动释放局部引用
        env->DeleteLocalRef(bitmap);
    }

    if (images.size() < 2) return nullptr;

    // --- 2. 执行算法 ---
    SIFT stitcher;
    if (!stitcher.Load_image(std::move(images))) {
        return nullptr;
    }

    cv::Mat result = stitcher.Stitching(enable_linear_blending == JNI_TRUE);

    // --- 3. 输出转换 ---
    return Utils::matToBitmap(env, result);
}
