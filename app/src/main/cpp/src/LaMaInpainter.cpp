//
// Created by 31830 on 2025/12/21.
//

#include "LaMaInpainter.h"
#include "Logger.h"
#include "Constants.h"

LaMaInpainter::LaMaInpainter() : env(ORT_LOGGING_LEVEL_WARNING, "LaMaInpainter") {}

bool LaMaInpainter::init(const std::string& modelPath, ExecutionProvider provider) {
    try {
        Ort::SessionOptions sessionOptions;

        // 1. 基础 CPU 设置
        sessionOptions.SetIntraOpNumThreads(4);
        sessionOptions.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);

        // 2. 根据参数配置加速器
        if (provider == EP_NNAPI) {
            LOGI("尝试启用 NNAPI 加速...");

            uint32_t nnapi_flags = 0;

            // 允许 NPU 使用 FP16 (半精度) 计算
            nnapi_flags |= NNAPI_FLAG_USE_FP16;

            // 不要禁用 CPU 回退 (DO NOT set CPU_DISABLED)
            // 因为 LaMa 模型包含 FFT (傅里叶变换) 算子，目前大部分 NPU 不支持。
            // 如果不允许回退，模型加载会直接崩溃。
            // nnapi_flags |= NNAPI_FLAG_CPU_DISABLED; // <--- 绝对不要解开这行

            // 调用 C API 附加 NNAPI 提供者
            // Ort::SessionOptions 在 C++ 封装中可以自动转换为 C 的 OrtSessionOptions*
            OrtStatus* status = OrtSessionOptionsAppendExecutionProvider_Nnapi(sessionOptions, nnapi_flags);

            if (status != nullptr) {
                const char* msg = Ort::GetApi().GetErrorMessage(status);

                LOGE("NNAPI 启用失败 (将自动回退到 CPU): %s", msg);
                Ort::GetApi().ReleaseStatus(status);
            } else {
                LOGI("NNAPI 提供者已成功添加");
            }
        } else {
            LOGI("使用默认 CPU 推理 (XNNPACK)");
            // ONNX Runtime Android 版默认已经开启了 XNNPACK 优化
        }

        // 3. 创建 Session
        session = std::make_unique<Ort::Session>(env, modelPath.c_str(), sessionOptions);

        // 4. 节点名称配置 (保持不变)
        static const char* in_names[] = {"image", "mask"};
        static const char* out_names[] = {"output"};
        input_names = {in_names[0], in_names[1]};
        output_names = {out_names[0]};

        LOGI("LaMa 模型加载成功");
        return true;
    } catch (const std::exception& e) {
        LOGE("LaMa 模型加载失败: %s", e.what());
        return false;
    }
}

cv::Mat LaMaInpainter::process(const cv::Mat& inputImg) {
    if (!session || inputImg.empty()) return {};

    // 1. 生成 Mask (识别黑边)
    cv::Mat mask = createBlackBorderMask(inputImg);

    // 2. 预处理 (转为 Tensor 数据)
    std::vector<float> input_img_data;
    std::vector<float> input_mask_data;
    MetaInfo meta = {};

    preprocess(inputImg, mask, input_img_data, input_mask_data, meta);

    // 3. 构造 ONNX Tensor
    auto memory_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
    std::vector<int64_t> input_shape = {1, 3, Constants::MODEL_INPUT_SIZE, Constants::MODEL_INPUT_SIZE}; // [B, C, H, W]
    std::vector<int64_t> mask_shape = {1, 1, Constants::MODEL_INPUT_SIZE, Constants::MODEL_INPUT_SIZE};  // [B, C, H, W]

    std::vector<Ort::Value> input_tensors;
    input_tensors.push_back(Ort::Value::CreateTensor<float>(
            memory_info, input_img_data.data(), input_img_data.size(), input_shape.data(), input_shape.size()));

    input_tensors.push_back(Ort::Value::CreateTensor<float>(
            memory_info, input_mask_data.data(), input_mask_data.size(), mask_shape.data(), mask_shape.size()));

    // 4. 执行推理
    try {
        auto output_tensors = session->Run(
                Ort::RunOptions{nullptr},
                input_names.data(),
                input_tensors.data(),
                input_tensors.size(),
                output_names.data(),
                output_names.size()
        );

        // 5. 获取结果数据
        auto* raw_output = output_tensors[0].GetTensorMutableData<float>();
        size_t output_len = output_tensors[0].GetTensorTypeAndShapeInfo().GetElementCount();
        std::vector<float> output_data(raw_output, raw_output + output_len);

        // 6. 后处理
        return postprocess(output_data, inputImg, mask, meta);

    } catch (const std::exception& e) {
        LOGE("推理执行错误: %s", e.what());
        return {};
    }
}

