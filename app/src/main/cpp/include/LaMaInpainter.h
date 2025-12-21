//
// Created by 31830 on 2025/12/21.
//

#ifndef PANORAMAPRO_LAMAINPAINTER_H
#define PANORAMAPRO_LAMAINPAINTER_H

#include <string>
#include <vector>
#include <memory>
#include <opencv2/opencv.hpp>
#include "onnxruntime_cxx_api.h"
#include "nnapi_provider_factory.h"

class LaMaInpainter {
public:
    LaMaInpainter();
    ~LaMaInpainter() = default;

    // 定义加速模式枚举
    enum ExecutionProvider {
        EP_CPU = 0,    // 默认 CPU (通常包含 XNNPACK 优化)
        EP_NNAPI = 1   // Android NPU 加速
    };

    /**
     * 初始化模型
     * @param modelPath 模型文件路径
     * @return 是否初始化成功
     */
    bool init(const std::string& modelPath, ExecutionProvider provider);

    /**
     * 执行完整的补全流程
     * @param inputImage 原始输入图像 (BGR)
     * @return 补全后的图像 (BGR)
     */
    cv::Mat process(const cv::Mat& inputImage);

private:
    // --- ONNX Runtime 相关变量 ---
    Ort::Env env;
    std::unique_ptr<Ort::Session> session;
    std::vector<const char*> input_names;
    std::vector<const char*> output_names;

    // --- 内部辅助结构体：记录预处理时的缩放信息 ---
    struct MetaInfo {
        int orig_w, orig_h; // 原图尺寸
        int new_w, new_h;   // 缩放后尺寸 (不含 padding)
        int pad_top, pad_left; // Padding 的偏移量
        float scale;
    };

    // --- 内部图像处理函数 (原 ImageProcessor 逻辑) ---

    // 1. 生成黑色边缘的 Mask
    static cv::Mat createBlackBorderMask(const cv::Mat& img);

    // 2. Letterbox 预处理 (Resize + Pad + Normalize)
    // 输出: img_float_tensor, mask_float_tensor (CHW格式), 以及 MetaInfo
    static void preprocess(const cv::Mat& image, const cv::Mat& mask,
                    std::vector<float>& out_img_chw,
                    std::vector<float>& out_mask_chw,
                    MetaInfo& meta);

    // 3. 后处理 (Tensor -> Mat -> Crop -> Resize -> Blend)
    static cv::Mat postprocess(const std::vector<float>& output_tensor_data,
                        const cv::Mat& orig_img,
                        const cv::Mat& orig_mask,
                        const MetaInfo& meta);

    // HWC (OpenCV) 转 CHW (ONNX)
    static void hwc_to_chw(const cv::Mat& src, std::vector<float>& dst);
};


#endif //PANORAMAPRO_LAMAINPAINTER_H
