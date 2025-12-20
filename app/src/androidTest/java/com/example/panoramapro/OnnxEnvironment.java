package com.example.panoramapro;

public class OnnxEnvironment {
    static {
        // 加载你的主库 (它链接了 onnxruntime)
        System.loadLibrary("panoramapro");
    }

    /**
     * 尝试初始化 ONNX 环境并返回版本号
     * @return 成功返回版本号 (如 "1.17.1")，失败返回 "Error: ..."
     */
    public native String checkRuntime();
}