// ============================================================================
// 私有辅助函数实现
// ============================================================================

cv::Mat LaMaInpainter::createBlackBorderMask(const cv::Mat& img) {
    int h = img.rows;
    int w = img.cols;

    cv::Mat gray;
    cv::cvtColor(img, gray, cv::COLOR_BGR2GRAY);

    // FloodFill 需要大 2 像素的 mask
    cv::Mat mask = cv::Mat::zeros(h + 2, w + 2, CV_8UC1);

    // 4个角落作为种子点
    std::vector<cv::Point> seeds = {{0, 0}, {w - 1, 0}, {0, h - 1}, {w - 1, h - 1}};

    for (const auto& seed : seeds) {
        if (gray.at<uchar>(seed) <= Constants::BLACK_THRESHOLD) {
            cv::floodFill(gray, mask, seed, cv::Scalar(255),
                          nullptr, cv::Scalar(Constants::BLACK_THRESHOLD), cv::Scalar(Constants::BLACK_THRESHOLD),
                          cv::FLOODFILL_MASK_ONLY | (255 << 8));
        }
    }

    // 裁剪回原图大小
    cv::Mat result = mask(cv::Rect(1, 1, w, h)).clone();

    // 形态学操作：闭运算 + 膨胀
    cv::Mat kernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(5, 5));
    cv::morphologyEx(result, result, cv::MORPH_CLOSE, kernel);
    cv::dilate(result, result, kernel, cv::Point(-1, -1), 2);

    return result;
}

void LaMaInpainter::preprocess(const cv::Mat& image, const cv::Mat& mask,
                               std::vector<float>& out_img_chw,
                               std::vector<float>& out_mask_chw,
                               MetaInfo& meta) {
    meta.orig_w = image.cols;
    meta.orig_h = image.rows;

    // 1. 计算缩放比例 (Letterbox)
    float scale = std::min((float)Constants::MODEL_INPUT_SIZE / float(meta.orig_w), (float)Constants::MODEL_INPUT_SIZE / float(meta.orig_h));
    meta.scale = scale;
    meta.new_w = int(float(meta.orig_w) * scale);
    meta.new_h = int(float(meta.orig_h) * scale);

    // 2. Resize
    cv::Mat img_resized, mask_resized;
    cv::resize(image, img_resized, cv::Size(meta.new_w, meta.new_h), 0, 0, cv::INTER_AREA);
    cv::resize(mask, mask_resized, cv::Size(meta.new_w, meta.new_h), 0, 0, cv::INTER_NEAREST);

    // 3. Padding (创建 512x512 画布)
    cv::Mat pad_img(Constants::MODEL_INPUT_SIZE, Constants::MODEL_INPUT_SIZE, CV_8UC3, cv::Scalar(127, 127, 127));
    cv::Mat pad_mask(Constants::MODEL_INPUT_SIZE, Constants::MODEL_INPUT_SIZE, CV_8UC1, cv::Scalar(255)); // 默认全Mask

    meta.pad_top = (Constants::MODEL_INPUT_SIZE - meta.new_h) / 2;
    meta.pad_left = (Constants::MODEL_INPUT_SIZE - meta.new_w) / 2;

    cv::Rect roi(meta.pad_left, meta.pad_top, meta.new_w, meta.new_h);

    // 拷贝 Resize 后的图到画布中心
    img_resized.copyTo(pad_img(roi));

    // 处理 Mask：ROI 区域先清零，然后把 mask_resized 放进去
    pad_mask(roi).setTo(0);
    cv::Mat mask_roi = pad_mask(roi);
    cv::bitwise_or(mask_roi, mask_resized, mask_roi); // 合并原图的黑边Mask

    // 4. 归一化 (0-255 -> 0.0-1.0)
    cv::Mat img_float, mask_float;
    pad_img.convertTo(img_float, CV_32FC3, 1.0 / 255.0);

    // Mask 二值化为 0.0 或 1.0
    cv::threshold(pad_mask, mask_float, 127, 1.0, cv::THRESH_BINARY);
    mask_float.convertTo(mask_float, CV_32FC1);

    // 5. HWC -> CHW 并存入 vector
    hwc_to_chw(img_float, out_img_chw);
    // Mask 是单通道，直接把它展平就是 CHW (C=1)
    if (mask_float.isContinuous()) {
        out_mask_chw.assign((float*)mask_float.datastart, (float*)mask_float.dataend);
    } else {
        // 如果不连续，clone 一份连续的再拷贝
        cv::Mat continuous_mask = mask_float.clone();
        out_mask_chw.assign((float*)continuous_mask.datastart, (float*)continuous_mask.dataend);
    }
}

