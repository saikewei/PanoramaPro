package com.example.panoramapro.core;

import android.graphics.Bitmap;
import java.util.List;

public interface IStitcher {
    /**
     * @param inputImages 输入图片
     * @param enableLinearBlending 是否启用线性混合以减少接缝处的可见性
     * @return 拼接后的原始结果
     */
    Bitmap stitch(List<Bitmap> inputImages, boolean enableLinearBlending);
}