//
// Created by 31830 on 2025/12/19.
//

#ifndef PANORAMAPRO_FEATUREMATCHER_H
#define PANORAMAPRO_FEATUREMATCHER_H
#include <opencv2/opencv.hpp>
#include <vector>
#include <string>

class FeatureMatcher {
public:
    FeatureMatcher();
    ~FeatureMatcher() = default;

    // 执行特征提取和匹配
    // 输出: pts1 (image1 中的点), pts2 (image2 中的点)
    void Run(const cv::Mat& image1, const cv::Mat& image2,
             std::vector<cv::Point2f>& pts1,
             std::vector<cv::Point2f>& pts2);

private:
    void FeatureDetect(const cv::Mat& image1, const cv::Mat& image2);
    void Match();
    void SelectGoodMatches(std::vector<cv::Point2f>& pts1, std::vector<cv::Point2f>& pts2);

private:
    cv::Ptr<cv::Feature2D> detector;

    std::vector<cv::KeyPoint> kpt1, kpt2;
    cv::Mat des1, des2;
    std::vector<cv::DMatch> good_matches;

    const float good_ratio = 0.7f;
};
#endif //PANORAMAPRO_FEATUREMATCHER_H