void LaMaInpainter::hwc_to_chw(const cv::Mat& src, std::vector<float>& dst) {
    dst.clear();
    dst.reserve(src.total() * src.channels());
    std::vector<cv::Mat> channels;
    cv::split(src, channels);
    for (auto& ch : channels) {
        // 假设 ch 是连续的，如果不连续需要处理
        cv::Mat cont_ch = ch.isContinuous() ? ch : ch.clone();
        dst.insert(dst.end(), (float*)cont_ch.datastart, (float*)cont_ch.dataend);
    }
}

cv::Mat LaMaInpainter::postprocess(const std::vector<float>& output_tensor_data,
                                   const cv::Mat& orig_img,
                                   const cv::Mat& orig_mask,
                                   const MetaInfo& meta) {
    // 1. CHW -> HWC
    // output_tensor_data 是 3x512x512
    int pixels_per_channel = Constants::MODEL_INPUT_SIZE * Constants::MODEL_INPUT_SIZE;
    std::vector<cv::Mat> channels(3);
    auto* data_ptr = const_cast<float*>(output_tensor_data.data());

    channels[0] = cv::Mat(Constants::MODEL_INPUT_SIZE, Constants::MODEL_INPUT_SIZE, CV_32FC1, data_ptr);
    channels[1] = cv::Mat(Constants::MODEL_INPUT_SIZE, Constants::MODEL_INPUT_SIZE, CV_32FC1, data_ptr + pixels_per_channel);
    channels[2] = cv::Mat(Constants::MODEL_INPUT_SIZE, Constants::MODEL_INPUT_SIZE, CV_32FC1, data_ptr + pixels_per_channel * 2);

    cv::Mat pred_img_float;
    cv::merge(channels, pred_img_float); // 合并为 HWC float

    // 2. 转回 8位 (0-255)
    // 假设模型输出在 0-1 之间，需乘 255。如果模型输出本身是 0-255，则去掉 alpha
    // 大部分 LaMa 导出的是 0-255 范围的 float，或者做了 clip
    cv::Mat pred_img_8u;
    pred_img_float.convertTo(pred_img_8u, CV_8UC3); // 自动 saturate_cast

    // 3. 裁剪 (Remove Padding)
    cv::Rect roi(meta.pad_left, meta.pad_top, meta.new_w, meta.new_h);
    cv::Mat valid_region = pred_img_8u(roi);

    // 4. Resize 回原图尺寸
    cv::Mat final_pred;
    cv::resize(valid_region, final_pred, cv::Size(meta.orig_w, meta.orig_h), 0, 0, cv::INTER_LANCZOS4);

    // 5. 融合 (Blend)
    // 将原图 Mask 归一化用于 alpha blending
    cv::Mat mask_float;
    orig_mask.convertTo(mask_float, CV_32FC1, 1.0 / 255.0);
    cv::Mat mask_3c;
    cv::cvtColor(mask_float, mask_3c, cv::COLOR_GRAY2BGR);

    cv::Mat orig_float, pred_float_final;
    orig_img.convertTo(orig_float, CV_32FC3);
    final_pred.convertTo(pred_float_final, CV_32FC3);

    // 公式: Result = Original * (1 - Mask) + Predicted * Mask
    // 意思：保留原图中 Mask 为 0 (黑色) 的部分，用预测图填充 Mask 为 1 (白色) 的部分
    cv::Mat result_float = orig_float.mul(cv::Scalar(1.0, 1.0, 1.0) - mask_3c) + pred_float_final.mul(mask_3c);

    cv::Mat result;
    result_float.convertTo(result, CV_8UC3);

    return result;
}
