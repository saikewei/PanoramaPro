package com.example.panoramapro.core;

import android.graphics.Bitmap;

public class TinyPlanetProcessor implements IImageCompleter {

    private float scale = 1.0f;
    private int outputSize = 1024;

    public void setScale(float scale) {
        this.scale = Math.max(0.1f, scale);
    }

    public void setOutputSize(int size) {
        this.outputSize = size;
    }

    @Override
    public Bitmap complete(Bitmap roughPanorama) {
        if (roughPanorama == null) return null;

        // 预分配输出内存
        Bitmap output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888);

        // 调用 Native 层直接填充像素
        nativeProcessTinyPlanet(roughPanorama, output, scale);

        return output;
    }

    // 对应 C++: (JNIEnv*, jobject, jobject, jobject, jfloat)
    private native void nativeProcessTinyPlanet(Bitmap input, Bitmap output, float scale);

    static {
        System.loadLibrary("panoramapro");
    }

    @Override
    public void release() { }
}