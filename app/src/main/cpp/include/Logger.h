//
// Created by 31830 on 2025/12/19.
//

#ifndef PANORAMAPRO_LOGGER_H
#define PANORAMAPRO_LOGGER_H
#include <android/log.h>

// 定义 Log 标签，方便在 Logcat 中筛选
#define LOG_TAG "PanoramaPro_Native"

// 定义简化的宏，支持类似 printf 的格式化
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__) // Error (红色)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__) // Warning (黄色)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__) // Info (绿色)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__) // Debug (蓝色)
#endif //PANORAMAPRO_LOGGER_H
