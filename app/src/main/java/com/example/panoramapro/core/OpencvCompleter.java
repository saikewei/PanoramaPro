package com.example.panoramapro.core;

import android.graphics.Bitmap;

public class OpencvCompleter implements IImageCompleter{
    static {
        System.loadLibrary("panoramapro");
    }

    @Override
    public Bitmap complete(Bitmap roughPanorama) {
        return nativeCompleteImage(roughPanorama);
    }

    private native Bitmap nativeCompleteImage(Bitmap roughPanorama);
}
