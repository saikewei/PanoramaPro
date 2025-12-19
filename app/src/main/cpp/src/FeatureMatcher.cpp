//
// Created by 31830 on 2025/12/19.
//

#include "FeatureMatcher.h"
#include <iostream>

FeatureMatcher::FeatureMatcher() {
    detector = cv::SIFT::create();
}

void FeatureMatcher::FeatureDetect(const cv::Mat& image1, const cv::Mat& image2) {
    cv::Mat gray1, gray2;
    // 转灰度
    if (image1.channels() == 3) cv::cvtColor(image1, gray1, cv::COLOR_BGR2GRAY);
    else gray1 = image1.clone();

    if (image2.channels() == 3) cv::cvtColor(image2, gray2, cv::COLOR_BGR2GRAY);
    else gray2 = image2.clone();

    // 检测与计算
    detector->detectAndCompute(gray1, cv::noArray(), kpt1, des1);
    detector->detectAndCompute(gray2, cv::noArray(), kpt2, des2);
}

void FeatureMatcher::Match() {
    if (des1.empty() || des2.empty()) return;

    cv::BFMatcher matcher(cv::NORM_L2); // SIFT 使用 L2 范数
    std::vector<std::vector<cv::DMatch>> knn_matches;

    // k=2 近邻匹配
    matcher.knnMatch(des1, des2, knn_matches, 2);

    // Lowe's Ratio Test
    good_matches.clear();
    for (const auto& m_n : knn_matches) {
        if (m_n.size() < 2) continue;
        const auto& m = m_n[0];
        const auto& n = m_n[1];

        if (m.distance < n.distance * good_ratio) {
            good_matches.push_back(m);
        }
    }
}

void FeatureMatcher::SelectGoodMatches(std::vector<cv::Point2f>& pts1, std::vector<cv::Point2f>& pts2) {
    pts1.clear();
    pts2.clear();
    for (const auto& match : good_matches) {
        // queryIdx 对应 image1 (kpt1), trainIdx 对应 image2 (kpt2)
        pts1.push_back(kpt1[match.queryIdx].pt);
        pts2.push_back(kpt2[match.trainIdx].pt);
    }
}

void FeatureMatcher::Run(const cv::Mat& image1, const cv::Mat& image2,
                         std::vector<cv::Point2f>& pts1,
                         std::vector<cv::Point2f>& pts2) {
    FeatureDetect(image1, image2);
    Match();
    SelectGoodMatches(pts1, pts2);
}
