//
// Created by 31830 on 2025/12/19.
//

#include "APAP.h"
#include "Utils.h"
#include "Logger.h"
#include "Constants.h"
#include "FeatureMatcher.h"
#include <iostream>
#include <algorithm>

static Eigen::Matrix3d GetConditioner(const std::vector<Eigen::Vector2d>& points) {
    // 计算均值和标准差
    Eigen::Vector2d mean = Eigen::Vector2d::Zero();
    for (const auto& p : points) mean += p;
    mean /= (double)points.size();

    Eigen::Vector2d std_dev = Eigen::Vector2d::Zero();
    for (const auto& p : points) {
        std_dev += (p - mean).cwiseAbs2(); // (x-mean)^2
    }
    std_dev = (std_dev / (points.size() - 1)).cwiseSqrt(); // Sample std dev

    // 防止除以零
    double std_x = std_dev.x() + (std_dev.x() == 0 ? 1.0 : 0.0);
    double std_y = std_dev.y() + (std_dev.y() == 0 ? 1.0 : 0.0);

    double norm_x = std::sqrt(2.0) / std_x;
    double norm_y = std::sqrt(2.0) / std_y;

    Eigen::Matrix3d T;
    T << norm_x, 0, -norm_x * mean.x(),
            0, norm_y, -norm_y * mean.y(),
            0, 0, 1;

    return T;
}

static std::vector<Eigen::Vector2d> Condition2d(const Eigen::Matrix3d& C, const std::vector<Eigen::Vector2d>& points) {
    std::vector<Eigen::Vector2d> conditioned_pts;
    conditioned_pts.reserve(points.size());
    for (const auto& p : points) {
        Eigen::Vector3d vec(p.x(), p.y(), 1.0);
        Eigen::Vector3d res = C * vec;
        conditioned_pts.emplace_back(res.x(), res.y()); // res.z() should be 1
    }
    return conditioned_pts;
}

static Eigen::MatrixXd ConstructA(const std::vector<Eigen::Vector2d>& src, const std::vector<Eigen::Vector2d>& dst) {
    int n = (int)src.size();
    Eigen::MatrixXd A = Eigen::MatrixXd::Zero(2 * n, 9);

    for (int i = 0; i < n; ++i) {
        double x = src[i].x();
        double y = src[i].y();
        double xp = dst[i].x();
        double yp = dst[i].y();

        // Row 2*i:   [0, 0, 0, -x, -y, -1, y'*x, y'*y, y']
        // Row 2*i+1: [x, y, 1, 0, 0, 0, -x'*x, -x'*y, -x']

        // Row 1 (u 对应 x')
        A(2 * i, 0) = x;
        A(2 * i, 1) = y;
        A(2 * i, 2) = 1;
        A(2 * i, 6) = -x * xp;
        A(2 * i, 7) = -y * xp;
        A(2 * i, 8) = -xp;

        // Row 2 (v 对应 y')
        A(2 * i + 1, 3) = x;
        A(2 * i + 1, 4) = y;
        A(2 * i + 1, 5) = 1;
        A(2 * i + 1, 6) = -x * yp;
        A(2 * i + 1, 7) = -y * yp;
        A(2 * i + 1, 8) = -yp;
    }
    return A;
}

