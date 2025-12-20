#include <jni.h>
#include <string>
#include <vector>
#include <opencv2/opencv.hpp>
#include <android/bitmap.h>
#include "APAP.h"

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

    // 1. 解析输入 Bitmap 数组
    int count = env->GetArrayLength(bitmaps);
    if (count < 2) return nullptr;

    std::vector<cv::Mat> images;
    images.reserve(count);

    for (int i = 0; i < count; i++) {
        jobject bitmap = env->GetObjectArrayElement(bitmaps, i);
        AndroidBitmapInfo info;
        void* pixels = nullptr;

        if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
            env->DeleteLocalRef(bitmap); continue;
        }
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
            env->DeleteLocalRef(bitmap); continue;
        }
        if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
            env->DeleteLocalRef(bitmap); continue;
        }

        cv::Mat src(int(info.height), int(info.width), CV_8UC4, pixels);
        cv::Mat img;
        // RGBA -> BGR (深拷贝)
        cv::cvtColor(src, img, cv::COLOR_RGBA2BGR);

        if (!img.empty()) images.push_back(img);

        AndroidBitmap_unlockPixels(env, bitmap);
        env->DeleteLocalRef(bitmap);
    }

    if (images.size() < 2) return nullptr;

    // 2. 执行拼接
    APAP apap;
    if (!apap.Load_image(std::move(images))) return nullptr;

    cv::Mat result = apap.Stitching(enable_linear_blending == JNI_TRUE);
    if (result.empty()) return nullptr;

    // 3. 将结果转换为 Bitmap 返回给 Java
    // 3.1 BGR -> RGBA
    cv::Mat result_rgba;
    cv::cvtColor(result, result_rgba, cv::COLOR_BGR2RGBA);

    // 3.2 获取 Bitmap 类和 createBitmap 方法
    jclass bitmapCls = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMethod = env->GetStaticMethodID(bitmapCls, "createBitmap",
                                                          "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    jclass configCls = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID configField = env->GetStaticFieldID(configCls, "ARGB_8888",
                                                 "Landroid/graphics/Bitmap$Config;");
    jobject config = env->GetStaticObjectField(configCls, configField);

    // 3.3 创建新的 Java Bitmap 对象
    jobject newBitmap = env->CallStaticObjectMethod(bitmapCls, createBitmapMethod,
                                                    result_rgba.cols, result_rgba.rows, config);

    if (newBitmap == nullptr) return nullptr;

    // 3.4 填充数据
    void* resultPixels;
    if (AndroidBitmap_lockPixels(env, newBitmap, &resultPixels) < 0) {
        return nullptr;
    }

    // 获取新 Bitmap 的信息（主要是 stride/step）
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, newBitmap, &info);

    // 创建一个指向 Bitmap 内存的 Mat wrapper
    // 注意使用 info.stride，因为 Bitmap 可能会有行填充
    cv::Mat dst(int(info.height), int(info.width), CV_8UC4, resultPixels, info.stride);

    // 将数据拷贝进去
    result_rgba.copyTo(dst);

    AndroidBitmap_unlockPixels(env, newBitmap);

    return newBitmap;
}
