package com.example.panoramapro.effects;

import android.graphics.Bitmap;

public interface IEffectsProcessor {
    /**
     * 生成小行星效果
     * @param panorama 全景长图
     * @return 小行星视角的正方形图
     */
    Bitmap applyLittlePlanetEffect(Bitmap panorama);
}