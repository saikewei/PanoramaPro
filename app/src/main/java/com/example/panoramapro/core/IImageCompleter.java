package com.example.panoramapro.core;

import android.graphics.Bitmap;

public interface IImageCompleter {
    /**
     * @param roughPanorama 拼接好的粗糙全景图
     * @return 边缘规整后的图片
     */
    Bitmap complete(Bitmap roughPanorama);
}
