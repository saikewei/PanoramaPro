package com.example.panoramapro.core;

import android.graphics.Bitmap;
import android.util.Log;

public class LaMaCompleter implements IImageCompleter {
    private static final String TAG = "LaMaCompleter";

    // 加载 Native 库
    static {
        System.loadLibrary("panoramapro");
    }

    // 持有 C++ 对象的内存地址指针
    // 如果为 0，表示对象未初始化或已释放
    private long nativeHandle = 0;

    /**
     * 构造函数
     * @param modelPath 模型文件在手机文件系统中的绝对路径
     * (注意：ONNX Runtime 通常无法直接读取 assets 目录下的文件，需先拷贝到 files 目录)
     */
    public LaMaCompleter(String modelPath) {
        // 在构造时初始化 Native 引擎
        nativeHandle = nativeInit(modelPath);

        if (nativeHandle == 0) {
            Log.e(TAG, "LaMa Native 引擎初始化失败！请检查模型路径是否正确。");
        } else {
            Log.i(TAG, "LaMa Native 引擎初始化成功，Handle: " + nativeHandle);
        }
    }

    @Override
    public Bitmap complete(Bitmap roughPanorama) {
        // 安全检查
        if (nativeHandle == 0) {
            Log.e(TAG, "错误：尝试使用未初始化或已释放的引擎。");
            return null;
        }
        if (roughPanorama == null) {
            return null;
        }

        // 调用 Native 方法执行推理
        return nativeProcess(nativeHandle, roughPanorama);
    }

    /**
     * 不再使用时必须手动调用此方法释放 C++ 内存
     */
    @Override
    public void release() {
        if (nativeHandle != 0) {
            nativeRelease(nativeHandle);
            nativeHandle = 0;
            Log.i(TAG, "LaMa 引擎资源已释放");
        }
    }


    // 对应 C++: Java_com_example_panoramapro_core_LaMaCompleter_nativeInit
    private native long nativeInit(String modelPath);

    // 对应 C++: Java_com_example_panoramapro_core_LaMaCompleter_nativeProcess
    private native Bitmap nativeProcess(long handle, Bitmap bitmap);

    // 对应 C++: Java_com_example_panoramapro_core_LaMaCompleter_nativeRelease
    private native void nativeRelease(long handle);

}
