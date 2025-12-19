//
// Created by 31830 on 2025/12/19.
//

#include "Utils.h"
#include <numeric>

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