// 静态辅助函数：线性加权融合 (Linear Blending / Feathering)
// 输入：两张尺寸相同的图片（背景为黑色）
// 输出：融合后的图片
static cv::Mat BlendImages(const cv::Mat& img1, const cv::Mat& img2) {
    CV_Assert(img1.size() == img2.size() && img1.type() == img2.type());
    int h = img1.rows;
    int w = img1.cols;

    // 1. 生成掩码 (Mask) - 识别非黑区域
    cv::Mat mask1, mask2;
    cv::cvtColor(img1, mask1, cv::COLOR_BGR2GRAY);
    cv::threshold(mask1, mask1, 0, 255, cv::THRESH_BINARY);

    cv::cvtColor(img2, mask2, cv::COLOR_BGR2GRAY);
    cv::threshold(mask2, mask2, 0, 255, cv::THRESH_BINARY);

    // 2. 计算权重 (距离变换 Distance Transform)
    // 像素距离边缘越远，权重越大。这能保证图像中心清晰，边缘平滑过渡。
    cv::Mat weight1, weight2;
    cv::distanceTransform(mask1, weight1, cv::DIST_L2, 3);
    cv::distanceTransform(mask2, weight2, cv::DIST_L2, 3);

    // 3. 并行加权融合
    cv::Mat result = cv::Mat::zeros(h, w, CV_8UC3);

    cv::parallel_for_(cv::Range(0, h), [&](const cv::Range& range) {
        for (int y = range.start; y < range.end; ++y) {
            const auto p1 = img1.ptr<cv::Vec3b>(y);
            const auto p2 = img2.ptr<cv::Vec3b>(y);
            const float* w1 = weight1.ptr<float>(y);
            const float* w2 = weight2.ptr<float>(y);
            auto dst = result.ptr<cv::Vec3b>(y);

            for (int x = 0; x < w; ++x) {
                float val1 = w1[x];
                float val2 = w2[x];
                float sum = val1 + val2;

                if (sum > 1e-5) { // 避免除以零
                    float alpha = val1 / sum;
                    float beta = val2 / sum;

                    // 线性混合: pixel = p1 * w1% + p2 * w2%
                    dst[x][0] = (uchar)(float(p1[x][0]) * alpha + float(p2[x][0]) * beta);
                    dst[x][1] = (uchar)(float(p1[x][1]) * alpha + float(p2[x][1]) * beta);
                    dst[x][2] = (uchar)(float(p1[x][2]) * alpha + float(p2[x][2]) * beta);
                }
                // else: sum == 0, 保持黑色背景
            }
        }
    });

    return result;
}

std::vector<Eigen::Matrix3d> APAP::LocalHomography(
        const std::vector<cv::Point2f>& src_pts_cv,
        const std::vector<cv::Point2f>& dst_pts_cv,
        const std::vector<cv::Point2f>& mesh_vertices,
        int mesh_cols, int mesh_rows)
{
    // 1. Hartley Normalization
    std::vector<cv::Point2f> norm_pts1_cv, norm_pts2_cv;
    Eigen::Matrix3d N1, N2;
    Utils::Normalise2dPts(src_pts_cv, norm_pts1_cv, N1);
    Utils::Normalise2dPts(dst_pts_cv, norm_pts2_cv, N2);

    auto norm_pts1 = Utils::CV2Eigen(norm_pts1_cv);
    auto norm_pts2 = Utils::CV2Eigen(norm_pts2_cv);

    // 2. Conditioning (APAP 特有的二次归一化)
    Eigen::Matrix3d C1 = GetConditioner(norm_pts1);
    Eigen::Matrix3d C2 = GetConditioner(norm_pts2);

    auto cond_pts1 = Condition2d(C1, norm_pts1);
    auto cond_pts2 = Condition2d(C2, norm_pts2);

    // 3. 构建基础矩阵 A
    Eigen::MatrixXd A = ConstructA(cond_pts1, cond_pts2);
    int num_points = (int)src_pts_cv.size();

    // 预计算逆矩阵以便还原
    // H_final = inv(N2) * inv(C2) * H_local * C1 * N1
    Eigen::Matrix3d T_left = N2.inverse() * C2.inverse();
    Eigen::Matrix3d T_right = C1 * N1;

    std::vector<Eigen::Matrix3d> local_homographies;
    local_homographies.resize(mesh_rows * mesh_cols);

    double inv_sigma_sq = 1.0 / (Constants::SIGMA * Constants::SIGMA);
    int total_meshes = mesh_rows * mesh_cols;

    // =========================================================
    // [修改] 添加 OpenMP 并行指令
    // schedule(dynamic) 通常比 static 更好，因为 SVD 收敛时间可能不同
    // =========================================================
#pragma omp parallel for schedule(dynamic)
    for (int i = 0; i < total_meshes; ++i) {
        // [注意] 以下所有变量都在循环内部声明，因此是线程私有的（Thread-local）
        // 这对于 OpenMP 至关重要，否则数据会混乱

        cv::Point2f mv = mesh_vertices[i];

        // 计算权重
        Eigen::VectorXd weights(num_points);
        for (int k = 0; k < num_points; ++k) {
            double dx = mv.x - src_pts_cv[k].x;
            double dy = mv.y - src_pts_cv[k].y;
            double dist_sq = dx * dx + dy * dy;
            double w = std::exp(-dist_sq * inv_sigma_sq);
            weights(k) = std::max(Constants::GAMMA, w);
        }

        // 构建加权矩阵 WA
        Eigen::MatrixXd WA(2 * num_points, 9);
        for (int k = 0; k < num_points; ++k) {
            WA.row(2 * k) = A.row(2 * k) * weights(k);
            WA.row(2 * k + 1) = A.row(2 * k + 1) * weights(k);
        }

        // SVD 分解
        // 每个线程拥有自己独立的 svd 对象
        Eigen::JacobiSVD<Eigen::MatrixXd> svd(WA, Eigen::ComputeThinV);
        Eigen::VectorXd h_vec = svd.matrixV().col(8);

        Eigen::Matrix3d H_cond;
        H_cond << h_vec(0), h_vec(1), h_vec(2),
                h_vec(3), h_vec(4), h_vec(5),
                h_vec(6), h_vec(7), h_vec(8);

        // 还原 H
        // T_left, T_right 是只读的，可以共享
        Eigen::Matrix3d H = T_left * H_cond * T_right;

        // 归一化
        if (std::abs(H(2, 2)) > 1e-8) {
            H /= H(2, 2);
        }

        // 写入结果 (无竞争，因为每个线程写不同的 i)
        local_homographies[i] = H;
    }

    return local_homographies;
}

