//
// Created by 31830 on 2025/12/19.
//

#include "Utils.h"
#include "Logger.h"
#include <numeric>
#include <android/bitmap.h>

std::vector<Eigen::Vector2d> Utils::CV2Eigen(const std::vector<cv::Point2f>& cv_pts) {
    std::vector<Eigen::Vector2d> eigen_pts;
    eigen_pts.reserve(cv_pts.size());
    for (const auto& p : cv_pts) {
        eigen_pts.emplace_back(p.x, p.y);
    }
    return eigen_pts;
}

void Utils::Normalise2dPts(const std::vector<cv::Point2f>& src_pts,
                           std::vector<cv::Point2f>& dst_pts,
                           Eigen::Matrix3d& T) {
    if (src_pts.empty()) return;

    // 1. 计算质心 (Centroid)
    double mean_x = 0.0, mean_y = 0.0;
    for (const auto& p : src_pts) {
        mean_x += p.x;
        mean_y += p.y;
    }
    mean_x /= src_pts.size();
    mean_y /= src_pts.size();

    // 2. 计算与质心的平均距离 (Average Distance)
    double mean_dist = 0.0;
    for (const auto& p : src_pts) {
        mean_dist += std::sqrt(std::pow(p.x - mean_x, 2) + std::pow(p.y - mean_y, 2));
    }
    mean_dist /= src_pts.size();

    // 3. 计算缩放因子，使得平均距离为 sqrt(2)
    double scale = std::sqrt(2) / mean_dist;

    // 4. 构建变换矩阵 T
    // T = [scale,   0,   -scale * mean_x]
    //     [  0,   scale, -scale * mean_y]
    //     [  0,     0,          1       ]
    T.setIdentity();
    T(0, 0) = scale;
    T(1, 1) = scale;
    T(0, 2) = -scale * mean_x;
    T(1, 2) = -scale * mean_y;

    // 5. 应用变换得到归一化点集
    dst_pts.clear();
    dst_pts.reserve(src_pts.size());
    for (const auto& p : src_pts) {
        double nx = scale * p.x - scale * mean_x;
        double ny = scale * p.y - scale * mean_y;
        dst_pts.emplace_back((float)nx, (float)ny);
    }
}

void Utils::ResizeImageIfTooLarge(cv::Mat& img, int max_size) {
    int h = img.rows;
    int w = img.cols;
    if (h * w > max_size) {
        double ratio = std::sqrt((double)max_size / (h * w));
        int new_h = (int)(h * ratio);
        int new_w = (int)(w * ratio);
        cv::resize(img, img, cv::Size(new_w, new_h));
    }
}

std::vector<cv::Point2f> Utils::GetMeshVertices(int width, int height, int mesh_cols, int mesh_rows, double offset_x, double offset_y) {
    std::vector<cv::Point2f> vertices;
    vertices.reserve(mesh_cols * mesh_rows);

    double step_x = (double)width / mesh_cols;
    double step_y = (double)height / mesh_rows;

    // mesh_vertices 存储的是网格的中心点
    // x = linspace(0, w, mesh), next_x = x + w/2mesh
    for (int i = 0; i < mesh_rows; ++i) {
        for (int j = 0; j < mesh_cols; ++j) {
            double cx = j * step_x + step_x / 2.0;
            double cy = i * step_y + step_y / 2.0;
            // 减去 offset 以对齐到 Image 1 坐标系
            vertices.emplace_back((float)(cx - offset_x), (float)(cy - offset_y));
        }
    }
    return vertices;
}

cv::Mat Utils::bitmapToMat(JNIEnv *env, jobject bitmap) {
    // 1. 空指针检查
    if (bitmap == nullptr) {
        return {};
    }

    AndroidBitmapInfo info;
    void* pixels = nullptr;

    // 2. 获取信息并验证
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("无法获取 Bitmap 信息");
        return {};
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap 格式必须是 RGBA_8888");
        return {};
    }

    // 3. 锁定像素
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("锁定像素失败");
        return {};
    }

    // 4. 转换数据 (RGBA -> BGR)
    // CV_8UC4 对应 Android 的 RGBA_8888
    cv::Mat src(int(info.height), int(info.width), CV_8UC4, pixels);
    cv::Mat dst;

    // 执行深拷贝转换，dst 拥有自己的内存
    cv::cvtColor(src, dst, cv::COLOR_RGBA2BGR);

    // 5. 解锁像素
    AndroidBitmap_unlockPixels(env, bitmap);

    // 注意：这里不执行 env->DeleteLocalRef(bitmap)
    // 引用管理的责任归还给调用者

    return dst;
}

