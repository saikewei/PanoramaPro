package com.example.panoramapro.core;

import android.graphics.Bitmap;

public class NoOpCompleter implements IImageCompleter{
    @Override
    public Bitmap complete(Bitmap roughPanorama) {
        return roughPanorama;
    }
}
