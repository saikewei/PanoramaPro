package com.example.panoramapro.core;

public class StitchingEngine implements IStitchingEngine{
    static {
        System.loadLibrary("panoramapro");
    }

    @Override
    public android.graphics.Bitmap stitchImages(java.util.List<android.graphics.Bitmap> inputImages, boolean enableLinearBlending) {
        // 将 List 转换为数组传递给 native 层
        android.graphics.Bitmap[] bitmapArray = inputImages.toArray(new android.graphics.Bitmap[0]);
        return nativeStitchImages(bitmapArray, enableLinearBlending);
    }

    @Override
    public android.graphics.Bitmap autoCompleteEdges(android.graphics.Bitmap stitchedImage) {
        return null;
    }

    private native android.graphics.Bitmap nativeStitchImages(android.graphics.Bitmap[] inputImages, boolean enableLinearBlending);
}
