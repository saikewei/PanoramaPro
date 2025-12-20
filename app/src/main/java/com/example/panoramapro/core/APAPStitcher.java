package com.example.panoramapro.core;

import android.graphics.Bitmap;
import java.util.List;

public class APAPStitcher implements IStitcher {
    static {
        System.loadLibrary("panoramapro");
    }

    @Override
    public Bitmap stitch(List<Bitmap> inputImages, boolean enableLinearBlending) {
        Bitmap[] bitmapArray = inputImages.toArray(new Bitmap[0]);
        return nativeStitchImages(bitmapArray, enableLinearBlending);
    }

    private native Bitmap nativeStitchImages(Bitmap[] inputImages, boolean enableLinearBlending);
}
