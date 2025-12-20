//
// Created by 31830 on 2025/12/20.
//

#ifndef PANORAMAPRO_IMAGECOMPLETER_H
#define PANORAMAPRO_IMAGECOMPLETER_H

#include <opencv2/opencv.hpp>
#include <vector>


class ImageCompleter {
public:
    ImageCompleter() = default;
    ~ImageCompleter() = default;

    /**
     * 自动补全图像边缘
     * @param source 输入的原始拼接图（包含黑色/无效边缘）
     * @return 补全后的图像
     */
    static cv::Mat process(const cv::Mat& source);

private:
    // 辅助函数：生成需要补全区域的掩码
    static cv::Mat createMask(const cv::Mat& img);
};


#endif //PANORAMAPRO_IMAGECOMPLETER_H