cv::Mat APAP::LocalWarp(
        const cv::Mat& image,
        const std::vector<Eigen::Matrix3d>& local_homographies,
        int mesh_cols, int mesh_rows,
        cv::Size canvas_size,
        cv::Point2f offset)
{
    cv::Mat map_x(canvas_size, CV_32FC1);
    cv::Mat map_y(canvas_size, CV_32FC1);

    // 计算网格步长 (Mesh Step)
    double step_x = (double)canvas_size.width / mesh_cols;
    double step_y = (double)canvas_size.height / mesh_rows;

    // 并行计算映射表
    cv::parallel_for_(cv::Range(0, canvas_size.height), [&](const cv::Range& range) {
        for (int y = range.start; y < range.end; ++y) {

            // --- 下面是你原来的代码，完全不用动 ---
            auto ptr_x = map_x.ptr<float>(y);
            auto ptr_y = map_y.ptr<float>(y);

            for (int x = 0; x < canvas_size.width; ++x) {
                // ... 计算 idx_x, idx_y ...
                int idx_x = (int)(x / step_x);
                int idx_y = (int)(y / step_y);

                // ... 边界检查 ...
                if (idx_x >= mesh_cols) idx_x = mesh_cols - 1;
                if (idx_y >= mesh_rows) idx_y = mesh_rows - 1;
                if (idx_x < 0) idx_x = 0;
                if (idx_y < 0) idx_y = 0;

                int h_idx = idx_y * mesh_cols + idx_x;
                const Eigen::Matrix3d& H = local_homographies[h_idx];

                double ox = float(x) - offset.x;
                double oy = float(y) - offset.y;

                double w = H(2, 0) * ox + H(2, 1) * oy + H(2, 2);
                // 避免除以 0 的保护 (可选)
                double w_inv = (w != 0) ? 1.0 / w : 0;

                double u = (H(0, 0) * ox + H(0, 1) * oy + H(0, 2)) * w_inv;
                double v = (H(1, 0) * ox + H(1, 1) * oy + H(1, 2)) * w_inv;

                ptr_x[x] = (float)u;
                ptr_y[x] = (float)v;
            }
        }
    });

    cv::Mat warped;
    // 使用线性插值进行重映射，边界填充黑色
    cv::remap(image, warped, map_x, map_y, cv::INTER_LINEAR, cv::BORDER_CONSTANT, cv::Scalar(0, 0, 0));

    return warped;
}

bool APAP::Load_image(std::vector<cv::Mat> &&_images) {
    if (_images.empty() || _images.size() < 2) {
        LOGE("APAP::load_image - 输入图像数量不足");
        return false;
    }
    this->images = std::move(_images);
    return true;
}

