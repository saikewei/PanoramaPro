//
// Created by 31830 on 2025/12/31.
//

#include "SIFT.h"
#include "Utils.h"
#include "Logger.h"

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
        LOGE("[SIFT拼接] 至少需要两张图像才能进行拼接");
        return images.empty() ? cv::Mat() : images[0];
    }

    LOGI("[SIFT拼接] 开始进行基于光束法平差的全景拼接，图像数量：%zu",
         images.size());

    // 1. 创建 Stitcher（全景模式，适合旋转拍摄）
    cv::Ptr<cv::Stitcher> stitcher =
            cv::Stitcher::create(cv::Stitcher::PANORAMA);

    // =========================================================
    // 使用 SIFT 作为特征提取器（新版 OpenCV 写法）
    // =========================================================
    cv::Ptr<cv::SIFT> sift = cv::SIFT::create(
            0,     // 不限制特征点数量
            3,     // 每个 octave 的层数
            0.04,  // 对比度阈值
            10,    // 边缘阈值
            1.6    // 高斯模糊 sigma
    );
    stitcher->setFeaturesFinder(sift);

    LOGD("[SIFT拼接] 已配置 SIFT 特征提取器");

    // =========================================================
    // 配置 Bundle Adjustment（光束法平差，Ray 模型）
    // =========================================================
    stitcher->setBundleAdjuster(
            cv::makePtr<cv::detail::BundleAdjusterRay>());

    stitcher->setWaveCorrection(true);

    LOGD("[SIFT拼接] 已启用 Bundle Adjustment（Ray）与波形校正");

    // =========================================================
    // 配置融合器（Blender）
    // =========================================================
    if (enable_linear_blending) {
        stitcher->setBlender(
                cv::makePtr<cv::detail::MultiBandBlender>());
        LOGD("[SIFT拼接] 使用多频段融合（MultiBandBlender）");
    } else {
        stitcher->setBlender(
                cv::makePtr<cv::detail::FeatherBlender>());
        LOGD("[SIFT拼接] 使用羽化融合（FeatherBlender）");
    }

    // 2. 执行拼接
    cv::Mat pano;
    cv::Stitcher::Status status = stitcher->stitch(images, pano);

    // 3. 错误处理
    if (status != cv::Stitcher::OK) {
        LOGE("[SIFT拼接] 拼接失败，错误码：%d", static_cast<int>(status));

        switch (status) {
            case cv::Stitcher::ERR_NEED_MORE_IMGS:
                LOGE("[SIFT拼接] 特征点不足，无法完成图像匹配");
                break;

            case cv::Stitcher::ERR_HOMOGRAPHY_EST_FAIL:
                LOGE("[SIFT拼接] 单应性矩阵估计失败，可能是视角变化过大或匹配错误");
                break;

            case cv::Stitcher::ERR_CAMERA_PARAMS_ADJUST_FAIL:
                LOGE("[SIFT拼接] 相机参数优化失败（光束法平差未收敛）");
                break;

            default:
                LOGE("[SIFT拼接] 未知错误");
                break;
        }
        return {};
    }

    LOGI("[SIFT拼接] 拼接成功，输出图像尺寸：%d x %d",
         pano.cols, pano.rows);

    return pano;
}

//废弃
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