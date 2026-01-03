//
// Created by 31830 on 2025/12/31.
//

#ifndef PANORAMAPRO_SIFT_H
#define PANORAMAPRO_SIFT_H

#include <opencv2/opencv.hpp>
#include <opencv2/features2d.hpp>
#include <vector>
#include <iostream>

class SIFT {
public:
    SIFT() = default;
    ~SIFT() = default;

    /**
     * @brief 加载图像 (使用移动语义减少拷贝)
     * @param _images 图像列表
     * @return 是否加载成功
     */
    bool Load_image(std::vector<cv::Mat>&& _images);

    /**
     * @brief 进行图像拼接
     * @param enable_linear_blending 是否启用线性融合（去除拼缝）
     * @return 拼接结果图像
     */
    cv::Mat Stitching(bool enable_linear_blending = false);

private:
    std::vector<cv::Mat> images;

    /**
     * @brief 内部核心函数：拼接两张图片
     * @param img1 基准图片 (左图)
     * @param img2 待拼接图片 (右图)
     * @return 拼接后的中间结果
     */
    static cv::Mat StitchTwoImages(const cv::Mat& img1, const cv::Mat& img2, bool enable_linear_blending);
};


#endif //PANORAMAPRO_SIFT_H