jobject Utils::matToBitmap(JNIEnv *env, const cv::Mat &src) {
    if (src.empty()) return nullptr;

    // 1. 颜色转换 BGR -> RGBA
    cv::Mat result_rgba;
    cv::cvtColor(src, result_rgba, cv::COLOR_BGR2RGBA);

    // 2. 获取 Bitmap 类和 createBitmap 方法
    // 提示：频繁调用时，jclass 和 jmethodID 最好提升为全局变量缓存
    jclass bitmapCls = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMethod = env->GetStaticMethodID(bitmapCls, "createBitmap",
                                                          "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    jclass configCls = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID configField = env->GetStaticFieldID(configCls, "ARGB_8888",
                                                 "Landroid/graphics/Bitmap$Config;");
    jobject config = env->GetStaticObjectField(configCls, configField);

    // 3. 创建 Bitmap 对象
    jobject newBitmap = env->CallStaticObjectMethod(bitmapCls, createBitmapMethod,
                                                    result_rgba.cols, result_rgba.rows, config);

    if (newBitmap == nullptr) {
        LOGE("创建输出 Bitmap 失败");
        return nullptr;
    }

    // 4. 将 Mat 数据填充到 Bitmap
    void* resultPixels;
    if (AndroidBitmap_lockPixels(env, newBitmap, &resultPixels) < 0) {
        LOGE("锁定输出 Bitmap 像素失败");
        return nullptr;
    }

    // 利用 memcpy 或者 OpenCV 的 copyTo
    // 注意处理 stride (步长)，虽然 createBitmap 通常是紧凑的，但为了稳健可以用 Mat 包装
    cv::Mat dst_wrapper(result_rgba.rows, result_rgba.cols, CV_8UC4, resultPixels);
    result_rgba.copyTo(dst_wrapper);

    AndroidBitmap_unlockPixels(env, newBitmap);

    return newBitmap;
}

cv::Mat Utils::BlendImages(const cv::Mat& img1, const cv::Mat& img2) {
    CV_Assert(img1.size() == img2.size() && img1.type() == img2.type());
    int h = img1.rows;
    int w = img1.cols;

    // 1. 生成掩码 (Mask) - 识别非黑区域
    cv::Mat mask1, mask2;
    cv::cvtColor(img1, mask1, cv::COLOR_BGR2GRAY);
    cv::threshold(mask1, mask1, 0, 255, cv::THRESH_BINARY);

    cv::cvtColor(img2, mask2, cv::COLOR_BGR2GRAY);
    cv::threshold(mask2, mask2, 0, 255, cv::THRESH_BINARY);

    // 2. 计算权重 (距离变换 Distance Transform)
    // 像素距离边缘越远，权重越大。这能保证图像中心清晰，边缘平滑过渡。
    cv::Mat weight1, weight2;
    cv::distanceTransform(mask1, weight1, cv::DIST_L2, 3);
    cv::distanceTransform(mask2, weight2, cv::DIST_L2, 3);

    // 3. 并行加权融合
    cv::Mat result = cv::Mat::zeros(h, w, CV_8UC3);

    cv::parallel_for_(cv::Range(0, h), [&](const cv::Range& range) {
        for (int y = range.start; y < range.end; ++y) {
            const auto p1 = img1.ptr<cv::Vec3b>(y);
            const auto p2 = img2.ptr<cv::Vec3b>(y);
            const float* w1 = weight1.ptr<float>(y);
            const float* w2 = weight2.ptr<float>(y);
            auto dst = result.ptr<cv::Vec3b>(y);

            for (int x = 0; x < w; ++x) {
                float val1 = w1[x];
                float val2 = w2[x];
                float sum = val1 + val2;

                if (sum > 1e-5) { // 避免除以零
                    float alpha = val1 / sum;
                    float beta = val2 / sum;

                    // 线性混合: pixel = p1 * w1% + p2 * w2%
                    dst[x][0] = (uchar)(float(p1[x][0]) * alpha + float(p2[x][0]) * beta);
                    dst[x][1] = (uchar)(float(p1[x][1]) * alpha + float(p2[x][1]) * beta);
                    dst[x][2] = (uchar)(float(p1[x][2]) * alpha + float(p2[x][2]) * beta);
                }
                // else: sum == 0, 保持黑色背景
            }
        }
    });

    return result;
}

