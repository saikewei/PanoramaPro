package com.example.panoramapro.core;

import android.graphics.Bitmap;
import java.util.List;

public interface IStitchingEngine {
    /**
     * 执行全景拼接流程
     * @param inputImages 输入的一组照片
     * @return 拼接好的全景图
     */
    Bitmap stitchImages(List<Bitmap> inputImages, boolean enableLinearBlending);

    /**
     * 自动补全边缘 (Inpainting)
     * @param stitchedImage 拼接后但不规则的图
     * @return 补全后的矩形图
     */
    Bitmap autoCompleteEdges(Bitmap stitchedImage);
}
