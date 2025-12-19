//
// Created by 31830 on 2025/12/19.
//

#ifndef PANORAMAPRO_UTILS_H
#define PANORAMAPRO_UTILS_H
#include <opencv2/opencv.hpp>
#include <Eigen/Core>
#include <Eigen/Dense>
#include <vector>

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
};
#endif //PANORAMAPRO_UTILS_H
