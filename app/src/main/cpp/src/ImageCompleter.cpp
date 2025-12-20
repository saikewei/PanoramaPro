//
// Created by 31830 on 2025/12/20.
//

#include "ImageCompleter.h"
#include "Constants.h"
#include "Logger.h" // 假设你之前定义了 Log 宏


cv::Mat ImageCompleter::createMask(const cv::Mat& img) {
    cv::Mat gray, mask;

    // 1. 转灰度
    if (img.channels() == 3) {
        cv::cvtColor(img, gray, cv::COLOR_BGR2GRAY);
    } else if (img.channels() == 4) {
        cv::cvtColor(img, gray, cv::COLOR_BGRA2GRAY);
    } else {
        gray = img.clone();
    }

    // 2. 生成基础掩码
    // 逻辑：全景图中，像素值几乎为 0 的地方认为是无效区域
    // 阈值设为 1，只要不是纯黑，都算有效内容
    // THRESH_BINARY_INV: 大于 1 的变 0 (黑)，小于等于 1 的变 255 (白，即 Mask)
    cv::threshold(gray, mask, 1, 255, cv::THRESH_BINARY_INV);

    // 3. 关键步骤：膨胀掩码 (Dilation)
    // 让 Mask 向有效图像区域“侵蚀”几个像素，
    // 这样 Inpaint 算法才能读取到边缘的颜色信息，而不是读取到黑边的信息
    int kernelSize = 5;
    cv::Mat kernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(kernelSize, kernelSize));
    cv::dilate(mask, mask, kernel);

    return mask;
}

cv::Mat ImageCompleter::process(const cv::Mat& source) {
    if (source.empty()) return {};

    LOGI("开始生成补全掩码...");
    cv::Mat mask = createMask(source);

    // 检查 Mask 是否全黑（说明原图没有黑边，不需要补全）
    if (cv::countNonZero(mask) == 0) {
        LOGI("图像完整，无需补全");
        return source.clone();
    }

    LOGI("开始执行 Telea 补全算法...");
    cv::Mat result;

    // cv::INPAINT_TELEA : 基于快速行进法 (FMM)，效果较好，速度适中
    // cv::INPAINT_NS    : 基于纳维-斯托克斯方程 (流体力学)，速度较慢但对长线条恢复较好
    cv::inpaint(source, mask, result, Constants::INPAINT_RADIUS, cv::INPAINT_TELEA);

    LOGI("补全完成");
    return result;
}