cv::Mat APAP::Stitching(bool enable_linear_blending) {
    if (images.size() < 2) {
        LOGE("APAP::Stitching - 图像数量不足");
        return {};
    }

    LOGI("APAP::Stitching - 开始拼接 %zu 张图像", images.size());
    LOGI("是否启用线性融合: %s", enable_linear_blending ? "是" : "否");

    // 1. 调整图片大小
    for (auto &img : images) {
        Utils::ResizeImageIfTooLarge(img, Constants::IMAGE_MAX_SIZE);
    }

    cv::Mat canvas = images[0].clone();

    for (size_t i = 1; i < images.size(); ++i) {
        cv::Mat img1 = canvas;
        cv::Mat img2 = images[i];

        // 2. 特征匹配
        std::vector<cv::Point2f> pts1, pts2;
        FeatureMatcher matcher;

        matcher.Run(img1, img2, pts1, pts2);
        LOGI("匹配点数量: %zu", pts1.size());
        if (pts1.size() < 8 || pts2.size() < 8) {
            LOGE("APAP::Stitching - 匹配点数量不足，无法进行拼接");
            return {};
        }

        // 3. 计算全局单应性 (RANSAC) 用于确定画布大小
        std::vector<uchar> status;
        cv::Mat H_global = cv::findHomography(pts2, pts1, cv::RANSAC, Constants::RANSAC_THRESHOLD, status);

        // 筛选内点用于 APAP
        std::vector<cv::Point2f> final_pts1, final_pts2;
        for (size_t j = 0; j < status.size(); j++) {
            if (status[j]) {
                final_pts1.push_back(pts1[j]);
                final_pts2.push_back(pts2[j]);
            }
        }

        // 4. 计算新画布的边界 (Bounding Box)
        // new_img 的四个角变换到 canvas 坐标系
        std::vector<cv::Point2f> corners2(4);
        corners2[0] = cv::Point2f(0, 0);
        corners2[1] = cv::Point2f((float)img2.cols, 0);
        corners2[2] = cv::Point2f((float)img2.cols, (float)img2.rows);
        corners2[3] = cv::Point2f(0, (float)img2.rows);

        std::vector<cv::Point2f> proj_corners;
        cv::perspectiveTransform(corners2, proj_corners, H_global);

        // img1 自身的四个角 (0,0) -> (w,h)
        // 找出所有点中的 min_x, min_y, max_x, max_y
        double min_x = 0, min_y = 0;
        double max_x = img1.cols, max_y = img1.rows;

        for (const auto& p : proj_corners) {
            if (p.x < min_x) min_x = p.x;
            if (p.y < min_y) min_y = p.y;
            if (p.x > max_x) max_x = p.x;
            if (p.y > max_y) max_y = p.y;
        }

        // 计算偏移量 (如果向左/上扩展，偏移量为正，用于将所有像素移回正坐标)
        cv::Point2f offset(0.0f, 0.0f);
        if (min_x < 0) offset.x = (float)-min_x;
        if (min_y < 0) offset.y = (float)-min_y;

        int new_w = cvRound(max_x - min_x);
        int new_h = cvRound(max_y - min_y);

        // 限制一下最大尺寸，防止内存爆炸
        if (new_w * new_h > 5000 * 5000) {
            LOGE("APAP::Stitching - 计算出的画布尺寸过大");
            return {};
        }

        // 5. APAP 计算
        // 生成覆盖整个新画布的网格
        int mesh_cols = Constants::MESH_SIZE;
        int mesh_rows = Constants::MESH_SIZE;
        std::vector<cv::Point2f> mesh_vertices = Utils::GetMeshVertices(new_w, new_h, mesh_cols, mesh_rows, offset.x, offset.y);

        std::vector<Eigen::Matrix3d> local_Hs = LocalHomography(
                final_pts1, final_pts2,
                mesh_vertices,
                mesh_cols, mesh_rows);

        // 6. APAP Warp (新图片)
        // 将 new_img 变形并放置到新画布大小中 (考虑 offset)
        cv::Mat warped_new = LocalWarp(img2, local_Hs, mesh_cols, mesh_rows, cv::Size(new_w, new_h), offset);

        // 7. 放置旧 Canvas
        // 创建最终的大画布
        cv::Mat final_canvas = cv::Mat::zeros(new_h, new_w, CV_8UC3);

        // 将 base_canvas 拷贝到 final_canvas 的对应位置 (应用 offset)
        cv::Mat roi = final_canvas(cv::Rect(cvRound(offset.x), cvRound(offset.y), img1.cols, img1.rows));
        img1.copyTo(roi);

        // 8. 融合 (Blending)
        if (enable_linear_blending) {
            // 线性加权融合
            canvas = BlendImages(final_canvas, warped_new);
            continue;
        }
        // 简单的最大值融合
        // 实际项目中应使用 Multi-band Blending 或 Seam Carving
        cv::Mat mask_new;
        // 生成 warped_new 的掩码 (非黑区域)
        cv::cvtColor(warped_new, mask_new, cv::COLOR_BGR2GRAY);
        cv::threshold(mask_new, mask_new, 1, 255, cv::THRESH_BINARY);

        // 将 warped_new 拷贝到 final_canvas (覆盖模式，或加权模式)
        // 这里使用简单的加权：重叠区域取平均，非重叠区域直接拷贝
        cv::max(final_canvas, warped_new, canvas);
    }

    return canvas;
}
