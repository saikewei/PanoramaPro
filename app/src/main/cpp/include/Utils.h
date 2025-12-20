//
// Created by 31830 on 2025/12/19.
//

#ifndef PANORAMAPRO_UTILS_H
#define PANORAMAPRO_UTILS_H
#include <opencv2/opencv.hpp>
#include <Eigen/Core>
#include <Eigen/Dense>
#include <vector>
#include <jni.h>

class Utils {
public:
    // 将 OpenCV 的 Point2f 转换为 Eigen 的 Vector2d
    static std::vector<Eigen::Vector2d> CV2Eigen(const std::vector<cv::Point2f>& cv_pts);

    // 点集归一化 (Hartley's normalization)
    // 返回: 3x3 变换矩阵 T (Eigen类型)，以及归一化后的点集
    static void Normalise2dPts(const std::vector<cv::Point2f>& src_pts,
                               std::vector<cv::Point2f>& dst_pts,
                               Eigen::Matrix3d& T);

    // 简单的图片缩放逻辑，保持长宽比，限制最大像素数
    static void ResizeImageIfTooLarge(cv::Mat& img, int max_size);

    static std::vector<cv::Point2f> GetMeshVertices(int width, int height, int mesh_cols, int mesh_rows, double offset_x, double offset_y);

    /**
         * Bitmap -> cv::Mat
         * 1. 锁定像素
         * 2. 转换颜色 (RGBA -> BGR)
         * 3. 解锁像素
         * @return 转换后的 Mat (BGR格式)，如果失败返回空 Mat
         */
    static cv::Mat bitmapToMat(JNIEnv *env, jobject bitmap);

    /**
     * 将 C++ 的 cv::Mat 转换为 Java 的 Bitmap
     * 注意：会执行 BGR -> RGBA 的颜色转换
     */
    static jobject matToBitmap(JNIEnv *env, const cv::Mat& src);
};
#endif //PANORAMAPRO_UTILS_H
