//
// Created by 31830 on 2025/12/31.
//

#include "SIFT.h"
#include "Utils.h"

bool SIFT::Load_image(std::vector<cv::Mat>&& _images) {
    images = std::move(_images);
    if (images.empty()) {
        std::cerr << "[SIFTStitcher] No images loaded." << std::endl;
        return false;
    }
    return true;
}

cv::Mat SIFT::Stitching(bool enable_linear_blending) {
    if (images.size() < 2) {
        return images.empty() ? cv::Mat() : images[0];
    }

    // 简单的序列拼接策略：img1 + img2 -> result; result + img3 -> result...
    // 注意：对于高质量全景图，通常需要 Bundle Adjustment，但这里演示经典的逐对拼接
    cv::Mat result = images[0];

    for (size_t i = 1; i < images.size(); ++i) {
        std::cout << "[SIFTStitcher] Stitching image " << i << "..." << std::endl;
        cv::Mat temp_result = StitchTwoImages(result, images[i], enable_linear_blending);

        if (temp_result.empty()) {
            std::cerr << "[SIFTStitcher] Stitching failed at index " << i << std::endl;
            break;
        }
        result = temp_result;
    }

    return result;
}

cv::Mat SIFT::StitchTwoImages(const cv::Mat& img1, const cv::Mat& img2, bool enable_linear_blending) {
    // 1. SIFT 特征点检测与描述子提取
    cv::Ptr<cv::SIFT> sift = cv::SIFT::create();
    std::vector<cv::KeyPoint> kp1, kp2;
    cv::Mat des1, des2;

    sift->detectAndCompute(img1, cv::noArray(), kp1, des1);
    sift->detectAndCompute(img2, cv::noArray(), kp2, des2);

    if (kp1.size() < 10 || kp2.size() < 10) {
        std::cerr << "[SIFTStitcher] Not enough keypoints." << std::endl;
        return {};
    }

    // 2. 特征匹配 (使用 FLANN 或 BFMatcher)
    cv::BFMatcher matcher(cv::NORM_L2);
    std::vector<std::vector<cv::DMatch>> knn_matches;
    matcher.knnMatch(des1, des2, knn_matches, 2); // k=2 for ratio test

    // 3. Lowe's Ratio Test 过滤误匹配
    std::vector<cv::DMatch> good_matches;
    const float ratio_thresh = 0.75f;
    for (const auto& match : knn_matches) {
        if (match[0].distance < ratio_thresh * match[1].distance) {
            good_matches.push_back(match[0]);
        }
    }

    if (good_matches.size() < 4) {
        std::cerr << "[SIFTStitcher] Not enough matches to compute Homography." << std::endl;
        return {};
    }

    // 4. 获取匹配点坐标
    std::vector<cv::Point2f> pts1, pts2;
    for (const auto& m : good_matches) {
        pts1.push_back(kp1[m.queryIdx].pt);
        pts2.push_back(kp2[m.trainIdx].pt);
    }

    // 5. 计算单应性矩阵 (Homography)
    // 这里我们将 img2 变换到 img1 的视角 (img2 -> img1)
    cv::Mat H = cv::findHomography(pts2, pts1, cv::RANSAC, 4.0);
    if (H.empty()) return {};

    // 6. 计算画布大小 (Warping Canvas)
    // 需要计算变换后 img2 的四个角点，以确定全景图的大小
    std::vector<cv::Point2f> corners_img2(4);
    corners_img2[0] = cv::Point2f(0, 0);
    corners_img2[1] = cv::Point2f((float)img2.cols, 0);
    corners_img2[2] = cv::Point2f((float)img2.cols, (float)img2.rows);
    corners_img2[3] = cv::Point2f(0, (float)img2.rows);

    std::vector<cv::Point2f> corners_img2_trans;
    cv::perspectiveTransform(corners_img2, corners_img2_trans, H);

    // 获取 img1 和 变换后 img2 的整体边界
    float x_min = 0, y_min = 0;
    auto x_max = (float)img1.cols, y_max = (float)img1.rows;

    for (const auto& pt : corners_img2_trans) {
        if (pt.x < x_min) x_min = pt.x;
        if (pt.x > x_max) x_max = pt.x;
        if (pt.y < y_min) y_min = pt.y;
        if (pt.y > y_max) y_max = pt.y;
    }

    // 创建平移矩阵，如果由负坐标，需要将整体向右下平移
    cv::Mat translation = cv::Mat::eye(3, 3, CV_64F);
    if (x_min < 0) translation.at<double>(0, 2) = -x_min;
    if (y_min < 0) translation.at<double>(1, 2) = -y_min;

    cv::Mat H_final = translation * H;
    cv::Size output_size(cvRound(x_max - x_min), cvRound(y_max - y_min));

    // 7. 执行透视变换 (Warping)
    cv::Mat img2_warped;
    cv::warpPerspective(img2, img2_warped, H_final, output_size);

    // 8. 准备基准图画布 (Placing Base Image)

    cv::Mat img1_canvas = cv::Mat::zeros(output_size, CV_8UC3);

    double offset_x = (x_min < 0) ? -x_min : 0;
    double offset_y = (y_min < 0) ? -y_min : 0;

    // 定义 img1 放置的区域 (ROI)
    cv::Rect roi(cvRound(offset_x), cvRound(offset_y), img1.cols, img1.rows);

    // 将 img1 拷贝到画布对应位置
    img1.copyTo(img1_canvas(roi));

    // 9. 图像融合 (Blending)
    if (enable_linear_blending) {
        return Utils::BlendImages(img1_canvas, img2_warped);
    } else {
        // 简单融合策略：最大值法 (Max Blending)
        cv::Mat result;
        cv::max(img1_canvas, img2_warped, result);
        return result;
    }
}