//
// Created by 31830 on 2025/12/19.
//

#ifndef PANORAMAPRO_APAP_H
#define PANORAMAPRO_APAP_H
#include <opencv2/opencv.hpp>
#include <Eigen/Core>
#include <Eigen/Dense>
#include <vector>

class APAP {
public:
    APAP() = default;
    ~APAP() = default;

    /**
     * @brief 加载图像
     * @param _images 图像列表
     * @return 是否加载成功
     */
    bool Load_image(std::vector<cv::Mat>&& _images);

    /**
     * @brief 进行图像拼接
     * @return 拼接结果图像
     */
    cv::Mat Stitching(bool enable_linear_blending = false);

    /**
     * @brief 计算局部单应性矩阵 (Local Homography)
     * * @param src_pts      源图像匹配点 (归一化前)
     * @param dst_pts      目标图像匹配点 (归一化前)
     * @param mesh_vertices 网格中心点坐标 (用于计算权重) shape: [mesh_h * mesh_w]
     * @param canvas_width  画布宽度 (用于确定网格数量)
     * @param canvas_height 画布高度
     * @return std::vector<Eigen::Matrix3d> 每个网格对应的单应性矩阵 (行优先顺序: i*w + j)
     */
    static std::vector<Eigen::Matrix3d> LocalHomography(
            const std::vector<cv::Point2f>& src_pts,
            const std::vector<cv::Point2f>& dst_pts,
            const std::vector<cv::Point2f>& mesh_vertices,
            int mesh_cols, int mesh_rows);

    /**
     * @brief 局部扭曲 (Local Warp)
     * * @param image        需要扭曲的源图像 (Image 2)
     * @param local_homographies 计算好的局部单应性矩阵列表
     * @param mesh_cols    网格列数
     * @param mesh_rows    网格行数
     * @param canvas_size  最终画布大小
     * @param offset       画布偏移量 (x, y)，即 warp 后图像在画布上的左上角偏移
     * @return cv::Mat     扭曲后的图像
     */
    static cv::Mat LocalWarp(
            const cv::Mat& image,
            const std::vector<Eigen::Matrix3d>& local_homographies,
            int mesh_cols, int mesh_rows,
            cv::Size canvas_size,
            cv::Point2f offset);

private:
    std::vector<cv::Mat> images;
};
#endif //PANORAMAPRO_APAP_H
