//
// Created by 31830 on 2025/12/19.
//

#ifndef PANORAMAPRO_CONSTANTS_H
#define PANORAMAPRO_CONSTANTS_H
#include <string>

namespace Constants {
    // 图像处理参数
    constexpr int MESH_SIZE = 100;
    constexpr int IMAGE_MAX_SIZE = 700 * 700;

    // RANSAC 参数
    constexpr int MAX_ITERATION = 500;
    constexpr double RANSAC_THRESHOLD = 30.0;

    // APAP 参数
    constexpr double GAMMA = 0.1;
    constexpr double SIGMA = 8.5;

    // 融合参数
    constexpr int BLEND_WIDTH = 8;

    // 聚类数量
    constexpr int NUM_CLUSTERS = 2;

    constexpr int INPAINT_RADIUS = 3;

    constexpr int MODEL_INPUT_SIZE = 512;
    constexpr int BLACK_THRESHOLD = 2;
}
#endif //PANORAMAPRO_CONSTANTS_H
