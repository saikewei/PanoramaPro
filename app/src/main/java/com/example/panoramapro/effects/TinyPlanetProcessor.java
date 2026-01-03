package com.example.panoramapro.effects;

import android.graphics.Bitmap;

public class TinyPlanetProcessor implements IEffectsProcessor {

    private float scale = 1.2f;
    private int outputSize = 1024;

    public void setScale(float scale) {
        this.scale = Math.max(0.5f, Math.min(3.0f, scale));
    }

    public void setOutputSize(int size) {
        this.outputSize = size;
    }

    @Override
    public Bitmap applyLittlePlanetEffect(Bitmap panorama) {
        if (panorama == null) return null;

        // 预分配输出内存
        Bitmap output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888);

        // 调用 Native 层直接填充像素
        nativeProcessTinyPlanet(panorama, output, scale);

        return output;
    }

    // 对应 C++: (JNIEnv*, jobject, jobject, jobject, jfloat)
    private native void nativeProcessTinyPlanet(Bitmap input, Bitmap output, float scale);

    static {
        System.loadLibrary("panoramapro");
    }
}